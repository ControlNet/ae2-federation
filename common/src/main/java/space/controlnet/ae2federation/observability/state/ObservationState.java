package space.controlnet.ae2federation.observability.state;

import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.observability.id.ScopedObservationId;

public interface ObservationState {
    FabricReference scope();

    ScopedObservationId id();
}
