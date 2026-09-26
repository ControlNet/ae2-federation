package space.controlnet.ae2federation.test.mixin;

import appeng.helpers.patternprovider.PatternProviderLogic;
import java.util.function.Supplier;
import appeng.api.networking.IGridNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.ae2.processing.FederationPatternProviderTargetCache;
import space.controlnet.ae2federation.test.processing.ProviderRuntimeReplayControl;
import space.controlnet.ae2federation.test.processing.ProviderTargetObservation;
import space.controlnet.ae2federation.processing.provider.ProviderLogicProvenance;
import space.controlnet.ae2federation.processing.provider.ProviderTargetResolution;

@Mixin(FederationPatternProviderTargetCache.class)
public abstract class FederationPatternProviderTargetCacheReplayTraceMixin {
    @Inject(method = "bind", at = @At("RETURN"), require = 1)
    private static void ae2federation_test$observeBinding(PatternProviderLogic logic,
            ProviderLogicProvenance provenance, Supplier<ProviderTargetResolution> resolver,
            Supplier<IGridNode> sourceNode, org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
        ProviderTargetObservation.recordBinding();
    }

    @Inject(method = "find", at = @At("HEAD"))
    private static void ae2federation_test$observeCacheOwner(PatternProviderLogic logic,
            CallbackInfoReturnable<FederationPatternProviderTargetCache.Lookup> callback) {
        ProviderRuntimeReplayControl.observeCacheOwner(logic);
    }

    @Inject(method = "find", at = @At("RETURN"))
    private static void ae2federation_test$observeCacheResult(PatternProviderLogic logic,
            CallbackInfoReturnable<FederationPatternProviderTargetCache.Lookup> callback) {
        ProviderRuntimeReplayControl.observeCacheResult(logic, callback.getReturnValue());
    }
}
