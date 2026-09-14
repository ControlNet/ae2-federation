package space.controlnet.ae2federation.test.storage;

import appeng.api.storage.MEStorage;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.storage.NetworkStorage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

public final class StorageProofFixtures {
    private static final BlockPos STORAGE_POS = new BlockPos(1, 1, 1);

    private final GameTestHelper helper;

    public StorageProofFixtures(GameTestHelper helper) {
        this.helper = helper;
        helper.setBlock(STORAGE_POS.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(STORAGE_POS, AEBlocks.ME_CHEST.block());
        var chest = helper.<MEChestBlockEntity>getBlockEntity(STORAGE_POS);
        var cell = AEItems.ITEM_CELL_1K.stack();
        helper.assertTrue(StorageCells.getCellInventory(cell, null) != null, "Native item cell must resolve");
        chest.setCell(cell);
    }

    public boolean ready() {
        var node = chest().getMainNode().getNode();
        return node != null && node.hasGridBooted() && node.isActive();
    }

    public MEStorage aggregate() {
        return chest().getMainNode().getGrid().getStorageService().getInventory();
    }

    public appeng.api.networking.IGrid grid() {
        return chest().getMainNode().getGrid();
    }

    public IStorageProvider nativeProvider() {
        var provider = nativeNode().getService(IStorageProvider.class);
        helper.assertTrue(provider != null, "Native chest storage provider must be registered");
        return provider;
    }

    public appeng.api.networking.IGridNode nativeNode() {
        var node = chest().getMainNode().getNode();
        helper.assertTrue(node != null, "Native chest Grid node must be registered");
        return node;
    }

    public MEStorage nativeSource() {
        var source = new MEStorage[1];
        nativeProvider().mountInventories(new appeng.api.storage.IStorageMounts() {
            @Override
            public void mount(MEStorage storage, int priority) {
                if (source[0] == null && !(storage instanceof NetworkStorage)) {
                    source[0] = storage;
                }
            }
        });
        helper.assertTrue(source[0] != null, "Native storage provider must expose a native delegate");
        return source[0];
    }

    public NetworkStorage newAggregate() {
        return new NetworkStorage();
    }

    public IStorageProvider unclassifiedProvider(MEStorage storage, int priority) {
        return mounts -> mounts.mount(storage, priority);
    }

    private MEChestBlockEntity chest() {
        return helper.getBlockEntity(STORAGE_POS);
    }

    public static List<String> diamondRoutes() {
        return new ArrayList<>(List.of("fabric-1:A-B", "fabric-2:A-C", "fabric-3:B-D", "fabric-4:C-D"));
    }
}
