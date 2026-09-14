package space.controlnet.ae2federation.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.ae2.processing.endpoint.NativeEndpointTrace;

@Mixin(targets = "appeng.helpers.patternprovider.PatternProviderTargetCache")
public abstract class PatternProviderTargetCacheEndpointTrace {
    @Inject(method = "find", at = @At("HEAD"))
    private void ae2federation$traceEndpointTarget(CallbackInfoReturnable<Object> callback) {
        NativeEndpointTrace.recordTarget(this);
    }
}
