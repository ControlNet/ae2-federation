package space.controlnet.ae2federation.compat.mixin.probe;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.compat.probe.DispatchProbe;

/**
 * The commit of a Neo ECO FastPath preparation: a known protocol for the gap measurement. Its exact-order dispatch is
 * left unmarked on purpose, so a push through it shows as a gap. Optional, as older Neo ECO releases lack it.
 */
@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider$Preparation", remap = false)
abstract class NeoEcoFastPathWitness {
    @Inject(method = "push", at = @At("HEAD"), require = 0)
    private void ae2federation$enter(CallbackInfoReturnable<Boolean> callback) {
        DispatchProbe.enterKnown();
    }

    @Inject(method = "push", at = @At("RETURN"), require = 0)
    private void ae2federation$exit(CallbackInfoReturnable<Boolean> callback) {
        DispatchProbe.exitKnown();
    }
}
