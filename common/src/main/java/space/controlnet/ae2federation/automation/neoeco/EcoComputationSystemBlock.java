package space.controlnet.ae2federation.automation.neoeco;

import cn.dancingsnow.neoecoae.blocks.NEBlock;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import space.controlnet.ae2federation.automation.AutomationInterfaceScreens;
import space.controlnet.ae2federation.router.CableLinkedBlock;

/**
 * The controller of a Federation computation system: Neo ECO's computation system controller, whose front, the only
 * face the structure leaves open, links ME Federation Cable once the structure has formed. Using it opens the ID and
 * alias screen of a computing device instead of Neo ECO's crafting CPU panel.
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos position, Player player,
            BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        return player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(position) instanceof EcoComputationSystemBlockEntity device
                && AutomationInterfaceScreens.open(serverPlayer, device) ? InteractionResult.CONSUME
                : InteractionResult.PASS;
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
