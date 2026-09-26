package space.controlnet.ae2federation.observability;

import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.observability.state.ObservationSnapshotEnvelope;

public final class ObservationSnapshotSink {
    private static @Nullable BiConsumer<ServerPlayer, ObservationSnapshotEnvelope> sender;

    private ObservationSnapshotSink() {
    }

    public static void register(BiConsumer<ServerPlayer, ObservationSnapshotEnvelope> replacement) {
        sender = java.util.Objects.requireNonNull(replacement);
    }

    public static boolean send(ServerPlayer player, ObservationSnapshotEnvelope snapshot) {
        var current = sender;
        if (current != null) {
            current.accept(player, snapshot);
            ObservationRuntimeReceiptSink.snapshot(player, snapshot);
            return true;
        }
        return false;
    }
}
