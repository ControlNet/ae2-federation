package space.controlnet.ae2federation.mixin;

import appeng.api.stacks.KeyCounter;
import appeng.me.storage.DelegatingMEInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.ae2.storage.NativeStorageTrace;

@Mixin(DelegatingMEInventory.class)
public abstract class DelegatingMEInventoryTrace {
    @Inject(method = "getAvailableStacks(Lappeng/api/stacks/KeyCounter;)V", at = @At("HEAD"))
    private void ae2federation$traceDelegate(KeyCounter output, CallbackInfo callback) {
        NativeStorageTrace.recordDelegate(this);
    }

    @Inject(method = "getAvailableStacks()Lappeng/api/stacks/KeyCounter;", at = @At("HEAD"))
    private void ae2federation$traceDelegate(CallbackInfoReturnable<KeyCounter> callback) {
        NativeStorageTrace.recordDelegate(this);
    }
}
