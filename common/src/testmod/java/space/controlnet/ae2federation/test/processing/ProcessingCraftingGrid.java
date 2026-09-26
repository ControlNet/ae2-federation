package space.controlnet.ae2federation.test.processing;

import appeng.api.networking.IGridNode;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;

public final class ProcessingCraftingGrid implements AutoCloseable {
    private static final BlockPos STORAGE_POS = NativeProviderLaneFixtures.HOST_POS.north();
    private static final BlockPos CPU_POS = STORAGE_POS.north();
    public static final BlockPos REQUESTER_POS = STORAGE_POS.above();
    private final GameTestHelper helper;

    public ProcessingCraftingGrid(GameTestHelper helper, NetworkId networkId) {
        this.helper = helper;
        helper.setBlock(STORAGE_POS, AEBlocks.ME_CHEST.block());
        helper.setBlock(CPU_POS, AEBlocks.CRAFTING_STORAGE_1K.block());
        chest().getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", networkId));
        ((AENetworkedBlockEntity) helper.getBlockEntity(CPU_POS)).getMainNode()
                .loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", networkId));
        var cell = AEItems.ITEM_CELL_16K.stack();
        helper.assertTrue(StorageCells.getCellInventory(cell, null) != null,
                "Processing benchmark source cell must resolve");
        chest().setCell(cell);
    }

    public IGridNode node() {
        return chest().getMainNode().getNode();
    }

    public IGridNode cpuNode() {
        return ((AENetworkedBlockEntity) helper.getBlockEntity(CPU_POS)).getMainNode().getNode();
    }

    public MEStorage storage() {
        return chest().getInventory();
    }

    public int cpuCount() {
        return node().getGrid().getCraftingService().getCpus().size();
    }

    private MEChestBlockEntity chest() {
        return helper.getBlockEntity(STORAGE_POS);
    }

    @Override
    public void close() {
        helper.setBlock(CPU_POS, Blocks.AIR);
        helper.setBlock(STORAGE_POS, Blocks.AIR);
    }
}
