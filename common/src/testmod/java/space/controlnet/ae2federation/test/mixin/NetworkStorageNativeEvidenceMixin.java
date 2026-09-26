package space.controlnet.ae2federation.test.mixin;

import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.controlnet.ae2federation.test.processing.NativeStorageObservation;

@Mixin(NetworkStorage.class)
public abstract class NetworkStorageNativeEvidenceMixin {
    @Inject(method = "mount(ILappeng/api/storage/MEStorage;)V", at = @At("HEAD"), require = 1)
    private void ae2federation_test$observeMount(int priority, MEStorage inventory, CallbackInfo callback) {
        NativeStorageObservation.recordMount(this, inventory, priority);
    }
}
