package space.controlnet.ae2federation.test.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.controlnet.ae2federation.test.perf.MenuBroadcasts;

/** TEST-ONLY: records when each menu last sent its changes, so a benchmark can tell whether its player ticked it. */
@Mixin(AbstractContainerMenu.class)
public abstract class MenuBroadcastTickMixin {
    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void ae2federationTest$recordBroadcast(CallbackInfo callback) {
        MenuBroadcasts.record((AbstractContainerMenu) (Object) this);
    }
}
