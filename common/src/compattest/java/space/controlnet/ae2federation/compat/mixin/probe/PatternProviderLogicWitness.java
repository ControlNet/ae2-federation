package space.controlnet.ae2federation.compat.mixin.probe;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import space.controlnet.ae2federation.compat.probe.DispatchProbe;

/**
 * Every single push that reaches an AE2 pattern provider (and its subclasses that do not override it). Through a
 * projection, the dispatching service holds the projection, not this provider, so its attribution reads false there.
 */
@Mixin(value = PatternProviderLogic.class, remap = false)
abstract class PatternProviderLogicWitness {
    @WrapMethod(method = "pushPattern")
    private boolean ae2federation$witness(IPatternDetails details, KeyCounter[] inputs, Operation<Boolean> original) {
        boolean accepted = original.call(details, inputs);
        if (accepted) {
            DispatchProbe.witness("ae2:PatternProviderLogic.pushPattern", (ICraftingProvider) this, details, 1);
        }
        return accepted;
    }
}
