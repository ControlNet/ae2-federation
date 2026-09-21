package space.controlnet.ae2federation.observability.state;

import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.observability.id.TaskId;

public record TaskState(FabricReference scope, TaskId id, String status) implements ObservationState {
    public TaskState {
        ObservationStateSupport.validate(scope, id, status);
    }
}
