package space.controlnet.ae2federation.storage.provenance;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.storage.IStorageService;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.me.service.helpers.CraftingServiceStorage;
import appeng.me.storage.NetworkStorage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.ae2.storage.NativeMountLedger;
import space.controlnet.ae2federation.ae2.storage.NativeStorageAliasProbe;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkIdentityService;

/**
 * The single source index for native export sources. Source identity and generation are derived from AE2's real mount
 * table ({@link NativeMountLedger}), not by replaying {@code IStorageProvider.mountInventories}.
 *
 * <p>A domain is rebuilt only when its validity stamp changes: Grid identity, the Grid's {@code StorageService}
 * instance, the service's native mount generation (bumped by every real AE2 mount/unmount, which also covers
 * priority changes and cell swaps because AE2 remounts for those), the activation state of provider nodes that had
 * mounts, or an AE2 delegate link used for alias proof. Checking the stamp is O(provider nodes + delegate links) field
 * reads; it never scans Grid nodes and never calls provider callbacks. The cache holds only identity, delegate
 * references and generations; quantities stay in AE2.
 */
public final class NativeSourceDomainRegistry {
    private final Map<OriginNetworkId, NativeSourceDomain> current = new HashMap<>();
    private final Map<OriginNetworkId, NativeSourceDomain> lastValid = new HashMap<>();
    private final Map<OriginNetworkId, Long> generations = new HashMap<>();
    private final Map<OriginNetworkId, CachedDiscovery> cache = new HashMap<>();
    private long discoveryRebuilds;
    private long cachedDiscoveries;
    private long providerScans;

    public NativeSourceDomain discover(IGrid grid) {
        var confirmed = FabricRegistryAccess.confirmedNetworkId(grid);
        if (confirmed.isEmpty()) {
            current.values().stream().filter(domain -> domain.runtimeGrid() == grid)
                    .map(NativeSourceDomain::origin).toList().forEach(this::invalidate);
            throw new ProvenanceException(ProvenanceDiagnostic.UNSETTLED_ORIGIN,
                    "Native source Grid has no settled NetworkId");
        }
        var origin = new OriginNetworkId(confirmed.orElseThrow());
        var service = grid.getStorageService();
        var cached = cache.get(origin);
        if (cached != null && cached.stamp().matches(grid, service)) {
            cachedDiscoveries++;
            if (cached.failure() != null) {
                throw new ProvenanceException(cached.failure(), cached.message());
            }
            if (current.get(origin) == cached.domain()) {
                return cached.domain();
            }
        }
        cache.remove(origin);
        discoveryRebuilds++;
        Stamp stamp = null;
        try {
            if (!NativeMountLedger.available(service)) {
                throw new ProvenanceException(ProvenanceDiagnostic.NATIVE_MOUNT_TABLE_UNAVAILABLE,
                        "Grid storage service does not expose AE2's native mount table");
            }
            var snapshot = NativeMountLedger.snapshot(service);
            var capture = capture(grid, origin, snapshot);
            stamp = capture.stamp();
            var previous = lastValid.get(origin);
            if (previous != null && previous.runtimeGrid() != grid && !sharesSourceIdentity(previous, capture.sources())) {
                throw new ProvenanceException(ProvenanceDiagnostic.UNPROVEN_GRID_REBOUND,
                        "Rebound Grid retained NetworkId without callback-owned source continuity");
            }
            var active = current.get(origin);
            if (active != null && sameSnapshot(active, grid, capture.sources(), capture.nodes())) {
                cache.put(origin, new CachedDiscovery(stamp, active, null, null));
                return active;
            }
            var generation = new SourceGeneration(nextGeneration(origin));
            var sources = capture.sources().stream().map(source -> source.withGeneration(origin, generation)).toList();
            var domain = new NativeSourceDomain(origin, generation, grid, sources, capture.nodes());
            current.put(origin, domain);
            lastValid.put(origin, domain);
            cache.put(origin, new CachedDiscovery(stamp, domain, null, null));
            return domain;
        } catch (ProvenanceException exception) {
            invalidate(origin);
            if (stamp != null) {
                // Same native state yields the same rejection: remember it so repeated operations stay O(stamp).
                cache.put(origin, new CachedDiscovery(stamp, null, exception.diagnostic(), exception.getMessage()));
            }
            throw exception;
        } catch (RuntimeException exception) {
            invalidate(origin);
            throw exception;
        }
    }

    public boolean isCurrent(NativeSourceDomain domain) {
        var active = current.get(domain.origin());
        return active == domain && active.generation().equals(domain.generation());
    }

    public void invalidate(OriginNetworkId origin) {
        current.remove(origin);
        cache.remove(origin);
        nextGeneration(origin);
    }

    public void clear() {
        current.clear();
        lastValid.clear();
        generations.clear();
        cache.clear();
    }

    /** Number of full domain rebuilds (mount-table snapshots). Diagnostic counter only. */
    public long discoveryRebuilds() {
        return discoveryRebuilds;
    }

    /** Number of discoveries answered from the stamp-validated cache. Diagnostic counter only. */
    public long cachedDiscoveries() {
        return cachedDiscoveries;
    }

    /** Number of provider mount-table entries visited while rebuilding. Diagnostic counter only. */
    public long providerScans() {
        return providerScans;
    }

    private Capture capture(IGrid grid, OriginNetworkId origin, NativeMountLedger.Snapshot snapshot) {
        var identity = grid.getService(NetworkIdentityService.class);
        var registrations = new ArrayList<Registration>();
        var stampNodes = new ArrayList<IGridNode>();
        var stampActive = new ArrayList<Boolean>();
        for (var providerMounts : snapshot.nodeProviders()) {
            providerScans++;
            if (excluded(providerMounts.provider()) || providerMounts.mounts().isEmpty()) {
                continue;
            }
            var node = providerMounts.node();
            stampNodes.add(node);
            stampActive.add(node.isActive());
            if (!node.isActive()) {
                continue;
            }
            var entries = nativeEntries(providerMounts.mounts());
            if (!entries.isEmpty()) {
                registrations.add(new Registration(identity.lineage(node).nodeId(), node, entries));
            }
        }
        registrations.sort(Comparator.comparing(Registration::registrationId,
                (left, right) -> new SourceAliasId(left, 0).compareTo(new SourceAliasId(right, 0))));
        var nodes = registrations.stream().map(Registration::node).toList();
        var globalOrdinals = new HashMap<String, Integer>();
        for (var providerMounts : snapshot.globalProviders()) {
            providerScans++;
            if (excluded(providerMounts.provider()) || providerMounts.mounts().isEmpty()) {
                continue;
            }
            var entries = nativeEntries(providerMounts.mounts());
            var type = providerMounts.provider().getClass().getName();
            var ordinal = globalOrdinals.merge(type, 1, Integer::sum) - 1;
            if (!entries.isEmpty()) {
                registrations.add(new Registration(globalRegistrationId(origin, type, ordinal), null, entries));
            }
        }
        var links = new ArrayList<NativeStorageAliasProbe.DelegateLink>();
        var sources = buildSources(registrations, links);
        var stamp = new Stamp(grid, grid.getStorageService(), snapshot.generation(),
                stampNodes.toArray(IGridNode[]::new), toArray(stampActive),
                links.toArray(NativeStorageAliasProbe.DelegateLink[]::new));
        return new Capture(sources, nodes, stamp);
    }

    /**
     * Providers that must never become export sources: Federation's own projections and routes (recursion), and
     * AE2's crafting-service interception provider, which is not an inventory but the crafting capability's hook.
     */
    private static boolean excluded(IStorageProvider provider) {
        return provider instanceof FederationManagedStorageProvider || provider instanceof CraftingServiceStorage;
    }

    private static UUID globalRegistrationId(OriginNetworkId origin, String providerType, int ordinal) {
        return UUID.nameUUIDFromBytes(("ae2federation:global-storage-provider:" + origin.value() + ':' + providerType
                + '#' + ordinal).getBytes(StandardCharsets.UTF_8));
    }

    private static List<CallbackEntry> nativeEntries(List<NativeMountLedger.Mount> mounts) {
        var result = new ArrayList<CallbackEntry>();
        for (var callbackIndex = 0; callbackIndex < mounts.size(); callbackIndex++) {
            var entry = mounts.get(callbackIndex);
            if (entry.storage() instanceof FederationManagedStorage) {
                continue;
            }
            if (entry.storage() instanceof NetworkStorage) {
                throw new ProvenanceException(ProvenanceDiagnostic.COMPLETE_AGGREGATE,
                        "Complete native Grid aggregate cannot be an ExportSource");
            }
            result.add(new CallbackEntry(callbackIndex, entry));
        }
        return List.copyOf(result);
    }

    /**
     * Groups mounted handles into export sources. Identical handles (the same inventory mounted by several providers)
     * and AE2 {@code DelegatingMEInventory} chains that reach another mounted handle are provable aliases and share
     * one source. Distinct handles of one provider (e.g. several cells in a drive) are independent sources, which is
     * AE2's own contract: a ProviderState refuses to mount the same inventory twice. Handles that provably share an
     * unmounted inner inventory, or third-party handles that reference another mounted handle, cannot be safely
     * deduplicated and reject the domain with an explicit diagnostic instead of risking double counting.
     */
    private static List<SourceDraft> buildSources(List<Registration> registrations,
            List<NativeStorageAliasProbe.DelegateLink> links) {
        var mounted = Collections.newSetFromMap(new IdentityHashMap<MEStorage, Boolean>());
        registrations.forEach(registration -> registration.entries()
                .forEach(entry -> mounted.add(entry.mount().storage())));
        var chains = new IdentityHashMap<MEStorage, List<MEStorage>>();
        for (var storage : mounted) {
            chains.put(storage, NativeStorageAliasProbe.chain(storage, links));
        }
        var canonical = new IdentityHashMap<MEStorage, MEStorage>();
        for (var entry : chains.entrySet()) {
            MEStorage root = entry.getKey();
            for (var element : entry.getValue()) {
                if (mounted.contains(element)) {
                    root = element;
                }
            }
            canonical.put(entry.getKey(), root);
        }
        var elementOwner = new IdentityHashMap<MEStorage, MEStorage>();
        for (var entry : chains.entrySet()) {
            var root = canonical.get(entry.getKey());
            for (var element : entry.getValue()) {
                var owner = elementOwner.putIfAbsent(element, root);
                if (owner != null && owner != root) {
                    throw new ProvenanceException(ProvenanceDiagnostic.AMBIGUOUS_SHARED_DELEGATE,
                            "Several mounted wrappers forward to one native inventory; no safe deduplication exists");
                }
            }
        }
        var byStorage = new IdentityHashMap<MEStorage, SourceBuilder>();
        for (var registration : registrations) {
            for (var entry : registration.entries()) {
                var aliasId = new SourceAliasId(registration.registrationId(), entry.callbackIndex());
                byStorage.computeIfAbsent(canonical.get(entry.mount().storage()), SourceBuilder::new)
                        .add(aliasId, entry.mount().priority());
            }
        }
        var groups = new IdentityHashMap<MEStorage, Set<MEStorage>>();
        elementOwner.forEach((element, owner) -> groups
                .computeIfAbsent(owner, ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(element));
        for (var entry : chains.entrySet()) {
            var terminal = entry.getValue().getLast();
            var own = groups.get(canonical.get(entry.getKey()));
            if (NativeStorageAliasProbe.opaqueReference(terminal, own, elementOwner.keySet()) != null) {
                throw new ProvenanceException(ProvenanceDiagnostic.OPAQUE_EXTERNAL_ALIAS,
                        "Mounted third-party handle references another mounted native handle; "
                                + "aliasing cannot be proven, explicit adapter required");
            }
        }
        var result = new ArrayList<SourceDraft>();
        byStorage.values().forEach(builder -> result.add(builder.build()));
        result.sort((left, right) -> left.id().registration().compareTo(right.id().registration()));
        return List.copyOf(result);
    }

    private static boolean sharesSourceIdentity(NativeSourceDomain previous, List<SourceDraft> next) {
        return previous.sources().stream().map(ExportSource::id)
                .anyMatch(id -> next.stream().anyMatch(source -> source.id().equals(id)));
    }

    private static boolean sameSnapshot(NativeSourceDomain current, IGrid grid,
            List<SourceDraft> sources, List<IGridNode> nodes) {
        if (current.runtimeGrid() != grid || current.sources().size() != sources.size()
                || current.sourceNodes().size() != nodes.size()) {
            return false;
        }
        for (var index = 0; index < sources.size(); index++) {
            var oldSource = current.sources().get(index);
            var newSource = sources.get(index);
            if (!oldSource.id().equals(newSource.id()) || oldSource.storage() != newSource.storage()
                    || oldSource.priority() != newSource.priority()
                    || !oldSource.aliases().equals(newSource.aliases())) {
                return false;
            }
        }
        for (var index = 0; index < nodes.size(); index++) {
            if (current.sourceNodes().get(index) != nodes.get(index)) {
                return false;
            }
        }
        return true;
    }

    private long nextGeneration(OriginNetworkId origin) {
        var next = Math.incrementExact(generations.getOrDefault(origin, 0L));
        generations.put(origin, next);
        return next;
    }

    private static boolean[] toArray(List<Boolean> values) {
        var result = new boolean[values.size()];
        for (var index = 0; index < result.length; index++) {
            result[index] = values.get(index);
        }
        return result;
    }

    /**
     * Cheap validity stamp of one rebuilt domain. Every field is a revision or identity that AE2 changes when the
     * inputs of discovery change; no quantities are held.
     */
    private record Stamp(IGrid grid, IStorageService service, long mountGeneration, IGridNode[] providerNodes,
            boolean[] providerActive, NativeStorageAliasProbe.DelegateLink[] delegateLinks) {
        boolean matches(IGrid currentGrid, IStorageService currentService) {
            if (grid != currentGrid || service != currentService
                    || NativeMountLedger.generation(currentService) != mountGeneration) {
                return false;
            }
            for (var index = 0; index < providerNodes.length; index++) {
                var node = providerNodes[index];
                if (node.isActive() != providerActive[index] || node.getGrid() != grid) {
                    return false;
                }
            }
            for (var link : delegateLinks) {
                if (!link.current()) {
                    return false;
                }
            }
            return true;
        }
    }

    private record CachedDiscovery(Stamp stamp, @Nullable NativeSourceDomain domain,
            @Nullable ProvenanceDiagnostic failure, @Nullable String message) {
    }

    private record CallbackEntry(int callbackIndex, NativeMountLedger.Mount mount) {
    }

    private record Registration(UUID registrationId, @Nullable IGridNode node, List<CallbackEntry> entries) {
    }

    private record Capture(List<SourceDraft> sources, List<IGridNode> nodes, Stamp stamp) {
    }

    private record SourceDraft(ExportSourceId id, MEStorage storage, int priority, List<SourceAlias> aliases) {
        ExportSource withGeneration(OriginNetworkId origin, SourceGeneration generation) {
            return new ExportSource(id, origin, generation, storage, priority, aliases);
        }
    }

    private static final class SourceBuilder {
        private final MEStorage storage;
        private final List<AliasDraft> aliases = new ArrayList<>();

        private SourceBuilder(MEStorage storage) {
            this.storage = storage;
        }

        private void add(SourceAliasId id, int priority) {
            aliases.add(new AliasDraft(id, priority));
        }

        private SourceDraft build() {
            aliases.sort((left, right) -> left.id().compareTo(right.id()));
            var sourceId = new ExportSourceId(aliases.getFirst().id());
            var resolved = aliases.stream().map(alias -> new SourceAlias(alias.id(), sourceId, alias.priority())).toList();
            var priority = aliases.stream().mapToInt(AliasDraft::priority).max().orElseThrow();
            return new SourceDraft(sourceId, storage, priority, resolved);
        }
    }

    private record AliasDraft(SourceAliasId id, int priority) {
    }
}
