package space.controlnet.ae2federation.observability.state;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class ObservationClientStates {
    private final Map<ObservationSession, ObservationClientState> states = new HashMap<>();

    public boolean apply(ObservationSnapshotEnvelope envelope) {
        var state = states.computeIfAbsent(envelope.session(), ObservationClientState::new);
        return state.applySnapshot(envelope);
    }

    public boolean apply(ObservationDeltaEnvelope envelope) {
        var state = states.get(envelope.session());
        return state != null && state.applyDelta(envelope);
    }

    public Optional<ObservationClientState> state(ObservationSession session) {
        return Optional.ofNullable(states.get(session));
    }

    public void close(ObservationSession session) {
        states.remove(session);
    }
}
