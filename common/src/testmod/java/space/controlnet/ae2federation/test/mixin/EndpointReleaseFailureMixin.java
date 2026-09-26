package space.controlnet.ae2federation.test.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.processing.endpoint.EndpointBlockEntity;
import space.controlnet.ae2federation.test.ReleaseClaimFailureProbe;

@Mixin(value = EndpointBlockEntity.class, remap = false)
public abstract class EndpointReleaseFailureMixin {
    @Inject(method = "releaseClaim", at = @At("HEAD"), cancellable = true)
    private void ae2federation$rejectOnce(CallbackInfoReturnable<Boolean> callback) {
        if (ReleaseClaimFailureProbe.consume()) {
            callback.setReturnValue(false);
        }
    }
}
