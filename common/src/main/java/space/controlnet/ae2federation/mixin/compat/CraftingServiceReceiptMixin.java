package space.controlnet.ae2federation.mixin.compat;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import space.controlnet.ae2federation.crafting.projection.CraftingProjectionService;

/**
 * AE2 19.2.17 compatibility (19.2.18 is the same): the public CPU API has no receipt event. AE2's crafting storage,
 * mounted first and preferred for every key, hands what enters a Grid to {@code insertIntoCpus}, and every addon that
 * runs its own CPUs extends that method and {@code getRequestedAmount} together: Neo ECO 21.2.9 and Thunderbolt 2.0.1
 * (Lightning Tech) with injects just before the return, Advanced AE 1.6.12 with a cancellable inject at the return, and
 * Data Energistics 3.3.3 and 3.3.5 with a {@code @WrapMethod} of each. The high priority makes this wrapper the
 * outermost, so it runs around all of them. A MODULATE insert while the Grid carries a debt and its consumer reads how
 * much the CPUs wait for before and after ({@link CraftingProjectionService#waiting}), not the result: AE2 returns
 * only what a job's requester took of its final output, 0 without one, although the CPU took it. Only observed: the
 * insert and its result are unchanged, and a failure here is logged, never thrown into AE2.
 */
@Mixin(value = CraftingService.class, priority = 2000)
public abstract class CraftingServiceReceiptMixin {
    @Unique private static final Logger ae2federation$LOGGER = LogUtils.getLogger();

    @Shadow @Final private IGrid grid;

    @WrapMethod(method = "insertIntoCpus")
    private long ae2federation$received(AEKey what, long amount, Actionable type, Operation<Long> original) {
        long before = -1;
        if (what != null && type == Actionable.MODULATE) {
            try {
                if (CraftingProjectionService.owedWithin(grid, what) > 0) before = ae2federation$waiting(what);
            } catch (RuntimeException failure) {
                ae2federation$LOGGER.error("Could not read what the crafting CPUs wait for", failure);
            }
        }
        long inserted = original.call(what, amount, type);
        if (before >= 0) {
            try {
                CraftingProjectionService.cpusReceived(grid, what, amount, before, ae2federation$waiting(what));
            } catch (RuntimeException failure) {
                ae2federation$LOGGER.error("Could not settle what the crafting CPUs received", failure);
            }
        }
        return inserted;
    }

    @Unique
    private long ae2federation$waiting(AEKey what) {
        return CraftingProjectionService.waiting((ICraftingService) (Object) this, what);
    }
}
