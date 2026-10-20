package space.controlnet.ae2federation.automation.neoeco;

import cn.dancingsnow.neoecoae.blocks.NEBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import space.controlnet.ae2federation.bundle.BundleBody;
import space.controlnet.ae2federation.router.CableLinkedBlock;
import space.controlnet.ae2federation.router.RouterBlock;

/**
 * A communication interface of a Federation computation system: a part of its side face that, once the structure has
 * formed, links one ME Federation Cable on its outward face ({@link #FACING}) for the controller. Four that fill a
 * 2x2 square of the side face are one body ({@link RouterBlock#CONNECTED}), against which a 2x2 bundled cable links
 * (user, 2026-10-20).
 */
public final class EcoComputationInterfaceBlock extends NEBlock<EcoComputationInterfaceBlockEntity>
        implements CableLinkedBlock, BundleBody {
    /** The outward face, out of the side face; set as the structure forms. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public EcoComputationInterfaceBlock(Properties properties) {
        super(properties);
        var state = defaultBlockState().setValue(FACING, Direction.NORTH);
        for (var property : RouterBlock.CONNECTED) state = state.setValue(property, false);
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING).add(RouterBlock.CONNECTED);
    }

    @Override
    public boolean linksCable(BlockState state, Direction face) {
        return state.getValue(FORMED) && state.getValue(FACING) == face;
    }

    @Override
    public boolean bodyFormed(BlockState state) {
        return state.getValue(FORMED);
    }

    @Override
    public int bodyConnections(BlockState state) {
        int connections = 0;
        for (int i = 0; i < RouterBlock.CONNECTED.length; i++) {
            if (state.getValue(RouterBlock.CONNECTED[i])) connections |= 1 << i;
        }
        return connections;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos position, Block neighborBlock,
            BlockPos neighborPosition, boolean movedByPiston) {
        super.neighborChanged(state, level, position, neighborBlock, neighborPosition, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(position) instanceof EcoComputationInterfaceBlockEntity part) {
            part.neighborChanged(neighborPosition);
        }
    }
}
