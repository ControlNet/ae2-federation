package space.controlnet.ae2federation.test.mixin;

import appeng.helpers.patternprovider.PatternProviderTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.processing.provider.AuthorizedNativeTarget;
import space.controlnet.ae2federation.test.processing.ProviderTargetObservation;

@Mixin(targets = "space.controlnet.ae2federation.ae2.processing.FederationPatternProviderTargetCache$Binding")
public abstract class FederationNativeTargetEvidenceMixin {
    @Inject(method = "find(Lspace/controlnet/ae2federation/processing/provider/AuthorizedNativeTarget;)Lappeng/helpers/patternprovider/PatternProviderTarget;",
            at = @At("HEAD"), require = 1)
    private void ae2federation_test$observeLookup(AuthorizedNativeTarget authorized,
            CallbackInfoReturnable<PatternProviderTarget> callback) {
        ProviderTargetObservation.recordNativeTargetLookup();
    }

    @Inject(method = "find(Lspace/controlnet/ae2federation/processing/provider/AuthorizedNativeTarget;)Lappeng/helpers/patternprovider/PatternProviderTarget;",
            at = @At("RETURN"), require = 1)
    private void ae2federation_test$observeResult(AuthorizedNativeTarget authorized,
            CallbackInfoReturnable<PatternProviderTarget> callback) {
        if (callback.getReturnValue() != null) {
            ProviderTargetObservation.recordNativeTargetFound();
        }
    }
}
