package space.controlnet.ae2federation.test.mixin;

import appeng.api.networking.IGrid;
import appeng.parts.automation.ImportBusPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.test.automation.AutomationNativeObservation;

@Mixin(ImportBusPart.class)
abstract class ImportBusAutomationEvidenceMixin {
    @Inject(method = "doBusWork", at = @At("RETURN"))
    private void ae2federation$afterWork(IGrid grid, CallbackInfoReturnable<Boolean> callback) {
        AutomationNativeObservation.importBus(this, callback.getReturnValue());
    }
}
