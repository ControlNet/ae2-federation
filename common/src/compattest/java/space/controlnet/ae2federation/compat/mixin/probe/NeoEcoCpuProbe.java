package space.controlnet.ae2federation.compat.mixin.probe;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import appeng.me.service.CraftingService;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.UUID;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import space.controlnet.ae2federation.compat.probe.DispatchProbe;

/** Neo ECO's crafting CPU: its AE2-style loop, and the single, batch and plain pushes of its normal-provider path. */
@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPULogic", remap = false)
abstract class NeoEcoCpuProbe {
    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"))
    private boolean ae2federation$probeLoop(ICraftingProvider provider, IPatternDetails details, KeyCounter[] inputs,
            Operation<Boolean> original, @Local(argsOnly = true) CraftingService service) {
        return DispatchProbe.dispatch("neoecoae:ECOCraftingCPULogic.executeCrafting", service, provider, details, 1,
                () -> original.call(provider, details, inputs));
    }

    @WrapOperation(method = "invokeNormalProvider", at = @At(value = "INVOKE",
            target = "Lcn/dancingsnow/neoecoae/crafting/execution/fastpath/ECOSingleCraftingExecutor;pushPattern(Lappeng/api/networking/crafting/ICraftingProvider;Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;Lappeng/api/stacks/KeyCounter;Lappeng/api/stacks/KeyCounter;Lnet/minecraft/world/level/Level;Ljava/util/UUID;)Z"))
    private boolean ae2federation$probeSingle(ICraftingProvider provider, IPatternDetails details, KeyCounter[] inputs,
            KeyCounter first, KeyCounter second, Level level, UUID job, Operation<Boolean> original) {
        return DispatchProbe.dispatch("neoecoae:ECOCraftingCPULogic.invokeNormalProvider/single", null, provider, details, 1,
                () -> original.call(provider, details, inputs, first, second, level, job));
    }

    @WrapOperation(method = "invokeNormalProvider", at = @At(value = "INVOKE",
            target = "Lcn/dancingsnow/neoecoae/api/me/provider/ECOParallelCraftingProvider;eco$pushPatternBatch(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;JLjava/util/UUID;)Z"))
    private boolean ae2federation$probeBatch(@Coerce Object provider, IPatternDetails details, KeyCounter[] inputs,
            long runs, UUID job, Operation<Boolean> original) {
        return DispatchProbe.dispatch("neoecoae:ECOCraftingCPULogic.invokeNormalProvider/batch", null, provider, details, runs,
                () -> original.call(provider, details, inputs, runs, job));
    }

    @WrapOperation(method = "invokeNormalProvider", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"))
    private boolean ae2federation$probePlain(ICraftingProvider provider, IPatternDetails details, KeyCounter[] inputs,
            Operation<Boolean> original) {
        return DispatchProbe.dispatch("neoecoae:ECOCraftingCPULogic.invokeNormalProvider/plain", null, provider, details, 1,
                () -> original.call(provider, details, inputs));
    }
}
