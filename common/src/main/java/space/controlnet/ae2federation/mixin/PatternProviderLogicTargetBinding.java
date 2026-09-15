package space.controlnet.ae2federation.mixin;

import appeng.helpers.patternprovider.PatternProviderLogic;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.ae2.processing.FederationPatternProviderTargetCache;

@Mixin(PatternProviderLogic.class)
public abstract class PatternProviderLogicTargetBinding {
    @Inject(method = "findAdapter", at = @At("HEAD"), cancellable = true)
    private void ae2federation$findAuthorizedTarget(Direction side, CallbackInfoReturnable<Object> callback) {
        var result = FederationPatternProviderTargetCache.find((PatternProviderLogic) (Object) this);
        if (result.bound()) {
            callback.setReturnValue(result.target());
        }
    }
}
