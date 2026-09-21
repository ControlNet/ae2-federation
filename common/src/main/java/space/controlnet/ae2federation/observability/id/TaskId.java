package space.controlnet.ae2federation.observability.id;

import space.controlnet.ae2federation.fabric.FabricId;

public record TaskId(FabricId fabricId, String value) implements ScopedObservationId {
    public TaskId {
        StableObservationId.validate(fabricId, "task", value);
    }

    public static TaskId of(FabricId fabricId, String nativeKey) {
        return new TaskId(fabricId, StableObservationId.create(fabricId, "task", nativeKey));
    }
}
