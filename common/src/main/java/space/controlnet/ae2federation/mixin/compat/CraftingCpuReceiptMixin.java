package space.controlnet.ae2federation.mixin.compat;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.crafting.projection.CraftingProjectionService;

/**
 * AE2 19.2.17 compatibility: the public CPU API has no receipt event. Its change listener names only a key, and a
 * finishing job clears what it still waits for and notifies the same way, without marking a standalone link done, so a
 * receipt cannot be told from outputs that never came. At the head of a MODULATE insert, what the CPU takes is exactly
 * {@code min(amount, getWaitingFor(what))}: the method returns early only without a job or a wait, and caps the amount
 * at the wait. Only observed; the insert itself is unchanged.
 */
@Mixin(CraftingCpuLogic.class)
public abstract class CraftingCpuReceiptMixin {
    @Shadow @Final CraftingCPUCluster cluster;

    @Shadow
    public abstract long getWaitingFor(AEKey template);

    @Inject(method = "insert", at = @At("HEAD"))
    private void ae2federation$received(AEKey what, long amount, Actionable type, CallbackInfoReturnable<Long> callback) {
        if (what == null || type != Actionable.MODULATE) return;
        long received = Math.min(amount, getWaitingFor(what));
        if (received > 0) CraftingProjectionService.cpuReceived(cluster, what, received);
    }
}
