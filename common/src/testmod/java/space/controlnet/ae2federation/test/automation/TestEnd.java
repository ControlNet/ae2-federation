package space.controlnet.ae2federation.test.automation;

import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;

/**
 * Test-only: runs a cleanup when the test ends, passed, failed or timed out, so a local server it started never
 * outlives it. A cleanup inside the test's last step would be skipped by a failure before it.
 */
public final class TestEnd {
    private TestEnd() {
    }

    /** Runs {@code cleanup} once, on the server thread, when the test passes or fails. */
    public static void then(GameTestHelper helper, Runnable cleanup) {
        var done = new AtomicBoolean();
        helper.testInfo.addListener(new GameTestListener() {
            @Override
            public void testStructureLoaded(GameTestInfo info) {
            }

            @Override
            public void testPassed(GameTestInfo info, GameTestRunner runner) {
                end();
            }

            @Override
            public void testFailed(GameTestInfo info, GameTestRunner runner) {
                end();
            }

            @Override
            public void testAddedForRerun(GameTestInfo old, GameTestInfo rerun, GameTestRunner runner) {
            }

            private void end() {
                if (done.compareAndSet(false, true)) {
                    cleanup.run();
                }
            }
        });
    }
}
