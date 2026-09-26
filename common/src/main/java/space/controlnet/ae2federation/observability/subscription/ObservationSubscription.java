package space.controlnet.ae2federation.observability.subscription;

import java.util.ArrayDeque;
import java.util.List;
import space.controlnet.ae2federation.domain.FederationDomainReference;
import space.controlnet.ae2federation.observability.id.SubscriptionId;
import space.controlnet.ae2federation.observability.state.FederationDomainStateDelta;
import space.controlnet.ae2federation.observability.state.ObservationDeltaEnvelope;
import space.controlnet.ae2federation.observability.state.ObservationSession;
import space.controlnet.ae2federation.observability.state.ObservationSnapshotEnvelope;

public final class ObservationSubscription {
    private final SubscriptionId id;
    private final ObservationAuthority authority;
    private final FederationDomainReference scope;
    private final long generation;
    private final ObservationSession session;
    private final int queueLimit;
    private final ArrayDeque<FederationDomainStateDelta> pending = new ArrayDeque<>();
    private boolean closed;
    private boolean resnapshotRequired;

    ObservationSubscription(ObservationSession session, ObservationAuthority authority, int queueLimit) {
        this.session = session;
        var id = session.subscriptionId();
        this.id = id;
        this.authority = authority;
        this.scope = session.scope();
        this.generation = session.subscriptionGeneration();
        this.queueLimit = queueLimit;
    }

    public SubscriptionId id() {
        return id;
    }

    public long generation() {
        return generation;
    }

    public boolean closed() {
        return closed;
    }

    public boolean resnapshotRequired() {
        return resnapshotRequired;
    }

    public ObservationSession session() {
        return session;
    }

    public List<FederationDomainStateDelta> drain() {
        if (resnapshotRequired) {
            pending.clear();
            return List.of();
        }
        var result = List.copyOf(pending);
        pending.clear();
        return result;
    }

    ObservationAuthority authority() {
        return authority;
    }

    FederationDomainReference scope() {
        return scope;
    }

    void publish(FederationDomainStateDelta delta) {
        if (closed || resnapshotRequired) {
            return;
        }
        if (authority.deliver(new ObservationDeltaEnvelope(session, delta))) {
            return;
        }
        if (pending.size() == queueLimit) {
            pending.clear();
            resnapshotRequired = true;
            return;
        }
        pending.addLast(delta);
    }

    boolean recover(ObservationSnapshotEnvelope snapshot) {
        if (closed || !session.equals(snapshot.session()) || !authority.current()
                || !authority.deliverSnapshot(snapshot)) {
            return false;
        }
        pending.clear();
        resnapshotRequired = false;
        return true;
    }

    void close() {
        closed = true;
        pending.clear();
    }
}
