package space.controlnet.ae2federation.observability.subscription;

import java.util.UUID;
import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.observability.state.ObservationDeltaEnvelope;
import space.controlnet.ae2federation.observability.state.ObservationSnapshotEnvelope;

public interface ObservationAuthority {
    UUID playerId();

    UUID sessionId();

    FabricReference scope();

    boolean current();

    default boolean deliver(ObservationDeltaEnvelope delta) {
        return false;
    }

    default boolean deliverSnapshot(ObservationSnapshotEnvelope snapshot) {
        return false;
    }
}
