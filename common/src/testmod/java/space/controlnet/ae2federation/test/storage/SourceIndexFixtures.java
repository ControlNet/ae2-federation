package space.controlnet.ae2federation.test.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;

/**
 * Plain third-party-style storages and providers that use only AE2's public storage API. They stand in for mods that
 * mount inventories through {@code IStorageProvider} / {@code addGlobalStorageProvider} without knowing Federation.
 */
public final class SourceIndexFixtures {
    private SourceIndexFixtures() {
    }

    /** Distinct item keys that differ only by a data component. */
    public static List<AEItemKey> distinctKeys(int count) {
        var keys = new ArrayList<AEItemKey>(count);
        for (var index = 0; index < count; index++) {
            var stack = new ItemStack(Items.PAPER);
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(index + 1));
            keys.add(AEItemKey.of(stack));
        }
        return List.copyOf(keys);
    }

    /** Independent inventory with a total-amount capacity. Quantities live only here. */
    public static final class CountingStorage implements MEStorage {
        private final String name;
        private final long capacity;
        private final KeyCounter stored = new KeyCounter();

        public CountingStorage(String name, long capacity) {
            this.name = name;
            this.capacity = capacity;
        }

        public CountingStorage preload(List<? extends AEKey> keys, long amount) {
            keys.forEach(key -> stored.add(key, amount));
            return this;
        }

        public long amount(AEKey key) {
            return stored.get(key);
        }

        public long total() {
            var total = 0L;
            for (var entry : stored) {
                total += entry.getLongValue();
            }
            return total;
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            var accepted = Math.min(amount, Math.max(0, capacity - total()));
            if (mode == Actionable.MODULATE && accepted > 0) {
                stored.add(what, accepted);
            }
            return accepted;
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            var extracted = Math.min(amount, stored.get(what));
            if (mode == Actionable.MODULATE && extracted > 0) {
                stored.remove(what, extracted);
                stored.removeZeros();
            }
            return extracted;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            for (var entry : stored) {
                out.add(entry.getKey(), entry.getLongValue());
            }
        }

        @Override
        public Component getDescription() {
            return Component.literal("source-index " + name);
        }
    }

    /**
     * Native provider mounting several independent inventories, like a drive with several cells. Changes take effect
     * only after AE2 remounts (the caller uses {@code IStorageProvider.requestUpdate}).
     */
    public static final class MultiHandleProvider implements IStorageProvider {
        private final Map<MEStorage, Boolean> handles = new LinkedHashMap<>();
        private int priority;

        public MultiHandleProvider(int priority, MEStorage... storages) {
            this.priority = priority;
            for (var storage : storages) {
                handles.put(storage, Boolean.TRUE);
            }
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }

        public void add(MEStorage storage) {
            handles.put(storage, Boolean.TRUE);
        }

        public void remove(MEStorage storage) {
            handles.remove(storage);
        }

        @Override
        public void mountInventories(IStorageMounts storageMounts) {
            handles.keySet().forEach(storage -> storageMounts.mount(storage, priority));
        }
    }

    /** Opaque third-party wrapper: forwards everything but is not AE2's DelegatingMEInventory. */
    public record OpaqueWrapper(MEStorage delegate) implements MEStorage {
        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            return delegate.insert(what, amount, mode, source);
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            return delegate.extract(what, amount, mode, source);
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            delegate.getAvailableStacks(out);
        }

        @Override
        public Component getDescription() {
            return Component.literal("opaque wrapper");
        }
    }
}
