package space.controlnet.ae2federation.test.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import space.controlnet.ae2federation.test.automation.AutomationNativeObservation;

/** Reads each projection operation's result as it returns; allocates nothing, so benchmarks pay only the call. */
@Mixin(targets = "space.controlnet.ae2federation.storage.mount.AuthorizedStorageProjection")
abstract class AuthorizedStorageProjectionAutomationEvidenceMixin {
    @ModifyReturnValue(method = "insert", at = @At("RETURN"))
    private long ae2federation$afterInsert(long accepted, AEKey key, long amount, Actionable mode,
            IActionSource source) {
        if (mode == Actionable.MODULATE) {
            AutomationNativeObservation.projection(this, "insert", key, amount, accepted);
        }
        return accepted;
    }

    @ModifyReturnValue(method = "extract", at = @At("RETURN"))
    private long ae2federation$afterExtract(long accepted, AEKey key, long amount, Actionable mode,
            IActionSource source) {
        if (mode == Actionable.MODULATE) {
            AutomationNativeObservation.projection(this, "extract", key, amount, accepted);
        }
        return accepted;
    }
}
