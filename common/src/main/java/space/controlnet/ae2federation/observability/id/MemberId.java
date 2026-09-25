package space.controlnet.ae2federation.observability.id;

import space.controlnet.ae2federation.domain.FederationDomainId;
import space.controlnet.ae2federation.identity.NetworkId;

public record MemberId(FederationDomainId federationDomainId, String value) implements ScopedObservationId {
    public MemberId {
        StableObservationId.validate(federationDomainId, "member", value);
    }

    public static MemberId forNetwork(FederationDomainId federationDomainId, NetworkId networkId) {
        return new MemberId(federationDomainId, StableObservationId.create(federationDomainId, "member", networkId.toString()));
    }
}
