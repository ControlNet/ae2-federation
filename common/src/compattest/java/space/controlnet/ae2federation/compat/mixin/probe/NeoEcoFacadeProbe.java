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

/** Neo ECO's fast-path facade, which other mods' CPUs call to push one run for a job. */
@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade", remap = false)
abstract class NeoEcoFacadeProbe {
    @WrapOperation(method = "pushPatternForJob", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"))
    private static boolean ae2federation$probe(ICraftingProvider provider, IPatternDetails details,
            KeyCounter[] inputs, Operation<Boolean> original) {
        return DispatchProbe.dispatch("neoecoae:ECOFastPathFacade.pushPatternForJob", null, provider, 1,
                () -> original.call(provider, details, inputs));
    }
}
