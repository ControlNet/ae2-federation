package space.controlnet.ae2federation.automation.neoeco;

import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The controller of a Federation computation system: Neo ECO's computation system controller. It links no cable
 * itself; ME Federation Cable reaches it through the structure's communication interfaces (user, 2026-10-20). Using it
 * opens Neo ECO's controller screen, which the block entity fills with the device's readouts.
 */
public final class EcoComputationSystemBlock extends ECOComputationSystem {
    public EcoComputationSystemBlock(Properties properties) {
        super(properties);
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
