package space.controlnet.ae2federation.ae2.processing;

import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import java.util.List;
import java.util.function.IntPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import space.controlnet.ae2federation.mixin.PatternProviderLogicAccess;
import space.controlnet.ae2federation.processing.provider.ProviderObservationRegistry;

public final class NativeProviderLane extends PatternProviderLogic {
    private final InternalInventory ownerInventory;
    private final IntPredicate assignedSlot;
    private final PatternProviderLogicHost laneHost;
    private boolean editingView;
    private Runnable patternsRefreshed = () -> {
    };

    NativeProviderLane(IManagedGridNode node, PatternProviderLogicHost host, InternalInventory ownerInventory,
            IntPredicate assignedSlot) {
        super(node, host, ownerInventory.size());
        this.ownerInventory = ownerInventory;
        this.assignedSlot = assignedSlot;
        this.laneHost = host;
        // The Lane's own Pattern inventory is only its view of the Provider's slots: nothing may put Patterns into it.
        view().setFilter(new IAEItemFilter() {
            @Override
            public boolean allowInsert(InternalInventory inventory, int slot, ItemStack stack) {
                return false;
            }

            @Override
            public boolean allowExtract(InternalInventory inventory, int slot, int amount) {
                return false;
            }
        });
    }

    /**
     * Copies the Provider's Patterns assigned to this Lane into the Lane's own Pattern inventory, the rest left empty,
     * and refreshes through AE2's own {@code updatePatterns}. Addons hook that method to drop, expand or mark Patterns,
     * so the Lane offers the Patterns a native Pattern Provider with the same slots would. The copies are never saved,
     * dropped or exported; the Provider's slots stay the only Patterns.
     */
    @Override
    public void updatePatterns() {
        if (ownerInventory == null) {
            // Called while PatternProviderLogic is still being constructed; the Lane refreshes once it exists.
            return;
        }
        var view = view();
        editingView = true;
        try {
            for (int slot = 0; slot < ownerInventory.size(); slot++) {
                view.setItemDirect(slot, assignedSlot.test(slot) ? ownerInventory.getStackInSlot(slot).copy()
                        : ItemStack.EMPTY);
            }
        } finally {
            editingView = false;
        }
        if (laneHost.getBlockEntity().getLevel() == null) {
            // Loaded before the block entity joined its level; the Provider refreshes Lanes once it is ready.
            var access = (PatternProviderLogicAccess) (Object) this;
            access.ae2federation$getPatterns().clear();
            access.ae2federation$getPatternInputs().clear();
            return;
        }
        super.updatePatterns();
        patternsRefreshed.run();
    }

    /** Runs after every refresh of the Lane's patterns, including an addon's own call to {@code updatePatterns}. */
    void onPatternsRefreshed(Runnable listener) {
        patternsRefreshed = listener;
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inventory, int slot) {
        // The Lane's own edits of its view neither save the Provider nor refresh again.
        if (!editingView) super.onChangeInventory(inventory, slot);
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inventory) {
        if (!editingView) super.saveChangedInventory(inventory);
    }

    private AppEngInternalInventory view() {
        return (AppEngInternalInventory) getPatternInv();
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        // Native PatternProviderLogic accepts any Pattern equal to one it currently holds. AE2's CPU passes one shared
        // details instance for every medium of a Pattern, so each Lane must accept it by equality as well.
        var access = (PatternProviderLogicAccess) (Object) this;
        var before = java.util.List.copyOf(access.ae2federation$getSendList());
        var pushed = super.pushPattern(patternDetails, inputHolder);
        if (pushed) {
            ProviderObservationRegistry.recordSend(this, inputHolder, before,
                    java.util.List.copyOf(access.ae2federation$getSendList()));
            if (laneHost instanceof NativeLaneDispatchListener listener) {
                listener.onLaneDispatched();
            }
        }
        return pushed;
    }

    boolean isAssignedSlot(int slot) {
        return assignedSlot.test(slot);
    }

    public boolean hasPendingSend() {
        return !((PatternProviderLogicAccess) (Object) this).ae2federation$getSendList().isEmpty();
    }

    @Override
    public void writeToNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeToNBT(tag, registries);
        // The view is rebuilt from the Provider's slots; saving it would store every Pattern twice.
        tag.remove(NBT_MEMORY_CARD_PATTERNS);
    }

    @Override
    public void readFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.readFromNBT(tag, registries);
    }

    @Override
    public void clearContent() {
        // As in a native Pattern Provider the Patterns are gone after this, until the Provider refreshes the Lane.
        editingView = true;
        try {
            super.clearContent();
        } finally {
            editingView = false;
        }
        var access = (PatternProviderLogicAccess) (Object) this;
        access.ae2federation$getPatterns().clear();
        access.ae2federation$getPatternInputs().clear();
    }

    @Override
    public void addDrops(List<ItemStack> drops) {
        // The Provider drops its Patterns from its own slots; the Lane drops only what it is sending or returning.
        var view = view();
        var copies = new ItemStack[view.size()];
        editingView = true;
        try {
            for (int slot = 0; slot < view.size(); slot++) {
                copies[slot] = view.getStackInSlot(slot);
                view.setItemDirect(slot, ItemStack.EMPTY);
            }
            super.addDrops(drops);
        } finally {
            for (int slot = 0; slot < copies.length; slot++) {
                view.setItemDirect(slot, copies[slot]);
            }
            editingView = false;
        }
    }
}
