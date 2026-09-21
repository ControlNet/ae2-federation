package space.controlnet.ae2federation.client.policy;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.observability.LevelObservabilityService;
import space.controlnet.ae2federation.observability.ObservationDeltaSink;
import space.controlnet.ae2federation.observability.ObservationSnapshotSink;
import space.controlnet.ae2federation.observability.state.ObservationDeltaEnvelope;
import space.controlnet.ae2federation.observability.state.ObservationSnapshotEnvelope;
import space.controlnet.ae2federation.observability.subscription.ObservationAuthority;
import space.controlnet.ae2federation.observability.subscription.ObservationSubscription;

final class FabricPolicyObservation implements ObservationAuthority {
    private final FabricPolicySession session;
    private final ServerPlayer player;
    private final ServerLevel level;
    private final FabricReference scope;
    private final UUID sessionId = UUID.randomUUID();

    FabricPolicyObservation(FabricPolicySession session, ServerPlayer player, FabricReference scope) {
        this.session = session;
        this.player = player;
        level = player.serverLevel();
        this.scope = scope;
    }

    Optional<ObservationSubscription> open() {
        if (!current()) {
            return Optional.empty();
        }
        var service = LevelObservabilityService.get(level);
        var snapshot = service.snapshot(scope);
        var subscription = service.subscriptions().subscribe(this, scope);
        if (!ObservationSnapshotSink.send(player, new ObservationSnapshotEnvelope(subscription.session(), snapshot))) {
            service.subscriptions().close(subscription);
            return Optional.empty();
        }
        service.transportMeter().acknowledgeSnapshot(scope);
        return Optional.of(subscription);
    }

    void close(ObservationSubscription subscription) {
        LevelObservabilityService.get(level).subscriptions().close(subscription);
    }

    @Override
    public UUID playerId() {
        return player.getUUID();
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
        return session.isStillValid(player) && FabricRegistryAccess.get(level).isCurrent(scope);
    }

    @Override
    public boolean deliver(ObservationDeltaEnvelope delta) {
        return current() && ObservationDeltaSink.send(player, delta);
    }

    @Override
    public boolean deliverSnapshot(ObservationSnapshotEnvelope snapshot) {
        return current() && ObservationSnapshotSink.send(player, snapshot);
    }
}
