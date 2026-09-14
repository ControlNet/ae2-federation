package space.controlnet.ae2federation.test.processing;

import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEBlocks;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.me.service.CraftingService;
import appeng.api.crafting.PatternDetailsHelper;
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
import space.controlnet.ae2federation.ae2.processing.NativeProviderLane;
import space.controlnet.ae2federation.ae2.processing.NativeProviderLaneComposition;

public final class NativeProviderLaneFixtures implements AutoCloseable {
    private static final IGridNodeListener<NativeProviderLaneFixtures> LISTENER = (owner, node) -> {
    };
    private static final BlockPos HOST_POS = new BlockPos(3, 2, 3);
    private static final BlockPos ENERGY_POS = HOST_POS.west();
    private static final BlockPos TARGET_POS = HOST_POS.east();
    private static final BlockPos BYPASS_POS = HOST_POS.north();
    private final GameTestHelper helper;
    private final IManagedGridNode node;
    private final NativeProviderLaneComposition composition;
    private final List<LaneHost> hosts;

    public NativeProviderLaneFixtures(GameTestHelper helper, List<IntPredicate> assignments) {
        this.helper = helper;
        helper.setBlock(HOST_POS, Blocks.CHEST);
        helper.setBlock(TARGET_POS, Blocks.CHEST);
        helper.setBlock(ENERGY_POS, appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
        node = GridHelper.createManagedNode(this, LISTENER)
                .setInWorldNode(true)
                .setIdlePowerUsage(0)
                .setExposedOnSides(EnumSet.of(Direction.WEST));
        hosts = List.of(new LaneHost(helper), new LaneHost(helper), new LaneHost(helper));
        composition = new NativeProviderLaneComposition(node, hosts.getFirst(), hosts, 3, assignments);
        for (int index = 0; index < hosts.size(); index++) {
            hosts.get(index).logic = composition.lanes().get(index);
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
        var service = (CraftingService) node.getGrid().getCraftingService();
        var providers = new ArrayList<ICraftingProvider>();
        service.getProviders(lane(laneIndex).getAvailablePatterns().get(patternIndex)).forEach(providers::add);
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

    public boolean connectEnergy() {
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

    public NativeProviderLaneComposition composition() {
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

    public BlockEntity hostBlockEntity() {
        return helper.getBlockEntity(HOST_POS);
    }

    @Override
    public void close() {
        composition.close();
        node.destroy();
    }

    private static final class LaneHost implements PatternProviderLogicHost {
        private final GameTestHelper helper;
        private PatternProviderLogic logic;

        private LaneHost(GameTestHelper helper) {
            this.helper = helper;
        }

        @Override
        public PatternProviderLogic getLogic() {
            return logic;
        }

        @Override
        public BlockEntity getBlockEntity() {
            return helper.getBlockEntity(HOST_POS);
        }

        @Override
        public EnumSet<Direction> getTargets() {
            return EnumSet.of(Direction.EAST);
        }

        @Override
        public void saveChanges() {
            getBlockEntity().setChanged();
        }

        @Override
        public AEItemKey getTerminalIcon() {
            return AEItemKey.of(AEItems.PROCESSING_PATTERN.asItem());
        }

        @Override
        public ItemStack getMainMenuIcon() {
            return AEItems.PROCESSING_PATTERN.stack();
        }
    }
}
