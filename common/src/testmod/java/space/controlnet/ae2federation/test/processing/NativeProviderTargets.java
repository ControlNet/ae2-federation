package space.controlnet.ae2federation.test.processing;

import appeng.api.networking.IGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;

final class NativeProviderTargets {
    private static final BlockPos ENDPOINT_TARGET_POS = NativeProviderLaneFixtures.HOST_POS.east(2);
    private final GameTestHelper helper;

    NativeProviderTargets(GameTestHelper helper) {
        this.helper = helper;
    }

    void removeConfiguredTarget() {
        helper.setBlock(NativeProviderLaneFixtures.TARGET_POS, Blocks.AIR);
    }

    void restoreConfiguredTarget() {
        helper.setBlock(NativeProviderLaneFixtures.TARGET_POS, Blocks.CHEST);
    }

    void installEndpointTarget() {
        installEndpointTarget(ENDPOINT_TARGET_POS, AEBlocks.INTERFACE.block(), Direction.EAST);
    }

    void installFederationEndpointTarget() {
        installEndpointTarget(ENDPOINT_TARGET_POS, ProcessingRegistration.ENDPOINT.get(), Direction.NORTH);
    }

    void seedFederationEndpointTarget(NetworkId networkId) {
        var endpoint = (AENetworkedBlockEntity) helper.getBlockEntity(ENDPOINT_TARGET_POS);
        endpoint.getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", networkId));
        var backend = helper.<MEChestBlockEntity>getBlockEntity(ENDPOINT_TARGET_POS.north());
        backend.getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", networkId));
    }

    void installFederationEndpointTarget(BlockPos endpointPosition) {
        installEndpointTarget(endpointPosition, ProcessingRegistration.ENDPOINT.get(), Direction.NORTH);
    }

    IGridNode endpointTargetNode() {
        return endpointTargetNode(ENDPOINT_TARGET_POS);
    }

    IGridNode endpointTargetNode(BlockPos endpointPosition) {
        var blockEntity = helper.getBlockEntity(endpointPosition);
        return blockEntity instanceof AENetworkedBlockEntity endpoint ? endpoint.getMainNode().getNode() : null;
    }

    BlockPos endpointTargetPosition() {
        return helper.absolutePos(ENDPOINT_TARGET_POS);
    }

    int targetItemCount() {
        var chest = targetChest();
        if (chest == null) {
            return 0;
        }
        var total = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            total += chest.getItem(slot).getCount();
        }
        return total;
    }

    int targetItemCount(Item item) {
        var chest = targetChest();
        if (chest == null) {
            return 0;
        }
        var total = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (chest.getItem(slot).is(item)) {
                total += chest.getItem(slot).getCount();
            }
        }
        return total;
    }

    long extractTargetItem(Item item, long amount) {
        var chest = targetChest();
        long extracted = 0;
        for (int slot = 0; slot < chest.getContainerSize() && extracted < amount; slot++) {
            var stack = chest.getItem(slot);
            if (stack.is(item)) {
                var removed = Math.min(stack.getCount(), (int) (amount - extracted));
                stack.shrink(removed);
                extracted += removed;
            }
        }
        return extracted;
    }

    String targetSnapshot() {
        var chest = targetChest();
        var snapshot = new StringBuilder();
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (slot > 0) {
                snapshot.append(',');
            }
            var stack = chest.getItem(slot);
            snapshot.append(stack.isEmpty() ? "empty" : net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(stack.getItem()) + ":" + stack.getCount());
        }
        return snapshot.toString();
    }

    Object targetOwner() {
        return targetChest();
    }

    void leaveOneSharedTargetSlot() {
        var chest = targetChest();
        for (int slot = 0; slot < chest.getContainerSize() - 1; slot++) {
            chest.setItem(slot, new ItemStack(Items.SAND, 64));
        }
        chest.setItem(chest.getContainerSize() - 1, ItemStack.EMPTY);
    }

    long endpointTargetItemCount() {
        var targetNode = endpointTargetNode();
        return targetNode == null || targetNode.getGrid() == null ? 0
                : targetNode.getGrid().getStorageService().getInventory().getAvailableStacks()
                        .get(AEItemKey.of(Items.COBBLESTONE));
    }

    private void installEndpointTarget(BlockPos endpointPosition, net.minecraft.world.level.block.Block endpointBlock,
            Direction backendSide) {
        helper.setBlock(endpointPosition, endpointBlock);
        var backendPosition = endpointPosition.relative(backendSide);
        helper.setBlock(backendPosition, AEBlocks.ME_CHEST.block());
        helper.setBlock(backendPosition.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        var chest = helper.<MEChestBlockEntity>getBlockEntity(backendPosition);
        var cell = AEItems.ITEM_CELL_1K.stack();
        helper.assertTrue(StorageCells.getCellInventory(cell, null) != null, "Native Endpoint item cell must exist");
        chest.setCell(cell);
    }

    private ChestBlockEntity targetChest() {
        return helper.getLevel().getBlockEntity(helper.absolutePos(NativeProviderLaneFixtures.TARGET_POS))
                instanceof ChestBlockEntity chest ? chest : null;
    }
}
