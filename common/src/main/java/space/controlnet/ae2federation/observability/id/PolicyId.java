package space.controlnet.ae2federation.observability.id;

import space.controlnet.ae2federation.domain.FederationDomainId;
import space.controlnet.ae2federation.policy.PolicyKey;

public record PolicyId(FederationDomainId federationDomainId, String value) implements ScopedObservationId {
    public PolicyId {
        StableObservationId.validate(federationDomainId, "policy", value);
    }

    public static PolicyId forKey(FederationDomainId federationDomainId, PolicyKey key) {
        var nativeKey = key.consumerNetworkId() + ">" + key.providerNetworkId() + ":" + key.capability();
        return new PolicyId(federationDomainId, StableObservationId.create(federationDomainId, "policy", nativeKey));
    }
}
