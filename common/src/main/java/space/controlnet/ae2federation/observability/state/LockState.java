package space.controlnet.ae2federation.observability.state;

import space.controlnet.ae2federation.domain.FederationDomainReference;
import space.controlnet.ae2federation.observability.id.LockId;

public record LockState(FederationDomainReference scope, LockId id, String status) implements ObservationState {
    public LockState {
        ObservationStateSupport.validate(scope, id, status);
    }
}
