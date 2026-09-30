package space.controlnet.ae2federation.test.processing;

import java.util.Optional;
import java.util.TreeMap;
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
 * TEST-ONLY synthetic topology for fixtures that have no room for real Federation blocks. One synthetic node per source
 * network stands for the Federation face of that network's fixture Providers: it carries the source network's native
 * membership, as a Federation Pattern Provider's front does, and links directly to each Processing Endpoint's domain
 * node, as a Provider front touching an Endpoint's Federation face would. The evidence is reciprocal, so the registry
 * forms a real domain with the Provider face and the Endpoints as nodes and the source network as a member. Fixtures
 * pass {@link #providerFace} to their {@code ProviderRuntime}. No rule is involved.
 */
public final class SyntheticEndpointDomain {
    private static final String HUB_DIMENSION = "ae2federation_test:synthetic_provider_face";
    private static final String ENDPOINT_PORT = "east";

    private SyntheticEndpointDomain() {
    }

    /** The synthetic node standing for the Federation face of every fixture Provider of {@code source}. */
    public static FederationDomainNodeId providerFace(NetworkId source) {
        var id = source.value();
        return new FederationDomainNodeId(HUB_DIMENSION, id.getMostSignificantBits() ^ id.getLeastSignificantBits());
    }

    /**
     * Makes sure the Endpoint at {@code endpointPosition} is in the domain of {@code source}'s Provider face. A production
     * Endpoint republishes its own evidence when it reloads or its port changes, so callers may call this again; it only
     * writes evidence when the link is missing.
     */
    public static boolean ensure(ServerLevel level, BlockPos endpointPosition, NetworkId source) {
        if (domain(level, endpointPosition, source).isPresent()) {
            return true;
        }
        var endpoint = FederationDomainRegistryAccess.nodeId(level, endpointPosition);
        publishFace(level, source, endpoint, null);
        FederationDomainRegistryAccess.get(level).upsertNode(new FederationDomainNodeEvidence(endpoint, java.util.Map.of(
                ENDPOINT_PORT, new FederationDomainPortEvidence.Federation(
                        new FederationDomainPortId(providerFace(source), facePort(endpoint))))));
        return domain(level, endpointPosition, source).isPresent();
    }

    /** The Endpoint's domain, when it holds {@code source}'s Provider face and has {@code source} as a member. */
    public static Optional<FederationDomainSnapshot> domain(ServerLevel level, BlockPos endpointPosition, NetworkId source) {
        return FederationDomainRegistryAccess.get(level)
                .federationDomainOf(FederationDomainRegistryAccess.nodeId(level, endpointPosition))
                .filter(federationDomain -> federationDomain.nodes().contains(providerFace(source))
                        && federationDomain.memberships().containsKey(source));
    }

    /**
     * Unlinks the Endpoint from every synthetic Provider face. {@code endpointPublishes} says whether a production
     * Endpoint block owns the node: then its own unconnected evidence is restored, otherwise the synthetic node is removed.
     */
    public static void remove(ServerLevel level, BlockPos endpointPosition, boolean endpointPublishes) {
        var registry = FederationDomainRegistryAccess.get(level);
        var endpoint = FederationDomainRegistryAccess.nodeId(level, endpointPosition);
        var faces = registry.federationDomainOf(endpoint).map(federationDomain -> federationDomain.nodes().stream()
                .filter(node -> node.dimension().equals(HUB_DIMENSION)).toList()).orElse(java.util.List.of());
        if (endpointPublishes) {
            registry.upsertNode(new FederationDomainNodeEvidence(endpoint, java.util.Map.of()));
        } else {
            registry.removeNode(endpoint);
        }
        for (var face : faces) {
            var members = registry.federationDomainOf(face).map(FederationDomainSnapshot::memberships).orElse(java.util.Map.of());
            // Each face carries exactly its own network's native membership.
            members.keySet().stream().filter(network -> providerFace(network).equals(face)).findFirst()
                    .ifPresent(network -> publishFace(level, network, null, endpoint));
        }
    }

    /**
     * Republishes {@code source}'s Provider face with a port for every Endpoint still linked to it, plus {@code added}
     * and minus {@code removed}; a face with no Endpoint left is removed.
     */
    private static void publishFace(ServerLevel level, NetworkId source, FederationDomainNodeId added,
            FederationDomainNodeId removed) {
        var registry = FederationDomainRegistryAccess.get(level);
        var face = providerFace(source);
        var endpoints = new java.util.TreeSet<FederationDomainNodeId>();
        registry.federationDomainOf(face).ifPresent(federationDomain -> federationDomain.nodes().stream()
                .filter(node -> !node.dimension().equals(HUB_DIMENSION)).forEach(endpoints::add));
        if (added != null) endpoints.add(added);
        if (removed != null) endpoints.remove(removed);
        if (endpoints.isEmpty()) {
            registry.removeNode(face);
            return;
        }
        var ports = new TreeMap<String, FederationDomainPortEvidence>();
        ports.put("native", new FederationDomainPortEvidence.Native(FederationDomainSourceId.nativePort(
                new FederationDomainPortId(face, "native")), source));
        for (var endpoint : endpoints) {
            ports.put(facePort(endpoint), new FederationDomainPortEvidence.Federation(
                    new FederationDomainPortId(endpoint, ENDPOINT_PORT)));
        }
        registry.upsertNode(new FederationDomainNodeEvidence(face, ports));
    }

    private static String facePort(FederationDomainNodeId endpoint) {
        return "endpoint-" + Long.toUnsignedString(endpoint.blockPosition());
    }
}
