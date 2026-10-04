package space.controlnet.ae2federation.processing.endpoint;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.processing.provider.ProviderObservationRegistry;

/**
 * The owner's native return inventory as AE2's generic internal inventory, as AE2's own Pattern Provider exposes it.
 * Addons wrap this capability for their own resource types (Applied Mekanistics adds Mekanism's chemical handler to
 * every block that has it), so a machine can return those resources through the Endpoint. Inserts are recorded like
 * the item and fluid return paths.
 */
final class ObservedGenericReturnInventory implements GenericInternalInventory {
    private final ServerLevel level;
    private final EndpointReturnOwner owner;
    private final GenericInternalInventory delegate;

    ObservedGenericReturnInventory(ServerLevel level, EndpointReturnOwner owner) {
        this.level = java.util.Objects.requireNonNull(level);
        this.owner = java.util.Objects.requireNonNull(owner);
        this.delegate = owner.inventory();
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public GenericStack getStack(int slot) {
        return delegate.getStack(slot);
    }

    @Override
    public AEKey getKey(int slot) {
        return delegate.getKey(slot);
    }

    @Override
    public long getAmount(int slot) {
        return delegate.getAmount(slot);
    }

    @Override
    public long getMaxAmount(AEKey key) {
        return delegate.getMaxAmount(key);
    }

    @Override
    public long getCapacity(AEKeyType keyType) {
        return delegate.getCapacity(keyType);
    }

    @Override
    public boolean canInsert() {
        return delegate.canInsert();
    }

    @Override
    public boolean canExtract() {
        return delegate.canExtract();
    }

    @Override
    public void setStack(int slot, GenericStack stack) {
        delegate.setStack(slot, stack);
    }

    @Override
    public boolean isSupportedType(AEKeyType keyType) {
        return delegate.isSupportedType(keyType);
    }

    @Override
    public boolean isAllowedIn(int slot, AEKey key) {
        return delegate.isAllowedIn(slot, key);
    }

    @Override
    public long insert(int slot, AEKey key, long amount, Actionable mode) {
        var inserted = delegate.insert(slot, key, amount, mode);
        if (mode == Actionable.MODULATE && inserted > 0) {
            owner.lane().ifPresent(lane -> ProviderObservationRegistry.recordAggregateReturn(level, lane, key, inserted));
        }
        return inserted;
    }

    @Override
    public long extract(int slot, AEKey key, long amount, Actionable mode) {
        return delegate.extract(slot, key, amount, mode);
    }

    @Override
    public void beginBatch() {
        delegate.beginBatch();
    }

    @Override
    public void endBatch() {
        delegate.endBatch();
    }

    @Override
    public void endBatchSuppressed() {
        delegate.endBatchSuppressed();
    }

    @Override
    public void onChange() {
        delegate.onChange();
    }
}
