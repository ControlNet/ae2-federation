package space.controlnet.ae2federation.test.processing;

import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEBlocks;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.me.service.CraftingService;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.storage.StorageCells;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.IntPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import space.controlnet.ae2federation.ae2.processing.NativeProviderLane;
import space.controlnet.ae2federation.processing.provider.MappedPatternProvider;
import space.controlnet.ae2federation.processing.provider.MappedPatternProviderHost;
import space.controlnet.ae2federation.processing.ProcessingRegistration;

public final class NativeProviderLaneFixtures implements MappedPatternProviderHost, AutoCloseable {
    private static final IGridNodeListener<NativeProviderLaneFixtures> LISTENER = (owner, node) -> {
    };
    static final BlockPos HOST_POS = new BlockPos(3, 2, 3);
    private static final BlockPos ENERGY_POS = HOST_POS.west();
    private static final BlockPos ISOLATED_ENERGY_POS = HOST_POS.west(2);
    static final BlockPos TARGET_POS = HOST_POS.east();
    private static final BlockPos ENDPOINT_TARGET_POS = HOST_POS.east(2);
    private static final BlockPos BYPASS_POS = HOST_POS.north();
    private final GameTestHelper helper;
    private final IManagedGridNode node;
    private final MappedPatternProvider composition;
    private final List<NativeProviderLaneHost> hosts;
    private final boolean explicitEnergyConnection;
    private int ownerSaveCalls;

    public NativeProviderLaneFixtures(GameTestHelper helper, List<IntPredicate> assignments) {
        this(helper, assignments, false);
    }

    public NativeProviderLaneFixtures(GameTestHelper helper, List<IntPredicate> assignments, boolean isolatedPower) {
        this.helper = helper;
        explicitEnergyConnection = !isolatedPower;
        helper.setBlock(HOST_POS, Blocks.CHEST);
        helper.setBlock(TARGET_POS, Blocks.CHEST);
        var energyPosition = isolatedPower ? ISOLATED_ENERGY_POS : ENERGY_POS;
        if (isolatedPower) {
            helper.setBlock(ENERGY_POS, Blocks.AIR);
        }
        helper.setBlock(energyPosition, AEBlocks.CREATIVE_ENERGY_CELL.block());
        var managedNode = GridHelper.createManagedNode(this, LISTENER)
                .setTagName("provider")
                .setInWorldNode(true)
                .setIdlePowerUsage(0)
                .setExposedOnSides(EnumSet.of(Direction.WEST));
        if (isolatedPower) {
            managedNode.addService(IAEPowerStorage.class,
                    helper.<CreativeEnergyCellBlockEntity>getBlockEntity(energyPosition));
        }
        node = managedNode;
        hosts = List.of(new NativeProviderLaneHost(helper, HOST_POS), new NativeProviderLaneHost(helper, HOST_POS),
                new NativeProviderLaneHost(helper, HOST_POS));
        composition = new MappedPatternProvider(node, this, hosts, 3);
        for (int index = 0; index < hosts.size(); index++) {
            hosts.get(index).setLogic(composition.lanes().get(index));
        }
        for (int slot = 0; slot < 3; slot++) {
            var assignedLanes = new java.util.TreeSet<Integer>();
            for (int lane = 0; lane < assignments.size(); lane++) {
                if (assignments.get(lane).test(slot)) {
                    assignedLanes.add(lane);
                }
            }
            composition.replaceMapping(composition.mappingHandle(slot), assignedLanes);
        }
        node.create(helper.getLevel(), helper.absolutePos(HOST_POS));
    }

    public static List<IntPredicate> sharedPatternAssignments() {
        return List.of(slot -> slot == 0, slot -> slot == 0, slot -> slot == 0);
    }

    public static List<IntPredicate> subsetAssignments() {
        return List.of(slot -> slot == 0, slot -> slot <= 1, slot -> slot >= 1);
    }

    public void register() {
        composition.register();
    }

    public boolean wakeNativeTicker() {
        return node.getGrid().getTickManager().alertDevice(node.getNode());
    }

    public List<Long> nativeTickerInvocations() {
        return composition.nativeTickerInvocations();
    }

    public List<ICraftingProvider> publishedProviders(int laneIndex, int patternIndex) {
        return publishedProviders(lane(laneIndex).getAvailablePatterns().get(patternIndex));
    }

    public List<ICraftingProvider> publishedProviders(appeng.api.crafting.IPatternDetails pattern) {
        var service = (CraftingService) node.getGrid().getCraftingService();
        var providers = new ArrayList<ICraftingProvider>();
        service.getProviders(pattern).forEach(providers::add);
        return List.copyOf(providers);
    }

    public void installBypassCraftingMachine() {
        helper.setBlock(BYPASS_POS, AEBlocks.MOLECULAR_ASSEMBLER.block());
    }

    public boolean hasBypassCraftingMachine() {
        var machine = ICraftingMachine.of(helper.getLevel(), helper.absolutePos(BYPASS_POS), Direction.SOUTH);
        return machine != null && machine.acceptsPlans();
    }

    public boolean bypassCraftingMachineIsEmpty() {
        var blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(BYPASS_POS));
        if (!(blockEntity instanceof appeng.blockentity.crafting.MolecularAssemblerBlockEntity assembler)) {
            return false;
        }
        return assembler.getInternalInventory().isEmpty();
    }

    public void removeConfiguredTarget() {
        helper.setBlock(TARGET_POS, Blocks.AIR);
    }

    public void installEndpointTarget() {
        installEndpointTarget(AEBlocks.INTERFACE.block(), Direction.EAST);
    }

    public void installFederationEndpointTarget() {
        installEndpointTarget(ProcessingRegistration.ENDPOINT.get(), Direction.NORTH);
    }

    private void installEndpointTarget(net.minecraft.world.level.block.Block endpointBlock, Direction backendSide) {
        helper.setBlock(ENDPOINT_TARGET_POS, endpointBlock);
        var backendPosition = ENDPOINT_TARGET_POS.relative(backendSide);
        helper.setBlock(backendPosition, AEBlocks.ME_CHEST.block());
        helper.setBlock(backendPosition.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        var chest = helper.<MEChestBlockEntity>getBlockEntity(backendPosition);
        var cell = AEItems.ITEM_CELL_1K.stack();
        helper.assertTrue(StorageCells.getCellInventory(cell, null) != null, "Native Endpoint item cell must exist");
        chest.setCell(cell);
    }

    public IGridNode endpointTargetNode() {
        var blockEntity = helper.getBlockEntity(ENDPOINT_TARGET_POS);
        return blockEntity instanceof AENetworkedBlockEntity endpoint ? endpoint.getMainNode().getNode() : null;
    }

    public BlockPos endpointTargetPosition() {
        return helper.absolutePos(ENDPOINT_TARGET_POS);
    }

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

    public MappedPatternProvider composition() {
        return composition;
    }

    @Override
    public MappedPatternProvider mappedPatternProvider() {
        return composition;
    }

    public NativeProviderLane lane(int index) {
        return composition.lanes().get(index);
    }

    public void installPatterns(int count) {
        var inputs = List.of(Items.COBBLESTONE, Items.DIRT, Items.SAND);
        var outputs = List.of(Items.DIAMOND, Items.GOLD_INGOT, Items.IRON_INGOT);
        for (int slot = 0; slot < count; slot++) {
            var pattern = PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(AEItemKey.of(inputs.get(slot)), 1)),
                    List.of(new GenericStack(AEItemKey.of(outputs.get(slot)), 1)));
            composition.patternInventory().setItemDirect(slot, pattern);
        }
        composition.refreshPatterns();
    }

    public boolean push(int laneIndex, int patternIndex) {
        var pattern = lane(laneIndex).getAvailablePatterns().get(patternIndex);
        var input = pattern.getInputs()[0].getPossibleInputs()[0];
        var counter = new KeyCounter();
        counter.add(input.what(), input.amount());
        return lane(laneIndex).pushPattern(pattern, new KeyCounter[] { counter });
    }

    public void lockUntilResult(int laneIndex) {
        lane(laneIndex).getConfigManager().putSetting(Settings.LOCK_CRAFTING_MODE,
                LockCraftingMode.LOCK_UNTIL_RESULT);
    }

    public int targetItemCount() {
        var target = helper.getLevel().getBlockEntity(helper.absolutePos(TARGET_POS));
        if (!(target instanceof ChestBlockEntity chest)) {
            return 0;
        }
        var total = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            total += chest.getItem(slot).getCount();
        }
        return total;
    }

    public long endpointTargetItemCount() {
        var targetNode = endpointTargetNode();
        return targetNode == null || targetNode.getGrid() == null ? 0
                : targetNode.getGrid().getStorageService().getInventory().getAvailableStacks()
                        .get(AEItemKey.of(Items.COBBLESTONE));
    }

    public BlockEntity hostBlockEntity() {
        return helper.getBlockEntity(HOST_POS);
    }

    @Override
    public BlockEntity getBlockEntity() {
        return hostBlockEntity();
    }

    @Override
    public EnumSet<Direction> getTargets() {
        return EnumSet.of(Direction.EAST);
    }

    @Override
    public void saveChanges() {
        ownerSaveCalls++;
        getBlockEntity().setChanged();
    }

    public int ownerSaveCalls() {
        return ownerSaveCalls;
    }

    public int laneSaveCalls() {
        return hosts.stream().mapToInt(NativeProviderLaneHost::saveCalls).sum();
    }

    public IManagedGridNode managedNode() {
        return node;
    }

    GameTestHelper helper() {
        return helper;
    }

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
    }

}
