package space.controlnet.ae2federation.storage.provenance;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.pathing.IPathingService;
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
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.IdentityEpoch;
import space.controlnet.ae2federation.identity.NetworkIdentityService;
import space.controlnet.ae2federation.policy.AuthorityEpoch;

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
    private final Map<OriginNetworkId, Long> generations = new HashMap<>();
    private final Map<OriginNetworkId, CachedDiscovery> cache = new HashMap<>();
    /** Advances before any change to {@link #current} or {@link #cache}. */
    private long mutations;
    private long discoveryRebuilds;
    private long cachedDiscoveries;
    private long providerScans;

    /**
     * As {@link #discover(IGrid)}, answered from {@code probe} while this registry's cache and current domains are
     * unchanged since {@code probe} saw {@code discover} return a domain for the same Grid, and no settlement changed
     * ({@link IdentityEpoch}; discover returns a domain only for a settled Grid): then only that domain's stamp is
     * matched again, and the result is the one {@code discover} gives.
     */
    public NativeSourceDomain discover(IGrid grid, Probe probe) {
        var cached = probe.cached;
        if (probe.grid == grid && probe.mutations == mutations && probe.epoch == IdentityEpoch.current()
                && cached.stamp().matches(grid, probe.service)) {
            cachedDiscoveries++;
            return cached.domain();
        }
        var epoch = IdentityEpoch.current();
        var domain = discover(grid);
        // discover returned the domain its origin's cache entry and current domain both hold.
        probe.grid = grid;
        probe.epoch = epoch;
        probe.service = grid.getStorageService();
        probe.cached = cache.get(domain.origin());
        probe.mutations = mutations;
        return domain;
    }

    /**
     * Whether the domain {@code probe}'s last {@link #discover(IGrid, Probe)} returned still matches its Grid's native
     * state, for a caller that knows {@link space.controlnet.ae2federation.policy.AuthorityEpoch} did not change since:
     * neither this registry nor the Grid's membership, mount table, power or booting state did, so of the stamp only
     * the delegate links, which change without any event, are read again. Then {@code discover} would return it again.
     */
    public boolean stillMatches(Probe probe) {
        var cached = probe.cached;
        return cached != null && cached.domain() != null && cached.stamp().linksCurrent();
    }

    /** One caller's memo of the last {@link #discover(IGrid, Probe)} answer; owned by that caller. */
    public static final class Probe {
        private IGrid grid;
        private long epoch = -1;
        private IStorageService service;
        private CachedDiscovery cached;
        private long mutations = -1;
    }

    public NativeSourceDomain discover(IGrid grid) {
        var confirmed = FederationDomainRegistryAccess.confirmedNetworkId(grid);
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
        mutations++;
        AuthorityEpoch.advance();
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
            var active = current.get(origin);
            if (active != null && sameSnapshot(active, grid, capture.sources(), capture.nodes(), capture.skipped())) {
                cache.put(origin, new CachedDiscovery(stamp, active, null, null));
                return active;
            }
            var generation = new SourceGeneration(nextGeneration(origin));
            var sources = capture.sources().stream().map(source -> source.withGeneration(origin, generation)).toList();
            var domain = new NativeSourceDomain(origin, generation, grid, sources, capture.nodes(), capture.skipped());
            current.put(origin, domain);
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
        mutations++;
        AuthorityEpoch.advance();
        current.remove(origin);
        cache.remove(origin);
        nextGeneration(origin);
    }

    public void clear() {
        mutations++;
        AuthorityEpoch.advance();
        current.clear();
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
        var nodeRegistrations = List.copyOf(registrations);
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
        var built = buildSources(registrations, links);
        var skipped = new ArrayList<SkippedSource>();
        for (var registration : registrations) {
            for (var entry : registration.entries()) {
                var diagnostic = built.skipped().get(entry.mount().storage());
                if (diagnostic != null) {
                    skipped.add(new SkippedSource(new SourceAliasId(registration.registrationId(),
                            entry.callbackIndex()), diagnostic));
                }
            }
        }
        skipped.sort(Comparator.comparing(SkippedSource::alias));
        // A node whose every handle is skipped exports nothing, so its readiness does not gate the domain.
        var nodes = nodeRegistrations.stream()
                .filter(registration -> registration.entries().stream()
                        .anyMatch(entry -> !built.skipped().containsKey(entry.mount().storage())))
                .map(Registration::node).toList();
        var stamp = new Stamp(grid, grid.getStorageService(), grid.getEnergyService(), grid.getPathingService(),
                snapshot.generation(),
                stampNodes.toArray(IGridNode[]::new), toArray(stampActive),
                links.toArray(NativeStorageAliasProbe.DelegateLink[]::new));
        return new Capture(built.sources(), nodes, List.copyOf(skipped), stamp);
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
            result.add(new CallbackEntry(callbackIndex, entry));
        }
        return List.copyOf(result);
    }

    /**
     * Groups mounted handles into export sources, separating source identity from execution, and leaves out the
     * handles whose identity cannot be proven. Identical handles (the same inventory mounted by several providers) are
     * one source. A mounted handle whose AE2 delegate chain reaches another mounted handle shares that handle's
     * identity; it is executed through that handle only when every wrapper on the way is
     * {@linkplain NativeStorageAliasProbe#transparent transparent}. Otherwise one identity would need two different
     * behaviours (e.g. a filtering Storage Bus handler over an inventory that is also mounted directly), which is
     * {@link ProvenanceDiagnostic#NON_TRANSPARENT_ALIAS}. Distinct handles of one provider (e.g. several cells in a
     * drive) are independent sources, which is AE2's own contract: a ProviderState refuses to mount the same inventory
     * twice. Handles that provably share an unmounted inner inventory
     * ({@link ProvenanceDiagnostic#AMBIGUOUS_SHARED_DELEGATE}), and third-party handles that reference another mounted
     * handle ({@link ProvenanceDiagnostic#OPAQUE_EXTERNAL_ALIAS}), cannot be safely deduplicated either.
     *
     * <p>Such a conflict skips its whole alias group: every mounted handle whose delegate chain overlaps the
     * conflicting handles', since no single one of them is provably the one to keep. A complete
     * {@link NetworkStorage} aggregate is skipped alone ({@link ProvenanceDiagnostic#COMPLETE_AGGREGATE}). The other
     * handles still become sources; a skipped handle is never exported. Every chain is walked once, so {@code links}
     * also holds the skipped handles' delegate links and a retarget still invalidates the domain.
     */
    private static Built buildSources(List<Registration> registrations,
            List<NativeStorageAliasProbe.DelegateLink> links) {
        var mounted = Collections.newSetFromMap(new IdentityHashMap<MEStorage, Boolean>());
        registrations.forEach(registration -> registration.entries()
                .forEach(entry -> mounted.add(entry.mount().storage())));
        var chains = new IdentityHashMap<MEStorage, List<MEStorage>>();
        for (var storage : mounted) {
            chains.put(storage, NativeStorageAliasProbe.chain(storage, links));
        }
        var skipped = new IdentityHashMap<MEStorage, ProvenanceDiagnostic>();
        for (var storage : mounted) {
            if (storage instanceof NetworkStorage) {
                skipped.put(storage, ProvenanceDiagnostic.COMPLETE_AGGREGATE);
            }
        }
        // Each round skips at least one candidate, so this ends within one round per mounted handle.
        while (true) {
            var candidates = Collections.newSetFromMap(new IdentityHashMap<MEStorage, Boolean>());
            mounted.stream().filter(storage -> !skipped.containsKey(storage)).forEach(candidates::add);
            var resolution = resolve(registrations, chains, candidates);
            if (resolution.conflict() == null) {
                return new Built(resolution.sources(), skipped);
            }
            for (var storage : aliasGroup(resolution.conflict().handles(), chains, candidates)) {
                skipped.put(storage, resolution.conflict().diagnostic());
            }
        }
    }

    /** The candidates whose delegate chains overlap {@code handles} or one another's, transitively. */
    private static Set<MEStorage> aliasGroup(List<MEStorage> handles, Map<MEStorage, List<MEStorage>> chains,
            Set<MEStorage> candidates) {
        var group = Collections.newSetFromMap(new IdentityHashMap<MEStorage, Boolean>());
        var elements = Collections.newSetFromMap(new IdentityHashMap<MEStorage, Boolean>());
        elements.addAll(handles);
        var grew = true;
        while (grew) {
            grew = false;
            for (var candidate : candidates) {
                if (!group.contains(candidate) && chains.get(candidate).stream().anyMatch(elements::contains)) {
                    group.add(candidate);
                    elements.addAll(chains.get(candidate));
                    grew = true;
                }
            }
        }
        return group;
    }

    /** The export sources of {@code candidates}, or the first identity conflict among them. */
    private static Resolution resolve(List<Registration> registrations, Map<MEStorage, List<MEStorage>> chains,
            Set<MEStorage> candidates) {
        var canonical = new IdentityHashMap<MEStorage, MEStorage>();
        for (var storage : candidates) {
            var chain = chains.get(storage);
            MEStorage root = storage;
            // Whether every chain element before the current one forwards unchanged.
            var transparentSoFar = NativeStorageAliasProbe.transparent(root);
            for (var index = 1; index < chain.size(); index++) {
                var element = chain.get(index);
                if (candidates.contains(element)) {
                    if (!transparentSoFar) {
                        return Resolution.of(new Conflict(ProvenanceDiagnostic.NON_TRANSPARENT_ALIAS,
                                List.of(storage, element)));
                    }
                    root = element;
                }
                transparentSoFar &= NativeStorageAliasProbe.transparent(element);
            }
            canonical.put(storage, root);
        }
        var elementOwner = new IdentityHashMap<MEStorage, MEStorage>();
        for (var storage : candidates) {
            var root = canonical.get(storage);
            for (var element : chains.get(storage)) {
                var owner = elementOwner.putIfAbsent(element, root);
                if (owner != null && owner != root) {
                    return Resolution.of(new Conflict(ProvenanceDiagnostic.AMBIGUOUS_SHARED_DELEGATE,
                            List.of(owner, root, element)));
                }
            }
        }
        var byStorage = new IdentityHashMap<MEStorage, SourceBuilder>();
        for (var registration : registrations) {
            for (var entry : registration.entries()) {
                if (!candidates.contains(entry.mount().storage())) {
                    continue;
                }
                var aliasId = new SourceAliasId(registration.registrationId(), entry.callbackIndex());
                byStorage.computeIfAbsent(canonical.get(entry.mount().storage()), SourceBuilder::new)
                        .add(aliasId, entry.mount().priority());
            }
        }
        var groups = new IdentityHashMap<MEStorage, Set<MEStorage>>();
        elementOwner.forEach((element, owner) -> groups
                .computeIfAbsent(owner, ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(element));
        for (var storage : candidates) {
            var terminal = chains.get(storage).getLast();
            var own = groups.get(canonical.get(storage));
            var referenced = NativeStorageAliasProbe.opaqueReference(terminal, own, elementOwner.keySet());
            if (referenced != null) {
                return Resolution.of(new Conflict(ProvenanceDiagnostic.OPAQUE_EXTERNAL_ALIAS,
                        List.of(storage, terminal, referenced)));
            }
        }
        var result = new ArrayList<SourceDraft>();
        byStorage.values().forEach(builder -> result.add(builder.build()));
        result.sort((left, right) -> left.id().registration().compareTo(right.id().registration()));
        return new Resolution(List.copyOf(result), null);
    }

    private static boolean sameSnapshot(NativeSourceDomain current, IGrid grid,
            List<SourceDraft> sources, List<IGridNode> nodes, List<SkippedSource> skipped) {
        if (current.runtimeGrid() != grid || current.sources().size() != sources.size()
                || current.sourceNodes().size() != nodes.size() || !current.skipped().equals(skipped)) {
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
    /** {@code energy} and {@code pathing} are {@code grid}'s services, fixed for the Grid's lifetime. */
    private record Stamp(IGrid grid, IStorageService service, IEnergyService energy, IPathingService pathing,
            long mountGeneration, IGridNode[] providerNodes, boolean[] providerActive,
            NativeStorageAliasProbe.DelegateLink[] delegateLinks) {
        boolean matches(IGrid currentGrid, IStorageService currentService) {
            if (grid != currentGrid || service != currentService
                    || NativeMountLedger.generation(currentService) != mountGeneration) {
                return false;
            }
            if (providerNodes.length > 0) {
                var powered = energy.isNetworkPowered();
                var booted = !pathing.isNetworkBooting();
                for (var index = 0; index < providerNodes.length; index++) {
                    var node = providerNodes[index];
                    // A node now on another Grid, or destroyed, no longer matches; one still on this Grid is
                    // active exactly when this Grid is powered and booted and the node has its channels.
                    if (NodeActivity.gridOf(node) != grid
                            || NodeActivity.activeOnGrid(node, powered, booted) != providerActive[index]) {
                        return false;
                    }
                }
            }
            return linksCurrent();
        }

        boolean linksCurrent() {
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

    private record Capture(List<SourceDraft> sources, List<IGridNode> nodes, List<SkippedSource> skipped,
            Stamp stamp) {
    }

    private record Built(List<SourceDraft> sources, Map<MEStorage, ProvenanceDiagnostic> skipped) {
    }

    private record Conflict(ProvenanceDiagnostic diagnostic, List<MEStorage> handles) {
    }

    private record Resolution(List<SourceDraft> sources, @Nullable Conflict conflict) {
        static Resolution of(Conflict conflict) {
            return new Resolution(List.of(), conflict);
        }
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
