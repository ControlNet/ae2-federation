package space.controlnet.ae2federation.observability.state;

import java.util.Objects;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.domain.FederationDomainReference;

public final class ObservationClientState {
    private final ObservationSession session;
    private @Nullable FederationDomainStateSnapshot snapshot;
    private boolean resnapshotRequired = true;

    public ObservationClientState(ObservationSession session) {
        this.session = Objects.requireNonNull(session);
    }

    public boolean applySnapshot(ObservationSnapshotEnvelope envelope) {
        Objects.requireNonNull(envelope);
        var replacement = envelope.snapshot();
        if (!session.equals(envelope.session()) || !session.scope().equals(replacement.scope())
                || snapshot != null && (replacement.topologyRevision() < snapshot.topologyRevision()
                        || replacement.policyRevision() < snapshot.policyRevision()
                        || replacement.dataRevision() < snapshot.dataRevision())) {
            return false;
        }
        snapshot = replacement;
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

    public Optional<FederationDomainStateSnapshot> snapshot() {
        return Optional.ofNullable(snapshot);
    }
}
