package space.controlnet.ae2federation.observability.id;

import java.util.UUID;
import space.controlnet.ae2federation.fabric.FabricId;

public record FlowId(FabricId fabricId, String value) implements ScopedObservationId {
    public FlowId {
        StableObservationId.validate(fabricId, "flow", value);
    }

    public static FlowId forEvent(FabricId fabricId, UUID eventId) {
        return new FlowId(fabricId, StableObservationId.create(fabricId, "flow", eventId.toString()));
    }
}
