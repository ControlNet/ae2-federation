package space.controlnet.ae2federation.test.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.crafting.binding.CraftingCapabilityBinding;
import space.controlnet.ae2federation.test.crafting.TerminalNativeObservation;

@Mixin(CraftingCapabilityBinding.class)
public abstract class TerminalBackendThreadGuardMixin {
    @Inject(method = "isCurrent", at = @At("HEAD"), require = 1)
    private void ae2federation_test$guard(CallbackInfoReturnable<Boolean> callback) {
        TerminalNativeObservation.guardMutableAccess("backend");
    }
}
