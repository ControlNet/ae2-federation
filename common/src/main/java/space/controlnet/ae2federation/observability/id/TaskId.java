package space.controlnet.ae2federation.observability.id;

import space.controlnet.ae2federation.domain.FederationDomainId;

public record TaskId(FederationDomainId federationDomainId, String value) implements ScopedObservationId {
    public TaskId {
        StableObservationId.validate(federationDomainId, "task", value);
    }

    public static TaskId of(FederationDomainId federationDomainId, String nativeKey) {
        return new TaskId(federationDomainId, StableObservationId.create(federationDomainId, "task", nativeKey));
    }
}
