package space.controlnet.ae2federation.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.fabric.FabricId;
import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.observability.state.FabricStateDelta;
import space.controlnet.ae2federation.observability.state.FabricStateSnapshot;
import space.controlnet.ae2federation.observability.state.ObservationClientState;
import space.controlnet.ae2federation.observability.state.MemberState;
import space.controlnet.ae2federation.observability.state.ObservationDeltaEnvelope;
import space.controlnet.ae2federation.observability.state.ObservationSession;
import space.controlnet.ae2federation.observability.state.ObservationSnapshotEnvelope;
import space.controlnet.ae2federation.observability.id.MemberId;
import space.controlnet.ae2federation.identity.NetworkId;

class ObservationClientStateTest {
    @Test
    void rejectsStaleReplayAndOutOfOrderDeltaWithoutMutation() {
        var scope = new FabricReference(new FabricId("physical:client"), 2);
        var session = ObservationSession.create(UUID.randomUUID(), UUID.randomUUID(), scope, 1);
        var member = new MemberState(scope, MemberId.forNetwork(scope.fabricId(), new NetworkId(UUID.randomUUID())),
                new NetworkId(UUID.randomUUID()), "online");
        var initial = new FabricStateSnapshot(scope, 10, 4, 5, List.of(member), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of());
        var state = new ObservationClientState(session);
        state.applySnapshot(new ObservationSnapshotEnvelope(session, initial));

        var replacement = new FabricStateSnapshot(scope, 10, 4, 6, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of());
        assertTrue(state.applyDelta(new ObservationDeltaEnvelope(session,
                new FabricStateDelta(replacement, 5, false))));
        assertFalse(state.applyDelta(new ObservationDeltaEnvelope(session,
                new FabricStateDelta(new FabricStateSnapshot(scope, 10, 4, 7, List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of()), 5, false))));
        assertEquals(6, state.dataRevision());
        assertTrue(state.snapshot().orElseThrow().members().isEmpty());
        assertTrue(state.resnapshotRequired());
    }

    @Test
    void rejectsPayloadFromAnotherObservationSessionWithoutMutation() {
        var scope = new FabricReference(new FabricId("physical:client-session"), 3);
        var first = ObservationSession.create(UUID.randomUUID(), UUID.randomUUID(), scope, 1);
        var second = ObservationSession.create(first.playerId(), UUID.randomUUID(), scope, 1);
        var initial = new FabricStateSnapshot(scope, 1, 2, 3, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of());
        var state = new ObservationClientState(first);
        state.applySnapshot(new ObservationSnapshotEnvelope(first, initial));

        assertFalse(state.applyDelta(new ObservationDeltaEnvelope(second,
                new FabricStateDelta(new FabricStateSnapshot(scope, 1, 2, 4, List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of()), 3, false))));
        assertEquals(initial, state.snapshot().orElseThrow());
    }
}
