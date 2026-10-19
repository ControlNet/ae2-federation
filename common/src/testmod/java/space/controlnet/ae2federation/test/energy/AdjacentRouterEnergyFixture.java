package space.controlnet.ae2federation.test.energy;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import appeng.core.definitions.AEBlocks;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.port.RouterPortBinding;
import space.controlnet.ae2federation.energy.EnergySharingService;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.test.router.RouterFixtures;

/**
 * Two native Grids, each with an empty AE2 energy cell, on the far faces of two Routers placed face to face with no
 * Federation Cable between them. The consumer Grid is west of the west Router, the provider Grid east of the east one.
 */
public final class AdjacentRouterEnergyFixture implements AutoCloseable {
    private static final BlockPos CONSUMER_ROUTER = new BlockPos(6, 5, 6);
    private static final BlockPos PROVIDER_ROUTER = CONSUMER_ROUTER.east();

    private final GameTestHelper helper;
    private final RouterFixtures routers;
    private boolean routersPlaced;

    public AdjacentRouterEnergyFixture(GameTestHelper helper) {
        this.helper = helper;
        routers = new RouterFixtures(helper);
        routers.placeNativeDevice(CONSUMER_ROUTER, Direction.WEST);
        routers.placeNativeDevice(PROVIDER_ROUTER, Direction.EAST);
        helper.setBlock(CONSUMER_ROUTER.west().below(), AEBlocks.ENERGY_CELL.block());
        helper.setBlock(PROVIDER_ROUTER.east().below(), AEBlocks.ENERGY_CELL.block());
    }

    /** Places both Routers once the native identities settle, then waits until they link and share a domain. */
    public boolean ready() {
        if (grids().stream().anyMatch(grid -> FederationDomainRegistryAccess.confirmedNetworkId(grid).isEmpty())) {
            return false;
        }
        if (!routersPlaced) {
            routers.placeSwitch(CONSUMER_ROUTER);
            routers.placeSwitch(PROVIDER_ROUTER);
            routersPlaced = true;
            return false;
        }
        if (!(routers.router(CONSUMER_ROUTER).binding(Direction.WEST) instanceof RouterPortBinding.Native)
                || !(routers.router(PROVIDER_ROUTER).binding(Direction.EAST) instanceof RouterPortBinding.Native)
                || !routersLinked()) {
            return false;
        }
        var registry = FederationDomainRegistryAccess.get(helper.getLevel());
        var consumerDomains = registry.federationdomainsFor(network(consumerGrid()));
        if (consumerDomains.isEmpty() || !consumerDomains.equals(registry.federationdomainsFor(network(providerGrid())))) {
            return false;
        }
        EnergySharingService.get(helper.getLevel()).observeFederationDomainMembers(grids());
        return true;
    }

    /** Both touching faces resolve as a Federation link, with no native edge between them. */
    public boolean routersLinked() {
        var consumer = routers.router(CONSUMER_ROUTER);
        var provider = routers.router(PROVIDER_ROUTER);
        return consumer.binding(Direction.EAST) instanceof RouterPortBinding.Federation
                && provider.binding(Direction.WEST) instanceof RouterPortBinding.Federation
                && consumer.boundaryNode(Direction.EAST).getConnections().isEmpty()
                && provider.boundaryNode(Direction.WEST).getConnections().isEmpty();
    }

    public IGrid consumerGrid() {
        return routers.nativeDeviceNode(CONSUMER_ROUTER, Direction.WEST).getGrid();
    }

    public IGrid providerGrid() {
        return routers.nativeDeviceNode(PROVIDER_ROUTER, Direction.EAST).getGrid();
    }

    /** The ME power rule in which the consumer Grid draws on the provider Grid. */
    public PolicyKey key() {
        return new PolicyKey(network(consumerGrid()), network(providerGrid()), PolicyCapability.ME_POWER);
    }

    public void enable() {
        var policies = PolicyService.get(helper.getLevel());
        var result = policies.edit(new PolicyEdit(key(), policies.revision(key()),
                PolicyRule.enabled(Set.of(PolicyOperation.SUPPLY))));
        helper.assertTrue(result instanceof PolicyMutationResult.Accepted, "The ME power rule must be accepted");
    }

    public boolean shares() {
        return EnergySharingService.shares(helper.getLevel(), key());
    }

    public void chargeProvider(double amount) {
        var cell = providerCell();
        var overflow = cell.injectAEPower(amount - cell.getAECurrentPower(), Actionable.MODULATE);
        helper.assertValueEqual(overflow, 0.0, "The provider cell must accept its charge");
    }

    public double extractFromConsumer(double amount) {
        return consumerGrid().getEnergyService().extractAEPower(amount, Actionable.MODULATE, PowerMultiplier.ONE);
    }

    public double providerStored() {
        return providerCell().getAECurrentPower();
    }

    /** Removes the provider's Router, which leaves the two Grids with no shared domain. */
    public void removeProviderRouter() {
        helper.setBlock(PROVIDER_ROUTER, Blocks.AIR);
    }

    private List<IGrid> grids() {
        return List.of(consumerGrid(), providerGrid());
    }

    private EnergyCellBlockEntity providerCell() {
        return helper.getBlockEntity(PROVIDER_ROUTER.east().below());
    }

    private static space.controlnet.ae2federation.identity.NetworkId network(IGrid grid) {
        return FederationDomainRegistryAccess.confirmedNetworkId(grid).orElseThrow();
    }

    @Override
    public void close() {
        routers.close();
    }
}
