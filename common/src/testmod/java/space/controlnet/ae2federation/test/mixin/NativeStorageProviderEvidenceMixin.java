package space.controlnet.ae2federation.test.mixin;

import appeng.api.storage.IStorageMounts;
import appeng.api.storage.MEStorage;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.controlnet.ae2federation.ae2.storage.NativeStorageProvider;
import space.controlnet.ae2federation.test.processing.NativeStorageObservation;

@Mixin(NativeStorageProvider.class)
public abstract class NativeStorageProviderEvidenceMixin {
    @Shadow
    @Final
    private Map<MEStorage, Integer> mounts;

    @Inject(method = "mountInventories(Lappeng/api/storage/IStorageMounts;)V",
            at = @At("HEAD"), require = 1)
    private void ae2federation_test$observeProviderMount(IStorageMounts storageMounts, CallbackInfo callback) {
        mounts.forEach((storage, priority) -> NativeStorageObservation.recordProviderMount(this, storage, priority));
    }
}
