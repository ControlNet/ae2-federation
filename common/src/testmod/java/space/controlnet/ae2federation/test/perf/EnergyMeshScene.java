package space.controlnet.ae2federation.test.perf;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.port.RouterPortBinding;
import space.controlnet.ae2federation.energy.EnergySharingService;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.test.energy.LargeEnergyCellRegistration;
import space.controlnet.ae2federation.test.router.RouterFixtures;

/**
 * TEST-ONLY benchmark scene: one Router joins {@code size} native Grids (an ME Chest on each horizontal face, then UP),
 * each with a large energy cell and {@code padding} extra virtual nodes, and every ordered pair of Grids has an enabled
 * ME power SUPPLY rule, which puts all of them in one shared energy pool. Grid 0's own cell is drained, so its demand
 * falls through to the other Grids' cells.
 */
public final class EnergyMeshScene implements AutoCloseable {
    private static final BlockPos CENTER = new BlockPos(6, 5, 6);
    private static final List<Direction> ALL_FACES = List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST,
            Direction.WEST, Direction.UP);
    private static final IGridNodeListener<EnergyMeshScene> LISTENER = (owner, node) -> {
    };

    private final GameTestHelper helper;
    private final RouterFixtures routers;
    private final List<Direction> faces;
    private final int padding;
    private final List<IManagedGridNode> paddingNodes = new ArrayList<>();
    private boolean routerPlaced;
    private boolean padded;
    private String status = "created";

    public EnergyMeshScene(GameTestHelper helper, int size, int padding) {
        if (size < 2 || size > ALL_FACES.size()) {
            throw new IllegalArgumentException("Energy mesh size must be 2.." + ALL_FACES.size());
        }
        this.helper = helper;
        this.padding = padding;
        faces = ALL_FACES.subList(0, size);
        routers = new RouterFixtures(helper);
        for (var face : faces) {
            routers.placeNativeDevice(CENTER, face);
            helper.setBlock(cellPosition(face), LargeEnergyCellRegistration.BLOCK.get());
        }
    }

    public boolean ready() {
        if (grids().stream().anyMatch(grid -> FederationDomainRegistryAccess.confirmedNetworkId(grid).isEmpty())) {
            status = "identities";
            return false;
        }
        if (!padded) {
            for (var face : faces) {
                pad(routers.nativeDeviceNode(CENTER, face));
            }
            padded = true;
            status = "padding";
            return false;
        }
        if (!routerPlaced) {
            routers.placeRouter(CENTER);
            routerPlaced = true;
            status = "router";
            return false;
        }
        var router = routers.router(CENTER);
        if (faces.stream().anyMatch(face -> !(router.binding(face) instanceof RouterPortBinding.Native))) {
            status = "router-bindings";
            return false;
        }
        var grids = grids();
        if (new HashSet<>(grids).size() != faces.size()) {
            status = "distinct-grids";
            return false;
        }
        if (grids.stream().anyMatch(grid -> FederationDomainRegistryAccess.confirmedNetworkId(grid).isEmpty())) {
            status = "padded-identities";
            return false;
        }
        var registry = FederationDomainRegistryAccess.get(helper.getLevel());
        var common = registry.federationdomainsFor(network(grids.getFirst()));
        if (grids.stream().skip(1).anyMatch(grid -> registry.federationdomainsFor(network(grid)).stream()
                .noneMatch(common::contains))) {
            status = "common-domain";
            return false;
        }
        status = "ready";
        return true;
    }

    public String status() {
        return status;
    }

    /** Enables SUPPLY on every ordered pair; returns the number of rules. */
    public int enableMesh() {
        var policies = PolicyService.get(helper.getLevel());
        var grids = grids();
        var count = 0;
        for (var consumer : grids) {
            for (var provider : grids) {
                if (consumer == provider) {
                    continue;
                }
                var key = new PolicyKey(network(consumer), network(provider), PolicyCapability.ME_POWER);
                var result = policies.edit(new PolicyEdit(key, policies.revision(key),
                        PolicyRule.enabled(Set.of(PolicyOperation.SUPPLY))));
                helper.assertTrue(result instanceof PolicyMutationResult.Accepted,
                        "Every ordered mesh pair must accept its ME power rule");
                count++;
            }
        }
        EnergySharingService.get(helper.getLevel()).observeFederationDomainMembers(grids);
        return count;
    }

    public void chargeProviders(double amount) {
        for (var index = 1; index < faces.size(); index++) {
            var cell = cell(index);
            cell.injectAEPower(Math.max(0, amount - cell.getAECurrentPower()), Actionable.MODULATE);
        }
    }

    public void drainConsumer() {
        var cell = cell(0);
        cell.extractAEPower(cell.getAECurrentPower(), Actionable.MODULATE, PowerMultiplier.ONE);
    }

    public double consumerCellStored() {
        return cell(0).getAECurrentPower();
    }

    public double providersStored() {
        var total = 0.0;
        for (var index = 1; index < faces.size(); index++) {
            total += cell(index).getAECurrentPower();
        }
        return total;
    }

    /** The unordered Grid pairs that share energy. */
    public int sharedPairCount() {
        return EnergySharingService.get(helper.getLevel()).sharedPairCount();
    }

    public IGrid consumerGrid() {
        return grids().getFirst();
    }

    public double extractConsumer(double amount, Actionable mode) {
        return consumerGrid().getEnergyService().extractAEPower(amount, mode, PowerMultiplier.ONE);
    }

    public int consumerNodeCount() {
        return consumerGrid().size();
    }

    public List<IGrid> grids() {
        return faces.stream().map(face -> routers.nativeDeviceNode(CENTER, face).getGrid()).toList();
    }

    private void pad(IGridNode anchor) {
        for (var index = 0; index < padding; index++) {
            var managed = GridHelper.createManagedNode(this, LISTENER).setInWorldNode(false).setIdlePowerUsage(0);
            managed.create(helper.getLevel(), null);
            paddingNodes.add(managed);
            GridHelper.createConnection(anchor, managed.getNode());
        }
    }

    private EnergyCellBlockEntity cell(int index) {
        return helper.getBlockEntity(cellPosition(faces.get(index)));
    }

    private static BlockPos cellPosition(Direction face) {
        var chest = CENTER.relative(face);
        return face == Direction.UP ? chest.above() : chest.below();
    }

    private static NetworkId network(IGrid grid) {
        return FederationDomainRegistryAccess.confirmedNetworkId(grid).orElseThrow();
    }

    @Override
    public void close() {
        paddingNodes.forEach(IManagedGridNode::destroy);
        paddingNodes.clear();
        routers.close();
    }
}
