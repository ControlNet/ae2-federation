package space.controlnet.ae2federation.test.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.test.processing.NativeEndpointObservation;

@Mixin(targets = "appeng.helpers.patternprovider.PatternProviderTargetCache", priority = 2000)
public abstract class PatternProviderTargetCacheNativeEvidenceMixin {
    @Inject(method = "find()Lappeng/helpers/patternprovider/PatternProviderTarget;", at = @At("HEAD"), require = 1)
    private void ae2federation_test$observeFind(CallbackInfoReturnable<Object> callback) {
        NativeEndpointObservation.recordTarget(this);
    }
}
