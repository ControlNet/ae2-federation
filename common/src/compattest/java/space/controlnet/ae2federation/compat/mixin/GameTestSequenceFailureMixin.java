package space.controlnet.ae2federation.compat.mixin;

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
 * Vanilla sequences catch only assertion failures. Any other exception leaves the test's tick early, so even when the
 * runner then fails the test, its listeners never hear of it and the next batch never starts: the server idles until
 * the run's timeout and leaves no report. Here the exception fails only the test that threw, inside its own tick.
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
        callback.cancel();
        try {
            tick(tick);
        } catch (GameTestAssertException waiting) {
            // An assertion that has not held yet: the sequence tries again next tick, as vanilla does.
        } catch (RuntimeException exception) {
            parent.fail(exception);
        }
    }

    @Inject(method = "tickAndFailIfNotComplete", at = @At("HEAD"), cancellable = true)
    private void ae2federation$failOnlyThisTestAtTimeout(long tick, CallbackInfo callback) {
        callback.cancel();
        try {
            tick(tick);
        } catch (RuntimeException exception) {
            parent.fail(exception);
        }
    }
}
