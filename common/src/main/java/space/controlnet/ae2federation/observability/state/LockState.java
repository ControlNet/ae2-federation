package space.controlnet.ae2federation.observability.state;

import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.observability.id.LockId;

public record LockState(FabricReference scope, LockId id, String status) implements ObservationState {
    public LockState {
        ObservationStateSupport.validate(scope, id, status);
    }
}
