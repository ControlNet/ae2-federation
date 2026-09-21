package space.controlnet.ae2federation.observability;

import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.observability.state.FlowState;
import space.controlnet.ae2federation.observability.state.ObservationDeltaEnvelope;
import space.controlnet.ae2federation.observability.state.ObservationSession;
import space.controlnet.ae2federation.observability.state.ObservationSnapshotEnvelope;

public final class ObservationRuntimeReceiptSink {
    private static @Nullable Listener listener;

    private ObservationRuntimeReceiptSink() {
    }

    public static void register(Listener replacement) {
        listener = Objects.requireNonNull(replacement);
    }

    public static void subscription(ObservationSession session, SubscriptionEvent event) {
        var current = listener;
        if (current != null) {
            current.subscription(session, event);
        }
    }

    public static void snapshot(ServerPlayer player, ObservationSnapshotEnvelope snapshot) {
        var current = listener;
        if (current != null) {
            current.snapshot(player, snapshot);
        }
    }

    public static void delta(ServerPlayer player, ObservationDeltaEnvelope delta) {
        var current = listener;
        if (current != null) {
            current.delta(player, delta);
        }
    }

    public static void flow(FabricReference scope, FlowState flow) {
        var current = listener;
        if (current != null) {
            current.flow(scope, flow);
        }
    }

    public enum SubscriptionEvent {
        OPENED,
        CLOSED,
        RECOVERED
    }

    public interface Listener {
        default void subscription(ObservationSession session, SubscriptionEvent event) {
        }

        default void snapshot(ServerPlayer player, ObservationSnapshotEnvelope snapshot) {
        }

        default void delta(ServerPlayer player, ObservationDeltaEnvelope delta) {
        }

        default void flow(FabricReference scope, FlowState flow) {
        }
    }
}
