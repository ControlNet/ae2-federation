package space.controlnet.ae2federation.compat.mixin.probe;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import appeng.me.service.CraftingService;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import space.controlnet.ae2federation.compat.probe.DispatchProbe;

/** Advanced AE's quantum computer CPU, a copy of AE2's loop. */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic", remap = false)
abstract class AdvancedAeCpuProbe {
    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"))
    private boolean ae2federation$probe(ICraftingProvider provider, IPatternDetails details, KeyCounter[] inputs,
            Operation<Boolean> original, @Local(argsOnly = true) CraftingService service) {
        return DispatchProbe.dispatch("advanced_ae:AdvCraftingCPULogic.executeCrafting", service, provider, details, 1,
                () -> original.call(provider, details, inputs));
    }
}
