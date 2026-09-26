package space.controlnet.ae2federation.observability;

import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.observability.state.ObservationDeltaEnvelope;

public final class ObservationDeltaSink {
    private static @Nullable BiConsumer<ServerPlayer, ObservationDeltaEnvelope> sender;

    private ObservationDeltaSink() {
    }

    public static void register(BiConsumer<ServerPlayer, ObservationDeltaEnvelope> replacement) {
        sender = java.util.Objects.requireNonNull(replacement);
    }

    public static boolean send(ServerPlayer player, ObservationDeltaEnvelope delta) {
        var current = sender;
        if (current == null) {
            return false;
        }
        current.accept(player, delta);
        ObservationRuntimeReceiptSink.delta(player, delta);
        return true;
    }
}
