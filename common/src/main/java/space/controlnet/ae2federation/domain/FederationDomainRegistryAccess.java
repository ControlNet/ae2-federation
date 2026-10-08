package space.controlnet.ae2federation.domain;

import appeng.api.networking.IGrid;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.identity.IdentityStatus;
import space.controlnet.ae2federation.identity.NetworkIdentityService;
import space.controlnet.ae2federation.policy.AuthorityEpoch;

/**
 * One domain registry per server, like AE2's one set of Grids: node ids carry their dimension, so a domain can span
 * dimensions. The level passed in only names its server. A level that closes takes out only its own nodes.
 */
public final class FederationDomainRegistryAccess {
    private static final Map<MinecraftServer, FederationDomainRegistry> REGISTRIES = new WeakHashMap<>();

    private FederationDomainRegistryAccess() {
    }

    /**
     * The registry {@link #get} returned last, read without the lock: storage, crafting and energy operations look the
     * registry up on every call, nearly always for the level being ticked. Replaced under the lock, cleared on close.
     */
    private static volatile LastRegistry last;

    private record LastRegistry(MinecraftServer server, FederationDomainRegistry registry) {
    }

    /** The registry of {@code level}'s server. */
    public static FederationDomainRegistry get(ServerLevel level) {
        var server = level.getServer();
        var cached = last;
        return cached != null && cached.server() == server ? cached.registry() : getLocked(server);
    }

    private static synchronized FederationDomainRegistry getLocked(MinecraftServer server) {
        var registry = REGISTRIES.computeIfAbsent(server, ignored -> {
            var created = new FederationDomainRegistry(FederationDomainRecomputeBudget.standard());
            AuthorityEpoch.advance();
            // Shared energy pools draw without asking Federation, so a topology change dissolves them at once.
            created.onMutation(space.controlnet.ae2federation.energy.EnergySharingService::topologyChangedAll);
            return created;
        });
        last = new LastRegistry(server, registry);
        return registry;
    }

    /** Takes the closing level's nodes and Bridges out of its server's registry; the other dimensions keep theirs. */
    public static synchronized FederationDomainRegistry.DimensionRemoval closeLevel(ServerLevel level) {
        AuthorityEpoch.advance();
        var registry = REGISTRIES.get(level.getServer());
        return registry == null ? new FederationDomainRegistry.DimensionRemoval(dimension(level), 0, 0, 0)
                : registry.removeDimension(dimension(level));
    }

    /** Drops the server's registry once it has stopped, so a world opened next in the same game starts empty. */
    public static synchronized void closeServer(MinecraftServer server) {
        last = null;
        AuthorityEpoch.advance();
        REGISTRIES.remove(server);
    }

    public static synchronized void removeNodeIfPresent(ServerLevel level, FederationDomainNodeId nodeId) {
        var registry = REGISTRIES.get(level.getServer());
        if (registry != null) {
            registry.removeNode(nodeId);
        }
    }

    public static synchronized void invalidateNodeIfPresent(ServerLevel level, FederationDomainNodeId nodeId,
            FederationDomainInvalidationReason reason) {
        var registry = REGISTRIES.get(level.getServer());
        if (registry != null) {
            registry.invalidateNode(nodeId, reason);
        }
    }

    public static synchronized void invalidateDirectBridgeIfPresent(ServerLevel level, FederationDomainSourceId source) {
        var registry = REGISTRIES.get(level.getServer());
        if (registry != null) {
            registry.invalidateDirectBridge(source);
        }
    }

    public static FederationDomainNodeId nodeId(ServerLevel level, BlockPos position) {
        return new FederationDomainNodeId(dimension(level), position.asLong());
    }

    /** The dimension string of the level's node ids; one shared string per dimension, compared on every lookup. */
    public static String dimension(ServerLevel level) {
        return DIMENSION_NAMES.computeIfAbsent(level.dimension(), key -> key.location().toString());
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
}
