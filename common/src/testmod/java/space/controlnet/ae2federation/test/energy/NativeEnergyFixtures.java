package space.controlnet.ae2federation.test.energy;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.networking.energy.IEnergyService;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.me.energy.IEnergyOverlayGridConnection;
import appeng.me.service.EnergyService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

public final class NativeEnergyFixtures implements AutoCloseable {
    private static final IGridNodeListener<NativeEnergyFixtures> LISTENER = (owner, node) -> {
    };
    private static final List<BlockPos> CELL_POSITIONS = List.of(
            new BlockPos(1, 2, 1), new BlockPos(4, 2, 1), new BlockPos(7, 2, 1));

    private final GameTestHelper helper;
    private final List<EnergySide> sides = new ArrayList<>();

    public NativeEnergyFixtures(GameTestHelper helper, int count) {
        this.helper = helper;
        for (int index = 0; index < count; index++) {
            var sideIndex = index;
            var position = CELL_POSITIONS.get(index);
            helper.setBlock(position, AEBlocks.ENERGY_CELL.block());
            var cell = helper.<EnergyCellBlockEntity>getBlockEntity(position);
            var boundary = GridHelper.createManagedNode(this, LISTENER)
                    .setIdlePowerUsage(0)
                    .setFlags(GridFlags.CANNOT_CARRY)
                    .addService(IEnergyOverlayGridConnection.class, () -> connectedServices(sideIndex));
            boundary.create(helper.getLevel(), helper.absolutePos(position.above()));
            sides.add(new EnergySide(cell, boundary));
        }
    }

    public boolean ready() {
        for (var side : sides) {
            var cellNode = side.cell().getMainNode().getNode();
            var boundaryNode = side.boundary().getNode();
            if (cellNode == null || boundaryNode == null) {
                return false;
            }
            if (cellNode.getGrid() != boundaryNode.getGrid()) {
                GridHelper.createConnection(cellNode, boundaryNode);
                return false;
            }
            if (!cellNode.hasGridBooted() || !boundaryNode.hasGridBooted()) {
                return false;
            }
        }
        return true;
    }

    public void connectOneWay(int from, int to) {
        sides.get(from).targets().clear();
        sides.get(from).targets().add(to);
        invalidateAll();
    }

    public void connectRing() {
        for (int index = 0; index < sides.size(); index++) {
            connectOneWay(index, (index + 1) % sides.size());
        }
    }

    public void charge(int index, double amount) {
        var overflow = storage(index).injectAEPower(amount, Actionable.MODULATE);
        helper.assertValueEqual(overflow, 0.0, "Native energy cell must accept fixture charge");
    }

    public double extract(int index, double amount) {
        return service(index).extractAEPower(amount, Actionable.MODULATE, PowerMultiplier.ONE);
    }

    public void discoverOverlay(int index) {
        service(index).extractAEPower(0, Actionable.SIMULATE, PowerMultiplier.ONE);
    }

    public double stored(int index) {
        return storage(index).getAECurrentPower();
    }

    public boolean powered(int index) {
        return service(index).isNetworkPowered();
    }

    public IGrid grid(int index) {
        return sides.get(index).boundary().getNode().getGrid();
    }

    public IEnergyService service(int index) {
        return grid(index).getEnergyService();
    }

    public IAEPowerStorage storage(int index) {
        return sides.get(index).cell();
    }

    public int usedChannels(int index) {
        return sides.get(index).boundary().getNode().getUsedChannels();
    }

    @Override
    public void close() {
        sides.forEach(side -> side.boundary().destroy());
        sides.clear();
    }

    private Collection<EnergyService> connectedServices(int index) {
        return sides.get(index).targets().stream().map(target -> (EnergyService) service(target)).toList();
    }

    private void invalidateAll() {
        sides.forEach(side -> ((EnergyService) side.boundary().getNode().getGrid().getEnergyService())
                .invalidateOverlayEnergyGrid());
    }

    private record EnergySide(EnergyCellBlockEntity cell, IManagedGridNode boundary, List<Integer> targets) {
        private EnergySide(EnergyCellBlockEntity cell, IManagedGridNode boundary) {
            this(cell, boundary, new ArrayList<>());
        }
    }
}
