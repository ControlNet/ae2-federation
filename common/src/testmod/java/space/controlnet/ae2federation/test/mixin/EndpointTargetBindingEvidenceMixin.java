package space.controlnet.ae2federation.test.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.processing.endpoint.EndpointTargetAccess;
import space.controlnet.ae2federation.processing.endpoint.EndpointTargetBinding;
import space.controlnet.ae2federation.test.processing.ProviderTargetObservation;

@Mixin(EndpointTargetBinding.class)
public abstract class EndpointTargetBindingEvidenceMixin {
    @Inject(method = "find(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Lspace/controlnet/ae2federation/processing/endpoint/EndpointTargetAccess;",
            at = @At("HEAD"), require = 1)
    private static void ae2federation_test$observeCapabilityLookup(ServerLevel level, BlockPos position, Direction side,
            CallbackInfoReturnable<EndpointTargetAccess> callback) {
        ProviderTargetObservation.recordCapabilityLookup();
    }
}
