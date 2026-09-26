package space.controlnet.ae2federation.test.processing;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class ProcessingBenchmarkPatternMarker {
    private ProcessingBenchmarkPatternMarker() {
    }

    static ItemStack stack(int patternSlot, int amount) {
        var stack = new ItemStack(Items.DIAMOND, amount);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("task20-pattern-" + patternSlot));
        return stack;
    }

    static GenericStack generic(int patternSlot, int amount) {
        return new GenericStack(AEItemKey.of(stack(patternSlot, amount)), amount);
    }

    static AEItemKey key(int patternSlot) {
        return AEItemKey.of(stack(patternSlot, 1));
    }
}
