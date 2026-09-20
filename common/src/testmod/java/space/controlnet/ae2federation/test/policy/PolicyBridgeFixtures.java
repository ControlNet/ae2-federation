package space.controlnet.ae2federation.test.policy;

import appeng.api.networking.IGrid;
import appeng.api.util.AEColor;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import space.controlnet.ae2federation.bridge.MultipartBridgePart;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.test.bridge.BridgeFixtures;
import space.controlnet.ae2federation.test.storage.InvalidSecondCallbackProvider;

public final class PolicyBridgeFixtures implements AutoCloseable {
    private final GameTestHelper helper;
    private final BridgeFixtures bridges;
    private final BlockPos firstPosition;
    private final BlockPos secondPosition;
    private final boolean withProviderFluidChest;
    private MultipartBridgePart first;
    private MultipartBridgePart second;
    private final InvalidSecondCallbackProvider callbackProbe = new InvalidSecondCallbackProvider();

    public PolicyBridgeFixtures(GameTestHelper helper, BlockPos firstPosition) {
        this(helper, firstPosition, false);
    }

    public PolicyBridgeFixtures(GameTestHelper helper, BlockPos firstPosition, boolean withProviderFluidChest) {
        this.helper = helper;
        this.firstPosition = firstPosition;
        this.withProviderFluidChest = withProviderFluidChest;
        secondPosition = firstPosition.east();
        bridges = new BridgeFixtures(helper);
        bridges.nativePorts().placeCable(firstPosition, AEColor.RED);
        bridges.nativePorts().placeCable(secondPosition, AEColor.RED);
        bridges.nativePorts().placeChest(firstPosition.south());
        bridges.nativePorts().placeCable(firstPosition.north(), AEColor.BLUE);
        bridges.nativePorts().placeCable(secondPosition.north(), AEColor.BLUE);
        bridges.nativePorts().placeChest(firstPosition.north(2));
        if (withProviderFluidChest) {
            bridges.nativePorts().placeChest(secondPosition.north(2));
        }
    }

    public boolean networksSettled() {
        return FabricRegistryAccess.confirmedNetworkId(mainGrid()).isPresent()
                && FabricRegistryAccess.confirmedNetworkId(outerGrid()).isPresent();
    }

    public void installStorageCells() {
        helper.setBlock(firstPosition.south().below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(firstPosition.north(2).below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.<MEChestBlockEntity>getBlockEntity(firstPosition.south()).setCell(AEItems.ITEM_CELL_1K.stack());
        helper.<MEChestBlockEntity>getBlockEntity(firstPosition.north(2)).setCell(AEItems.ITEM_CELL_1K.stack());
        if (withProviderFluidChest) {
            helper.setBlock(secondPosition.north(2).below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
            providerFluidChest().setCell(AEItems.FLUID_CELL_1K.stack());
        } else {
            bridges.nativePorts().createStorageProvider(Direction.SOUTH, secondPosition.north(2), callbackProbe);
        }
    }

    public MultipartBridgePart placeFirstBridge() {
        first = bridges.placeBridge(firstPosition, Direction.NORTH);
        return first;
    }

    public MultipartBridgePart placeSecondBridge() {
        second = bridges.placeBridge(secondPosition, Direction.NORTH);
        return second;
    }

    public boolean firstBridgeReady() {
        return bridgeReady(first);
    }

    public void refreshFirstBridge() {
        first.onNeighborChanged(helper.getLevel(), helper.absolutePos(firstPosition),
                helper.absolutePos(firstPosition.north()));
    }

    public boolean secondBridgeReady() {
        return bridgeReady(second);
    }

    public void refreshSecondBridge() {
        second.onNeighborChanged(helper.getLevel(), helper.absolutePos(secondPosition),
                helper.absolutePos(secondPosition.north()));
    }

    public void removeFirstBridge() {
        helper.assertTrue(first.getHost().removePart(first), "Original Bridge part must be removed from its AE2 host");
    }

    public void removeSecondBridge() {
        helper.assertTrue(second.getHost().removePart(second), "Redundant Bridge part must be removed from its AE2 host");
    }

    public MEChestBlockEntity providerChest() {
        return helper.getBlockEntity(firstPosition.north(2));
    }

    public MEChestBlockEntity providerFluidChest() {
        if (!withProviderFluidChest) {
            throw new IllegalStateException("Provider fluid chest is not installed");
        }
        return helper.getBlockEntity(secondPosition.north(2));
    }

    public MEChestBlockEntity consumerChest() {
        return helper.getBlockEntity(firstPosition.south());
    }

    public void replaceCallbackProbeWithSecondChest() {
        helper.setBlock(secondPosition.north(2), AEBlocks.ME_CHEST.block());
        secondProviderChest().setCell(AEItems.ITEM_CELL_1K.stack());
    }

    public MEChestBlockEntity secondProviderChest() {
        return helper.getBlockEntity(secondPosition.north(2));
    }

    public InvalidSecondCallbackProvider callbackProbe() {
        return callbackProbe;
    }

    public void removeProviderChest() {
        helper.setBlock(firstPosition.north(2), Blocks.AIR);
    }

    public IGrid mainGrid() {
        return bridges.nativePorts().exposedNode(firstPosition, Direction.UP).getGrid();
    }

    public IGrid outerGrid() {
        return bridges.nativePorts().exposedNode(firstPosition.north(), Direction.UP).getGrid();
    }

    public NetworkId mainNetwork() {
        return FabricRegistryAccess.confirmedNetworkId(mainGrid()).orElseThrow();
    }

    public NetworkId outerNetwork() {
        return FabricRegistryAccess.confirmedNetworkId(outerGrid()).orElseThrow();
    }

    public int firstBridgeIdentity() {
        return System.identityHashCode(first);
    }

    public int secondBridgeIdentity() {
        return System.identityHashCode(second);
    }

    private boolean bridgeReady(MultipartBridgePart bridge) {
        if (bridge == null || bridge.membershipCandidate().isEmpty()) {
            return false;
        }
        var mainFabrics = FabricRegistryAccess.get(helper.getLevel()).fabricsFor(mainNetwork());
        var outerFabrics = FabricRegistryAccess.get(helper.getLevel()).fabricsFor(outerNetwork());
        return mainFabrics.stream().anyMatch(outerFabrics::contains);
    }

    @Override
    public void close() {
        bridges.close();
    }
}
