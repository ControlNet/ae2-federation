package space.controlnet.ae2federation.observability.id;

import space.controlnet.ae2federation.fabric.FabricId;

public record ProviderId(FabricId fabricId, String value) implements ScopedObservationId {
    public ProviderId {
        StableObservationId.validate(fabricId, "provider", value);
    }

    public static ProviderId of(FabricId fabricId, String nativeKey) {
        return new ProviderId(fabricId, StableObservationId.create(fabricId, "provider", nativeKey));
    }
}
