package space.controlnet.ae2federation.observability.state;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;

public final class ObservationClientStates {
    private final Map<ObservationSession, ObservationClientState> states = new HashMap<>();
    private final Set<ObservationSession> closed = new HashSet<>();

    public boolean open(ObservationSession session) {
        java.util.Objects.requireNonNull(session);
        return !closed.contains(session) && states.putIfAbsent(session, new ObservationClientState(session)) == null;
    }

    public boolean apply(ObservationSnapshotEnvelope envelope) {
        var state = states.get(envelope.session());
        return state != null && state.applySnapshot(envelope);
    }

    public boolean apply(ObservationDeltaEnvelope envelope) {
        var state = states.get(envelope.session());
        return state != null && state.applyDelta(envelope);
    }

    public Optional<ObservationClientState> state(ObservationSession session) {
        return Optional.ofNullable(states.get(session));
    }

    public boolean close(ObservationSession session) {
        closed.add(session);
        return states.remove(session) != null;
    }

    public void reset() {
        states.clear();
        closed.clear();
    }
}
