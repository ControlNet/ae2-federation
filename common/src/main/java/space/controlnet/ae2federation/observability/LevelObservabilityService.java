package space.controlnet.ae2federation.observability;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
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

/** One service per server: a domain it observes may span dimensions. */
public final class LevelObservabilityService implements AutoCloseable {
    private static final Map<MinecraftServer, LevelObservabilityService> SERVICES = new WeakHashMap<>();

    private final NativeTransportMeter transportMeter = new NativeTransportMeter(ObservationLimits.MAX_FLOWS);
    private final ObservationSubscriptionService subscriptions = new ObservationSubscriptionService(
            ObservationLimits.MAX_SUBSCRIPTIONS_PER_PLAYER, ObservationLimits.MAX_DELTA_EVENTS);
    /** The overworld: it names the server and keeps the game time of every dimension. */
    private final ServerLevel level;
    /** Accepted deliveries per directional rule over the last five seconds, for flow indication in the workspace. */
    private final Map<space.controlnet.ae2federation.policy.PolicyKey,
            space.controlnet.ae2federation.observability.meter.PairFlowWindow> pairFlows = new java.util.HashMap<>();
    public static final long PAIR_FLOW_WINDOW_TICKS = 100;
    private final java.util.Map<LaneKey, space.controlnet.ae2federation.observability.meter.PairFlowWindow> laneFlows =
            new java.util.HashMap<>();
    /** Scopes that accepted a flow since the last sweep; only subscribed ones are projected, once per tick. */
    private final java.util.Set<FederationDomainReference> flowedScopes = new java.util.HashSet<>();
    /**
     * The scope added to {@link #flowedScopes} last since the sweep, and the rule window recorded into last: one
     * binding's operations repeat the same scope and key objects, which an identity test answers without hashing.
     */
    private FederationDomainReference lastFlowedScope;
    private space.controlnet.ae2federation.policy.PolicyKey lastPairKey;
    private space.controlnet.ae2federation.observability.meter.PairFlowWindow lastPairWindow;
    private static final long PRUNE_INTERVAL_TICKS = 100;
    /** The key type a storage record named last and its unit; a type's id never changes. */
    private appeng.api.stacks.AEKeyType lastKeyType;
    private ResourceUnit lastKeyUnit;

    private LevelObservabilityService(MinecraftServer server) {
        this.level = server.overworld();
    }

    /**
     * The service {@link #get} returned last, read without the lock: every accepted energy demand looks it up.
     * Replaced under the lock, cleared on close.
     */
    private static volatile LevelObservabilityService last;

    /** The service of {@code level}'s server. */
    public static LevelObservabilityService get(ServerLevel level) {
        var server = level.getServer();
        var cached = last;
        return cached != null && cached.level.getServer() == server ? cached : getLocked(server);
    }

    private static synchronized LevelObservabilityService getLocked(MinecraftServer server) {
        var service = SERVICES.computeIfAbsent(server, LevelObservabilityService::new);
        last = service;
        return service;
    }

    /** Forgets the closed level's Providers; the server's subscriptions and meters stay. */
    public static synchronized void levelClosed(ServerLevel level) {
        ProviderObservationRegistry.closeLevel(level);
    }

    public static synchronized void closeServer(MinecraftServer server) {
        last = null;
        var service = SERVICES.remove(server);
        if (service != null) {
            service.close();
        }
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
        for (var scope : subscriptions.activeScopes()) {
            subscriptions.synchronizeProjection(new FederationDomainStateProjector(level).snapshot(scope),
                    flowedScopes.contains(scope) && transportMeter.window(scope).resnapshotRequired());
        }
        flowedScopes.clear();
        lastFlowedScope = null;
        var recovered = subscriptions.recoverRequired(this::snapshot);
        recovered.forEach(transportMeter::acknowledgeSnapshot);
        if (level.getGameTime() % PRUNE_INTERVAL_TICKS == 0) {
            prune();
        }
    }

    /** Drops meter windows of domain generations that no longer exist and flow windows that went quiet. */
    private void prune() {
        var registry = space.controlnet.ae2federation.domain.FederationDomainRegistryAccess.get(level);
        var active = subscriptions.activeScopes();
        transportMeter.retain(scope -> active.contains(scope) || registry.isCurrent(scope));
        var now = level.getGameTime();
        pairFlows.values().removeIf(window -> !window.summarize(now).active());
        lastPairKey = null;
        lastPairWindow = null;
        laneFlows.values().removeIf(window -> !window.summarize(now).active());
    }

    public FederationDomainStateSnapshot snapshot(FederationDomainReference scope) {
        return subscriptions.synchronizeProjection(new FederationDomainStateProjector(level).snapshot(scope), false);
    }

    public void recordAccepted(Iterable<FederationDomainReference> scopes, OperationEventId eventId, String resource, long amount,
            ResourceUnit unit, FlowState.Attribution attribution) {
        java.util.Objects.requireNonNull(eventId);
        java.util.Objects.requireNonNull(resource);
        recordAccepted(scopes, eventId, () -> resource, amount, unit, attribution);
    }

    /** {@code eventId} null: the operation is new, and each scope's record shares the id the first one made. */
    private void recordAccepted(Iterable<FederationDomainReference> scopes,
            @org.jetbrains.annotations.Nullable OperationEventId eventId, java.util.function.Supplier<String> resource,
            long amount, ResourceUnit unit, FlowState.Attribution attribution) {
        if (amount <= 0) {
            return;
        }
        var created = eventId == null;
        // The meter records every accepted operation at once; subscribers see it in the tick's sweep, which projects
        // each subscribed scope once however many operations it accepted.
        for (var scope : scopes) {
            boolean recorded;
            if (created) {
                eventId = transportMeter.recordNew(scope, eventId, resource, amount, unit, attribution);
                recorded = true;
            } else {
                recorded = transportMeter.recordAccepted(scope, eventId, resource, amount, unit, attribution);
            }
            if (recorded) {
                flowed(scope);
            }
        }
    }

    private void flowed(FederationDomainReference scope) {
        if (scope != lastFlowedScope) {
            flowedScopes.add(scope);
            lastFlowedScope = scope;
        }
    }

    /** Records one accepted delivery that a rule allowed; callers pass only amounts the target actually took. */
    public void recordPairFlow(space.controlnet.ae2federation.policy.PolicyKey key, long amount) {
        if (amount <= 0) return;
        if (key != lastPairKey) {
            lastPairWindow = pairFlows.computeIfAbsent(key,
                    ignored -> new space.controlnet.ae2federation.observability.meter.PairFlowWindow(
                            PAIR_FLOW_WINDOW_TICKS));
            lastPairKey = key;
        }
        lastPairWindow.record(level.getGameTime(), amount);
    }

    /**
     * One Provider lane (the channel to one Endpoint) in one direction, for one resource type: {@code provider} is the
     * Provider identity's string form and {@code keyType} the AE key type id, so item counts and fluid volumes stay apart.
     */
    public record LaneKey(String provider, int lane, boolean returned, String keyType) {
    }

    /** Records what one lane actually delivered to its Endpoint, or what the Endpoint returned through it. */
    public void recordLaneFlow(LaneKey key, long amount) {
        if (amount <= 0) return;
        laneFlows.computeIfAbsent(key, ignored -> new space.controlnet.ae2federation.observability.meter.PairFlowWindow(
                PAIR_FLOW_WINDOW_TICKS)).record(level.getGameTime(), amount);
    }

    /** Adds what one lane moved in one direction over the flow window, by resource type, to {@code totals}. */
    public void collectLaneFlow(String provider, int lane, boolean returned,
            space.controlnet.ae2federation.observability.meter.LaneFlowTotals totals) {
        var now = level.getGameTime();
        var entries = laneFlows.entrySet().iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            var key = entry.getKey();
            if (key.lane() != lane || key.returned() != returned || !key.provider().equals(provider)) continue;
            var summary = entry.getValue().summarize(now);
            if (summary.active()) totals.add(key.keyType(), summary);
            else entries.remove();
        }
    }

    public space.controlnet.ae2federation.observability.meter.PairFlowWindow.Summary pairFlow(
            space.controlnet.ae2federation.policy.PolicyKey key) {
        var window = pairFlows.get(key);
        if (window == null) return space.controlnet.ae2federation.observability.meter.PairFlowWindow.Summary.NONE;
        var summary = window.summarize(level.getGameTime());
        if (!summary.active()) {
            pairFlows.remove(key);
            lastPairKey = null;
            lastPairWindow = null;
        }
        return summary;
    }

    public void recordAcceptedStorage(Iterable<FederationDomainReference> scopes, AcceptedStorageOperation operation) {
        var key = operation.resource();
        var type = key.getType();
        if (type != lastKeyType) {
            lastKeyUnit = type.getId().getPath().contains("fluid") ? ResourceUnit.FLUID_DROPLET : ResourceUnit.ITEM;
            lastKeyType = type;
        }
        var unit = lastKeyUnit;
        // The resource id is a registry lookup and a new string; only a kept or reported flow needs it.
        recordAccepted(scopes, null, () -> key.getId().toString(), operation.amount(), unit,
                FlowState.Attribution.EXACT_OPERATION);
    }

    @Override
    public void close() {
        subscriptions.close();
    }
}
