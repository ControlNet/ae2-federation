package space.controlnet.ae2federation.test.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Font provider and glyph caches belong to the render thread, including in an integrated server. */
@Mixin(Font.class)
public abstract class ClientFontThreadGuardMixin {
    @Inject(method = "getFontSet", at = @At("HEAD"), require = 1)
    private void ae2federation_test$checkFontThread(CallbackInfoReturnable<FontSet> callback) {
        if (!Minecraft.getInstance().isSameThread()) {
            throw space.controlnet.ae2federation.test.ui.FontThreadEvidence.violation();
        }
    }
}
