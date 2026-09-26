package space.controlnet.ae2federation.ae2.processing;

import appeng.api.networking.IManagedGridNode;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.util.inv.AppEngInternalInventory;
import java.util.function.IntConsumer;

/**
 * The native PatternProviderLogic that owns a Federation Pattern Provider's physical Pattern slots and settings. It is
 * what AE2's own Pattern Provider menu, Pattern Access Terminal and memory card operate on, so Pattern insertion,
 * Blocking, Lock Crafting mode, priority and terminal visibility use the native UI and persistence unchanged.
 *
 * <p>It never executes: its ticker and crafting-provider services go to a discarded facade instead of the physical node,
 * so AE2 cannot select it as a pattern medium. Execution belongs to the per-Endpoint {@link NativeProviderLane}s, which
 * read Patterns from this logic's inventory and hold their own native send, return and lock state.
 */
public final class NativeProviderOwnerLogic extends PatternProviderLogic {
    private IntConsumer slotChanged = slot -> {
    };

    public NativeProviderOwnerLogic(IManagedGridNode physicalNode, PatternProviderLogicHost host, int patternSlots) {
        super(new CapturedManagedGridNode(physicalNode, new NativeProviderLaneServices()), host, patternSlots);
    }

    public void onPatternSlotChanged(IntConsumer listener) {
        slotChanged = java.util.Objects.requireNonNull(listener);
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inventory, int slot) {
        saveChanges();
        slotChanged.accept(slot);
    }

    @Override
    public void updatePatterns() {
        // The owner publishes no patterns of its own; Lanes decode the shared slots when they are refreshed.
    }
}
