package space.controlnet.ae2federation.test.mixin;

import appeng.api.networking.IGrid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.test.crafting.CraftingLifecycleAuthorityObservation;

@Mixin(targets = "space.controlnet.ae2federation.crafting.binding.NativeCraftingBackendRegistry")
abstract class CraftingBackendLifecycleEvidenceMixin {
    @Inject(method = "discover", at = @At("HEAD"), require = 1)
    private void ae2federation$discover(IGrid grid, CallbackInfoReturnable<Object> callback) {
        CraftingLifecycleAuthorityObservation.recordBackendDiscovery();
    }
}
