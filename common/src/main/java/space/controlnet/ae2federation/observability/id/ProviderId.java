package space.controlnet.ae2federation.observability.id;

import space.controlnet.ae2federation.domain.FederationDomainId;

public record ProviderId(FederationDomainId federationDomainId, String value) implements ScopedObservationId {
    public ProviderId {
        StableObservationId.validate(federationDomainId, "provider", value);
    }

    public static ProviderId of(FederationDomainId federationDomainId, String nativeKey) {
        return new ProviderId(federationDomainId, StableObservationId.create(federationDomainId, "provider", nativeKey));
    }
}
