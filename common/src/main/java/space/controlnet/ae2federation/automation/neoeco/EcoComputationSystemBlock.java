package space.controlnet.ae2federation.automation.neoeco;

import cn.dancingsnow.neoecoae.blocks.NEBlock;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import space.controlnet.ae2federation.router.CableLinkedBlock;

/**
 * The controller of a Federation computation system: Neo ECO's computation system controller, whose front, the only
 * face the structure leaves open, links ME Federation Cable once the structure has formed. Using it opens Neo ECO's
 * controller screen, which the block entity fills with the device's readouts.
 */
public final class EcoComputationSystemBlock extends ECOComputationSystem implements CableLinkedBlock {
    public EcoComputationSystemBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean linksCable(BlockState state, Direction face) {
        return state.getValue(NEBlock.FORMED) && state.getValue(FACING) == face;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos position, Block neighborBlock,
            BlockPos neighborPosition, boolean movedByPiston) {
        super.neighborChanged(state, level, position, neighborBlock, neighborPosition, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(position) instanceof EcoComputationSystemBlockEntity device) {
            device.neighborChanged(neighborPosition);
        }
    }
}
