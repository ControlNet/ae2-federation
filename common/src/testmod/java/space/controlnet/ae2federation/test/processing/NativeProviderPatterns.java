package space.controlnet.ae2federation.test.processing;

import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import java.util.List;
import net.minecraft.world.item.Items;
import space.controlnet.ae2federation.ae2.processing.NativeProviderLane;
import space.controlnet.ae2federation.processing.provider.MappedPatternProvider;

final class NativeProviderPatterns {
    private final MappedPatternProvider provider;

    NativeProviderPatterns(MappedPatternProvider provider) {
        this.provider = provider;
    }

    NativeProviderLane lane(int index) {
        return provider.lanes().get(index);
    }

    void installPatterns(int count) {
        var inputs = List.of(Items.COBBLESTONE, Items.DIRT, Items.SAND);
        var outputs = List.of(Items.DIAMOND, Items.GOLD_INGOT, Items.IRON_INGOT);
        for (int slot = 0; slot < count; slot++) {
            setPattern(slot, List.of(new GenericStack(AEItemKey.of(inputs.get(slot)), 1)),
                    List.of(new GenericStack(AEItemKey.of(outputs.get(slot)), 1)));
        }
        refreshPatterns();
    }

    void installPattern(int slot, List<GenericStack> inputs, List<GenericStack> outputs) {
        setPattern(slot, inputs, outputs);
        refreshPatterns();
    }

    void setPattern(int slot, List<GenericStack> inputs, List<GenericStack> outputs) {
        provider.patternInventory().setItemDirect(slot, PatternDetailsHelper.encodeProcessingPattern(inputs, outputs));
    }

    void refreshPatterns() {
        provider.refreshPatterns();
    }

    boolean pushInputs(int laneIndex, int patternIndex, List<GenericStack> inputs) {
        var holders = inputs.stream().map(input -> {
            var counter = new KeyCounter();
            counter.add(input.what(), input.amount());
            return counter;
        }).toArray(KeyCounter[]::new);
        return lane(laneIndex).pushPattern(lane(laneIndex).getAvailablePatterns().get(patternIndex), holders);
    }

    boolean push(int laneIndex, int patternIndex) {
        var pattern = lane(laneIndex).getAvailablePatterns().get(patternIndex);
        var input = pattern.getInputs()[0].getPossibleInputs()[0];
        var counter = new KeyCounter();
        counter.add(input.what(), input.amount());
        return lane(laneIndex).pushPattern(pattern, new KeyCounter[] { counter });
    }

    void lockUntilResult(int laneIndex) {
        lane(laneIndex).getConfigManager().putSetting(Settings.LOCK_CRAFTING_MODE,
                LockCraftingMode.LOCK_UNTIL_RESULT);
    }
}
