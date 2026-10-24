package space.controlnet.ae2federation.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;

/**
 * GuideMe's interactive progress hint must not measure fonts or update key state during async search indexing.
 * Verified against GuideME 21.1.1 (the build) and 21.1.19 (the compatibility profiles); a renamed target only
 * disables this guard.
 */
@Mixin(targets = "guideme.internal.hotkey.OpenGuideHotkey", remap = false)
public abstract class GuideTooltipThreadMixin {
    @WrapMethod(method = "handleTooltip", require = 0)
    private static void ae2federation$interactiveTooltipOnRenderThread(ItemStack stack, TooltipFlag flag,
            List<Component> lines, Operation<Void> original) {
        if (Minecraft.getInstance().isSameThread()) original.call(stack, flag, lines);
    }
}
