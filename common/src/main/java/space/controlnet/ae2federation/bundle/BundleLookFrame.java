package space.controlnet.ae2federation.bundle;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

/**
 * The looks of bundled cells read in one game tick. Each layer's corner cell walks the whole stretch of layers a device
 * body takes to find where its beam turns; with the looks kept for the tick, every layer's look is read once per tick,
 * not once per layer that walks over it. Kept per thread, as the renderer reads it on its own.
 */
public final class BundleLookFrame {
    private static final ThreadLocal<BundleLookFrame> CURRENT = new ThreadLocal<>();

    private final WeakReference<BlockGetter> level;
    private final long tick;
    private final Map<BlockPos, Optional<BundleLook>> looks = new HashMap<>();

    public BundleLookFrame(BlockGetter level, long tick) {
        this.level = new WeakReference<>(level);
        this.tick = tick;
    }

    /** The frame of {@code level}'s current game tick on this thread. */
    public static BundleLookFrame of(Level level) {
        var frame = CURRENT.get();
        if (frame == null || frame.level.get() != level || frame.tick != level.getGameTime()) {
            frame = new BundleLookFrame(level, level.getGameTime());
            CURRENT.set(frame);
        }
        return frame;
    }

    /** {@link BundleLook#of(BlockGetter, BlockPos)}, read once in this frame. */
    public Optional<BundleLook> look(BlockPos pos) {
        var cached = looks.get(pos);
        if (cached != null) return cached;
        var getter = level.get();
        var look = getter == null ? Optional.<BundleLook>empty() : BundleLook.of(getter, pos);
        looks.put(pos.immutable(), look);
        return look;
    }
}
