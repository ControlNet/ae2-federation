package space.controlnet.ae2federation.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.fabric.FabricId;
import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.observability.state.FabricStateDelta;
import space.controlnet.ae2federation.observability.state.FabricStateSnapshot;
import space.controlnet.ae2federation.observability.state.ProviderState;
import space.controlnet.ae2federation.observability.id.ProviderId;
import space.controlnet.ae2federation.observability.subscription.ObservationAuthority;
import space.controlnet.ae2federation.observability.subscription.ObservationSubscriptionService;

class ObservationSubscriptionServiceTest {
    private static final FabricReference SCOPE = new FabricReference(new FabricId("physical:scope"), 7);
    private static final FabricReference OTHER = new FabricReference(new FabricId("physical:other"), 7);

    @Test
    void authorizationIsExactAndSupersedingGenerationClosesOldSubscription() {
        var service = new ObservationSubscriptionService(4, 2);
        var authority = new MutableAuthority(UUID.randomUUID(), UUID.randomUUID(), SCOPE, true);

        assertThrows(IllegalArgumentException.class, () -> service.subscribe(authority, OTHER));
        assertEquals(0, service.activeCount());
        var first = service.subscribe(authority, SCOPE);
        var second = service.subscribe(authority, SCOPE);

        assertTrue(first.closed());
        assertFalse(second.closed());
        assertEquals(first.generation() + 1, second.generation());
        assertEquals(1, service.activeCount());
    }

    @Test
    void closeInvalidationAndOverflowAreIdempotentAndBounded() {
        var service = new ObservationSubscriptionService(4, 1);
        var authority = new MutableAuthority(UUID.randomUUID(), UUID.randomUUID(), SCOPE, true);
        var subscription = service.subscribe(authority, SCOPE);
        service.publish(delta(0, 1));
        service.publish(delta(1, 2));

        assertTrue(subscription.resnapshotRequired());
        assertTrue(subscription.drain().isEmpty());
        authority.current = false;
        service.sweep();
        service.closePlayer(authority.playerId());
        service.closePlayer(authority.playerId());
        assertEquals(0, service.activeCount());
        assertEquals(1, service.removalCount());
    }

    @Test
    void overflowRecoverySendsFreshSessionBoundSnapshotAndResumesDelivery() {
        var service = new ObservationSubscriptionService(4, 1);
        var authority = new MutableAuthority(UUID.randomUUID(), UUID.randomUUID(), SCOPE, true);
        authority.snapshotDelivery = true;
        var subscription = service.subscribe(authority, SCOPE);
        service.publish(delta(0, 1));
        service.publish(delta(1, 2));

        var recovered = service.recoverRequired(scope -> delta(2, 3).replacement());

        assertEquals(java.util.Set.of(SCOPE), recovered);
        assertFalse(subscription.resnapshotRequired());
        assertEquals(1, authority.snapshotDeliveries);
    }

    @Test
    void nonFlowProjectionChangesPublishFullReplacementsWithoutTopologyChurn() {
        var service = new ObservationSubscriptionService(4, 2);
        var authority = new MutableAuthority(UUID.randomUUID(), UUID.randomUUID(), SCOPE, true);
        authority.deltaDelivery = true;
        service.synchronizeProjection(snapshot(0, java.util.List.of()), false);
        service.subscribe(authority, SCOPE);

        var added = service.synchronizeProjection(snapshot(0, java.util.List.of(provider("one"))), false);
        var unchanged = service.synchronizeProjection(snapshot(0, java.util.List.of(provider("one"))), false);
        var removed = service.synchronizeProjection(snapshot(0, java.util.List.of()), false);

        assertEquals(1, added.dataRevision());
        assertEquals(added, unchanged);
        assertEquals(2, removed.dataRevision());
        assertEquals(2, authority.deltaDeliveries);
        assertEquals(1, authority.lastDelta.replacement().topologyRevision());
        assertTrue(authority.lastDelta.replacement().providers().isEmpty());
    }

    private static FabricStateDelta delta(long baseRevision, long dataRevision) {
        return new FabricStateDelta(new FabricStateSnapshot(SCOPE, 1, 0, dataRevision, java.util.List.of(),
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of()), baseRevision, false);
    }

    private static FabricStateSnapshot snapshot(long dataRevision, java.util.List<ProviderState> providers) {
        return new FabricStateSnapshot(SCOPE, 1, 0, dataRevision, java.util.List.of(), providers,
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of());
    }

    private static ProviderState provider(String value) {
        return new ProviderState(SCOPE, ProviderId.of(SCOPE.fabricId(), value), "ready");
    }

    private static final class MutableAuthority implements ObservationAuthority {
        private final UUID playerId;
        private final UUID sessionId;
        private final FabricReference scope;
        private boolean current;
        private boolean snapshotDelivery;
        private boolean deltaDelivery;
        private int snapshotDeliveries;
        private int deltaDeliveries;
        private FabricStateDelta lastDelta;

        private MutableAuthority(UUID playerId, UUID sessionId, FabricReference scope, boolean current) {
            this.playerId = playerId;
            this.sessionId = sessionId;
            this.scope = scope;
            this.current = current;
        }

        @Override
        public UUID playerId() {
            return playerId;
        }

        @Override
        public UUID sessionId() {
            return sessionId;
        }

        @Override
        public FabricReference scope() {
            return scope;
        }

        @Override
        public boolean current() {
            return current;
        }

        @Override
        public boolean deliverSnapshot(
                space.controlnet.ae2federation.observability.state.ObservationSnapshotEnvelope snapshot) {
            snapshotDeliveries++;
            return snapshotDelivery;
        }

        @Override
        public boolean deliver(space.controlnet.ae2federation.observability.state.ObservationDeltaEnvelope delta) {
            deltaDeliveries++;
            lastDelta = delta.delta();
            return deltaDelivery;
        }
    }
}
