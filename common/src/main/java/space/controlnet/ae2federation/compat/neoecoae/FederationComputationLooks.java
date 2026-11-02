package space.controlnet.ae2federation.compat.neoecoae;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * The formed Federation computation structures the client knows of, by controller, so the host's parts inside them
 * are drawn dark (user, 2026-11-02). Their controllers send their bounds; this refers to no Neo ECO class, so the
 * client's models may ask it whether Neo ECO is loaded or not.
 */
public final class FederationComputationLooks {
    private static final Map<BlockPos, BoundingBox> FORMED = new ConcurrentHashMap<>();
    private static Consumer<BoundingBox> repaint = bounds -> {
    };

    private FederationComputationLooks() {
    }

    /** What redraws the blocks of a structure whose look changed; the client sets it. */
    public static void registerRepaint(Consumer<BoundingBox> repaint) {
        FederationComputationLooks.repaint = Objects.requireNonNull(repaint);
    }

    /** The structure of the controller at {@code controller} is now {@code bounds}, or not formed when null. */
    public static void place(BlockPos controller, @Nullable BoundingBox bounds) {
        var old = bounds == null ? FORMED.remove(controller) : FORMED.put(controller.immutable(), bounds);
        if (Objects.equals(old, bounds)) return;
        if (old != null) repaint.accept(old);
        if (bounds != null) repaint.accept(bounds);
    }

    public static boolean contains(BlockPos position) {
        for (var bounds : FORMED.values()) {
            if (bounds.isInside(position)) return true;
        }
        return false;
    }

    /** The client left its level: the next one's controllers send theirs again. */
    public static void clear() {
        FORMED.clear();
    }
}
