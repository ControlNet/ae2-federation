package space.controlnet.ae2federation.test.mixin;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.test.crafting.TerminalNativeObservation;

@Mixin(GameTestHelper.class)
public abstract class TerminalGameTestHelperThreadGuardMixin {
    @Inject(method = "getLevel", at = @At("HEAD"), require = 1)
    private void ae2federation_test$guard(CallbackInfoReturnable<ServerLevel> callback) {
        TerminalNativeObservation.guardMutableAccess("gametest-helper");
    }
}
