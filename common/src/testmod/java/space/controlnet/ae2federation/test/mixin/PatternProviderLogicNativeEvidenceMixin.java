package space.controlnet.ae2federation.test.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderTarget;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.test.processing.NativeEndpointObservation;
import space.controlnet.ae2federation.test.processing.ProviderRuntimeReplayControl;
import space.controlnet.ae2federation.test.processing.ProviderTargetObservation;
import space.controlnet.ae2federation.test.crafting.TerminalNativeObservation;

@Mixin(value = PatternProviderLogic.class, priority = 500)
public abstract class PatternProviderLogicNativeEvidenceMixin {
    @Inject(method = "pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z",
            at = @At("HEAD"), require = 1)
    private void ae2federation_test$observePush(IPatternDetails patternDetails, KeyCounter[] inputHolder,
            CallbackInfoReturnable<Boolean> callback) {
        NativeEndpointObservation.recordPush(this);
        TerminalNativeObservation.recordProviderPush(this);
    }

    /**
     * Outside Federation's own findAdapter wrapper (a later order wraps it), so a bound Lane's lookup is seen although
     * the native lookup does not run for it.
     */
    @WrapMethod(method = "findAdapter(Lnet/minecraft/core/Direction;)Lappeng/helpers/patternprovider/PatternProviderTarget;",
            order = 2000, require = 1)
    private PatternProviderTarget ae2federation_test$observeBoundLookup(Direction side,
            Operation<PatternProviderTarget> original) {
        var logic = (PatternProviderLogic) (Object) this;
        ProviderTargetObservation.recordMixinLookup();
        ProviderRuntimeReplayControl.observeMixinOwner(logic);
        return original.call(side);
    }
}
