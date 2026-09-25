package space.controlnet.ae2federation.domain;

import appeng.api.networking.IGrid;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.identity.IdentityStatus;
import space.controlnet.ae2federation.identity.NetworkIdentityService;

public final class FederationDomainRegistryAccess {
    private static final Map<ServerLevel, FederationDomainRegistry> REGISTRIES = new WeakHashMap<>();

    private FederationDomainRegistryAccess() {
    }

    public static synchronized FederationDomainRegistry get(ServerLevel level) {
        return REGISTRIES.computeIfAbsent(level, ignored -> new FederationDomainRegistry(FederationDomainRecomputeBudget.standard()));
    }

    public static synchronized LevelCloseResult closeLevel(ServerLevel level) {
        var registered = REGISTRIES.get(level);
        var removed = REGISTRIES.remove(level);
        return new LevelCloseResult(registered != null,
                registered != null && removed == registered, !REGISTRIES.containsKey(level));
    }

    public static synchronized void removeNodeIfPresent(ServerLevel level, FederationDomainNodeId nodeId) {
        var registry = REGISTRIES.get(level);
        if (registry != null) {
            registry.removeNode(nodeId);
        }
    }

    public static synchronized void invalidateNodeIfPresent(ServerLevel level, FederationDomainNodeId nodeId,
            FederationDomainInvalidationReason reason) {
        var registry = REGISTRIES.get(level);
        if (registry != null) {
            registry.invalidateNode(nodeId, reason);
        }
    }

    public static synchronized void invalidateDirectBridgeIfPresent(ServerLevel level, FederationDomainSourceId source) {
        var registry = REGISTRIES.get(level);
        if (registry != null) {
            registry.invalidateDirectBridge(source);
        }
    }

    public static FederationDomainNodeId nodeId(ServerLevel level, BlockPos position) {
        return new FederationDomainNodeId(level.dimension().location().toString(), position.asLong());
    }

    public static FederationDomainPortEvidence nativeEvidence(IGrid grid, FederationDomainPortId port) {
        var settlement = grid.getService(NetworkIdentityService.class).settlement();
        if (settlement.status() == IdentityStatus.SETTLED && settlement.networkId().isPresent()) {
            return new FederationDomainPortEvidence.Native(FederationDomainSourceId.nativePort(port), settlement.networkId().get());
        }
        return new FederationDomainPortEvidence.Unsettled(FederationDomainSourceId.nativePort(port), settlement.status().name());
    }

    public static Optional<space.controlnet.ae2federation.identity.NetworkId> confirmedNetworkId(IGrid grid) {
        var settlement = grid.getService(NetworkIdentityService.class).settlement();
        return settlement.status() == IdentityStatus.SETTLED ? settlement.networkId() : Optional.empty();
    }

    public record LevelCloseResult(boolean registryPresentBefore, boolean removedRegisteredInstance,
            boolean registryAbsentAfter) {
    }
}
