package space.controlnet.ae2federation.observability.state;

import java.util.Objects;

public record ObservationDeltaEnvelope(ObservationSession session, FabricStateDelta delta) {
    public ObservationDeltaEnvelope {
        Objects.requireNonNull(session);
        Objects.requireNonNull(delta);
        if (!session.scope().equals(delta.scope())) {
            throw new IllegalArgumentException("Delta scope does not match observation session");
        }
    }
}
