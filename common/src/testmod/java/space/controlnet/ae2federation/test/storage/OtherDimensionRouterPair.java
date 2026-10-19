package space.controlnet.ae2federation.test.storage;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import space.controlnet.ae2federation.domain.FederationDomainNodeId;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.port.RouterPortBinding;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.router.RouterBlockEntity;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.test.world.OtherDimensionSite;

/**
 * TEST-ONLY: {@link RouterStorageMountFixture}'s layout in another dimension's site. Two ME networks, each an ME chest
 * with a 1k cell and a creative energy cell; once both are settled, a Router north of each chest and Federation cable
 * between the Routers join them into one Federation Domain.
 */
public final class OtherDimensionRouterPair {
    /** The site size this layout needs. */
    public static final BlockPos SIZE = new BlockPos(9, 2, 4);
    private static final BlockPos LEFT = new BlockPos(1, 0, 2);
    private static final BlockPos RIGHT = new BlockPos(7, 0, 2);

    private final OtherDimensionSite site;

    public OtherDimensionRouterPair(OtherDimensionSite site) {
        this.site = site;
    }

    /** Places both networks; call once the site is ready. */
    public void place() {
        for (var router : new BlockPos[] { LEFT, RIGHT }) {
            site.setBlock(router.north(), AEBlocks.ME_CHEST.block().defaultBlockState());
            site.setBlock(router.north(2), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
            site.<MEChestBlockEntity>getBlockEntity(router.north()).setCell(AEItems.ITEM_CELL_1K.stack());
        }
    }

    public boolean networksSettled() {
        return ready(chestNode(LEFT)) && ready(chestNode(RIGHT))
                && FederationDomainRegistryAccess.confirmedNetworkId(consumerGrid()).isPresent()
                && FederationDomainRegistryAccess.confirmedNetworkId(providerGrid()).isPresent();
    }

    public void connectRouters() {
        site.setBlock(LEFT, RouterRegistration.SWITCH.get().defaultBlockState());
        site.setBlock(RIGHT, RouterRegistration.SWITCH.get().defaultBlockState());
        for (var x = LEFT.getX() + 1; x < RIGHT.getX(); x++) {
            site.setBlock(new BlockPos(x, LEFT.getY(), LEFT.getZ()),
                    RouterRegistration.FEDERATION_CABLE.get().defaultBlockState());
        }
    }

    public boolean connected() {
        if (!(router(LEFT).binding(Direction.NORTH) instanceof RouterPortBinding.Native)
                || !(router(RIGHT).binding(Direction.NORTH) instanceof RouterPortBinding.Native)) {
            return false;
        }
        var registry = FederationDomainRegistryAccess.get(site.level());
        var consumer = registry.federationdomainsFor(key().consumerNetworkId());
        return registry.federationdomainsFor(key().providerNetworkId()).stream().anyMatch(consumer::contains);
    }

    /** The left Router's node in the server-wide registry, which names this site's dimension. */
    public FederationDomainNodeId routerNode() {
        return FederationDomainRegistryAccess.nodeId(site.level(), site.absolute(LEFT));
    }

    public IGrid consumerGrid() {
        return chestNode(LEFT).getGrid();
    }

    public IGrid providerGrid() {
        return chestNode(RIGHT).getGrid();
    }

    public PolicyKey key() {
        return new PolicyKey(FederationDomainRegistryAccess.confirmedNetworkId(consumerGrid()).orElseThrow(),
                FederationDomainRegistryAccess.confirmedNetworkId(providerGrid()).orElseThrow(), PolicyCapability.STORAGE);
    }

    private RouterBlockEntity router(BlockPos position) {
        return site.getBlockEntity(position);
    }

    private IGridNode chestNode(BlockPos router) {
        MEChestBlockEntity chest = site.getBlockEntity(router.north());
        return chest == null ? null : chest.getMainNode().getNode();
    }

    private static boolean ready(IGridNode node) {
        return node != null && node.isActive() && node.hasGridBooted();
    }
}
