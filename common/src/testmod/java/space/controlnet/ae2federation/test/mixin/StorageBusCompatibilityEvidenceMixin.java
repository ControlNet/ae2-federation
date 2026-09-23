package space.controlnet.ae2federation.test.mixin;

import appeng.api.storage.IStorageMounts;
import appeng.api.storage.MEStorage;
import appeng.parts.storagebus.StorageBusPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import space.controlnet.ae2federation.test.processing.NativeStorageObservation;

@Mixin(StorageBusPart.class)
public abstract class StorageBusCompatibilityEvidenceMixin {
    @Redirect(method = "mountInventories(Lappeng/api/storage/IStorageMounts;)V",
            at = @At(value = "INVOKE", target = "Lappeng/api/storage/IStorageMounts;mount(Lappeng/api/storage/MEStorage;I)V"),
            require = 1)
    private void ae2federation_test$recordStorageBusMount(IStorageMounts mounts, MEStorage storage, int priority) {
        mounts.mount(storage, priority);
        NativeStorageObservation.recordProviderMount(this, storage, priority);
    }
}
