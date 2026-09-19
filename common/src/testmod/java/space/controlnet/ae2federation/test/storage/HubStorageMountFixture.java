package space.controlnet.ae2federation.test.storage;

import appeng.api.networking.IGrid;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.fabric.port.HubPortBinding;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.test.hub.HubFixtures;

public final class HubStorageMountFixture implements AutoCloseable {
    private static final BlockPos LEFT = new BlockPos(3, 4, 6);
    private static final BlockPos RIGHT = new BlockPos(9, 4, 6);

    private final GameTestHelper helper;
    private final HubFixtures hubs;

    public HubStorageMountFixture(GameTestHelper helper) {
        this.helper = helper;
        hubs = new HubFixtures(helper);
        hubs.placeNativeDevice(LEFT, Direction.NORTH);
        hubs.placeNativeDevice(RIGHT, Direction.NORTH);
        helper.setBlock(LEFT.north().below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(RIGHT.north().below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        consumerChest().setCell(AEItems.ITEM_CELL_1K.stack());
        providerChest().setCell(AEItems.ITEM_CELL_1K.stack());
    }

    public boolean networksSettled() {
        return FabricRegistryAccess.confirmedNetworkId(consumerGrid()).isPresent()
                && FabricRegistryAccess.confirmedNetworkId(providerGrid()).isPresent()
                && hubs.nativeDeviceNode(LEFT, Direction.NORTH).isActive()
                && hubs.nativeDeviceNode(RIGHT, Direction.NORTH).isActive()
                && hubs.nativeDeviceNode(LEFT, Direction.NORTH).hasGridBooted()
                && hubs.nativeDeviceNode(RIGHT, Direction.NORTH).hasGridBooted();
    }

    public void connectHubs() {
        hubs.placeHub(LEFT);
        hubs.placeHub(RIGHT);
        for (var x = LEFT.getX() + 1; x < RIGHT.getX(); x++) {
            hubs.placeFederationCable(new BlockPos(x, LEFT.getY(), LEFT.getZ()));
        }
    }

    public boolean connected() {
        if (!(hubs.hub(LEFT).binding(Direction.NORTH) instanceof HubPortBinding.Native)
                || !(hubs.hub(RIGHT).binding(Direction.NORTH) instanceof HubPortBinding.Native)) {
            return false;
        }
        var consumerFabrics = FabricRegistryAccess.get(helper.getLevel()).fabricsFor(key().consumerNetworkId());
        var providerFabrics = FabricRegistryAccess.get(helper.getLevel()).fabricsFor(key().providerNetworkId());
        return consumerFabrics.stream().anyMatch(providerFabrics::contains);
    }

    public IGrid consumerGrid() {
        return hubs.nativeDeviceNode(LEFT, Direction.NORTH).getGrid();
    }

    public IGrid providerGrid() {
        return hubs.nativeDeviceNode(RIGHT, Direction.NORTH).getGrid();
    }

    public PolicyKey key() {
        return new PolicyKey(FabricRegistryAccess.confirmedNetworkId(consumerGrid()).orElseThrow(),
                FabricRegistryAccess.confirmedNetworkId(providerGrid()).orElseThrow(), PolicyCapability.STORAGE);
    }

    public MEChestBlockEntity consumerChest() {
        return helper.getBlockEntity(LEFT.north());
    }

    public MEChestBlockEntity providerChest() {
        return helper.getBlockEntity(RIGHT.north());
    }

    @Override
    public void close() {
        hubs.close();
    }
}
