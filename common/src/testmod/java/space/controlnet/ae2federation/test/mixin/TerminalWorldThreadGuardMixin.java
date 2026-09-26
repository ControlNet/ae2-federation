package space.controlnet.ae2federation.test.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.test.crafting.TerminalNativeObservation;

@Mixin(Level.class)
public abstract class TerminalWorldThreadGuardMixin {
    @Inject(method = "getBlockEntity", at = @At("HEAD"), require = 1)
    private void ae2federation_test$guard(BlockPos position, CallbackInfoReturnable<BlockEntity> callback) {
        TerminalNativeObservation.guardMutableAccess("world-block");
    }
}
