package space.controlnet.ae2federation.test.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.test.crafting.TerminalNativeObservation;
import space.controlnet.ae2federation.test.crafting.TerminalResultAuthorityReceipt;

@Mixin(CraftingCPUCluster.class)
public abstract class TerminalCraftingCpuEvidenceMixin {
    @Inject(method = "submitJob", at = @At("RETURN"), require = 1)
    private void ae2federation_test$submitted(IGrid grid, ICraftingPlan plan, IActionSource source,
            @Nullable ICraftingRequester requester, CallbackInfoReturnable<ICraftingSubmitResult> callback) {
        var cpu = (CraftingCPUCluster) (Object) this;
        TerminalNativeObservation.recordCpuSubmission(cpu, callback.getReturnValue());
        TerminalResultAuthorityReceipt.recordSubmission(cpu, callback.getReturnValue());
    }
}
