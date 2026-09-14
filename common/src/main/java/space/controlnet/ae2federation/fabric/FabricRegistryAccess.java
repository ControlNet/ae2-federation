package space.controlnet.ae2federation.fabric;

import appeng.api.networking.IGrid;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.identity.IdentityStatus;
import space.controlnet.ae2federation.identity.NetworkIdentityService;

public final class FabricRegistryAccess {
    private static final Map<ServerLevel, FabricRegistry> REGISTRIES = new WeakHashMap<>();

    private FabricRegistryAccess() {
    }

    public static synchronized FabricRegistry get(ServerLevel level) {
        return REGISTRIES.computeIfAbsent(level, ignored -> new FabricRegistry(FabricRecomputeBudget.standard()));
    }

    public static FabricNodeId nodeId(ServerLevel level, BlockPos position) {
        return new FabricNodeId(level.dimension().location().toString(), position.asLong());
    }

    public static FabricPortEvidence nativeEvidence(IGrid grid, FabricPortId port) {
        var settlement = grid.getService(NetworkIdentityService.class).settlement();
        if (settlement.status() == IdentityStatus.SETTLED && settlement.networkId().isPresent()) {
            return new FabricPortEvidence.Native(FabricSourceId.nativePort(port), settlement.networkId().get());
        }
        return new FabricPortEvidence.Unsettled(FabricSourceId.nativePort(port), settlement.status().name());
    }

    public static Optional<space.controlnet.ae2federation.identity.NetworkId> confirmedNetworkId(IGrid grid) {
        var settlement = grid.getService(NetworkIdentityService.class).settlement();
        return settlement.status() == IdentityStatus.SETTLED ? settlement.networkId() : Optional.empty();
    }
}
