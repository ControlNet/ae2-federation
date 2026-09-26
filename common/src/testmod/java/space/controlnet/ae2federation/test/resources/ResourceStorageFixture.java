package space.controlnet.ae2federation.test.resources;

import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;

public final class ResourceStorageFixture {
    private static final BlockPos ITEM_POS = new BlockPos(1, 1, 4);
    private static final BlockPos FLUID_POS = new BlockPos(4, 1, 4);

    private final GameTestHelper helper;

    public ResourceStorageFixture(GameTestHelper helper) {
        this.helper = helper;
        placeChest(ITEM_POS, AEItems.ITEM_CELL_1K.stack());
        placeChest(FLUID_POS, AEItems.FLUID_CELL_1K.stack());
    }

    public boolean ready() {
        return ready(ITEM_POS) && ready(FLUID_POS);
    }

    public MEStorage itemStorage() {
        return nativeStorage(ITEM_POS);
    }

    public MEStorage fluidStorage() {
        return nativeStorage(FLUID_POS);
    }

    private void placeChest(BlockPos position, ItemStack cell) {
        helper.setBlock(position.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(position, AEBlocks.ME_CHEST.block());
        helper.assertTrue(StorageCells.getCellInventory(cell, null) != null, "Native storage cell must resolve");
        helper.<MEChestBlockEntity>getBlockEntity(position).setCell(cell);
    }

    private boolean ready(BlockPos position) {
        var node = chest(position).getMainNode().getNode();
        return node != null && node.hasGridBooted() && node.isActive();
    }

    private MEStorage nativeStorage(BlockPos position) {
        var node = chest(position).getMainNode().getNode();
        helper.assertTrue(node != null, "Native storage node must exist");
        var provider = node.getService(IStorageProvider.class);
        helper.assertTrue(provider != null, "Native storage provider must exist");
        var storage = new MEStorage[1];
        provider.mountInventories((mounted, priority) -> {
            if (storage[0] == null) {
                storage[0] = mounted;
            }
        });
        helper.assertTrue(storage[0] != null, "Native storage provider must expose a delegate");
        return storage[0];
    }

    private MEChestBlockEntity chest(BlockPos position) {
        return helper.getBlockEntity(position);
    }
}
