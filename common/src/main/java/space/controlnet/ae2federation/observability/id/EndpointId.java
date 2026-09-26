package space.controlnet.ae2federation.observability.id;

import space.controlnet.ae2federation.domain.FederationDomainId;

public record EndpointId(FederationDomainId federationDomainId, String value) implements ScopedObservationId {
    public EndpointId {
        StableObservationId.validate(federationDomainId, "endpoint", value);
    }

    public static EndpointId of(FederationDomainId federationDomainId, String nativeKey) {
        return new EndpointId(federationDomainId, StableObservationId.create(federationDomainId, "endpoint", nativeKey));
    }
}
