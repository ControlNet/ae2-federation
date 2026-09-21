package space.controlnet.ae2federation.observability.state;

import java.util.Objects;
import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.observability.ObservationLimits;
import space.controlnet.ae2federation.observability.id.FlowId;
import space.controlnet.ae2federation.observability.meter.OperationEventId;

public record FlowState(FabricReference scope, FlowId id, OperationEventId eventId, String resource, long amount,
        ResourceUnit unit, Attribution attribution, boolean exactBatchCompletion) implements ObservationState {
    public FlowState {
        ObservationStateSupport.validate(scope, id, resource);
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(unit);
        Objects.requireNonNull(attribution);
        if (amount <= 0) {
            throw new IllegalArgumentException("Observed flow amount must be positive");
        }
        ObservationLimits.boundedString(resource, "Flow resource");
        if (exactBatchCompletion && attribution == Attribution.AGGREGATE_LANE_RETURN) {
            throw new IllegalArgumentException("Aggregate Lane returns cannot prove exact Batch completion");
        }
    }

    public enum Attribution {
        EXACT_OPERATION,
        AGGREGATE_LANE_RETURN
    }
}
