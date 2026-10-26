package space.controlnet.ae2federation.compat.mixin.probe;

import appeng.me.service.CraftingService;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import space.controlnet.ae2federation.compat.probe.DispatchProbe;

/** The crafting service whose end-of-tick CPU work is running, for executors that do not pass it along. */
@Mixin(value = CraftingService.class, remap = false)
abstract class CraftingServiceScopeProbe {
    @WrapMethod(method = "onServerEndTick")
    private void ae2federation$scope(Operation<Void> original) {
        var service = (CraftingService) (Object) this;
        DispatchProbe.enterScope(service);
        try {
            original.call();
        } finally {
            DispatchProbe.exitScope(service);
        }
    }
}
