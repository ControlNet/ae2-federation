package space.controlnet.ae2federation.test.mixin;

import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.helpers.MultiCraftingTracker;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.test.automation.AutomationNativeObservation;
import space.controlnet.ae2federation.test.crafting.CraftingLifecycleAuthorityObservation;

@Mixin(MultiCraftingTracker.class)
abstract class MultiCraftingTrackerAutomationEvidenceMixin {
    @Shadow
    @Final
    private ICraftingRequester owner;

    @Inject(method = "handleCrafting", at = @At("HEAD"))
    private void ae2federation$beforeHandle(int slot, AEKey key, long amount, Level level, ICraftingService service,
            IActionSource source, CallbackInfoReturnable<Boolean> callback) {
        AutomationNativeObservation.trackerCall(owner);
        CraftingLifecycleAuthorityObservation.recordTrackerCall(owner, key, amount, service);
    }

    @Inject(method = "handleCrafting", at = @At("RETURN"))
    private void ae2federation$afterHandle(int slot, AEKey key, long amount, Level level, ICraftingService service,
            IActionSource source, CallbackInfoReturnable<Boolean> callback) {
        AutomationNativeObservation.trackerResult(owner, callback.getReturnValue());
        CraftingLifecycleAuthorityObservation.recordTrackerResult(owner, callback.getReturnValue());
    }
}
