package space.controlnet.ae2federation.automation;

import appeng.block.AEBaseEntityBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Creative Computing Device block (a placeholder, see {@link ComputingDeviceBlockEntity}). It has no screen yet. */
public final class ComputingDeviceBlock extends AEBaseEntityBlock<ComputingDeviceBlockEntity> {
    public ComputingDeviceBlock(Properties properties) {
        super(properties);
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
