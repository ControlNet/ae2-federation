package space.controlnet.ae2federation.test.processing;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

final class ProcessingCraftingOutputStorage implements MEStorage {
    private final KeyCounter contents = new KeyCounter();

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (mode == Actionable.MODULATE) {
            contents.add(what, amount);
        }
        return amount;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        var extracted = Math.min(contents.get(what), amount);
        if (mode == Actionable.MODULATE) {
            contents.remove(what, extracted);
        }
        return extracted;
    }

    @Override
    public void getAvailableStacks(KeyCounter output) {
        output.addAll(contents);
    }

    @Override
    public Component getDescription() {
        return Component.literal("Processing benchmark crafted outputs");
    }

    long itemAmount(Item item) {
        long amount = 0;
        for (var entry : contents) {
            if (entry.getKey() instanceof AEItemKey itemKey && itemKey.getItem() == item) {
                amount = Math.addExact(amount, entry.getLongValue());
            }
        }
        return amount;
    }
}
