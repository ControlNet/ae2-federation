package space.controlnet.ae2federation.compat.mixin.probe;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderTarget;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.controlnet.ae2federation.compat.probe.DispatchProbe;

/**
 * Data Energistics' counted batches, which write a pattern provider's send list directly and so never reach
 * {@code PatternProviderLogic.pushPattern}.
 */
@Pseudo
@Mixin(targets = "com.fish_dan_.data_energistics.ae2.patternprovider.PatternProviderBatching", remap = false)
abstract class DataEnergisticsBatchWitness {
    @Inject(method = "pushExpanded", at = @At("HEAD"))
    private static void ae2federation$witnessExpanded(IPatternDetails details, KeyCounter[] inputs, long runs,
            @Coerce Object access, Direction side, Runnable done, CallbackInfo callback) {
        DispatchProbe.witness("data_energistics:PatternProviderBatching.pushExpanded", null, details, runs);
    }

    @Inject(method = "pushExpandedToTarget", at = @At("HEAD"))
    private static void ae2federation$witnessTarget(IPatternDetails details, KeyCounter[] inputs, long runs,
            PatternProviderTarget target, Runnable done, CallbackInfo callback) {
        DispatchProbe.witness("data_energistics:PatternProviderBatching.pushExpandedToTarget", null, details, runs);
    }
}
