package space.controlnet.ae2federation.automation;

import appeng.block.AEBaseEntityBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Creative Computing Device block (a placeholder, see {@link ComputingDeviceBlockEntity}). Using it opens the ID and
 * alias screen, as the Automation Interface's does.
 */
public final class ComputingDeviceBlock extends AEBaseEntityBlock<ComputingDeviceBlockEntity> {
    public ComputingDeviceBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos position, Player player,
            BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        return player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(position) instanceof ComputingDeviceBlockEntity device
                && AutomationInterfaceScreens.open(serverPlayer, device) ? InteractionResult.CONSUME
                : InteractionResult.PASS;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos position, Block neighborBlock,
            BlockPos neighborPosition, boolean movedByPiston) {
        if (!level.isClientSide() && level.getBlockEntity(position) instanceof ComputingDeviceBlockEntity device) {
            device.neighborChanged(neighborPosition);
            device.redstoneChanged();
        }
    }
}
