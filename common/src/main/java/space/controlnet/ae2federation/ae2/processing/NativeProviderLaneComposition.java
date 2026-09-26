package space.controlnet.ae2federation.ae2.processing;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.ticking.IGridTickable;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import space.controlnet.ae2federation.processing.provider.ProviderTargetResolution;
import space.controlnet.ae2federation.processing.provider.ProviderLogicProvenance;

public final class NativeProviderLaneComposition implements InternalInventoryHost, AutoCloseable {
    private static final String PATTERNS_TAG = "patterns";
    private final IManagedGridNode physicalNode;
    private final PatternProviderLogicHost ownerHost;
    private final InternalInventory patternInventory;
    private final boolean ownsPatternInventory;
    private final List<NativeProviderLane> lanes = new ArrayList<>();
    private final List<NativeProviderLaneServices> services = new ArrayList<>();
    private final NativeProviderLaneTicker physicalTicker;
    private ICraftingService craftingService;

    public NativeProviderLaneComposition(IManagedGridNode physicalNode, PatternProviderLogicHost ownerHost,
            List<? extends PatternProviderLogicHost> laneHosts, int patternSlots,
            List<IntPredicate> laneAssignments) {
        this(physicalNode, ownerHost, null, patternSlots);
        if (laneHosts.size() != laneAssignments.size() || laneHosts.isEmpty()) {
            throw new IllegalArgumentException("Every native lane requires one host and one Pattern assignment");
        }
        for (int index = 0; index < laneHosts.size(); index++) {
            addLane(laneHosts.get(index), laneAssignments.get(index));
        }
    }

    /**
     * Creates a composition whose Pattern slots live in {@code externalPatterns} (for example the native
     * PatternProviderLogic inventory that the production block exposes through AE2's own menu), or in a new inventory
     * owned by this composition when {@code externalPatterns} is null. Lanes may be added later with
     * {@link #addLane}; the physical ticker and crafting-provider registrations follow them.
     */
    public NativeProviderLaneComposition(IManagedGridNode physicalNode, PatternProviderLogicHost ownerHost,
            InternalInventory externalPatterns, int patternSlots) {
        this.physicalNode = physicalNode;
        this.ownerHost = ownerHost;
        this.ownsPatternInventory = externalPatterns == null;
        this.patternInventory = externalPatterns == null ? new AppEngInternalInventory(this, patternSlots, 1)
                : externalPatterns;
        if (patternInventory.size() != patternSlots) {
            throw new IllegalArgumentException("External Pattern inventory size does not match the composition");
        }
        physicalTicker = new NativeProviderLaneTicker(services);
        physicalNode.addService(IGridTickable.class, physicalTicker);
    }

    /**
     * Adds one native execution Lane. Its PatternProviderLogic installs its ticker and crafting provider into a
     * captured facade, so the physical node keeps one ticker and each Lane is published as its own global provider.
     */
    public NativeProviderLane addLane(PatternProviderLogicHost laneHost, IntPredicate assignment) {
        var captured = new NativeProviderLaneServices();
        var lane = new NativeProviderLane(new CapturedManagedGridNode(physicalNode, captured), laneHost,
                patternInventory, assignment);
        if (captured.provider() != lane) {
            throw new IllegalStateException("PatternProviderLogic installed an unexpected crafting provider");
        }
        captured.ticker();
        services.add(captured);
        lanes.add(lane);
        lane.updatePatterns();
        if (craftingService != null) {
            craftingService.addGlobalCraftingProvider(lane);
        }
        physicalNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
        return lane;
    }

    /**
     * Moves the Lanes' global crafting-provider registrations to the physical node's current Grid. AE2 tracks node
     * services across Grid merges and splits by itself, but global providers are registered on one Grid's crafting
     * service and must follow the node explicitly.
     */
    public void rebindGrid() {
        if (craftingService == null) {
            return;
        }
        var grid = physicalNode.getGrid();
        var current = grid == null ? null : grid.getCraftingService();
        if (current == craftingService) {
            return;
        }
        for (var lane : lanes) {
            craftingService.removeGlobalCraftingProvider(lane);
        }
        craftingService = current;
        if (current != null) {
            for (var lane : lanes) {
                current.addGlobalCraftingProvider(lane);
            }
            refreshPatterns();
        }
    }

    public boolean registered() {
        return craftingService != null;
    }

    public InternalInventory patternInventory() {
        return patternInventory;
    }

    public List<NativeProviderLane> lanes() {
        return java.util.Collections.unmodifiableList(lanes);
    }

    public void bindTarget(int laneIndex, ProviderLogicProvenance provenance,
            Supplier<ProviderTargetResolution> resolver) {
        if (laneIndex < 0 || laneIndex >= lanes.size()) {
            throw new IndexOutOfBoundsException("Native Lane target index is outside the composition");
        }
        FederationPatternProviderTargetCache.bind(lanes.get(laneIndex), provenance, resolver, physicalNode::getNode);
    }

    public List<Long> nativeTickerInvocations() {
        return services.stream().map(NativeProviderLaneServices::tickerInvocations).toList();
    }

    public List<Long> nativeProviderRefreshInvocations() {
        return services.stream().map(NativeProviderLaneServices::providerRefreshInvocations).toList();
    }

    public int nativeTickerDelegateCount() {
        return services.size();
    }

    public boolean hasPhysicalTickerService() {
        var node = physicalNode.getNode();
        return node != null && node.getService(IGridTickable.class) == physicalTicker;
    }

    public boolean hasPhysicalCraftingProviderService() {
        var node = physicalNode.getNode();
        return node != null && node.getService(appeng.api.networking.crafting.ICraftingProvider.class) != null;
    }

    public boolean isActive() {
        return physicalNode.isActive();
    }

    public void register() {
        var grid = physicalNode.getGrid();
        if (grid == null || craftingService != null) {
            throw new IllegalStateException("Physical node must be ready and composition must be unregistered");
        }
        craftingService = grid.getCraftingService();
        for (var lane : lanes) {
            craftingService.addGlobalCraftingProvider(lane);
        }
        refreshPatterns();
    }

    public void refreshPatterns() {
        refreshLanes(java.util.stream.IntStream.range(0, lanes.size()).boxed().toList());
    }

    public void refreshPatternSlot(int slot) {
        if (slot < 0 || slot >= patternInventory.size()) {
            throw new IndexOutOfBoundsException("Pattern slot is outside the physical inventory: " + slot);
        }
        var affected = new TreeSet<Integer>();
        for (int laneIndex = 0; laneIndex < lanes.size(); laneIndex++) {
            if (lanes.get(laneIndex).isAssignedSlot(slot)) {
                affected.add(laneIndex);
            }
        }
        refreshLanes(affected);
    }

    public void refreshLanes(Collection<Integer> laneIndexes) {
        var ordered = new TreeSet<>(laneIndexes);
        if (ordered.stream().anyMatch(index -> index < 0 || index >= lanes.size())) {
            throw new IndexOutOfBoundsException("Native Lane refresh index is outside the composition");
        }
        for (var laneIndex : ordered) {
            var lane = lanes.get(laneIndex);
            lane.updatePatterns();
            if (craftingService != null) {
                craftingService.refreshGlobalCraftingProvider(lane);
                services.get(laneIndex).recordProviderRefresh();
            }
        }
    }

    public void writeToNBT(CompoundTag tag, HolderLookup.Provider registries) {
        if (ownsPatternInventory) {
            ((AppEngInternalInventory) patternInventory).writeToNBT(tag, PATTERNS_TAG, registries);
        }
        for (int index = 0; index < lanes.size(); index++) {
            var laneTag = new CompoundTag();
            lanes.get(index).writeToNBT(laneTag, registries);
            tag.put("lane" + index, laneTag);
        }
    }

    public void readFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        if (ownsPatternInventory) {
            ((AppEngInternalInventory) patternInventory).readFromNBT(tag, PATTERNS_TAG, registries);
        }
        for (int index = 0; index < lanes.size(); index++) {
            lanes.get(index).readFromNBT(tag.getCompound("lane" + index), registries);
        }
        refreshPatterns();
    }

    public void addDrops(List<ItemStack> drops) {
        if (ownsPatternInventory) {
            patternInventory.forEach(stack -> drops.add(stack.copy()));
        }
        lanes.forEach(lane -> lane.addDrops(drops));
    }

    @Override
    public void close() {
        lanes.forEach(FederationPatternProviderTargetCache::unbind);
        if (craftingService != null) {
            lanes.forEach(craftingService::removeGlobalCraftingProvider);
            craftingService = null;
        }
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inventory) {
        ownerHost.saveChanges();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inventory, int slot) {
        refreshPatternSlot(slot);
    }

    @Override
    public boolean isClientSide() {
        var level = ownerHost.getBlockEntity().getLevel();
        return level == null || level.isClientSide();
    }
}
