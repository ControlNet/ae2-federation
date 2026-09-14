package space.controlnet.ae2federation.hub;

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

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
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
        return level.isClientSide() ? null : createTickerHelper(type, HubRegistration.FEDERATION_CABLE_BLOCK_ENTITY.get(),
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
