package space.controlnet.ae2federation.observability.id;

import java.util.UUID;
import space.controlnet.ae2federation.domain.FederationDomainId;

public record FlowId(FederationDomainId federationDomainId, String value) implements ScopedObservationId {
    public FlowId {
        StableObservationId.validate(federationDomainId, "flow", value);
    }

    public static FlowId forEvent(FederationDomainId federationDomainId, UUID eventId) {
        return new FlowId(federationDomainId, StableObservationId.create(federationDomainId, "flow", eventId.toString()));
    }
}
