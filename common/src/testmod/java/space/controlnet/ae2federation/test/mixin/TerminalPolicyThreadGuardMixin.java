package space.controlnet.ae2federation.test.mixin;

import java.util.Optional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyRecord;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.test.crafting.TerminalNativeObservation;

@Mixin(PolicyService.class)
public abstract class TerminalPolicyThreadGuardMixin {
    @Inject(method = "configured", at = @At("HEAD"), require = 1)
    private void ae2federation_test$guard(PolicyKey key,
            CallbackInfoReturnable<Optional<PolicyRecord.Configured>> callback) {
        TerminalNativeObservation.guardMutableAccess("policy");
    }
}
