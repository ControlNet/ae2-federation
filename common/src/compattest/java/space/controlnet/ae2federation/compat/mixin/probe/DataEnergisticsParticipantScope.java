package space.controlnet.ae2federation.compat.mixin.probe;

import appeng.me.service.CraftingService;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.compat.probe.DispatchProbe;

/**
 * Data Energistics' Trinity CPUs dispatch after the crafting service's tick, from a participant that keeps the
 * service; its dispatch step is that service's scope.
 */
@Pseudo
@Mixin(targets = "com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.server.GridCraftingDispatchParticipant",
        remap = false)
abstract class DataEnergisticsParticipantScope {
    @Shadow
    @Final
    private CraftingService craftingService;

    @Inject(method = "dispatchStep", at = @At("HEAD"))
    private void ae2federation$enter(CallbackInfoReturnable<?> callback) {
        DispatchProbe.enterScope(craftingService);
    }

    @Inject(method = "dispatchStep", at = @At("RETURN"))
    private void ae2federation$exit(CallbackInfoReturnable<?> callback) {
        DispatchProbe.exitScope(craftingService);
    }
}
