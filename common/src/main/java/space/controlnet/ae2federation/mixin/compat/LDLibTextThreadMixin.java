package space.controlnet.ae2federation.mixin.compat;

import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** LDLib2 2.2.34 checks physical distribution here; integrated-server menus must not touch glyph caches. */
@Mixin(value = TextElement.class, remap = false)
public abstract class LDLibTextThreadMixin {
    @Inject(method = "recompute", at = @At("HEAD"), cancellable = true, require = 1)
    private void ae2federation$measureOnRenderThread(CallbackInfo callback) {
        // Server copies retain their text and bindings. Their independent client copies perform visual layout.
        if (!Minecraft.getInstance().isSameThread()) callback.cancel();
    }
}
