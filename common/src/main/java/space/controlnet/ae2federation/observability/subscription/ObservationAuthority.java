package space.controlnet.ae2federation.observability.subscription;

import java.util.UUID;
import space.controlnet.ae2federation.domain.FederationDomainReference;
import space.controlnet.ae2federation.observability.state.ObservationDeltaEnvelope;
import space.controlnet.ae2federation.observability.state.ObservationSession;
import space.controlnet.ae2federation.observability.state.ObservationSnapshotEnvelope;

public interface ObservationAuthority {
    UUID playerId();

    UUID sessionId();

    FederationDomainReference scope();

    boolean current();

    default boolean deliver(ObservationDeltaEnvelope delta) {
        return false;
    }

    default boolean deliverSnapshot(ObservationSnapshotEnvelope snapshot) {
        return false;
    }

    default void close(ObservationSession session) {
    }
}
