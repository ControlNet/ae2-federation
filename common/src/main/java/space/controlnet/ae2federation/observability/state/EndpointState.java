package space.controlnet.ae2federation.observability.state;

import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.observability.id.EndpointId;

public record EndpointState(FabricReference scope, EndpointId id, String status) implements ObservationState {
    public EndpointState {
        ObservationStateSupport.validate(scope, id, status);
    }
}
