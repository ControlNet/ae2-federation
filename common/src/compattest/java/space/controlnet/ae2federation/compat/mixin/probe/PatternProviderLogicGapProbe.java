package space.controlnet.ae2federation.compat.mixin.probe;

import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderLogic;
import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.controlnet.ae2federation.compat.probe.DispatchProbe;
import space.controlnet.ae2federation.compat.probe.RecordingSendList;

/**
 * The two places a push leaves its mark on an AE2 pattern provider whatever protocol drove it: a successful push
 * (which AE2 records for its lock modes) and a growing send list. Either outside a known protocol is a gap.
 */
@Mixin(value = PatternProviderLogic.class, remap = false)
abstract class PatternProviderLogicGapProbe {
    @Shadow
    @Final
    @Mutable
    private List<GenericStack> sendList;

    @Inject(method = "<init>(Lappeng/api/networking/IManagedGridNode;Lappeng/helpers/patternprovider/PatternProviderLogicHost;I)V",
            at = @At("RETURN"))
    private void ae2federation$recordSendList(CallbackInfo callback) {
        if (!(sendList instanceof RecordingSendList)) sendList = new RecordingSendList(this, sendList);
    }

    @Inject(method = "onPushPatternSuccess", at = @At("HEAD"))
    private void ae2federation$pushSucceeded(CallbackInfo callback) {
        DispatchProbe.pushSucceeded(this);
    }

    @Inject(method = "readFromNBT", at = @At("HEAD"))
    private void ae2federation$enterLoading(CallbackInfo callback) {
        DispatchProbe.enterLoading();
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"))
    private void ae2federation$exitLoading(CallbackInfo callback) {
        DispatchProbe.exitLoading();
    }
}
