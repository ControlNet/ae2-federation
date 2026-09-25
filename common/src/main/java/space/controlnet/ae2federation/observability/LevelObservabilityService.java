package space.controlnet.ae2federation.observability;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.domain.FederationDomainReference;
import space.controlnet.ae2federation.observability.meter.NativeTransportMeter;
import space.controlnet.ae2federation.observability.meter.OperationEventId;
import space.controlnet.ae2federation.observability.state.FlowState;
import space.controlnet.ae2federation.observability.state.ResourceUnit;
import space.controlnet.ae2federation.observability.id.FlowId;
import space.controlnet.ae2federation.observability.subscription.ObservationSubscriptionService;
import space.controlnet.ae2federation.processing.provider.ProviderObservationRegistry;
import space.controlnet.ae2federation.observability.state.FederationDomainStateProjector;
import space.controlnet.ae2federation.observability.state.FederationDomainStateSnapshot;
import space.controlnet.ae2federation.storage.mount.AcceptedStorageOperation;

public final class LevelObservabilityService implements AutoCloseable {
    private static final Map<ServerLevel, LevelObservabilityService> SERVICES = new WeakHashMap<>();

    private final NativeTransportMeter transportMeter = new NativeTransportMeter(ObservationLimits.MAX_FLOWS);
    private final ObservationSubscriptionService subscriptions = new ObservationSubscriptionService(
            ObservationLimits.MAX_SUBSCRIPTIONS_PER_PLAYER, ObservationLimits.MAX_DELTA_EVENTS);
    private final ServerLevel level;

    private LevelObservabilityService(ServerLevel level) {
        this.level = level;
    }

    public static synchronized LevelObservabilityService get(ServerLevel level) {
        return SERVICES.computeIfAbsent(level, LevelObservabilityService::new);
    }

    public static synchronized void closeLevel(ServerLevel level) {
        var service = SERVICES.remove(level);
        if (service != null) {
            service.close();
        }
        ProviderObservationRegistry.closeLevel(level);
    }

    public static synchronized void closePlayer(UUID playerId) {
        SERVICES.values().forEach(service -> service.subscriptions.closePlayer(playerId));
    }

    public static synchronized void sweepAll() {
        SERVICES.values().forEach(LevelObservabilityService::sweep);
    }

    public NativeTransportMeter transportMeter() {
        return transportMeter;
    }

    public ObservationSubscriptionService subscriptions() {
        return subscriptions;
    }

    public void sweep() {
        subscriptions.activeScopes().forEach(this::snapshot);
        var recovered = subscriptions.recoverRequired(this::snapshot);
        recovered.forEach(transportMeter::acknowledgeSnapshot);
    }

    public FederationDomainStateSnapshot snapshot(FederationDomainReference scope) {
        return subscriptions.synchronizeProjection(new FederationDomainStateProjector(level).snapshot(scope), false);
    }

    public void recordAccepted(Iterable<FederationDomainReference> scopes, OperationEventId eventId, String resource, long amount,
            ResourceUnit unit, FlowState.Attribution attribution) {
        if (amount <= 0) {
            return;
        }
        for (var scope : scopes) {
            if (!transportMeter.recordAccepted(scope, eventId, resource, amount, unit, attribution)) {
                continue;
            }
            var window = transportMeter.window(scope);
            subscriptions.synchronizeProjection(new FederationDomainStateProjector(level).snapshot(scope),
                    window.resnapshotRequired());
        }
    }

    public void recordAcceptedStorage(Iterable<FederationDomainReference> scopes, AcceptedStorageOperation operation) {
        var key = operation.resource();
        var typePath = key.getType().getId().getPath();
        var unit = typePath.contains("fluid") ? ResourceUnit.FLUID_DROPLET : ResourceUnit.ITEM;
        recordAccepted(scopes, operation.eventId(), key.getId().toString(), operation.amount(), unit,
                FlowState.Attribution.EXACT_OPERATION);
    }

    @Override
    public void close() {
        subscriptions.close();
    }
}
