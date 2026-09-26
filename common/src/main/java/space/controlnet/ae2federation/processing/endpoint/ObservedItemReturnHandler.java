package space.controlnet.ae2federation.processing.endpoint;

import appeng.api.stacks.AEItemKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import space.controlnet.ae2federation.processing.provider.ProviderObservationRegistry;

final class ObservedItemReturnHandler implements IItemHandler {
    private final ServerLevel level;
    private final EndpointReturnOwner owner;
    private final IItemHandler delegate;

    ObservedItemReturnHandler(ServerLevel level, EndpointReturnOwner owner, IItemHandler delegate) {
        this.level = java.util.Objects.requireNonNull(level);
        this.owner = java.util.Objects.requireNonNull(owner);
        this.delegate = java.util.Objects.requireNonNull(delegate);
    }

    @Override
    public int getSlots() {
        return delegate.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return delegate.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        var remainder = delegate.insertItem(slot, stack, simulate);
        var accepted = stack.getCount() - remainder.getCount();
        if (!simulate && accepted > 0) {
            owner.lane().ifPresent(lane -> ProviderObservationRegistry.recordAggregateReturn(level, lane,
                    AEItemKey.of(stack), accepted));
        }
        return remainder;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        var extracted = delegate.extractItem(slot, amount, simulate);
        if (!simulate && !extracted.isEmpty()) {
            owner.lane().ifPresent(lane -> ProviderObservationRegistry.recordAggregateReturn(level, lane,
                    AEItemKey.of(extracted), extracted.getCount()));
        }
        return extracted;
    }

    @Override
    public int getSlotLimit(int slot) {
        return delegate.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return delegate.isItemValid(slot, stack);
    }
}
