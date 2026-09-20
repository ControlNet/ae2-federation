package space.controlnet.ae2federation.test.mixin;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.CraftingCpuLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.test.crafting.TerminalNativeObservation;
import space.controlnet.ae2federation.test.crafting.TerminalResultAuthorityReceipt;

@Mixin(CraftingCpuLogic.class)
public abstract class TerminalCraftingResultEvidenceMixin {
    @Inject(method = "insert", at = @At("HEAD"), require = 1)
    private void ae2federation_test$result(AEKey key, long amount, Actionable mode,
            CallbackInfoReturnable<Long> callback) {
        var logic = (CraftingCpuLogic) (Object) this;
        TerminalNativeObservation.recordResultCallback(logic, key, amount, mode);
        TerminalResultAuthorityReceipt.recordCallback(logic, key, amount, mode);
    }
}
