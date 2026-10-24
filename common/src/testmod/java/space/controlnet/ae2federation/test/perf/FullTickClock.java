package space.controlnet.ae2federation.test.perf;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * TEST-ONLY wall time of whole server ticks, from the first {@link ServerTickEvent.Pre} listener to the last
 * {@link ServerTickEvent.Post} listener. {@link MinecraftServer#getTickTimesNanos()} stops before the Post event, where
 * AE2 ticks its Grids (every bus, every storage service) and Federation runs its tick work, so it misses both.
 */
public final class FullTickClock {
    private static final long[] TIMES = new long[100];
    private static boolean registered;
    private static long started;
    private static int completed;

    private FullTickClock() {
    }

    private static synchronized void register() {
        if (registered) return;
        registered = true;
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ServerTickEvent.Pre.class,
                event -> started = System.nanoTime());
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ServerTickEvent.Post.class, event -> {
            if (started == 0) return;
            TIMES[completed % TIMES.length] = System.nanoTime() - started;
            completed++;
        });
    }

    /** A window over whole server ticks, opened during the current tick. */
    public static final class Window {
        private final MinecraftServer server;
        private final int startTick;
        private final int startCompleted;

        public Window(MinecraftServer server) {
            register();
            this.server = server;
            startTick = server.getTickCount();
            startCompleted = completed;
        }

        public int ticks() {
            return server.getTickCount() - startTick;
        }

        /** The whole ticks completed after the one that opened the window, at most the last 100. */
        private long[] times() {
            // The opening tick completes after the window opens; it is the first one counted from startCompleted.
            var count = Math.min(completed - startCompleted - 1, TIMES.length);
            if (count <= 0) throw new IllegalStateException("Tick window has no completed ticks");
            var window = new long[count];
            for (var index = 0; index < count; index++) {
                window[index] = TIMES[Math.floorMod(completed - 1 - index, TIMES.length)];
            }
            return window;
        }

        public double medianTickNanos() {
            var window = times();
            java.util.Arrays.sort(window);
            var count = window.length;
            return count % 2 == 1 ? window[count / 2] : (window[count / 2 - 1] + window[count / 2]) / 2.0;
        }

        public double meanTickNanos() {
            return java.util.Arrays.stream(times()).average().orElseThrow();
        }

        public double maxTickNanos() {
            return java.util.Arrays.stream(times()).max().orElseThrow();
        }
    }
}
