package space.controlnet.ae2federation.mixin;

import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.me.service.StorageService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.controlnet.ae2federation.storage.subscription.NativeStorageNotificationHub;

@Mixin(StorageService.class)
public abstract class StorageServiceNotificationMixin {
    @Inject(method = "postWatcherUpdate", at = @At("HEAD"))
    private void ae2federation$forwardAbsoluteAmount(AEKey key, long absoluteAmount, CallbackInfo callback) {
        NativeStorageNotificationHub.publishAbsolute((IStorageService) this, key, absoluteAmount);
    }

    @Inject(method = "onServerEndTick", at = @At("TAIL"))
    private void ae2federation$refreshSubscribedSnapshot(CallbackInfo callback) {
        NativeStorageNotificationHub.reconcileBudgeted((IStorageService) this);
    }
}
