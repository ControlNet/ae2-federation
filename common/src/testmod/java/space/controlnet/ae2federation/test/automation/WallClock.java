package space.controlnet.ae2federation.test.automation;

import java.util.function.BooleanSupplier;
import net.minecraft.gametest.framework.GameTestAssertException;

/**
 * Test-only: waits in real time for something outside the game, such as a websocket answer. GameTest waits count
 * ticks, and a batch of tests runs ticks back to back, so a hundred ticks can pass before a local server answers.
 */
public final class WallClock {
    private static final long LIMIT_NANOS = 10_000_000_000L;

    private WallClock() {
    }

    /** Blocks this tick until {@code condition} holds, failing the test after ten seconds. */
    public static void await(String what, BooleanSupplier condition) {
        long until = System.nanoTime() + LIMIT_NANOS;
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > until) {
                throw new GameTestAssertException(what + ": not within 10 s");
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new GameTestAssertException(what + ": interrupted");
            }
        }
    }
}
