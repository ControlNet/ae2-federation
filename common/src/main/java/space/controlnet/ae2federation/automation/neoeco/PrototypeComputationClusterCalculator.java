package space.controlnet.ae2federation.automation.neoeco;

import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoprototype.multiblock.calculator.SimplifyComputationClusterCalculator;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

/**
 * Neo ECO Prototype's C1 geometry with N4's side face: the 2x3 end where C1 wants its interface holds casing and one
 * to {@value EcoComputationClusterCalculator#MAX_INTERFACES} Federation communication interfaces instead, in any of
 * its cells. So N1, like N4, has no way into an ME network. C1 checks every other line of the structure itself, through
 * the hook it opened for this (reliqwq/NeoECOPrototype#11).
 */
final class PrototypeComputationClusterCalculator extends SimplifyComputationClusterCalculator {
    PrototypeComputationClusterCalculator(NEBlockEntity<NEComputationCluster, ?> blockEntity) {
        super(blockEntity);
    }

    /** Our interface when the side face holds one to four of them and casing elsewhere; empty otherwise. */
    @Override
    protected Optional<Block> validateInterfaceFace(ServerLevel level, BlockPos controllerPos, Direction back,
            Direction top, Direction down, Direction interfaceSide, BlockPos energizedCorePos) {
        int interfaces = 0;
        for (var cell : EcoComputationClusterCalculator.sideFace(controllerPos, back, top, down, interfaceSide)) {
            var state = level.getBlockState(cell);
            if (state.getBlock() instanceof EcoComputationInterfaceBlock) {
                interfaces++;
            } else if (!state.is(casing())) {
                return Optional.empty();
            }
        }
        return interfaces >= 1 && interfaces <= EcoComputationClusterCalculator.MAX_INTERFACES
                ? Optional.of(NeoEcoComputing.INTERFACE.get()) : Optional.empty();
    }
}
