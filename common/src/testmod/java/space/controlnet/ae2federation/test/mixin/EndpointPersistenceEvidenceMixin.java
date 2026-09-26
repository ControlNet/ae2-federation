package space.controlnet.ae2federation.test.mixin;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.controlnet.ae2federation.processing.endpoint.EndpointBlockEntity;
import space.controlnet.ae2federation.test.processing.endpoint.EndpointPersistenceObservation;

@Mixin(EndpointBlockEntity.class)
public abstract class EndpointPersistenceEvidenceMixin {
    @Inject(method = "saveAdditional", at = @At("RETURN"), require = 1)
    private void ae2federation_test$observeSave(CompoundTag tag, HolderLookup.Provider registries,
            CallbackInfo callback) {
        EndpointPersistenceObservation.observeSave((EndpointBlockEntity) (Object) this, tag);
    }

    @Inject(method = "loadTag", at = @At("RETURN"), require = 1)
    private void ae2federation_test$observeLoad(CompoundTag tag, HolderLookup.Provider registries,
            CallbackInfo callback) {
        EndpointPersistenceObservation.observeLoad((EndpointBlockEntity) (Object) this, tag);
    }

    @Inject(method = "onChunkUnloaded", at = @At("HEAD"), require = 1)
    private void ae2federation_test$observeUnload(CallbackInfo callback) {
        EndpointPersistenceObservation.observeUnload((EndpointBlockEntity) (Object) this);
    }

    @Inject(method = "onReady", at = @At("RETURN"), require = 1)
    private void ae2federation_test$observeReady(CallbackInfo callback) {
        EndpointPersistenceObservation.observeReady((EndpointBlockEntity) (Object) this);
    }
}
