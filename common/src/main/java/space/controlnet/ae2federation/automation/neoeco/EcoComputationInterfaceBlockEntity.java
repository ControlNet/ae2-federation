package space.controlnet.ae2federation.automation.neoeco;

import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationSystem;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.automation.DomainDevice;
import space.controlnet.ae2federation.domain.FederationDomainNodeKind;
import space.controlnet.ae2federation.domain.FederationDomainPortEvidence;
import space.controlnet.ae2federation.domain.FederationDomainPortId;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.port.FederationPort;
import space.controlnet.ae2federation.router.RouterBlock;

/**
 * A communication interface of a Federation computation system: a member of Neo ECO's structure, and a node of its
 * Federation domain linked to the controller's node by a remote port on each side, so a cable at any interface reaches
 * the one device. It has no ID of its own.
 */
public final class EcoComputationInterfaceBlockEntity
        extends NEBlockEntity<NEComputationCluster, EcoComputationInterfaceBlockEntity> implements DomainDevice.Host {
    /** The port toward the controller; the controller's port toward this interface is {@link #portOf(BlockPos)}. */
    static final String CONTROLLER_PORT = "controller";

    private final DomainDevice device = new DomainDevice(this, this);

    public EcoComputationInterfaceBlockEntity(BlockEntityType<?> type, BlockPos position, BlockState state) {
        super(type, position, state, EcoComputationClusterCalculator::new);
    }

    /** The controller's port toward the interface at {@code position}. */
    static String portOf(BlockPos position) {
        return "interface " + position.toShortString();
    }

    @Override
    public FederationDomainNodeKind kind() {
        return FederationDomainNodeKind.COMPUTING_INTERFACE;
    }

    @Override
    public @Nullable String claim(ServerLevel level, @Nullable String stored) {
        return null;
    }

    @Override
    public void release(ServerLevel level, String id) {
    }

    @Override
    public Map<String, FederationDomainPortEvidence> remotePorts() {
        var controller = controller();
        if (controller == null || !(level instanceof ServerLevel serverLevel)) return Map.of();
        return Map.of(CONTROLLER_PORT, new FederationDomainPortEvidence.Federation(new FederationDomainPortId(
                FederationDomainRegistryAccess.nodeId(serverLevel, controller.getBlockPos()), portOf(worldPosition))));
    }

    /** The Federation controller of the formed structure this interface belongs to. */
    @Nullable EcoComputationSystemBlockEntity controller() {
        return cluster != null && cluster.getController() instanceof EcoComputationSystemBlockEntity controller
                ? controller : null;
    }

    public @Nullable FederationPort federationPort(@Nullable Direction face) {
        return device.federationPort(face);
    }

    public static void serverTick(Level level, BlockPos position, BlockState state,
            EcoComputationInterfaceBlockEntity entity) {
        entity.device.tick();
    }

    public void neighborChanged(BlockPos neighborPosition) {
        device.neighborChanged(neighborPosition);
    }

    /**
     * The structure formed or broke: the interface faces out of the side face, joins the other three of a filled 2x2
     * square, and links only while it is formed.
     */
    @Override
    public void updateState(boolean updateExposed) {
        super.updateState(updateExposed);
        if (level == null || notLoaded() || isRemoved() || isServerStopping()) return;
        var state = level.getBlockState(worldPosition);
        if (!(state.getBlock() instanceof EcoComputationInterfaceBlock)) return;
        var shaped = shape(state);
        if (shaped != state) {
            level.setBlock(worldPosition, shaped, Block.UPDATE_CLIENTS);
            if (level instanceof ServerLevel serverLevel) {
                // A bundled cable links only to a whole square; nothing else tells the cables beside it to judge again.
                serverLevel.invalidateCapabilities(worldPosition);
                for (var face : Direction.values()) serverLevel.invalidateCapabilities(worldPosition.relative(face));
            }
        }
        device.openPorts();
    }

    /** {@code state} with its outward face and square, from the controller's place; unchanged while not formed. */
    private BlockState shape(BlockState state) {
        var controller = controller();
        int connections = 0;
        if (controller != null) {
            var front = controller.getBlockState().getValue(ECOComputationSystem.FACING);
            var axis = front.getClockWise().getAxis();
            var outward = Direction.fromAxisAndDirection(axis,
                    worldPosition.get(axis) > controller.getBlockPos().get(axis) ? Direction.AxisDirection.POSITIVE
                            : Direction.AxisDirection.NEGATIVE);
            state = state.setValue(EcoComputationInterfaceBlock.FACING, outward);
            connections = square(controller.getBlockPos(), front.getOpposite(), outward);
        }
        for (int i = 0; i < RouterBlock.CONNECTED.length; i++) {
            state = state.setValue(RouterBlock.CONNECTED[i], (connections & 1 << i) != 0);
        }
        return state;
    }

    /**
     * The sides toward the other interfaces of a 2x2 square that four interfaces fill, one bit per 3D data value; none
     * when the side face's interfaces fill no square.
     */
    private int square(BlockPos controller, Direction back, Direction side) {
        var cells = EcoComputationClusterCalculator.sideFace(controller, back, Direction.UP, Direction.DOWN, side);
        Set<BlockPos> interfaces = new HashSet<>();
        for (var cell : cells) {
            if (level.getBlockState(cell).getBlock() instanceof EcoComputationInterfaceBlock) interfaces.add(cell);
        }
        // Bottom to top in each column: a square is two neighbouring rows of both columns.
        boolean filled = interfaces.size() == 4 && (interfaces.containsAll(java.util.List.of(cells.get(0),
                cells.get(1), cells.get(3), cells.get(4))) || interfaces.containsAll(java.util.List.of(cells.get(1),
                cells.get(2), cells.get(4), cells.get(5))));
        if (!filled || !interfaces.contains(worldPosition)) return 0;
        int connections = 0;
        for (var step : Direction.values()) {
            if (interfaces.contains(worldPosition.relative(step))) connections |= 1 << step.get3DDataValue();
        }
        return connections;
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        device.clearRemoved();
    }

    @Override
    public void onReady() {
        super.onReady();
        device.onReady();
    }

    @Override
    public void onChunkUnloaded() {
        device.onChunkUnloaded();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        device.setRemoved();
        super.setRemoved();
    }
}
