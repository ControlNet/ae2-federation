package space.controlnet.ae2federation.compat.mixin.probe;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.compat.probe.DispatchProbe;

/**
 * Neo ECO's ordinary batch push, which only the Large Integrated Working Station implements: a known protocol for the
 * gap measurement. Optional, as older Neo ECO releases lack it.
 */
@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.blocks.entity.LargeWorkstationPatternProvider", remap = false)
abstract class NeoEcoBatchWitness {
    @Inject(method = "eco$pushPatternBatch", at = @At("HEAD"), require = 0)
    private void ae2federation$enter(CallbackInfoReturnable<Boolean> callback) {
        DispatchProbe.enterKnown();
    }

    @Inject(method = "eco$pushPatternBatch", at = @At("RETURN"), require = 0)
    private void ae2federation$exit(CallbackInfoReturnable<Boolean> callback) {
        DispatchProbe.exitKnown();
    }
}
