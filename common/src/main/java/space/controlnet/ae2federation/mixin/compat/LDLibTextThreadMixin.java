package space.controlnet.ae2federation.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

/**
 * LDLib2 checks physical distribution here; integrated-server menus must not touch glyph caches. Verified against
 * LDLib2 2.2.34 (the build), 2.2.41 and 2.2.42; a renamed target only disables this guard.
 */
@Mixin(value = TextElement.class, remap = false)
public abstract class LDLibTextThreadMixin {
    @WrapMethod(method = "recompute", require = 0)
    private void ae2federation$measureOnRenderThread(Operation<Void> original) {
        // Server copies retain their text and bindings. Their independent client copies perform visual layout.
        if (Minecraft.getInstance().isSameThread()) original.call();
    }
}
