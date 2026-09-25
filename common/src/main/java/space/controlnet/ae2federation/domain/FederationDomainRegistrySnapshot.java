package space.controlnet.ae2federation.domain;

import java.util.Map;
import java.util.Set;
import space.controlnet.ae2federation.identity.NetworkId;

public record FederationDomainRegistrySnapshot(Map<FederationDomainId, FederationDomainSnapshot> federationDomains,
        Map<NetworkId, Set<FederationDomainId>> networkIndex,
        Map<FederationDomainNodeId, FederationDomainInvalidationReason> invalidations,
        long topologyRevision) {
    public FederationDomainRegistrySnapshot {
        federationDomains = Map.copyOf(federationDomains);
        networkIndex = Map.copyOf(networkIndex);
        invalidations = Map.copyOf(invalidations);
    }
}
