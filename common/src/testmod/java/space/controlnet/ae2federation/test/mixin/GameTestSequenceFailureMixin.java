package space.controlnet.ae2federation.test.mixin;

import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TEST-ONLY, development batch selection: vanilla sequences catch only assertion failures, so any other exception from a
 * test's sequence crashes the whole server. That ends a single-test run the same way, but in a batch it would drop every
 * later test; here it fails only the test that threw.
 */
@Mixin(GameTestSequence.class)
abstract class GameTestSequenceFailureMixin {
    @Shadow
    @Final
    GameTestInfo parent;

    @Shadow
    private void tick(long tick) {
        throw new AssertionError();
    }

    @Inject(method = "tickAndContinue", at = @At("HEAD"), cancellable = true)
    private void ae2federation$failOnlyThisTest(long tick, CallbackInfo callback) {
        if (batchSelection()) {
            callback.cancel();
            try {
                tick(tick);
            } catch (GameTestAssertException waiting) {
                // An assertion that has not held yet: the sequence tries again next tick, as vanilla does.
            } catch (RuntimeException exception) {
                parent.fail(exception);
            }
        }
    }

    @Inject(method = "tickAndFailIfNotComplete", at = @At("HEAD"), cancellable = true)
    private void ae2federation$failOnlyThisTestAtTimeout(long tick, CallbackInfo callback) {
        if (batchSelection()) {
            callback.cancel();
            try {
                tick(tick);
            } catch (RuntimeException exception) {
                parent.fail(exception);
            }
        }
    }

    private static boolean batchSelection() {
        return "batch".equals(System.getProperty("ae2federation.testSelection"));
    }
}
