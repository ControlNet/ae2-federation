package space.controlnet.ae2federation.test.storage;

import appeng.api.networking.IGrid;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.port.RouterPortBinding;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.test.router.RouterFixtures;

public final class RouterStorageMountFixture implements AutoCloseable {
    private static final BlockPos LEFT = new BlockPos(3, 4, 6);
    private static final BlockPos RIGHT = new BlockPos(9, 4, 6);

    private final GameTestHelper helper;
    private final RouterFixtures routers;

    public RouterStorageMountFixture(GameTestHelper helper) {
        this.helper = helper;
        routers = new RouterFixtures(helper);
        routers.placeNativeDevice(LEFT, Direction.NORTH);
        routers.placeNativeDevice(RIGHT, Direction.NORTH);
        helper.setBlock(LEFT.north().below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(RIGHT.north().below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        consumerChest().setCell(AEItems.ITEM_CELL_1K.stack());
        providerChest().setCell(AEItems.ITEM_CELL_1K.stack());
    }

    public boolean networksSettled() {
        return FederationDomainRegistryAccess.confirmedNetworkId(consumerGrid()).isPresent()
                && FederationDomainRegistryAccess.confirmedNetworkId(providerGrid()).isPresent()
                && routers.nativeDeviceNode(LEFT, Direction.NORTH).isActive()
                && routers.nativeDeviceNode(RIGHT, Direction.NORTH).isActive()
                && routers.nativeDeviceNode(LEFT, Direction.NORTH).hasGridBooted()
                && routers.nativeDeviceNode(RIGHT, Direction.NORTH).hasGridBooted();
    }

    public void connectRouters() {
        routers.placeRouter(LEFT);
        routers.placeRouter(RIGHT);
        for (var x = LEFT.getX() + 1; x < RIGHT.getX(); x++) {
            routers.placeFederationCable(new BlockPos(x, LEFT.getY(), LEFT.getZ()));
        }
    }

    public boolean connected() {
        if (!(routers.router(LEFT).binding(Direction.NORTH) instanceof RouterPortBinding.Native)
                || !(routers.router(RIGHT).binding(Direction.NORTH) instanceof RouterPortBinding.Native)) {
            return false;
        }
        var consumerFederationDomains = FederationDomainRegistryAccess.get(helper.getLevel()).federationdomainsFor(key().consumerNetworkId());
        var providerFederationDomains = FederationDomainRegistryAccess.get(helper.getLevel()).federationdomainsFor(key().providerNetworkId());
        return consumerFederationDomains.stream().anyMatch(providerFederationDomains::contains);
    }

    public IGrid consumerGrid() {
        return routers.nativeDeviceNode(LEFT, Direction.NORTH).getGrid();
    }

    public IGrid providerGrid() {
        return routers.nativeDeviceNode(RIGHT, Direction.NORTH).getGrid();
    }

    public PolicyKey key() {
        return new PolicyKey(FederationDomainRegistryAccess.confirmedNetworkId(consumerGrid()).orElseThrow(),
                FederationDomainRegistryAccess.confirmedNetworkId(providerGrid()).orElseThrow(), PolicyCapability.STORAGE);
    }

    public MEChestBlockEntity consumerChest() {
        return helper.getBlockEntity(LEFT.north());
    }

    public MEChestBlockEntity providerChest() {
        return helper.getBlockEntity(RIGHT.north());
    }

    @Override
    public void close() {
        routers.close();
    }
}
