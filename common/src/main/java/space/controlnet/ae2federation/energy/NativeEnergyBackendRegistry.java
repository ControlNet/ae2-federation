package space.controlnet.ae2federation.energy;

import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IAEPowerStorage;
import java.util.ArrayList;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkIdentityService;

final class NativeEnergyBackendRegistry {
    private final EnergyProviderGenerationLedger<appeng.api.networking.energy.IEnergyService, NativeEnergySource> ledger =
            new EnergyProviderGenerationLedger<>();
    /**
     * Per provider Grid, its nodes that carry a native energy storage, as of the Grid's node revision. A node's
     * services are fixed when it is created, so only a node joining or leaving changes this list; each discovery still
     * reads every listed node's current state. {@link #retain} drops Grids that no longer provide.
     */
    private final java.util.Map<IGrid, StorageNodes> storageNodes = new java.util.IdentityHashMap<>();

    NativeEnergyBackend discover(IGrid grid) {
        var origin = FederationDomainRegistryAccess.confirmedNetworkId(grid)
                .orElseThrow(() -> new EnergyBackendUnavailableException(space.controlnet.ae2federation.policy.BindingDiagnostic.Reason.IDENTITY_UNCONFIRMED));
        var identity = grid.getService(NetworkIdentityService.class);
        var sources = new ArrayList<NativeEnergySource>();
        for (var node : storageNodes(grid, identity)) {
            var storage = node.getService(IAEPowerStorage.class);
            if (node.getGrid() == grid && node.hasGridBooted() && storage.isAEPublicPowerStorage()
                    && storage.getPowerFlow().isAllowExtraction()) {
                sources.add(new NativeEnergySource(identity.lineage(node).nodeId(), node, storage));
            }
        }
        sources.sort((left, right) -> left.registrationNodeId().toString()
                .compareTo(right.registrationNodeId().toString()));
        if (sources.isEmpty()) {
            ledger.invalidate(origin);
            throw new EnergyBackendUnavailableException(space.controlnet.ae2federation.policy.BindingDiagnostic.Reason.ENERGY_SOURCE_MISSING);
        }
        var service = grid.getEnergyService();
        var snapshot = ledger.update(origin, service, sources);
        return new NativeEnergyBackend(origin, snapshot.generation(), grid, service, sources);
    }

    boolean isCurrent(NativeEnergyBackend backend) {
        try {
            var current = discover(backend.sourceGrid());
            return current.generation().equals(backend.generation()) && current.service() == backend.service()
                    && sameSources(current.sources(), backend.sources());
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    void clear() {
        ledger.clear();
        storageNodes.clear();
    }

    /** Forgets the node lists of Grids other than {@code providerGrids}. */
    void retain(java.util.Collection<IGrid> providerGrids) {
        var keep = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<IGrid, Boolean>());
        keep.addAll(providerGrids);
        storageNodes.keySet().removeIf(grid -> !keep.contains(grid));
    }

    private java.util.List<appeng.api.networking.IGridNode> storageNodes(IGrid grid, NetworkIdentityService identity) {
        var revision = identity.nodeRevision();
        var cached = storageNodes.get(grid);
        if (cached != null && cached.nodeRevision() == revision) {
            return cached.nodes();
        }
        var nodes = new ArrayList<appeng.api.networking.IGridNode>();
        for (var node : grid.getNodes()) {
            var storage = node.getService(IAEPowerStorage.class);
            if (storage != null && !(storage instanceof DirectionalEnergySource)) {
                nodes.add(node);
            }
        }
        storageNodes.put(grid, new StorageNodes(revision, java.util.List.copyOf(nodes)));
        return nodes;
    }

    private record StorageNodes(long nodeRevision, java.util.List<appeng.api.networking.IGridNode> nodes) {
    }

    private static boolean sameSources(java.util.List<NativeEnergySource> first,
            java.util.List<NativeEnergySource> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (var index = 0; index < first.size(); index++) {
            var left = first.get(index);
            var right = second.get(index);
            if (!left.registrationNodeId().equals(right.registrationNodeId()) || left.node() != right.node()
                    || left.storage() != right.storage()) {
                return false;
            }
        }
        return true;
    }
}
