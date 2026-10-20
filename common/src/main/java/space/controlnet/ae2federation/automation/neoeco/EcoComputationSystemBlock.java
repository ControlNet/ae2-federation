package space.controlnet.ae2federation.automation.neoeco;

import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import space.controlnet.ae2federation.automation.AutomationInterfaceScreens;

/**
 * The controller of a Federation computation system: Neo ECO's computation system controller. It links no cable
 * itself; ME Federation Cable reaches it through the structure's communication interfaces (user, 2026-10-20). Using it
 * opens the ID and alias screen of a computing device instead of Neo ECO's crafting CPU panel.
 */
public final class EcoComputationSystemBlock extends ECOComputationSystem {
    public EcoComputationSystemBlock(Properties properties) {
        super(properties);
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
