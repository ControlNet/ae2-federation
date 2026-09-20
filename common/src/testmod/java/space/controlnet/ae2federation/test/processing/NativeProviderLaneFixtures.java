package space.controlnet.ae2federation.test.processing;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEBlocks;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.blockentity.misc.InterfaceBlockEntity;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.IntPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import space.controlnet.ae2federation.ae2.processing.NativeProviderLane;
import space.controlnet.ae2federation.processing.provider.MappedPatternProvider;
import space.controlnet.ae2federation.processing.provider.MappedPatternProviderHost;
import space.controlnet.ae2federation.identity.NetworkId;

public final class NativeProviderLaneFixtures implements MappedPatternProviderHost, AutoCloseable {
    static final BlockPos HOST_POS = new BlockPos(3, 2, 3);
    private static final BlockPos ENERGY_POS = HOST_POS.west();
    public static final BlockPos TARGET_POS = HOST_POS.east();
    private final GameTestHelper helper;
    private final IManagedGridNode node;
    private final MappedPatternProvider composition;
    private final List<NativeProviderLaneHost> hosts;
    private final boolean explicitEnergyConnection;
    private final BlockPos energyPosition;
    private final NativeProviderTargets targets;
    private final NativeProviderPatterns patterns;
    private final NativeProviderInspection inspection;
    private int ownerSaveCalls;

    public NativeProviderLaneFixtures(GameTestHelper helper, List<IntPredicate> assignments) {
        this(helper, assignments, false, 3);
    }

    public NativeProviderLaneFixtures(GameTestHelper helper, List<IntPredicate> assignments, boolean isolatedPower) {
        this(helper, assignments, isolatedPower, 3);
    }

    public NativeProviderLaneFixtures(GameTestHelper helper, List<IntPredicate> assignments, boolean isolatedPower,
            int patternSlots) {
        this(helper, assignments, isolatedPower, patternSlots, null);
    }

    public NativeProviderLaneFixtures(GameTestHelper helper, List<IntPredicate> assignments, boolean isolatedPower,
            int patternSlots, NetworkId networkId) {
        this.helper = helper;
        targets = new NativeProviderTargets(helper);
        var setup = NativeProviderSetup.create(helper, this, assignments, isolatedPower, patternSlots, networkId);
        node = setup.node();
        composition = setup.composition();
        hosts = setup.hosts();
        energyPosition = setup.energyPosition();
        explicitEnergyConnection = setup.explicitEnergyConnection();
        patterns = new NativeProviderPatterns(composition);
        inspection = new NativeProviderInspection(helper, node, composition);
    }

    public static List<IntPredicate> sharedPatternAssignments() {
        return sharedPatternAssignments(3);
    }

    public static List<IntPredicate> sharedPatternAssignments(int laneCount) {
        var assignments = new ArrayList<IntPredicate>(laneCount);
        for (int index = 0; index < laneCount; index++) {
            assignments.add(slot -> slot == 0);
        }
        return List.copyOf(assignments);
    }

    public static List<IntPredicate> subsetAssignments() {
        return List.of(slot -> slot == 0, slot -> slot <= 1, slot -> slot >= 1);
    }

    public void register() { composition.register(); }

    public boolean wakeNativeTicker() {
        return node.getGrid().getTickManager().alertDevice(node.getNode());
    }

    public List<Long> nativeTickerInvocations() { return inspection.nativeTickerInvocations(); }

    public List<ICraftingProvider> publishedProviders(int laneIndex, int patternIndex) {
        return publishedProviders(lane(laneIndex).getAvailablePatterns().get(patternIndex));
    }

    public List<ICraftingProvider> publishedProviders(appeng.api.crafting.IPatternDetails pattern) {
        return inspection.publishedProviders(pattern);
    }

    public void installBypassCraftingMachine() { inspection.installBypassCraftingMachine(); }

    public boolean hasBypassCraftingMachine() { return inspection.hasBypassCraftingMachine(); }

    public boolean bypassCraftingMachineIsEmpty() { return inspection.bypassCraftingMachineIsEmpty(); }

    public void removeConfiguredTarget() { targets.removeConfiguredTarget(); }

    public void restoreConfiguredTarget() { targets.restoreConfiguredTarget(); }

    public void installEndpointTarget() { targets.installEndpointTarget(); }

    public void installFederationEndpointTarget() { targets.installFederationEndpointTarget(); }

    public void installFederationEndpointTarget(BlockPos endpointPosition) {
        targets.installFederationEndpointTarget(endpointPosition);
    }

    public IGridNode endpointTargetNode() { return targets.endpointTargetNode(); }

    public IGridNode endpointTargetNode(BlockPos endpointPosition) {
        return targets.endpointTargetNode(endpointPosition);
    }

    public BlockPos endpointTargetPosition() { return targets.endpointTargetPosition(); }

    public boolean connectEnergy() {
        if (!explicitEnergyConnection) {
            return node.isActive();
        }
        var providerNode = node.getNode();
        var energyNode = GridHelper.getExposedNode(helper.getLevel(), helper.absolutePos(ENERGY_POS), Direction.EAST);
        if (providerNode == null || energyNode == null) {
            return false;
        }
        if (providerNode.getGrid() == energyNode.getGrid()) {
            return true;
        }
        GridHelper.createConnection(providerNode, energyNode);
        return false;
    }

    public void connectTo(IGridNode gridNode) {
        var providerNode = node.getNode();
        if (providerNode != null && providerNode.getGrid() != gridNode.getGrid()) {
            GridHelper.createConnection(providerNode, gridNode);
        }
    }

    public MappedPatternProvider composition() { return composition; }

    @Override
    public MappedPatternProvider mappedPatternProvider() { return composition; }

    public NativeProviderLane lane(int index) { return patterns.lane(index); }

    public void installPatterns(int count) { patterns.installPatterns(count); }

    public void installPattern(int slot, List<GenericStack> inputs, List<GenericStack> outputs) {
        patterns.installPattern(slot, inputs, outputs);
    }

    public void setPattern(int slot, List<GenericStack> inputs, List<GenericStack> outputs) {
        patterns.setPattern(slot, inputs, outputs);
    }

    public void refreshPatterns() { patterns.refreshPatterns(); }

    public boolean pushInputs(int laneIndex, int patternIndex, List<GenericStack> inputs) {
        return patterns.pushInputs(laneIndex, patternIndex, inputs);
    }

    public boolean push(int laneIndex, int patternIndex) {
        return patterns.push(laneIndex, patternIndex);
    }

    public void lockUntilResult(int laneIndex) { patterns.lockUntilResult(laneIndex); }

    public int targetItemCount() { return targets.targetItemCount(); }

    public int targetItemCount(Item item) {
        return targets.targetItemCount(item);
    }

    public long extractTargetItem(Item item, long amount) {
        return targets.extractTargetItem(item, amount);
    }

    public String targetSnapshot() { return targets.targetSnapshot(); }

    public Object targetOwner() { return targets.targetOwner(); }

    public BlockPos targetPosition() { return helper.absolutePos(TARGET_POS); }

    public void leaveOneSharedTargetSlot() { targets.leaveOneSharedTargetSlot(); }

    public long endpointTargetItemCount() { return targets.endpointTargetItemCount(); }

    public BlockEntity hostBlockEntity() { return helper.getBlockEntity(HOST_POS); }

    @Override
    public BlockEntity getBlockEntity() { return hostBlockEntity(); }

    @Override
    public EnumSet<Direction> getTargets() { return EnumSet.of(Direction.EAST); }

    @Override
    public void saveChanges() {
        ownerSaveCalls++;
        getBlockEntity().setChanged();
    }

    public int ownerSaveCalls() { return ownerSaveCalls; }

    public int laneSaveCalls() {
        return hosts.stream().mapToInt(NativeProviderLaneHost::saveCalls).sum();
    }

    public IManagedGridNode managedNode() { return node; }

    public int laneCount() { return composition.lanes().size(); }

    GameTestHelper helper() { return helper; }

    @Override
    public AEItemKey getTerminalIcon() {
        return AEItemKey.of(AEItems.PROCESSING_PATTERN.asItem());
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return AEItems.PROCESSING_PATTERN.stack();
    }

    @Override
    public void close() {
        composition.close();
        node.destroy();
        helper.setBlock(energyPosition, Blocks.AIR);
    }

}
