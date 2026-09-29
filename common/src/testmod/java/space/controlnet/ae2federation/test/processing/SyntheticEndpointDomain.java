package space.controlnet.ae2federation.test.processing;

import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.domain.FederationDomainNodeEvidence;
import space.controlnet.ae2federation.domain.FederationDomainNodeId;
import space.controlnet.ae2federation.domain.FederationDomainPortEvidence;
import space.controlnet.ae2federation.domain.FederationDomainPortId;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.FederationDomainSnapshot;
import space.controlnet.ae2federation.domain.FederationDomainSourceId;
import space.controlnet.ae2federation.identity.NetworkId;

/**
 * TEST-ONLY synthetic topology for fixtures that have no room for real Federation blocks: links a Processing Endpoint's
 * domain node to a synthetic hub node that carries a source network's native membership, as a Federation Cable from the
 * Endpoint's Federation face to a member network would. The evidence is reciprocal, so the registry forms a real domain
 * with the Endpoint as a node and the source network as a member. No rule is involved.
 */
public final class SyntheticEndpointDomain {
    private static final String HUB_DIMENSION = "ae2federation_test:synthetic_hub";

    private SyntheticEndpointDomain() {
    }

    /**
     * Makes sure the Endpoint at {@code endpointPosition} is in a domain that has {@code source} as a member. A production
     * Endpoint republishes its own evidence when it reloads or its port changes, so callers may call this again; it only
     * writes evidence when the link is missing.
     */
    public static boolean ensure(ServerLevel level, BlockPos endpointPosition, NetworkId source) {
        if (domain(level, endpointPosition, source).isPresent()) {
            return true;
        }
        var registry = FederationDomainRegistryAccess.get(level);
        var endpoint = FederationDomainRegistryAccess.nodeId(level, endpointPosition);
        var hub = hub(endpointPosition);
        registry.upsertNode(new FederationDomainNodeEvidence(hub, Map.of(
                "west", new FederationDomainPortEvidence.Federation(new FederationDomainPortId(endpoint, "east")),
                "down", new FederationDomainPortEvidence.Native(FederationDomainSourceId.nativePort(
                        new FederationDomainPortId(hub, "down")), source))));
        registry.upsertNode(new FederationDomainNodeEvidence(endpoint, Map.of(
                "east", new FederationDomainPortEvidence.Federation(new FederationDomainPortId(hub, "west")))));
        return domain(level, endpointPosition, source).isPresent();
    }

    /** The Endpoint's domain, when it has {@code source} as a member. */
    public static Optional<FederationDomainSnapshot> domain(ServerLevel level, BlockPos endpointPosition, NetworkId source) {
        return FederationDomainRegistryAccess.get(level)
                .federationDomainOf(FederationDomainRegistryAccess.nodeId(level, endpointPosition))
                .filter(federationDomain -> federationDomain.memberships().containsKey(source));
    }

    /**
     * Removes the synthetic hub. {@code endpointPublishes} says whether a production Endpoint block owns the node: then
     * its own unconnected evidence is restored, otherwise the synthetic node is removed.
     */
    public static void remove(ServerLevel level, BlockPos endpointPosition, boolean endpointPublishes) {
        var registry = FederationDomainRegistryAccess.get(level);
        registry.removeNode(hub(endpointPosition));
        var endpoint = FederationDomainRegistryAccess.nodeId(level, endpointPosition);
        if (endpointPublishes) {
            registry.upsertNode(new FederationDomainNodeEvidence(endpoint, Map.of()));
        } else {
            registry.removeNode(endpoint);
        }
    }

    private static FederationDomainNodeId hub(BlockPos endpointPosition) {
        return new FederationDomainNodeId(HUB_DIMENSION, endpointPosition.asLong());
    }
}
