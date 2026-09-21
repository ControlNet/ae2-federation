package space.controlnet.ae2federation.energy;

import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IAEPowerStorage;
import java.util.ArrayList;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkIdentityService;

final class NativeEnergyBackendRegistry {
    private final EnergyProviderGenerationLedger<appeng.api.networking.energy.IEnergyService, NativeEnergySource> ledger =
            new EnergyProviderGenerationLedger<>();

    NativeEnergyBackend discover(IGrid grid) {
        var origin = FabricRegistryAccess.confirmedNetworkId(grid)
                .orElseThrow(() -> new IllegalStateException("Native energy provider identity is unsettled"));
        var identity = grid.getService(NetworkIdentityService.class);
        var sources = new ArrayList<NativeEnergySource>();
        for (var node : grid.getNodes()) {
            var storage = node.getService(IAEPowerStorage.class);
            if (storage != null && !(storage instanceof DirectionalEnergySource) && node.getGrid() == grid
                    && node.hasGridBooted() && storage.isAEPublicPowerStorage()
                    && storage.getPowerFlow().isAllowExtraction()) {
                sources.add(new NativeEnergySource(identity.lineage(node).nodeId(), node, storage));
            }
        }
        sources.sort((left, right) -> left.registrationNodeId().toString()
                .compareTo(right.registrationNodeId().toString()));
        if (sources.isEmpty()) {
            ledger.invalidate(origin);
            throw new IllegalStateException("Native energy provider has no local public source");
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
