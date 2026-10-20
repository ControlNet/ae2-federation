package space.controlnet.ae2federation.automation.neoeco;

import cn.dancingsnow.neoecoae.blocks.NENetworkSwitchBlock;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEComputationClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.multiblock.network.NENetworkSwitchUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Neo ECO's computation system geometry, with one change: the cell where Neo ECO wants its ME interface holds a casing
 * column instead, so a Federation computation system has no way into an ME network and never becomes an AE2 crafting CPU
 * (user, 2026-10-20). Nor may it hold a network switch: a switch pools clusters across systems, which would lend its
 * capacity to a Neo ECO system on ME. Neo ECO routes every member's check to the controller's calculator, so registering this for the
 * controller's block entity type covers the whole structure.
 */
final class EcoComputationClusterCalculator extends NEComputationClusterCalculator {
    EcoComputationClusterCalculator(NEBlockEntity<NEComputationCluster, ?> blockEntity) {
        super(blockEntity);
    }

    @Override
    protected boolean validateInterface(ServerLevel level, BlockPos interfacePos, Direction top, Direction down,
            Holder<Block> interfaceType, Holder<Block> casingType) {
        return super.validateInterface(level, interfacePos, top, down, casingType, casingType);
    }

    @Override
    public boolean verifyInternalStructure(ServerLevel level, BlockPos min, BlockPos max) {
        for (var pos : BlockPos.betweenClosed(min, max)) {
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof EcoComputationSystemBlock && (hasSwitch(level, pos, state, false)
                    || hasSwitch(level, pos, state, true))) {
                return false;
            }
        }
        return super.verifyInternalStructure(level, min, max);
    }

    private static boolean hasSwitch(ServerLevel level, BlockPos controller, BlockState state, boolean mirrored) {
        return level.getBlockState(NENetworkSwitchUtil.switchPosition(controller, state, mirrored))
                .getBlock() instanceof NENetworkSwitchBlock<?>;
    }
}
