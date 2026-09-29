package space.controlnet.ae2federation.processing.endpoint;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.block.AEBaseEntityBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * ME Federation Processing Endpoint block. Its facing is the Federation face, as on the Federation Pattern Provider;
 * AE2's facing strategy handles placement, wrench rotation and persistence of the orientation.
 */
public final class EndpointBlock extends AEBaseEntityBlock<EndpointBlockEntity> {
    public EndpointBlock(Properties properties) {
        super(properties);
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.facing();
    }

    /** Places the Federation face against the block that was clicked, e.g. a Federation Cable. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return getOrientationStrategy().setFacing(defaultBlockState(), context.getClickedFace().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos position, Player player,
            BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        return player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && space.controlnet.ae2federation.client.menu.FederationDomainPolicyMenu.openDevice(serverPlayer, position)
                ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos position, Block neighborBlock,
            BlockPos neighborPosition, boolean movedByPiston) {
        if (!level.isClientSide() && level.getBlockEntity(position) instanceof EndpointBlockEntity endpoint) {
            endpoint.neighborChanged(neighborPosition);
        }
    }
}
