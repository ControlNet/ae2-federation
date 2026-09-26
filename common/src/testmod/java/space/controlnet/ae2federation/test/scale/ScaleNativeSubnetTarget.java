package space.controlnet.ae2federation.test.scale;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.StorageCells;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;
import space.controlnet.ae2federation.test.mixed.MixedMachineBlockEntity;
import space.controlnet.ae2federation.test.mixed.MixedMachineRegistration;
import space.controlnet.ae2federation.test.processing.NativeProviderLaneFixtures;

public final class ScaleNativeSubnetTarget implements AutoCloseable {
    private final GameTestHelper helper;
    private final BlockPos interfacePosition;
    private final BlockPos chestPosition;
    private final BlockPos exportPosition;
    private final BlockPos machinePosition;
    private final appeng.parts.automation.ExportBusPart exportBus;
    private final boolean batch16;

    public ScaleNativeSubnetTarget(GameTestHelper helper) {
        this(helper, false);
    }

    public ScaleNativeSubnetTarget(GameTestHelper helper, boolean batch16) {
        this(helper, batch16, false);
    }

    public ScaleNativeSubnetTarget(GameTestHelper helper, boolean batch16, boolean settledReplay) {
        this(helper, batch16, settledReplay, Direction.EAST);
    }

    public ScaleNativeSubnetTarget(GameTestHelper helper, boolean batch16, boolean settledReplay, Direction face) {
        this(helper, batch16, settledReplay, NativeProviderLaneFixtures.HOST_POS.relative(face), face);
    }

    public ScaleNativeSubnetTarget(GameTestHelper helper, boolean batch16, boolean settledReplay,
            BlockPos interfacePosition, Direction face) {
        this.helper = helper;
        this.batch16 = batch16;
        this.interfacePosition = interfacePosition;
        chestPosition = interfacePosition.relative(face);
        exportPosition = face == Direction.EAST ? chestPosition.north() : chestPosition.west();
        machinePosition = exportPosition.relative(face);
        helper.setBlock(interfacePosition, AEBlocks.INTERFACE.block());
        helper.setBlock(chestPosition, AEBlocks.ME_CHEST.block());
        helper.setBlock(chestPosition.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        NetworkId targetId = settledReplay ? NetworkId.create() : null;
        if (targetId != null) {
            helper.<InterfaceBlockEntity>getBlockEntity(interfacePosition).getMainNode()
                    .loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", targetId));
            chest().getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", targetId));
        }
        var cell = AEItems.ITEM_CELL_1K.stack();
        helper.assertTrue(StorageCells.getCellInventory(cell, null) != null,
                "Native subnet must own a physical item cell");
        chest().setCell(cell);
        helper.setBlock(machinePosition, MixedMachineRegistration.BLOCK.get());
        exportBus = PartHelper.setPart(helper.getLevel(), helper.absolutePos(exportPosition), face, null,
                AEParts.EXPORT_BUS.get());
        helper.assertTrue(exportBus != null, "Native subnet must have an AE2 Export Bus facing the machine");
        if (targetId != null) {
            exportBus.getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("gn", targetId));
        }
        if (!batch16) exportBus.getConfig().insert(0, AEItemKey.of(Items.COBBLESTONE), 1, Actionable.MODULATE);
    }

    public boolean ready(IGridNode source) {
        var subnet = chest().getMainNode().getNode();
        var interfaceNode = helper.<InterfaceBlockEntity>getBlockEntity(interfacePosition).getMainNode().getNode();
        if (subnet == null || interfaceNode == null || !subnet.hasGridBooted() || !subnet.isActive()
                || !interfaceNode.hasGridBooted() || !interfaceNode.isActive()) return false;
        var busNode = exportBus.getGridNode();
        if (busNode == null || !busNode.hasGridBooted()) return false;
        if (busNode.getGrid() != subnet.getGrid()) {
            GridHelper.createConnection(busNode, subnet);
            return false;
        }
        return interfaceNode.getGrid() == subnet.getGrid() && source.getGrid() != subnet.getGrid()
                && exportBus.isActive();
    }

    public MixedMachineBlockEntity machine() {
        return helper.getBlockEntity(machinePosition);
    }

    public long inputAmount(AEItemKey key) {
        return chest().getOriginalCellInventory(0).extract(key, Long.MAX_VALUE, Actionable.SIMULATE,
                appeng.api.networking.security.IActionSource.empty());
    }

    public void export(AEItemKey key) {
        if (!batch16 || inputAmount(key) != 16) {
            throw new IllegalStateException("Native subnet must receive 16 inputs before Export Bus activation");
        }
        exportBus.getConfig().setStack(0, new GenericStack(key, 1));
    }

    public appeng.api.networking.IGrid grid() {
        return chest().getMainNode().getGrid();
    }

    private MEChestBlockEntity chest() {
        return helper.getBlockEntity(chestPosition);
    }

    @Override
    public void close() {
        helper.setBlock(exportPosition, Blocks.AIR);
        helper.setBlock(machinePosition, Blocks.AIR);
        helper.setBlock(chestPosition, Blocks.AIR);
        helper.setBlock(chestPosition.below(), Blocks.AIR);
        helper.setBlock(interfacePosition, Blocks.AIR);
    }
}
