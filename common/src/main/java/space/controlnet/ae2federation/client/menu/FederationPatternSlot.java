package space.controlnet.ae2federation.client.menu;

import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.EncodedPatternItem;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.world.item.ItemStack;

/**
 * A Provider pattern slot that shows what its pattern makes instead of the pattern item, as AE2's own Provider slots
 * do ({@code RestrictedInputSlot.getDisplayStack}). The slot still holds, moves and describes the pattern itself.
 */
public final class FederationPatternSlot extends ItemSlot {
    /** Keeps the {@code item-slot} type name, so style sheets style it as any other slot. */
    @Override
    public String name() {
        return "item-slot";
    }

    /** The stack drawn in the slot: the pattern's primary output, or what the slot holds when there is none. */
    public ItemStack displayStack() {
        return shown(getValue());
    }

    /**
     * Draws a pattern, held or being split or dragged in, as its output. AE2 draws a wrapped fluid output at once, not
     * through the batched buffer, so the slot's queued background is flushed first or it would cover the fluid.
     */
    @Override
    protected void drawItemStack(GUIContext guiContext, ItemStack itemStack) {
        var shown = shown(itemStack);
        if (GenericStack.isWrapped(shown)) guiContext.graphics.flush();
        super.drawItemStack(guiContext, shown);
    }

    private static ItemStack shown(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof EncodedPatternItem<?> pattern) {
            var output = pattern.getOutput(stack);
            if (!output.isEmpty()) return output;
        }
        return stack;
    }
}
