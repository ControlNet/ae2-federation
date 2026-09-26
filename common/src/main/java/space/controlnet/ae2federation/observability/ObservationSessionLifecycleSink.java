package space.controlnet.ae2federation.observability;

import java.util.Objects;
import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.observability.state.ObservationSession;

public final class ObservationSessionLifecycleSink {
    private static @Nullable BiConsumer<ServerPlayer, ObservationSession> opener;
    private static @Nullable BiConsumer<ServerPlayer, ObservationSession> closer;

    private ObservationSessionLifecycleSink() {
    }

    public static void register(BiConsumer<ServerPlayer, ObservationSession> openSender,
            BiConsumer<ServerPlayer, ObservationSession> closeSender) {
        opener = Objects.requireNonNull(openSender);
        closer = Objects.requireNonNull(closeSender);
    }

    public static boolean open(ServerPlayer player, ObservationSession session) {
        var current = opener;
        if (current == null) {
            return false;
        }
        current.accept(player, session);
        return true;
    }

    public static void close(ServerPlayer player, ObservationSession session) {
        var current = closer;
        if (current != null) {
            current.accept(player, session);
        }
    }
}
