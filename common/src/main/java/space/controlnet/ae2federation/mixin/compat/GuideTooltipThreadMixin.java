package space.controlnet.ae2federation.mixin.compat;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** GuideMe's interactive progress hint must not measure fonts or update key state during async search indexing. */
@Mixin(targets = "guideme.internal.hotkey.OpenGuideHotkey", remap = false)
public abstract class GuideTooltipThreadMixin {
    @Inject(method = "handleTooltip", at = @At("HEAD"), cancellable = true, require = 1)
    private static void ae2federation$interactiveTooltipOnRenderThread(CallbackInfo callback) {
        if (!Minecraft.getInstance().isSameThread()) callback.cancel();
    }
}
