package space.controlnet.ae2federation.test.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.test.automation.AutomationNativeObservation;

@Mixin(targets = "space.controlnet.ae2federation.storage.mount.AuthorizedStorageProjection")
abstract class AuthorizedStorageProjectionAutomationEvidenceMixin {
    @Inject(method = "insert", at = @At("RETURN"))
    private void ae2federation$afterInsert(AEKey key, long amount, Actionable mode, IActionSource source,
            CallbackInfoReturnable<Long> callback) {
        if (mode == Actionable.MODULATE) {
            AutomationNativeObservation.projection(this, "insert", key, amount, callback.getReturnValue());
        }
    }

    @Inject(method = "extract", at = @At("RETURN"))
    private void ae2federation$afterExtract(AEKey key, long amount, Actionable mode, IActionSource source,
            CallbackInfoReturnable<Long> callback) {
        if (mode == Actionable.MODULATE) {
            AutomationNativeObservation.projection(this, "extract", key, amount, callback.getReturnValue());
        }
    }
}
