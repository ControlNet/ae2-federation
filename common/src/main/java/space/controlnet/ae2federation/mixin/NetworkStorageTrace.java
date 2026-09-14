package space.controlnet.ae2federation.mixin;

import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.controlnet.ae2federation.ae2.storage.NativeStorageTrace;

@Mixin(NetworkStorage.class)
public abstract class NetworkStorageTrace {
    @Inject(method = "mount", at = @At("HEAD"))
    private void ae2federation$traceMount(int priority, MEStorage inventory, CallbackInfo callback) {
        NativeStorageTrace.recordMount(this, inventory, priority);
    }
}
