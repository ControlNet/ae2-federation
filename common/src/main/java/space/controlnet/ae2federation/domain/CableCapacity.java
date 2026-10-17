package space.controlnet.ae2federation.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The server's cable capacity counts, from {@link CableCapacityCounter}. A domain is counted when one of its cables is
 * first asked about and again only after any evidence changes, so repeated lookups (a HUD polling the cable a player
 * looks at) do not walk the domain each time. Server thread only.
 */
public final class CableCapacity {
    private static FederationDomainRegistry registry;
    private static long revision;
    private static final Map<FederationDomainId, Map<FederationDomainNodeId, CableCapacityCounter.Demand>> COUNTED =
            new HashMap<>();

    private CableCapacity() {
    }

    /** Forgets the counts and the registry they came from, when its server stops. */
    static void clear() {
        COUNTED.clear();
        registry = null;
    }

    /** The demand of the Federation Cable at {@code position}, or empty when no domain holds it yet. */
    public static Optional<CableCapacityCounter.Demand> demand(ServerLevel level, BlockPos position) {
        return demand(level, FederationDomainRegistryAccess.nodeId(level, position));
    }

    /** The demand of the cable or P2P tunnel {@code node}, or empty when no domain holds it yet. */
    public static Optional<CableCapacityCounter.Demand> demand(ServerLevel level, FederationDomainNodeId node) {
        var current = FederationDomainRegistryAccess.get(level);
        if (current != registry || current.evidenceRevision() != revision) {
            COUNTED.clear();
            registry = current;
            revision = current.evidenceRevision();
        }
        return current.federationDomainOf(node).map(domain -> COUNTED.computeIfAbsent(domain.federationDomainId(),
                id -> CableCapacityCounter.count(current.nodeEvidence(id))).get(node));
    }
}
