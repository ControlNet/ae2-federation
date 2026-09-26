package space.controlnet.ae2federation.router;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import com.mojang.serialization.MapCodec;

public final class FederationCableBlock extends BaseEntityBlock {
    public static final MapCodec<FederationCableBlock> CODEC = simpleCodec(FederationCableBlock::new);

    public FederationCableBlock(Properties properties) {
        super(properties);
    }

    private static final net.minecraft.world.phys.shapes.VoxelShape[] SHAPES = new net.minecraft.world.phys.shapes.VoxelShape[64];
    static {
        for (int mask = 0; mask < 64; mask++) {
            var shape = Block.box(5, 5, 5, 11, 11, 11);
            for (int bit = 0; bit < 6; bit++) {
                if ((mask & (1 << bit)) == 0) continue;
                var direction = CableVisualConnections.DIRECTIONS[bit];
                double[] lo = {4, 4, 4};
                double[] hi = {12, 12, 12};
                int axis = direction.getAxis().ordinal();
                lo[axis] = direction.getAxisDirection() == net.minecraft.core.Direction.AxisDirection.POSITIVE ? 8 : 0;
                hi[axis] = direction.getAxisDirection() == net.minecraft.core.Direction.AxisDirection.POSITIVE ? 16 : 8;
                shape = net.minecraft.world.phys.shapes.Shapes.or(shape, Block.box(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]));
            }
            SHAPES[mask] = shape.optimize();
        }
    }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,
            net.minecraft.world.level.BlockGetter level, BlockPos position,
            net.minecraft.world.phys.shapes.CollisionContext context) {
        return SHAPES[CableVisualConnections.mask(level, position)];
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new FederationCableBlockEntity(position, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, RouterRegistration.FEDERATION_CABLE_BLOCK_ENTITY.get(),
                FederationCableBlockEntity::serverTick);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos position, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, position, oldState, movedByPiston);
        level.invalidateCapabilities(position);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos position, BlockState newState,
            boolean movedByPiston) {
        level.invalidateCapabilities(position);
        super.onRemove(state, level, position, newState, movedByPiston);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos position, Block neighborBlock,
            BlockPos neighborPosition, boolean movedByPiston) {
        if (level.getBlockEntity(position) instanceof FederationCableBlockEntity cable) {
            cable.neighborChanged(neighborPosition);
        }
    }
}
