package space.controlnet.ae2federation.observability.meter;

import java.util.List;
import space.controlnet.ae2federation.observability.state.FlowState;

public record NativeTransportWindow(long dataRevision, boolean resnapshotRequired, List<FlowState> events) {
    public NativeTransportWindow {
        if (dataRevision < 0) {
            throw new IllegalArgumentException("Data revision cannot be negative");
        }
        events = List.copyOf(events);
    }
}
