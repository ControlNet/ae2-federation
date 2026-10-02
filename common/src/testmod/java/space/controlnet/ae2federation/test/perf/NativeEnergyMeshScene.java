package space.controlnet.ae2federation.test.perf;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.parts.PartHelper;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import space.controlnet.ae2federation.test.energy.LargeEnergyCellRegistration;
import space.controlnet.ae2federation.test.port.NativePortFixtures;

/**
 * TEST-ONLY native counterpart of {@link EnergyMeshScene}: {@code size} Grids, each an ME Chest with a large energy
 * cell and {@code padding} extra virtual nodes, share energy through AE2 Quartz Fibers instead of Federation. Grid 0 is
 * a glass cable with a fiber toward each other Grid's cable; its own cell is drained, so its demand falls through to
 * the fibers.
 */
public final class NativeEnergyMeshScene implements AutoCloseable {
    private static final BlockPos CONSUMER_CABLE = new BlockPos(6, 5, 6);
    private static final List<Direction> FIBER_FACES = List.of(Direction.EAST, Direction.WEST, Direction.NORTH,
            Direction.SOUTH);
    private static final IGridNodeListener<NativeEnergyMeshScene> LISTENER = (owner, node) -> {
    };

    private final GameTestHelper helper;
    private final NativePortFixtures ports;
    private final int size;
    private final int padding;
    private final List<IManagedGridNode> paddingNodes = new ArrayList<>();
    private boolean padded;
    private String status = "created";

    public NativeEnergyMeshScene(GameTestHelper helper, int size, int padding) {
        if (size < 2 || size > FIBER_FACES.size() + 1) {
            throw new IllegalArgumentException("Native energy mesh size must be 2.." + (FIBER_FACES.size() + 1));
        }
        this.helper = helper;
        this.size = size;
        this.padding = padding;
        ports = new NativePortFixtures(helper);
        ports.placeCable(CONSUMER_CABLE);
        for (var index = 0; index < size; index++) {
            helper.setBlock(chestPosition(index), AEBlocks.ME_CHEST.block());
            helper.setBlock(cellPosition(index), LargeEnergyCellRegistration.BLOCK.get());
            if (index > 0) {
                var face = FIBER_FACES.get(index - 1);
                ports.placeCable(CONSUMER_CABLE.relative(face));
                var fiber = PartHelper.setPart(helper.getLevel(), helper.absolutePos(CONSUMER_CABLE), face, null,
                        AEParts.QUARTZ_FIBER.asItem());
                helper.assertTrue(fiber != null, "Quartz Fiber must be placeable on the consumer cable");
            }
        }
    }

    public boolean ready() {
        if (grids().stream().anyMatch(grid -> grid == null)) {
            status = "grids";
            return false;
        }
        if (!padded) {
            for (var index = 0; index < size; index++) {
                pad(ports.chestNode(chestPosition(index)));
            }
            padded = true;
            status = "padding";
            return false;
        }
        if (new HashSet<>(grids()).size() != size) {
            status = "distinct-grids";
            return false;
        }
        status = "ready";
        return true;
    }

    public String status() {
        return status;
    }

    public void chargeProviders(double amount) {
        for (var index = 1; index < size; index++) {
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
        for (var index = 1; index < size; index++) {
            total += cell(index).getAECurrentPower();
        }
        return total;
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
        var grids = new ArrayList<IGrid>();
        for (var index = 0; index < size; index++) {
            var node = helper.<MEChestBlockEntity>getBlockEntity(chestPosition(index))
                    .getMainNode().getNode();
            grids.add(node == null ? null : node.getGrid());
        }
        return grids;
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
        return helper.getBlockEntity(cellPosition(index));
    }

    /** Grid 0's cell sits on top of its chest, since the consumer cable is below it; the others sit below theirs. */
    private static BlockPos cellPosition(int index) {
        return index == 0 ? chestPosition(0).above() : chestPosition(index).below();
    }

    /**
     * Grid 0's chest sits on the consumer cable. Each other Grid's chest sits beside its own cable, turned away
     * from the consumer cable, so no two chests, cells or cables of different Grids touch except through a fiber.
     */
    private static BlockPos chestPosition(int index) {
        if (index == 0) {
            return CONSUMER_CABLE.above();
        }
        var face = FIBER_FACES.get(index - 1);
        return CONSUMER_CABLE.relative(face, 2);
    }

    @Override
    public void close() {
        paddingNodes.forEach(IManagedGridNode::destroy);
        paddingNodes.clear();
        ports.close();
    }
}
