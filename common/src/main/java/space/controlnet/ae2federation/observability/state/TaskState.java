package space.controlnet.ae2federation.observability.state;

import space.controlnet.ae2federation.domain.FederationDomainReference;
import space.controlnet.ae2federation.observability.id.TaskId;

public record TaskState(FederationDomainReference scope, TaskId id, String status) implements ObservationState {
    public TaskState {
        ObservationStateSupport.validate(scope, id, status);
    }
}
