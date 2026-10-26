package space.controlnet.ae2federation.domain;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The server's cable capacity counts: the ones {@link FederationDomainRegistry} counted when it last judged each domain
 * against the limit, so a lookup (a cable syncing its clients, a HUD polling the cable a player looks at) walks
 * nothing. Server thread only.
 */
public final class CableCapacity {
    private CableCapacity() {
    }

    /** The demand of the Federation Cable at {@code position}, or empty when no domain holds it yet. */
    public static Optional<CableCapacityCounter.Demand> demand(ServerLevel level, BlockPos position) {
        return demand(level, FederationDomainRegistryAccess.nodeId(level, position));
    }

    /** The demand of the cable or P2P tunnel {@code node}, or empty when no domain holds it yet. */
    public static Optional<CableCapacityCounter.Demand> demand(ServerLevel level, FederationDomainNodeId node) {
        var registry = FederationDomainRegistryAccess.get(level);
        return registry.federationDomainOf(node)
                .map(domain -> registry.capacityDemand(domain.federationDomainId()).get(node));
    }
}
