package space.controlnet.ae2federation.bundle;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/** Sends a finished wrench selection to the server; the loader's network code registers the sender. */
public final class BundleFormSink {
    private static @Nullable Sender sender;

    private BundleFormSink() {
    }

    @FunctionalInterface
    public interface Sender {
        void send(BlockPos first, BlockPos second, BlockPos third, Direction face);
    }

    public static void register(Sender replacement) {
        sender = Objects.requireNonNull(replacement);
    }

    public static boolean send(BlockPos first, BlockPos second, BlockPos third, Direction face) {
        var current = sender;
        if (current == null) return false;
        current.send(first, second, third, face);
        return true;
    }
}
