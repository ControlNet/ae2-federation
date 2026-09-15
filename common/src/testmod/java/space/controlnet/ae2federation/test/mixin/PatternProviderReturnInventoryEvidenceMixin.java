package space.controlnet.ae2federation.test.mixin;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.test.processing.ProcessingNativeObservation;

@Mixin(PatternProviderReturnInventory.class)
public abstract class PatternProviderReturnInventoryEvidenceMixin {
    @Inject(method = "injectIntoNetwork", at = @At("RETURN"), require = 1)
    private void ae2federation_test$observeReturn(MEStorage storage, IActionSource source,
            Consumer<GenericStack> callback, CallbackInfoReturnable<Boolean> result) {
        ProcessingNativeObservation.recordReturn(this, result.getReturnValueZ());
    }
}
