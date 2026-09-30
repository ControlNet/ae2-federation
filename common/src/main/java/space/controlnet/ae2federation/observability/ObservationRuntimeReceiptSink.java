package space.controlnet.ae2federation.observability;

import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.domain.FederationDomainReference;
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

    /** Whether a listener takes flow receipts; without one, a meter need not build a flow nobody reads. */
    public static boolean wantsFlows() {
        var current = listener;
        return current != null && current.wantsFlows();
    }

    public static void flow(FederationDomainReference scope, FlowState flow) {
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

        default void flow(FederationDomainReference scope, FlowState flow) {
        }

        default boolean wantsFlows() {
            return true;
        }
    }
}
