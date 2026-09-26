package space.controlnet.ae2federation.test.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.processing.provider.ProviderLogicProvenance;
import space.controlnet.ae2federation.processing.provider.ProviderRuntime;
import space.controlnet.ae2federation.processing.provider.ProviderTargetResolution;
import space.controlnet.ae2federation.test.processing.ProviderRuntimeReplayControl;
import space.controlnet.ae2federation.test.processing.ProviderTargetObservation;

@Mixin(ProviderRuntime.class)
public abstract class ProviderRuntimeReplayMixin {
    @Inject(method = "resolveTarget", at = @At("RETURN"), cancellable = true)
    private void ae2federation_test$observeResolution(ProviderLogicProvenance provenance,
            CallbackInfoReturnable<ProviderTargetResolution> callback) {
        ProviderTargetObservation.recordAuthorization(callback.getReturnValue().state());
        callback.setReturnValue(ProviderRuntimeReplayControl.observeResolution((ProviderRuntime) (Object) this,
                provenance, callback.getReturnValue()));
    }
}
