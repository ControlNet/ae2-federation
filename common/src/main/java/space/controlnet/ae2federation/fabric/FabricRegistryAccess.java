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

    public static synchronized LevelCloseResult closeLevel(ServerLevel level) {
        var registered = REGISTRIES.get(level);
        var removed = REGISTRIES.remove(level);
        return new LevelCloseResult(registered != null,
                registered != null && removed == registered, !REGISTRIES.containsKey(level));
    }

    public static synchronized void removeNodeIfPresent(ServerLevel level, FabricNodeId nodeId) {
        var registry = REGISTRIES.get(level);
        if (registry != null) {
            registry.removeNode(nodeId);
        }
    }

    public static synchronized void invalidateNodeIfPresent(ServerLevel level, FabricNodeId nodeId,
            FabricInvalidationReason reason) {
        var registry = REGISTRIES.get(level);
        if (registry != null) {
            registry.invalidateNode(nodeId, reason);
        }
    }

    public static synchronized void invalidateDirectBridgeIfPresent(ServerLevel level, FabricSourceId source) {
        var registry = REGISTRIES.get(level);
        if (registry != null) {
            registry.invalidateDirectBridge(source);
        }
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

    public record LevelCloseResult(boolean registryPresentBefore, boolean removedRegisteredInstance,
            boolean registryAbsentAfter) {
    }
}
