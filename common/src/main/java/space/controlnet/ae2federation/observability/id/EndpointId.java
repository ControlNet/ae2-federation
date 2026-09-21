package space.controlnet.ae2federation.observability.id;

import space.controlnet.ae2federation.fabric.FabricId;

public record EndpointId(FabricId fabricId, String value) implements ScopedObservationId {
    public EndpointId {
        StableObservationId.validate(fabricId, "endpoint", value);
    }

    public static EndpointId of(FabricId fabricId, String nativeKey) {
        return new EndpointId(fabricId, StableObservationId.create(fabricId, "endpoint", nativeKey));
    }
}
