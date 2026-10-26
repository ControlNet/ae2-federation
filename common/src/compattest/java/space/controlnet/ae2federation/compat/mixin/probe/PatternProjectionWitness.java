package space.controlnet.ae2federation.compat.mixin.probe;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import space.controlnet.ae2federation.compat.probe.DispatchProbe;

/**
 * Every push of a federated provider, through the projection a consumer's crafting service holds: whether an executor
 * probe saw it, and whether that executor's crafting service is the one holding the projection.
 */
@Mixin(targets = "space.controlnet.ae2federation.crafting.projection.PatternProjection", remap = false)
abstract class PatternProjectionWitness {
    @Shadow
    @Final
    private ICraftingProvider registered;

    @WrapMethod(method = "pushPattern")
    private boolean ae2federation$witnessPush(IPatternDetails details, KeyCounter[] inputs,
            Operation<Boolean> original) {
        boolean accepted = original.call(details, inputs);
        if (accepted) DispatchProbe.witness("federation:PatternProjection.pushPattern", registered, details, 1);
        return accepted;
    }

    @WrapMethod(method = "pushBatch")
    private boolean ae2federation$witnessBatch(IPatternDetails details, List<GenericStack> outputs,
            KeyCounter containerItems, KeyCounter inputs, BooleanSupplier handOver, Operation<Boolean> original) {
        boolean accepted = original.call(details, outputs, containerItems, inputs, handOver);
        if (accepted) {
            DispatchProbe.witness("federation:PatternProjection.pushBatch", registered, details, DispatchProbe.UNKNOWN);
        }
        return accepted;
    }
}
