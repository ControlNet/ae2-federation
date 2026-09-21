package space.controlnet.ae2federation.observability.state;

import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.observability.id.PolicyId;

public record PolicyState(FabricReference scope, PolicyId id, String status) implements ObservationState {
    public PolicyState {
        ObservationStateSupport.validate(scope, id, status);
    }
}
