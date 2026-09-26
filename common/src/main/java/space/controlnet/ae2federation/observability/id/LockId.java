package space.controlnet.ae2federation.observability.id;

import space.controlnet.ae2federation.domain.FederationDomainId;

public record LockId(FederationDomainId federationDomainId, String value) implements ScopedObservationId {
    public LockId {
        StableObservationId.validate(federationDomainId, "lock", value);
    }

    public static LockId of(FederationDomainId federationDomainId, String nativeKey) {
        return new LockId(federationDomainId, StableObservationId.create(federationDomainId, "lock", nativeKey));
    }
}
