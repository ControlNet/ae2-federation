package space.controlnet.ae2federation.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.ae2.processing.endpoint.NativeEndpointTrace;

@Mixin(PatternProviderLogic.class)
public abstract class PatternProviderLogicEndpointTrace {
    @Inject(method = "pushPattern", at = @At("HEAD"))
    private void ae2federation$traceEndpointPush(IPatternDetails patternDetails, KeyCounter[] inputHolder,
            CallbackInfoReturnable<Boolean> callback) {
        NativeEndpointTrace.recordPush(this);
    }
}
