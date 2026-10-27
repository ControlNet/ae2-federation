package space.controlnet.ae2federation.compat.mixin.probe;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.me.service.CraftingService;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import space.controlnet.ae2federation.compat.probe.DispatchProbe;

/**
 * Thunderbolt's batch executor handing a provider many runs at once; the provider answers how many it took. Its
 * {@code pushBatch} is a known protocol for the gap measurement.
 */
@Pseudo
@Mixin(targets = "com.moakiee.thunderbolt.core.crafting.batch.BatchExecutor", remap = false)
abstract class ThunderboltBatchProbe {
    // The overload with every parameter; the shorter ones only delegate to it.
    @WrapOperation(method = "runBatchOnly(ILcom/moakiee/thunderbolt/core/crafting/batch/BatchCpuAccounting$Mode;Lappeng/me/service/CraftingService;Lappeng/api/networking/energy/IEnergyService;Lcom/moakiee/thunderbolt/api/crafting/batch/BatchJobView;Lappeng/crafting/inv/ListCraftingInventory;Ljava/util/Map;Ljava/lang/Runnable;Ljava/util/Map;IJZLcom/moakiee/thunderbolt/core/crafting/batch/TickProviderDispatchSchedule;Lcom/moakiee/thunderbolt/api/crafting/batch/BatchProviderAdapter;)Lcom/moakiee/thunderbolt/core/crafting/batch/BatchExecutor$BatchRunResult;",
            at = @At(value = "INVOKE",
            target = "Lcom/moakiee/thunderbolt/api/crafting/batch/IBatchCraftingProvider;pushBatch(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;JLcom/moakiee/thunderbolt/api/crafting/batch/BatchJobView;)J"))
    private static long ae2federation$probe(@Coerce Object provider, IPatternDetails details, KeyCounter[] inputs,
            long runs, @Coerce Object job, Operation<Long> original, @Local(argsOnly = true) CraftingService service) {
        return DispatchProbe.dispatchCount("thunderbolt:BatchExecutor.runBatchOnly", service, provider, details, () -> {
            DispatchProbe.enterKnown();
            try {
                return original.call(provider, details, inputs, runs, job);
            } finally {
                DispatchProbe.exitKnown();
            }
        });
    }
}
