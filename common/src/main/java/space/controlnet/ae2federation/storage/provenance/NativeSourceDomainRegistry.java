package space.controlnet.ae2federation.storage.provenance;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import space.controlnet.ae2federation.ae2.storage.NativeStorageProvenance;
import space.controlnet.ae2federation.ae2.storage.NativeStorageSource;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkIdentityService;

public final class NativeSourceDomainRegistry {
    private final Map<OriginNetworkId, NativeSourceDomain> current = new HashMap<>();
    private final Map<OriginNetworkId, NativeSourceDomain> lastValid = new HashMap<>();
    private final Map<OriginNetworkId, Long> generations = new HashMap<>();

    public NativeSourceDomain discover(IGrid grid) {
        var confirmed = FabricRegistryAccess.confirmedNetworkId(grid);
        if (confirmed.isEmpty()) {
            current.values().stream().filter(domain -> domain.runtimeGrid() == grid)
                    .map(NativeSourceDomain::origin).toList().forEach(this::invalidate);
            throw new ProvenanceException(ProvenanceDiagnostic.UNSETTLED_ORIGIN,
                    "Native source Grid has no settled NetworkId");
        }
        var networkId = confirmed.orElseThrow();
        var origin = new OriginNetworkId(networkId);
        try {
            var capture = capture(grid, origin);
            var previous = lastValid.get(origin);
            if (previous != null && previous.runtimeGrid() != grid && !sharesSourceIdentity(previous, capture.sources())) {
                throw new ProvenanceException(ProvenanceDiagnostic.UNPROVEN_GRID_REBOUND,
                        "Rebound Grid retained NetworkId without callback-owned source continuity");
            }
            var active = current.get(origin);
            if (active != null && sameSnapshot(active, grid, capture.sources(), capture.nodes())) {
                return active;
            }
            var generation = new SourceGeneration(nextGeneration(origin));
            var sources = capture.sources().stream().map(source -> source.withGeneration(origin, generation)).toList();
            var domain = new NativeSourceDomain(origin, generation, grid, sources, capture.nodes());
            current.put(origin, domain);
            lastValid.put(origin, domain);
            return domain;
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
        nextGeneration(origin);
    }

    public void clear() {
        current.clear();
        lastValid.clear();
        generations.clear();
    }

    private Capture capture(IGrid grid, OriginNetworkId origin) {
        var provenance = new NativeStorageProvenance();
        var registrations = new ArrayList<Registration>();
        var nodes = new ArrayList<IGridNode>();
        var identity = grid.getService(NetworkIdentityService.class);
        for (var node : grid.getNodes()) {
            var provider = node.getService(IStorageProvider.class);
            if (provider == null || provider instanceof FederationManagedStorageProvider || !node.isActive()) {
                continue;
            }
            var first = nativeEntries(provenance.qualify(node));
            var second = nativeEntries(provenance.qualify(node));
            if (!sameCallback(first, second)) {
                throw new ProvenanceException(ProvenanceDiagnostic.CALLBACK_CHANGED,
                        "Storage provider callback changed during one atomic discovery");
            }
            if (distinctStorages(second) > 1) {
                throw new ProvenanceException(ProvenanceDiagnostic.OPAQUE_EXTERNAL_ALIAS,
                        "Provider callback exposes multiple opaque storage handles; explicit adapter required");
            }
            if (!second.isEmpty()) {
                var nodeId = identity.lineage(node).nodeId();
                registrations.add(new Registration(nodeId, second));
                nodes.add(node);
            }
        }
        return new Capture(buildSources(origin, registrations), List.copyOf(nodes));
    }

    private static List<CallbackEntry> nativeEntries(List<NativeStorageSource> entries) {
        var result = new ArrayList<CallbackEntry>();
        for (var callbackIndex = 0; callbackIndex < entries.size(); callbackIndex++) {
            var entry = entries.get(callbackIndex);
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

    private static List<SourceDraft> buildSources(OriginNetworkId origin, List<Registration> registrations) {
        var byStorage = new IdentityHashMap<MEStorage, SourceBuilder>();
        for (var registration : registrations) {
            for (var entry : registration.entries()) {
                var aliasId = new SourceAliasId(registration.nodeId(), entry.callbackIndex());
                byStorage.computeIfAbsent(entry.source().storage(), SourceBuilder::new)
                        .add(aliasId, entry.source().priority());
            }
        }
        var result = new ArrayList<SourceDraft>();
        byStorage.values().forEach(builder -> result.add(builder.build(origin)));
        result.sort((left, right) -> left.id().registration().compareTo(right.id().registration()));
        return List.copyOf(result);
    }

    private static boolean sameCallback(List<CallbackEntry> first, List<CallbackEntry> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (var index = 0; index < first.size(); index++) {
            if (first.get(index).callbackIndex() != second.get(index).callbackIndex()
                    || first.get(index).source().storage() != second.get(index).source().storage()
                    || first.get(index).source().priority() != second.get(index).source().priority()) {
                return false;
            }
        }
        return true;
    }

    private static int distinctStorages(List<CallbackEntry> entries) {
        var storages = java.util.Collections.newSetFromMap(new IdentityHashMap<MEStorage, Boolean>());
        entries.forEach(entry -> storages.add(entry.source().storage()));
        return storages.size();
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

    private record CallbackEntry(int callbackIndex, NativeStorageSource source) {
    }

    private record Registration(java.util.UUID nodeId, List<CallbackEntry> entries) {
    }

    private record Capture(List<SourceDraft> sources, List<IGridNode> nodes) {
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

        private SourceDraft build(OriginNetworkId origin) {
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
