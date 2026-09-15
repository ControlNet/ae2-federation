package space.controlnet.ae2federation.test.mixin;

import appeng.api.stacks.KeyCounter;
import appeng.me.storage.DelegatingMEInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.test.processing.NativeStorageObservation;

@Mixin(DelegatingMEInventory.class)
public abstract class DelegatingMEInventoryNativeEvidenceMixin {
    @Inject(method = "getAvailableStacks(Lappeng/api/stacks/KeyCounter;)V", at = @At("HEAD"), require = 1)
    private void ae2federation_test$observeDelegate(KeyCounter output, CallbackInfo callback) {
        NativeStorageObservation.recordDelegate(this);
    }

    @Inject(method = "getAvailableStacks()Lappeng/api/stacks/KeyCounter;", at = @At("HEAD"), require = 1)
    private void ae2federation_test$observeDelegate(CallbackInfoReturnable<KeyCounter> callback) {
        NativeStorageObservation.recordDelegate(this);
    }
}
