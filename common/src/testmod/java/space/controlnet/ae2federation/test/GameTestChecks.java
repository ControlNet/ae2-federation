package space.controlnet.ae2federation.test;

import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Checks that follow an action in a {@code succeedWhen} step. That step runs again every tick until it passes, so a
 * check that fails after the action leaves the world changed, and the next tick fails some earlier wait on that changed
 * world; at the timeout the test reports that wait, not the check that failed. A check run through here ends the test
 * with its own failure on the next tick instead.
 */
public final class GameTestChecks {
    private GameTestChecks() {
    }

    /** Runs {@code checks}; a failure among them fails the test on the next tick, with that failure. */
    public static void once(GameTestHelper helper, Runnable checks) {
        try {
            checks.run();
        } catch (GameTestAssertException failure) {
            // An exception thrown by a scheduled task fails the test at once, where a step's exception is retried.
            helper.runAfterDelay(1, () -> {
                throw failure;
            });
            throw failure;
        }
    }
}
