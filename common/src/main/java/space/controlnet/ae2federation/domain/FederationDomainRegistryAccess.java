package space.controlnet.ae2federation.domain;

import appeng.api.networking.IGrid;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.identity.IdentityStatus;
import space.controlnet.ae2federation.identity.NetworkIdentityService;
import space.controlnet.ae2federation.policy.AuthorityEpoch;

public final class FederationDomainRegistryAccess {
    private static final Map<ServerLevel, FederationDomainRegistry> REGISTRIES = new WeakHashMap<>();

    private FederationDomainRegistryAccess() {
    }

    /**
     * The registry {@link #get} returned last, read without the lock: storage, crafting and energy operations look the
     * registry up on every call, nearly always for the level being ticked. Replaced under the lock, cleared on close.
     */
    private static volatile LastRegistry last;

    private record LastRegistry(ServerLevel level, FederationDomainRegistry registry) {
    }

    public static FederationDomainRegistry get(ServerLevel level) {
        var cached = last;
        return cached != null && cached.level() == level ? cached.registry() : getLocked(level);
    }

    private static synchronized FederationDomainRegistry getLocked(ServerLevel level) {
        var registry = REGISTRIES.computeIfAbsent(level, ignored -> {
            var created = new FederationDomainRegistry(FederationDomainRecomputeBudget.standard());
            AuthorityEpoch.advance();
            // Shared energy pools draw without asking Federation, so a topology change dissolves them at once.
            created.onMutation(() -> space.controlnet.ae2federation.energy.EnergySharingService.topologyChanged(level));
            return created;
        });
        last = new LastRegistry(level, registry);
        return registry;
    }

    public static synchronized LevelCloseResult closeLevel(ServerLevel level) {
        last = null;
        AuthorityEpoch.advance();
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
        // One shared string per dimension: node ids compare it on every registry lookup.
        var dimension = DIMENSION_NAMES.computeIfAbsent(level.dimension(), key -> key.location().toString());
        return new FederationDomainNodeId(dimension, position.asLong());
    }

    private static final java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, String>
            DIMENSION_NAMES = new java.util.concurrent.ConcurrentHashMap<>();

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
