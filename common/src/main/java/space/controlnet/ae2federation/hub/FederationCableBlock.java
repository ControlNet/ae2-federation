package space.controlnet.ae2federation.hub;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class FederationCableBlock extends Block {
    public FederationCableBlock(Properties properties) {
        super(properties);
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
}
