package space.controlnet.ae2federation.automation;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.block.AEBaseEntityBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * ME Federation Automation Interface block. Its facing is the face that acts on the world; AE2's facing strategy
 * handles placement, wrench rotation and wrench pickup. Using it opens the ID and alias screen.
 */
public final class AutomationInterfaceBlock extends AEBaseEntityBlock<AutomationInterfaceBlockEntity> {
    public AutomationInterfaceBlock(Properties properties) {
        super(properties);
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.facing();
    }

    /** Places the acting face against the block that was clicked, e.g. a chest, as the Endpoint places its front. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return getOrientationStrategy().setFacing(defaultBlockState(), context.getClickedFace().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos position, Player player,
            BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        return player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(position) instanceof AutomationInterfaceBlockEntity automationInterface
                && AutomationInterfaceScreens.open(serverPlayer, automationInterface)
                ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos position, Block neighborBlock,
            BlockPos neighborPosition, boolean movedByPiston) {
        if (!level.isClientSide()
                && level.getBlockEntity(position) instanceof AutomationInterfaceBlockEntity automationInterface) {
            automationInterface.neighborChanged(neighborPosition);
        }
    }
}
