package space.controlnet.ae2federation.test.mixin;

import appeng.api.networking.storage.IStorageService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.storage.subscription.NativeStorageAmount;
import space.controlnet.ae2federation.storage.subscription.NativeStorageListener;
import space.controlnet.ae2federation.storage.subscription.NativeStorageNotificationHub;
import space.controlnet.ae2federation.storage.subscription.NativeStorageRegistration;
import space.controlnet.ae2federation.test.storage.SubscriptionTestHooks;

@Mixin(value = NativeStorageNotificationHub.class, remap = false)
abstract class NativeStorageNotificationHubTestHookMixin {
    @Redirect(method = "publishAbsolute", at = @At(value = "INVOKE",
            target = "Lspace/controlnet/ae2federation/storage/subscription/NativeStorageListener;onAmountChanged"
                    + "(Lspace/controlnet/ae2federation/storage/subscription/NativeStorageAmount;)V"))
    private static void ae2federation$traceExactListenerDelivery(NativeStorageListener listener,
            NativeStorageAmount amount) {
        SubscriptionTestHooks.beginHubDelivery(listener);
        try {
            listener.onAmountChanged(amount);
        } finally {
            SubscriptionTestHooks.endHubDelivery(listener);
        }
    }

    @Inject(method = "register", at = @At("HEAD"))
    private static void ae2federation$captureRegistration(IStorageService service, NativeStorageListener listener,
            CallbackInfoReturnable<NativeStorageRegistration> callback) {
        SubscriptionTestHooks.captureRegistration(listener);
    }
}
