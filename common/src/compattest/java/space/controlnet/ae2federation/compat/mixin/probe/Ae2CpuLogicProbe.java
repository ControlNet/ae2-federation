package space.controlnet.ae2federation.compat.mixin.probe;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.service.CraftingService;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import space.controlnet.ae2federation.compat.probe.DispatchProbe;

/** AE2's own crafting CPU handing one run of a pattern to a provider. */
@Mixin(value = CraftingCpuLogic.class, remap = false)
abstract class Ae2CpuLogicProbe {
    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"))
    private boolean ae2federation$probe(ICraftingProvider provider, IPatternDetails details, KeyCounter[] inputs,
            Operation<Boolean> original, @Local(argsOnly = true) CraftingService service) {
        return DispatchProbe.dispatch("ae2:CraftingCpuLogic.executeCrafting", service, provider, 1,
                () -> original.call(provider, details, inputs));
    }
}
