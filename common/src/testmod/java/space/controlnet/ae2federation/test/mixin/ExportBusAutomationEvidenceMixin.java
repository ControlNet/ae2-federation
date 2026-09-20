package space.controlnet.ae2federation.test.mixin;

import appeng.api.networking.IGrid;
import appeng.parts.automation.ExportBusPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.test.automation.AutomationNativeObservation;

@Mixin(ExportBusPart.class)
abstract class ExportBusAutomationEvidenceMixin {
    @Inject(method = "doBusWork", at = @At("RETURN"))
    private void ae2federation$afterWork(IGrid grid, CallbackInfoReturnable<Boolean> callback) {
        AutomationNativeObservation.exportBus(this, callback.getReturnValue());
    }
}
