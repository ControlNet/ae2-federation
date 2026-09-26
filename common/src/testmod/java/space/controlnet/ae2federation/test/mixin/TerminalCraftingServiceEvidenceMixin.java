package space.controlnet.ae2federation.test.mixin;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import java.util.concurrent.Future;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.test.crafting.TerminalNativeObservation;

@Mixin(CraftingService.class)
public abstract class TerminalCraftingServiceEvidenceMixin {
    @Inject(method = "beginCraftingCalculation", at = @At("HEAD"), require = 1)
    private void ae2federation_test$begin(Level level, ICraftingSimulationRequester requester, AEKey key, long amount,
            CalculationStrategy strategy, CallbackInfoReturnable<Future<ICraftingPlan>> callback) {
        TerminalNativeObservation.recordBegin(this, requester);
    }

    @Inject(method = "submitJob", at = @At("HEAD"), require = 1)
    private void ae2federation_test$submit(ICraftingPlan plan, @Nullable ICraftingRequester requester,
            @Nullable ICraftingCPU target, boolean prioritizePower, IActionSource source,
            CallbackInfoReturnable<ICraftingSubmitResult> callback) {
        TerminalNativeObservation.recordSubmit(this);
    }
}
