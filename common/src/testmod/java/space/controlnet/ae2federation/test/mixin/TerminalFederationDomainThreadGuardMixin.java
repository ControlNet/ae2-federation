package space.controlnet.ae2federation.test.mixin;

import appeng.api.networking.IGrid;
import java.util.Optional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.test.crafting.TerminalNativeObservation;

@Mixin(FederationDomainRegistryAccess.class)
public abstract class TerminalFederationDomainThreadGuardMixin {
    @Inject(method = "confirmedNetworkId", at = @At("HEAD"), require = 1)
    private static void ae2federation_test$guard(IGrid grid, CallbackInfoReturnable<Optional<NetworkId>> callback) {
        TerminalNativeObservation.guardMutableAccess("domain");
    }
}
