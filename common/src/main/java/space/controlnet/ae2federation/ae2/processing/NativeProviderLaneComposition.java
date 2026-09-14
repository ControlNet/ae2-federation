package space.controlnet.ae2federation.ae2.processing;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.ticking.IGridTickable;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public final class NativeProviderLaneComposition implements InternalInventoryHost, AutoCloseable {
    private static final String PATTERNS_TAG = "patterns";
    private final IManagedGridNode physicalNode;
    private final PatternProviderLogicHost ownerHost;
    private final AppEngInternalInventory patternInventory;
    private final List<NativeProviderLane> lanes;
    private final List<NativeProviderLaneServices> services;
    private ICraftingService craftingService;

    public NativeProviderLaneComposition(IManagedGridNode physicalNode, PatternProviderLogicHost ownerHost,
            List<? extends PatternProviderLogicHost> laneHosts, int patternSlots,
            List<IntPredicate> laneAssignments) {
        if (laneHosts.size() != laneAssignments.size() || laneHosts.isEmpty()) {
            throw new IllegalArgumentException("Every native lane requires one host and one Pattern assignment");
        }
        this.physicalNode = physicalNode;
        this.ownerHost = ownerHost;
        this.patternInventory = new AppEngInternalInventory(this, patternSlots, 1);
        var mutableServices = new ArrayList<NativeProviderLaneServices>();
        var mutableLanes = new ArrayList<NativeProviderLane>();
        for (int index = 0; index < laneHosts.size(); index++) {
            var captured = new NativeProviderLaneServices();
            mutableServices.add(captured);
            var lane = new NativeProviderLane(new CapturedManagedGridNode(physicalNode, captured),
                    laneHosts.get(index), patternInventory, laneAssignments.get(index));
            if (captured.provider() != lane) {
                throw new IllegalStateException("PatternProviderLogic installed an unexpected crafting provider");
            }
            mutableLanes.add(lane);
        }
        lanes = List.copyOf(mutableLanes);
        services = List.copyOf(mutableServices);
        physicalNode.addService(IGridTickable.class, new NativeProviderLaneTicker(services));
    }

    public InternalInventory patternInventory() {
        return patternInventory;
    }

    public List<NativeProviderLane> lanes() {
        return lanes;
    }

    public List<Long> nativeTickerInvocations() {
        return services.stream().map(NativeProviderLaneServices::tickerInvocations).toList();
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
        for (var lane : lanes) {
            lane.updatePatterns();
            if (craftingService != null) {
                craftingService.refreshGlobalCraftingProvider(lane);
            }
        }
    }

    public void writeToNBT(CompoundTag tag, HolderLookup.Provider registries) {
        patternInventory.writeToNBT(tag, PATTERNS_TAG, registries);
        for (int index = 0; index < lanes.size(); index++) {
            var laneTag = new CompoundTag();
            lanes.get(index).writeToNBT(laneTag, registries);
            tag.put("lane" + index, laneTag);
        }
    }

    public void readFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        patternInventory.readFromNBT(tag, PATTERNS_TAG, registries);
        for (int index = 0; index < lanes.size(); index++) {
            lanes.get(index).readFromNBT(tag.getCompound("lane" + index), registries);
        }
        refreshPatterns();
    }

    public void addDrops(List<ItemStack> drops) {
        patternInventory.forEach(stack -> drops.add(stack.copy()));
        lanes.forEach(lane -> lane.addDrops(drops));
    }

    @Override
    public void close() {
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
        refreshPatterns();
    }

    @Override
    public boolean isClientSide() {
        var level = ownerHost.getBlockEntity().getLevel();
        return level == null || level.isClientSide();
    }
}
