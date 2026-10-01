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
        Objects.requireNonNull(eventId);
        if (!checkAmount(scope, unit, attribution, amount)) {
            return false;
        }
        var state = state(scope);
        var receipts = ObservationRuntimeReceiptSink.wantsFlows();
        // A window that already overflowed keeps no events until its next snapshot; without a receipt listener its
        // flow, with the hashed id, would be built for nobody.
        var flow = state.resnapshotRequired && !receipts ? null : new FlowState(scope,
                FlowId.forEvent(scope.federationDomainId(), eventId.value()), eventId, resource.get(), amount, unit,
                attribution, false);
        if (!state.eventIds.add(eventId)) {
            return false;
        }
        admit(state, eventId);
        keep(state, scope, flow, receipts);
        return true;
    }

    /**
     * Records one accepted operation that no other record can repeat, so it is not checked against the window's ids,
     * though it still takes its place in the window. {@code eventId} is the id this operation already has from an
     * earlier scope, or null: an id is made only when a flow is built. Returns the operation's id, null while none
     * was needed.
     */
    public @org.jetbrains.annotations.Nullable OperationEventId recordNew(FederationDomainReference scope,
            @org.jetbrains.annotations.Nullable OperationEventId eventId, java.util.function.Supplier<String> resource,
            long amount, ResourceUnit unit, FlowState.Attribution attribution) {
        if (!checkAmount(scope, unit, attribution, amount)) {
            return eventId;
        }
        var state = state(scope);
        var receipts = ObservationRuntimeReceiptSink.wantsFlows();
        FlowState flow = null;
        if (!state.resnapshotRequired || receipts) {
            if (eventId == null) {
                eventId = OperationEventId.create();
            }
            flow = new FlowState(scope, FlowId.forEvent(scope.federationDomainId(), eventId.value()), eventId,
                    resource.get(), amount, unit, attribution, false);
        }
        admit(state, null);
        keep(state, scope, flow, receipts);
        return eventId;
    }

    /** Validates one record's arguments; false for a zero amount, which records nothing. */
    private static boolean checkAmount(FederationDomainReference scope, ResourceUnit unit,
            FlowState.Attribution attribution, long amount) {
        Objects.requireNonNull(scope);
        Objects.requireNonNull(unit);
        Objects.requireNonNull(attribution);
        if (amount < 0) {
            throw new IllegalArgumentException("Accepted amount cannot be negative");
        }
        return amount != 0;
    }

    private WindowState state(FederationDomainReference scope) {
        var state = scope == lastScope ? lastState : windows.computeIfAbsent(scope, ignored -> new WindowState());
        lastScope = scope;
        lastState = state;
        return state;
    }

    /**
     * Places one recorded event in the window: an id that can repeat ({@code eventId} non-null) is kept until
     * {@link #eventLimit} later events were admitted, as the last {@code eventLimit} events hold it.
     */
    private void admit(WindowState state, @org.jetbrains.annotations.Nullable OperationEventId eventId) {
        var sequence = ++state.sequence;
        if (eventId != null) {
            state.admitted.addLast(new Admitted(eventId, sequence));
        }
        var oldest = state.admitted.peekFirst();
        while (oldest != null && oldest.sequence() <= sequence - eventLimit) {
            state.admitted.removeFirst();
            state.eventIds.remove(oldest.eventId());
            oldest = state.admitted.peekFirst();
        }
        state.dataRevision = Math.incrementExact(state.dataRevision);
    }

    private void keep(WindowState state, FederationDomainReference scope, @org.jetbrains.annotations.Nullable FlowState flow,
            boolean receipts) {
        if (flow == null) {
            return;
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

    private record Admitted(OperationEventId eventId, long sequence) {
    }

    private static final class WindowState {
        /** The window's ids that can repeat; {@link #recordNew} events keep none. */
        private final Set<OperationEventId> eventIds = new HashSet<>();
        /** {@link #eventIds} with the sequence each was admitted at, oldest first. */
        private final ArrayDeque<Admitted> admitted = new ArrayDeque<>();
        /** Events admitted so far. */
        private long sequence;
        private final ArrayList<FlowState> events = new ArrayList<>();
        private long dataRevision;
        private boolean resnapshotRequired;
    }
}
