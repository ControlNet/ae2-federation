package space.controlnet.ae2federation.ae2.processing;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import java.util.function.IntPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import space.controlnet.ae2federation.mixin.PatternProviderLogicAccess;
import space.controlnet.ae2federation.processing.provider.ProviderObservationRegistry;

public final class NativeProviderLane extends PatternProviderLogic {
    private final InternalInventory ownerInventory;
    private final IntPredicate assignedSlot;
    private final PatternProviderLogicHost laneHost;

    NativeProviderLane(IManagedGridNode node, PatternProviderLogicHost host, InternalInventory ownerInventory,
            IntPredicate assignedSlot) {
        super(node, host, 0);
        this.ownerInventory = ownerInventory;
        this.assignedSlot = assignedSlot;
        this.laneHost = host;
    }

    @Override
    public void updatePatterns() {
        var access = (PatternProviderLogicAccess) (Object) this;
        var patterns = access.ae2federation$getPatterns();
        var patternInputs = access.ae2federation$getPatternInputs();
        patterns.clear();
        patternInputs.clear();
        var level = laneHost.getBlockEntity().getLevel();
        if (level == null) {
            // Loaded before the block entity joined its level; the Provider refreshes Lanes once it is ready.
            return;
        }
        for (int slot = 0; slot < ownerInventory.size(); slot++) {
            if (!assignedSlot.test(slot)) {
                continue;
            }
            var details = PatternDetailsHelper.decodePattern(ownerInventory.getStackInSlot(slot), level);
            if (details != null) {
                patterns.add(details);
                for (var input : details.getInputs()) {
                    for (var candidate : input.getPossibleInputs()) {
                        patternInputs.add(candidate.what().dropSecondary());
                    }
                }
            }
        }
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
    }

    @Override
    public void readFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.readFromNBT(tag, registries);
    }
}
