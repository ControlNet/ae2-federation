package space.controlnet.ae2federation.observability.subscription;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import space.controlnet.ae2federation.domain.FederationDomainReference;
import space.controlnet.ae2federation.observability.id.SubscriptionId;
import space.controlnet.ae2federation.observability.state.FederationDomainStateDelta;
import space.controlnet.ae2federation.observability.state.FederationDomainStateSnapshot;
import space.controlnet.ae2federation.observability.state.ObservationSession;
import space.controlnet.ae2federation.observability.state.ObservationSnapshotEnvelope;
import space.controlnet.ae2federation.observability.ObservationRuntimeReceiptSink;

public final class ObservationSubscriptionService implements AutoCloseable {
    private final int playerLimit;
    private final int queueLimit;
    private final Map<SubscriptionKey, ObservationSubscription> subscriptions = new HashMap<>();
    private final Map<FederationDomainReference, FederationDomainStateSnapshot> projections = new HashMap<>();
    private long removalCount;

    public ObservationSubscriptionService(int playerLimit, int queueLimit) {
        if (playerLimit < 1 || queueLimit < 1) {
            throw new IllegalArgumentException("Subscription limits must be positive");
        }
        this.playerLimit = playerLimit;
        this.queueLimit = queueLimit;
    }

    public ObservationSubscription subscribe(ObservationAuthority authority, FederationDomainReference scope) {
        Objects.requireNonNull(authority);
        Objects.requireNonNull(scope);
        if (!authority.current() || !scope.equals(authority.scope())) {
            throw new IllegalArgumentException("Observation authority does not match the requested Federation Domain scope");
        }
        var key = new SubscriptionKey(authority.playerId(), authority.sessionId(), scope);
        var existing = subscriptions.get(key);
        if (existing == null && subscriptionsFor(authority.playerId()) >= playerLimit) {
            throw new IllegalStateException("Player observation subscription limit reached");
        }
        var generation = existing == null ? 1 : Math.incrementExact(existing.generation());
        if (existing != null) {
            close(key, existing);
        }
        var subscription = new ObservationSubscription(
                ObservationSession.create(authority.playerId(), authority.sessionId(), scope, generation), authority,
                queueLimit);
        subscriptions.put(key, subscription);
        ObservationRuntimeReceiptSink.subscription(subscription.session(),
                ObservationRuntimeReceiptSink.SubscriptionEvent.OPENED);
        return subscription;
    }

    public void publish(FederationDomainStateDelta delta) {
        Objects.requireNonNull(delta);
        sweep();
        subscriptions.values().stream().filter(subscription -> subscription.scope().equals(delta.scope()))
                .forEach(subscription -> subscription.publish(delta));
    }

    public FederationDomainStateSnapshot synchronizeProjection(FederationDomainStateSnapshot observed, boolean resnapshotRequired) {
        Objects.requireNonNull(observed);
        var previous = projections.get(observed.scope());
        if (previous == null) {
            projections.put(observed.scope(), observed);
            return observed;
        }
        if (!resnapshotRequired && observed.dataRevision() <= previous.dataRevision()
                && sameProjection(previous, observed)) {
            return previous;
        }
        var revision = Math.max(Math.incrementExact(previous.dataRevision()), observed.dataRevision());
        var replacement = new FederationDomainStateSnapshot(observed.scope(), observed.topologyRevision(),
                observed.policyRevision(), revision, observed.members(), observed.providers(), observed.endpoints(),
                observed.policies(), observed.locks(), observed.tasks(), observed.flows());
        projections.put(observed.scope(), replacement);
        publish(new FederationDomainStateDelta(replacement, previous.dataRevision(), resnapshotRequired));
        return replacement;
    }

    public java.util.Set<FederationDomainReference> activeScopes() {
        sweep();
        return subscriptions.values().stream().map(ObservationSubscription::scope)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public boolean recover(ObservationSubscription subscription, FederationDomainStateSnapshot snapshot) {
        Objects.requireNonNull(subscription);
        Objects.requireNonNull(snapshot);
        if (!subscriptions.containsValue(subscription)) {
            return false;
        }
        if (!subscription.recover(new ObservationSnapshotEnvelope(subscription.session(), snapshot))) {
            close(subscription);
            return false;
        }
        ObservationRuntimeReceiptSink.subscription(subscription.session(),
                ObservationRuntimeReceiptSink.SubscriptionEvent.RECOVERED);
        return true;
    }

    public void sweep() {
        for (var entry : new ArrayList<>(subscriptions.entrySet())) {
            if (!entry.getValue().authority().current()) {
                close(entry.getKey(), entry.getValue());
            }
        }
    }

    public java.util.Set<FederationDomainReference> recoverRequired(Function<FederationDomainReference, FederationDomainStateSnapshot> projector) {
        Objects.requireNonNull(projector);
        sweep();
        var recovered = new java.util.HashSet<FederationDomainReference>();
        for (var subscription : new ArrayList<>(subscriptions.values())) {
            if (subscription.resnapshotRequired() && recover(subscription, projector.apply(subscription.scope()))) {
                recovered.add(subscription.scope());
            }
        }
        return java.util.Set.copyOf(recovered);
    }

    public void closePlayer(UUID playerId) {
        Objects.requireNonNull(playerId);
        for (var entry : new ArrayList<>(subscriptions.entrySet())) {
            if (entry.getKey().playerId().equals(playerId)) {
                close(entry.getKey(), entry.getValue());
            }
        }
    }

    public void close(ObservationSubscription target) {
        Objects.requireNonNull(target);
        for (var entry : new ArrayList<>(subscriptions.entrySet())) {
            if (entry.getValue() == target) {
                close(entry.getKey(), target);
                return;
            }
        }
    }

    public int activeCount() {
        return subscriptions.size();
    }

    public long removalCount() {
        return removalCount;
    }

    @Override
    public void close() {
        for (var entry : new ArrayList<>(subscriptions.entrySet())) {
            close(entry.getKey(), entry.getValue());
        }
        projections.clear();
    }

    private long subscriptionsFor(UUID playerId) {
        return subscriptions.keySet().stream().filter(key -> key.playerId().equals(playerId)).count();
    }

    private void close(SubscriptionKey key, ObservationSubscription subscription) {
        if (subscriptions.remove(key, subscription)) {
            subscription.authority().close(subscription.session());
            subscription.close();
            ObservationRuntimeReceiptSink.subscription(subscription.session(),
                    ObservationRuntimeReceiptSink.SubscriptionEvent.CLOSED);
            removalCount = Math.incrementExact(removalCount);
            if (subscriptions.values().stream().noneMatch(value -> value.scope().equals(key.scope()))) {
                projections.remove(key.scope());
            }
        }
    }

    private static boolean sameProjection(FederationDomainStateSnapshot first, FederationDomainStateSnapshot second) {
        return first.scope().equals(second.scope())
                && first.topologyRevision() == second.topologyRevision()
                && first.policyRevision() == second.policyRevision()
                && first.members().equals(second.members())
                && first.providers().equals(second.providers())
                && first.endpoints().equals(second.endpoints())
                && first.policies().equals(second.policies())
                && first.locks().equals(second.locks())
                && first.tasks().equals(second.tasks())
                && first.flows().equals(second.flows());
    }

    private record SubscriptionKey(UUID playerId, UUID sessionId, FederationDomainReference scope) {
    }
}
