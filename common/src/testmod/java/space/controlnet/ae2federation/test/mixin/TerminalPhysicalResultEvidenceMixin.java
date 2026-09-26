package space.controlnet.ae2federation.test.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.me.cells.BasicCellInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.test.crafting.TerminalNativeObservation;
import space.controlnet.ae2federation.test.crafting.TerminalResultAuthorityReceipt;
import space.controlnet.ae2federation.test.crafting.CraftingLifecycleAuthorityObservation;

@Mixin(BasicCellInventory.class)
public abstract class TerminalPhysicalResultEvidenceMixin {
    @Inject(method = "insert", at = @At("RETURN"), require = 1)
    private void ae2federation_test$insert(AEKey key, long amount, Actionable mode, IActionSource source,
            CallbackInfoReturnable<Long> callback) {
        TerminalNativeObservation.recordPhysicalInsert(this, key, amount, mode, callback.getReturnValue());
        TerminalResultAuthorityReceipt.recordPhysicalInsert(this, key, amount, mode, callback.getReturnValue());
        CraftingLifecycleAuthorityObservation.observePhysicalInsertion(this, key, amount, mode,
                callback.getReturnValue());
    }
}
