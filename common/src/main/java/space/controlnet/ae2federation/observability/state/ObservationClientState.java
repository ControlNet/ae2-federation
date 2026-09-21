package space.controlnet.ae2federation.observability.state;

import java.util.Objects;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.fabric.FabricReference;

public final class ObservationClientState {
    private final ObservationSession session;
    private @Nullable FabricStateSnapshot snapshot;
    private boolean resnapshotRequired = true;

    public ObservationClientState(ObservationSession session) {
        this.session = Objects.requireNonNull(session);
    }

    public boolean applySnapshot(ObservationSnapshotEnvelope envelope) {
        Objects.requireNonNull(envelope);
        if (!session.equals(envelope.session())) {
            return false;
        }
        snapshot = envelope.snapshot();
        resnapshotRequired = false;
        return true;
    }

    public boolean applyDelta(ObservationDeltaEnvelope envelope) {
        Objects.requireNonNull(envelope);
        var delta = envelope.delta();
        if (!session.equals(envelope.session()) || snapshot == null || !snapshot.scope().equals(delta.scope())
                || snapshot.topologyRevision() != delta.topologyRevision()
                || snapshot.dataRevision() != delta.baseDataRevision() || delta.resnapshotRequired()) {
            resnapshotRequired = true;
            return false;
        }
        snapshot = delta.replacement();
        return true;
    }

    public long dataRevision() {
        return snapshot == null ? 0 : snapshot.dataRevision();
    }

    public boolean resnapshotRequired() {
        return resnapshotRequired;
    }

    public Optional<FabricStateSnapshot> snapshot() {
        return Optional.ofNullable(snapshot);
    }
}
