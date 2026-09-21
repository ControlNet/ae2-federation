package space.controlnet.ae2federation.observability.id;

import space.controlnet.ae2federation.fabric.FabricId;

public record LockId(FabricId fabricId, String value) implements ScopedObservationId {
    public LockId {
        StableObservationId.validate(fabricId, "lock", value);
    }

    public static LockId of(FabricId fabricId, String nativeKey) {
        return new LockId(fabricId, StableObservationId.create(fabricId, "lock", nativeKey));
    }
}
