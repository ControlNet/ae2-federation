package space.controlnet.ae2federation.test.mixin;

import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * TEST-ONLY: the GameTest server keeps {@link MinecraftServer#forceSynchronousWrites()}'s default of {@code true} and
 * reads no {@code sync-chunk-writes} setting, so its shutdown save opens every region file with DSYNC and waits for the
 * disk on each chunk write: 25-78 s per run on a virtual disk. Each run's world is task-owned and deleted afterwards,
 * and the next process still reads the written files from the page cache, so a GameTest server writes asynchronously.
 */
@Mixin(MinecraftServer.class)
abstract class GameTestServerChunkWritesMixin {
    @Inject(method = "forceSynchronousWrites", at = @At("HEAD"), cancellable = true)
    private void ae2federation$asynchronousGameTestWrites(CallbackInfoReturnable<Boolean> result) {
        if ((Object) this instanceof GameTestServer) {
            result.setReturnValue(false);
        }
    }
}
