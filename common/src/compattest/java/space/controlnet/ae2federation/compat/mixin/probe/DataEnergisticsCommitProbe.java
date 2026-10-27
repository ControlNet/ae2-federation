package space.controlnet.ae2federation.compat.mixin.probe;

import appeng.api.stacks.KeyCounter;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.lang.reflect.Method;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import space.controlnet.ae2federation.compat.probe.DispatchProbe;

/** Data Energistics' Trinity dispatch committing one admission, which may cover many runs, to a provider. */
@Pseudo
@Mixin(targets = "com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit.CraftingDispatchCommitter",
        remap = false)
abstract class DataEnergisticsCommitProbe {
    @WrapOperation(method = "commit", at = @At(value = "INVOKE",
            target = "Lcom/fish_dan_/data_energistics/api/crafting/dispatch/CountedCraftingAdmission;commit([Lappeng/api/stacks/KeyCounter;)Z"))
    private boolean ae2federation$probe(@Coerce Object admission, KeyCounter[] prototype,
            Operation<Boolean> original) {
        return DispatchProbe.dispatch("data_energistics:CraftingDispatchCommitter.commit", null, null, null, count(admission),
                () -> original.call(admission, prototype));
    }

    private static Method count;

    /** The admission's runs, through the public interface since its classes are often not public. */
    private static long count(Object admission) {
        try {
            if (count == null) {
                count = Class.forName("com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission",
                        false, admission.getClass().getClassLoader()).getMethod("count");
            }
            return (long) count.invoke(admission);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return DispatchProbe.UNKNOWN;
        }
    }
}
