package space.controlnet.ae2federation.test.perf;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/**
 * TEST-ONLY in-memory native storage with many distinct item types, mounted through an AE2 {@link IStorageProvider}.
 * It stands in for a large drive array: real cells cap each at 63 types. Beyond the registered items it adds sheets of
 * paper that differ only in their custom data, as named or enchanted variants of one item are distinct AE2 keys.
 */
public final class BulkItemStorage implements MEStorage, IStorageProvider {
    private final Map<AEKey, Long> amounts = new LinkedHashMap<>();
    private long operations;

    public BulkItemStorage(int types, long amountEach) {
        var added = 0;
        for (var item : BuiltInRegistries.ITEM) {
            if (added >= types) {
                break;
            }
            if (item == Items.AIR) {
                continue;
            }
            amounts.put(AEItemKey.of(item), amountEach);
            added++;
        }
        for (var variant = 0; added < types; variant++, added++) {
            var tag = new CompoundTag();
            tag.putInt("ae2federation_perf_variant", variant);
            var stack = new ItemStack(Items.PAPER);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            amounts.put(AEItemKey.of(stack), amountEach);
        }
        if (amounts.size() != types) {
            throw new IllegalStateException("Expected " + types + " distinct keys, made " + amounts.size());
        }
    }

    /** The stored keys, in the order they were made. */
    public java.util.List<AEKey> keys() {
        return java.util.List.copyOf(amounts.keySet());
    }

    /** Inserts and extracts that moved something, since this storage was made. */
    public long operations() {
        return operations;
    }

    public int types() {
        return amounts.size();
    }

    public long amount(AEKey key) {
        return amounts.getOrDefault(key, 0L);
    }

    @Override
    public void mountInventories(IStorageMounts storageMounts) {
        storageMounts.mount(this, 5);
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (mode == Actionable.MODULATE && amount > 0) {
            amounts.merge(what, amount, Long::sum);
            operations++;
        }
        return amount;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        var stored = amounts.getOrDefault(what, 0L);
        var extracted = Math.min(stored, amount);
        if (mode == Actionable.MODULATE && extracted > 0) {
            operations++;
            if (extracted == stored) {
                amounts.remove(what);
            } else {
                amounts.put(what, stored - extracted);
            }
        }
        return extracted;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        amounts.forEach(out::add);
    }

    @Override
    public Component getDescription() {
        return Component.literal("Benchmark bulk storage");
    }
}
