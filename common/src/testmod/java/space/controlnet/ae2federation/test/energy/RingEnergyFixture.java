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
import space.controlnet.ae2federation.energy.EnergyBindingService;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.port.RouterPortBinding;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.test.router.RouterFixtures;

public final class RingEnergyFixture implements AutoCloseable {
    private static final BlockPos CENTER = new BlockPos(6, 5, 6);
    private static final List<Direction> FACES = List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST);

    private final GameTestHelper helper;
    private final RouterFixtures routers;
    private boolean routerPlaced;

    public RingEnergyFixture(GameTestHelper helper) {
        this.helper = helper;
        routers = new RouterFixtures(helper);
        for (var face : FACES) {
            routers.placeNativeDevice(CENTER, face);
            helper.setBlock(CENTER.relative(face).below(), AEBlocks.ENERGY_CELL.block());
        }
    }

    public boolean ready() {
        if (grids().stream().anyMatch(grid -> FederationDomainRegistryAccess.confirmedNetworkId(grid).isEmpty())) {
            return false;
        }
        if (!routerPlaced) {
            routers.placeRouter(CENTER);
            routerPlaced = true;
            return false;
        }
        var router = routers.router(CENTER);
        if (FACES.stream().anyMatch(face -> !(router.binding(face) instanceof RouterPortBinding.Native))) {
            return false;
        }
        var grids = grids();
        if (Set.copyOf(grids).size() != 3) {
            return false;
        }
        var registry = FederationDomainRegistryAccess.get(helper.getLevel());
        var common = registry.federationdomainsFor(network(grids.getFirst()));
        if (grids.stream().skip(1).anyMatch(grid -> registry.federationdomainsFor(network(grid)).stream()
                .noneMatch(common::contains))) {
            return false;
        }
        EnergyBindingService.get(helper.getLevel()).observeFederationDomainMembers(grids);
        return true;
    }

    public List<IGrid> grids() {
        return FACES.stream().map(face -> routers.nativeDeviceNode(CENTER, face).getGrid()).toList();
    }

    public List<PolicyKey> keys() {
        var grids = grids();
        return List.of(key(grids.get(0), grids.get(1)), key(grids.get(1), grids.get(2)),
                key(grids.get(2), grids.get(0)));
    }

    public void enableCycle() {
        var policies = PolicyService.get(helper.getLevel());
        for (var key : keys()) {
            var result = policies.edit(new PolicyEdit(key, policies.revision(key),
                    PolicyRule.enabled(Set.of(PolicyOperation.SUPPLY))));
            helper.assertTrue(result instanceof PolicyMutationResult.Accepted,
                    "Every directed ring edge must accept its ME power Policy");
        }
        EnergyBindingService.get(helper.getLevel()).observeFederationDomainMembers(grids());
    }

    public void chargeSecond(double amount) {
        var cell = cell(1);
        var overflow = cell.injectAEPower(amount - cell.getAECurrentPower(), Actionable.MODULATE);
        helper.assertValueEqual(overflow, 0.0, "Ring provider cell must accept its charge");
    }

    public double extractFirst(double amount) {
        return grids().getFirst().getEnergyService().extractAEPower(amount, Actionable.MODULATE, PowerMultiplier.ONE);
    }

    public double totalStored() {
        var total = 0.0;
        for (var index = 0; index < FACES.size(); index++) {
            total += cell(index).getAECurrentPower();
        }
        return total;
    }

    public EnergyBindingService bindings() {
        return EnergyBindingService.get(helper.getLevel());
    }

    private EnergyCellBlockEntity cell(int index) {
        return helper.getBlockEntity(CENTER.relative(FACES.get(index)).below());
    }

    private static PolicyKey key(IGrid consumer, IGrid provider) {
        return new PolicyKey(network(consumer), network(provider), PolicyCapability.ME_POWER);
    }

    private static space.controlnet.ae2federation.identity.NetworkId network(IGrid grid) {
        return FederationDomainRegistryAccess.confirmedNetworkId(grid).orElseThrow();
    }

    @Override
    public void close() {
        routers.close();
    }
}
