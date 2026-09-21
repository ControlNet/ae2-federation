package space.controlnet.ae2federation.processing.provider;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.config.LockCraftingMode;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import space.controlnet.ae2federation.ae2.processing.NativeProviderLane;
import space.controlnet.ae2federation.ae2.processing.NativeProviderLaneComposition;

public final class MappedPatternProvider implements PatternContainer, AutoCloseable {
    private static final String MAPPING_TAG = "patternLaneMapping";
    private static final String NATIVE_TAG = "nativeProviderLanes";
    private final IManagedGridNode physicalNode;
    private final PatternLaneMapping mapping;
    private final NativeProviderLaneComposition composition;

    public MappedPatternProvider(IManagedGridNode physicalNode, PatternProviderLogicHost ownerHost,
            List<? extends PatternProviderLogicHost> laneHosts, int patternSlots) {
        this.physicalNode = physicalNode;
        mapping = new PatternLaneMapping(patternSlots, laneHosts.size());
        var assignments = java.util.stream.IntStream.range(0, laneHosts.size())
                .<java.util.function.IntPredicate>mapToObj(lane -> slot -> mapping.isAssigned(lane, slot)).toList();
        composition = new NativeProviderLaneComposition(physicalNode, ownerHost, laneHosts, patternSlots, assignments);
    }

    public void register() {
        composition.register();
    }

    public void refreshPatterns() {
        composition.refreshPatterns();
    }

    public PatternSlotHandle mappingHandle(int slot) {
        return mapping.handle(slot);
    }

    public boolean replaceMapping(PatternSlotHandle handle, Set<Integer> laneIndexes) {
        var changed = mapping.replace(handle, laneIndexes);
        changed.filter(indexes -> !indexes.isEmpty()).ifPresent(composition::refreshLanes);
        return changed.isPresent();
    }

    public Set<Integer> lanesForSlot(int slot) {
        return mapping.lanesForSlot(slot);
    }

    public Set<Integer> slotsForLane(int laneIndex) {
        return mapping.slotsForLane(laneIndex);
    }

    public NativeProviderLane nativeLane(int laneIndex) {
        return composition.lanes().get(laneIndex);
    }

    public List<NativeProviderLane> nativeLanes() {
        return composition.lanes();
    }

    public List<NativeProviderLane> lanes() {
        return composition.lanes();
    }

    public void bindTarget(int laneIndex, ProviderLogicProvenance provenance,
            Supplier<ProviderTargetResolution> resolver) {
        composition.bindTarget(laneIndex, provenance, resolver);
    }

    public InternalInventory patternInventory() {
        return composition.patternInventory();
    }

    public int priority() {
        return composition.lanes().getFirst().getPriority();
    }

    public void setPriority(int priority) {
        composition.lanes().forEach(lane -> lane.setPriority(priority));
        composition.refreshLanes(java.util.stream.IntStream.range(0, composition.lanes().size()).boxed().toList());
    }

    public void setPatternAccessTerminalVisible(boolean visible) {
        var value = visible ? YesNo.YES : YesNo.NO;
        composition.lanes().forEach(lane -> lane.getConfigManager().putSetting(Settings.PATTERN_ACCESS_TERMINAL, value));
    }

    public void setLockCraftingMode(LockCraftingMode mode) {
        composition.lanes().forEach(lane -> lane.getConfigManager().putSetting(Settings.LOCK_CRAFTING_MODE, mode));
    }

    public void updateRedstoneState() {
        composition.lanes().forEach(NativeProviderLane::updateRedstoneState);
    }

    public void onMainNodeStateChanged() {
        composition.lanes().forEach(NativeProviderLane::onMainNodeStateChanged);
    }

    public void writeToNBT(CompoundTag tag, HolderLookup.Provider registries) {
        var mappingTag = new CompoundTag();
        PatternLaneMappingCodec.write(mapping, mappingTag);
        tag.put(MAPPING_TAG, mappingTag);
        var nativeTag = new CompoundTag();
        composition.writeToNBT(nativeTag, registries);
        tag.put(NATIVE_TAG, nativeTag);
    }

    public void readFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        PatternLaneMappingCodec.read(mapping, tag.getCompound(MAPPING_TAG));
        composition.readFromNBT(tag.getCompound(NATIVE_TAG), registries);
    }

    public void addDrops(List<ItemStack> drops) {
        composition.addDrops(drops);
    }

    public List<Long> nativeTickerInvocations() {
        return composition.nativeTickerInvocations();
    }

    public List<Long> nativeProviderRefreshInvocations() {
        return composition.nativeProviderRefreshInvocations();
    }

    public int nativeTickerDelegateCount() {
        return composition.nativeTickerDelegateCount();
    }

    public boolean hasSinglePhysicalTickerService() {
        return composition.hasPhysicalTickerService();
    }

    public boolean hasPhysicalCraftingProviderService() {
        return composition.hasPhysicalCraftingProviderService();
    }

    public boolean isActive() {
        return composition.isActive();
    }

    @Override
    public InternalInventory getTerminalPatternInventory() {
        return composition.patternInventory();
    }

    @Override
    public boolean isVisibleInTerminal() {
        return composition.lanes().getFirst().getConfigManager()
                .getSetting(Settings.PATTERN_ACCESS_TERMINAL) == YesNo.YES;
    }

    @Override
    public long getTerminalSortOrder() {
        return composition.lanes().getFirst().getSortValue();
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        return composition.lanes().getFirst().getTerminalGroup();
    }

    @Override
    public IGrid getGrid() {
        return physicalNode.getGrid();
    }

    @Override
    public void close() {
        ProviderObservationRegistry.unregister(this);
        composition.close();
    }
}
