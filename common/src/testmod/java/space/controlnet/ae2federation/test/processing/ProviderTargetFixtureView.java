package space.controlnet.ae2federation.test.processing;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import net.minecraft.world.item.Items;

final class ProviderTargetFixtureView {
    private final NativeProviderLaneFixtures provider;

    ProviderTargetFixtureView(NativeProviderLaneFixtures provider) {
        this.provider = provider;
    }

    Object nativeRemainderDestination(int laneIndex) {
        return provider.lane(laneIndex).getReturnInv();
    }

    long nativeRemainderAmount(int laneIndex, int slot) {
        return provider.lane(laneIndex).getReturnInv().getAmount(slot);
    }

    appeng.helpers.patternprovider.PatternProviderLogic providerLogic(int laneIndex) {
        return provider.lane(laneIndex);
    }

    long targetItemCount() {
        return provider.endpointTargetItemCount();
    }

    long targetAmount(AEKey key) {
        return storage().extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }

    void fillTarget(AEKey key) {
        storage().insert(key, Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty());
    }

    void clearTarget(AEKey key) {
        storage().extract(key, Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty());
    }

    long extractTarget(AEKey key, long amount) {
        return storage().extract(key, amount, Actionable.MODULATE, IActionSource.empty());
    }

    int laneCount() {
        return provider.laneCount();
    }

    boolean wakeProviderTicker() {
        return provider.wakeNativeTicker();
    }

    String targetSnapshot() {
        return "minecraft:cobblestone:" + targetAmount(AEItemKey.of(Items.COBBLESTONE))
                + ",minecraft:dirt:" + targetAmount(AEItemKey.of(Items.DIRT));
    }

    void leaveOneSharedTargetSlot() {
        var storage = storage();
        storage.insert(AEItemKey.of(Items.DIRT), 1, Actionable.MODULATE, IActionSource.empty());
        storage.insert(AEItemKey.of(Items.COBBLESTONE), Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty());
        storage.extract(AEItemKey.of(Items.COBBLESTONE), 1, Actionable.MODULATE, IActionSource.empty());
    }

    IGrid sourceGrid() {
        return provider.managedNode().getGrid();
    }

    IGridNode sourceNode() {
        return provider.managedNode().getNode();
    }

    IGrid targetGrid() {
        return provider.endpointTargetNode().getGrid();
    }

    private appeng.api.storage.MEStorage storage() {
        return targetGrid().getStorageService().getInventory();
    }
}
