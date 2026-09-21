package space.controlnet.ae2federation.observability.state;

import java.util.Objects;

public record ObservationSnapshotEnvelope(ObservationSession session, FabricStateSnapshot snapshot) {
    public ObservationSnapshotEnvelope {
        Objects.requireNonNull(session);
        Objects.requireNonNull(snapshot);
        if (!session.scope().equals(snapshot.scope())) {
            throw new IllegalArgumentException("Snapshot scope does not match observation session");
        }
    }
}
