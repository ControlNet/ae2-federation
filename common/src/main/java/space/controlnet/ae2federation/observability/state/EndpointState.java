package space.controlnet.ae2federation.observability.state;

import space.controlnet.ae2federation.domain.FederationDomainReference;
import space.controlnet.ae2federation.observability.id.EndpointId;

public record EndpointState(FederationDomainReference scope, EndpointId id, String status) implements ObservationState {
    public EndpointState {
        ObservationStateSupport.validate(scope, id, status);
    }
}
