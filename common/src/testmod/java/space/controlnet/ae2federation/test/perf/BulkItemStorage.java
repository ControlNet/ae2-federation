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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

/**
 * TEST-ONLY in-memory native storage with many distinct item types, mounted through an AE2 {@link IStorageProvider}.
 * It stands in for a large drive array: real cells cap each at 63 types.
 */
public final class BulkItemStorage implements MEStorage, IStorageProvider {
    private final Map<AEKey, Long> amounts = new LinkedHashMap<>();

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
        if (added < types) {
            throw new IllegalStateException("Only " + added + " item types are registered");
        }
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
        if (mode == Actionable.MODULATE) {
            amounts.merge(what, amount, Long::sum);
        }
        return amount;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        var stored = amounts.getOrDefault(what, 0L);
        var extracted = Math.min(stored, amount);
        if (mode == Actionable.MODULATE && extracted > 0) {
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
