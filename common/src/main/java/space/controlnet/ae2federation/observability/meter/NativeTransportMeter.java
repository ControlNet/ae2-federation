package space.controlnet.ae2federation.observability.meter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.ArrayDeque;
import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.observability.id.FlowId;
import space.controlnet.ae2federation.observability.state.FlowState;
import space.controlnet.ae2federation.observability.state.ResourceUnit;
import space.controlnet.ae2federation.observability.ObservationRuntimeReceiptSink;

public final class NativeTransportMeter {
    private final int eventLimit;
    private final Map<FabricReference, WindowState> windows = new HashMap<>();

    public NativeTransportMeter(int eventLimit) {
        if (eventLimit < 1) {
            throw new IllegalArgumentException("Event limit must be positive");
        }
        this.eventLimit = eventLimit;
    }

    public boolean recordAccepted(FabricReference scope, OperationEventId eventId, String resource, long amount,
            ResourceUnit unit, FlowState.Attribution attribution) {
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
        var flow = new FlowState(scope, FlowId.forEvent(scope.fabricId(), eventId.value()), eventId,
                resource, amount, unit, attribution, false);
        var state = windows.computeIfAbsent(scope, ignored -> new WindowState());
        if (!state.eventIds.add(eventId)) {
            return false;
        }
        state.eventOrder.addLast(eventId);
        if (state.eventOrder.size() > eventLimit) {
            state.eventIds.remove(state.eventOrder.removeFirst());
        }
        state.dataRevision = Math.incrementExact(state.dataRevision);
        if (!state.resnapshotRequired) {
            state.events.add(flow);
            if (state.events.size() > eventLimit) {
                state.events.clear();
                state.resnapshotRequired = true;
            }
        }
        ObservationRuntimeReceiptSink.flow(scope, flow);
        return true;
    }

    public NativeTransportWindow window(FabricReference scope) {
        var state = windows.get(Objects.requireNonNull(scope));
        return state == null
                ? new NativeTransportWindow(0, false, java.util.List.of())
                : new NativeTransportWindow(state.dataRevision, state.resnapshotRequired, state.events);
    }

    public void acknowledgeSnapshot(FabricReference scope) {
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
