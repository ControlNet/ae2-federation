package space.controlnet.ae2federation.observability.meter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.ArrayDeque;
import space.controlnet.ae2federation.domain.FederationDomainReference;
import space.controlnet.ae2federation.observability.id.FlowId;
import space.controlnet.ae2federation.observability.state.FlowState;
import space.controlnet.ae2federation.observability.state.ResourceUnit;
import space.controlnet.ae2federation.observability.ObservationRuntimeReceiptSink;

public final class NativeTransportMeter {
    private final int eventLimit;
    private final Map<FederationDomainReference, WindowState> windows = new HashMap<>();
    /** The window recorded into last: one binding's operations repeat the same scope object. Cleared by retain. */
    private FederationDomainReference lastScope;
    private WindowState lastState;

    public NativeTransportMeter(int eventLimit) {
        if (eventLimit < 1) {
            throw new IllegalArgumentException("Event limit must be positive");
        }
        this.eventLimit = eventLimit;
    }

    public boolean recordAccepted(FederationDomainReference scope, OperationEventId eventId, String resource, long amount,
            ResourceUnit unit, FlowState.Attribution attribution) {
        Objects.requireNonNull(resource);
        return recordAccepted(scope, eventId, () -> resource, amount, unit, attribution);
    }

    /** As above, but names the resource only when the flow is built: a window that keeps no events never needs it. */
    public boolean recordAccepted(FederationDomainReference scope, OperationEventId eventId,
            java.util.function.Supplier<String> resource, long amount, ResourceUnit unit,
            FlowState.Attribution attribution) {
        Objects.requireNonNull(scope);
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(unit);
        Objects.requireNonNull(attribution);
        if (amount < 0) {
            throw new IllegalArgumentException("Accepted amount cannot be negative");
        }
        if (amount == 0) {
            return false;
        }
        var state = scope == lastScope ? lastState : windows.computeIfAbsent(scope, ignored -> new WindowState());
        lastScope = scope;
        lastState = state;
        var receipts = ObservationRuntimeReceiptSink.wantsFlows();
        // A window that already overflowed keeps no events until its next snapshot; without a receipt listener its
        // flow, with the hashed id, would be built for nobody.
        var flow = state.resnapshotRequired && !receipts ? null : new FlowState(scope,
                FlowId.forEvent(scope.federationDomainId(), eventId.value()), eventId, resource.get(), amount, unit,
                attribution, false);
        if (!state.eventIds.add(eventId)) {
            return false;
        }
        state.eventOrder.addLast(eventId);
        if (state.eventOrder.size() > eventLimit) {
            state.eventIds.remove(state.eventOrder.removeFirst());
        }
        state.dataRevision = Math.incrementExact(state.dataRevision);
        if (flow == null) {
            return true;
        }
        if (!state.resnapshotRequired) {
            state.events.add(flow);
            if (state.events.size() > eventLimit) {
                state.events.clear();
                state.resnapshotRequired = true;
            }
        }
        if (receipts) {
            ObservationRuntimeReceiptSink.flow(scope, flow);
        }
        return true;
    }

    public NativeTransportWindow window(FederationDomainReference scope) {
        var state = windows.get(Objects.requireNonNull(scope));
        return state == null
                ? new NativeTransportWindow(0, false, java.util.List.of())
                : new NativeTransportWindow(state.dataRevision, state.resnapshotRequired, state.events);
    }

    /** Keeps only the windows whose scope {@code keep} accepts. */
    public void retain(java.util.function.Predicate<FederationDomainReference> keep) {
        windows.keySet().removeIf(scope -> !keep.test(scope));
        lastScope = null;
        lastState = null;
    }

    public void acknowledgeSnapshot(FederationDomainReference scope) {
        var state = windows.get(Objects.requireNonNull(scope));
        if (state != null) {
            state.events.clear();
            state.resnapshotRequired = false;
        }
    }

    private static final class WindowState {
        private final Set<OperationEventId> eventIds = new HashSet<>();
        private final ArrayDeque<OperationEventId> eventOrder = new ArrayDeque<>();
        private final ArrayList<FlowState> events = new ArrayList<>();
        private long dataRevision;
        private boolean resnapshotRequired;
    }
}
