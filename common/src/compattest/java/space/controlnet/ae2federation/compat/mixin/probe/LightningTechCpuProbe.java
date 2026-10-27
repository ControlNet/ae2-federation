package space.controlnet.ae2federation.compat.mixin.probe;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import space.controlnet.ae2federation.compat.probe.DispatchProbe;

/** AE2 Lightning Tech's time-wheel CPU pushing one run; it does not pass the crafting service along. */
@Pseudo
@Mixin(targets = "com.moakiee.ae2lt.crafting.timewheel.Ae2LtTimeWheelCraftingCpuLogic", remap = false)
abstract class LightningTechCpuProbe {
    @WrapOperation(method = "tryPushPattern", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"))
    private boolean ae2federation$probe(ICraftingProvider provider, IPatternDetails details, KeyCounter[] inputs,
            Operation<Boolean> original) {
        return DispatchProbe.dispatch("ae2lt:Ae2LtTimeWheelCraftingCpuLogic.tryPushPattern", null, provider, details, 1,
                () -> original.call(provider, details, inputs));
    }
}
