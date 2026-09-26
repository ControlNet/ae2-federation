package space.controlnet.ae2federation.test.mixin;

import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.test.crafting.TerminalNativeObservation;

@Mixin(targets = "space.controlnet.ae2federation.crafting.terminal.CapturedTerminalRequester")
public abstract class CapturedTerminalRequesterEvidenceMixin {
    @Inject(method = "getActionSource()Lappeng/api/networking/security/IActionSource;", at = @At("HEAD"), require = 1)
    private void ae2federation_test$action(CallbackInfoReturnable<IActionSource> callback) {
        TerminalNativeObservation.recordRequesterAction(this);
    }

    @Inject(method = "getGridNode()Lappeng/api/networking/IGridNode;", at = @At("RETURN"), require = 1)
    private void ae2federation_test$node(CallbackInfoReturnable<IGridNode> callback) {
        TerminalNativeObservation.recordRequesterNode(this, callback.getReturnValue());
    }
}
