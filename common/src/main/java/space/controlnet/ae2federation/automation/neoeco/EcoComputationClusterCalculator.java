package space.controlnet.ae2federation.automation.neoeco;

import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEComputationClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

/**
 * Neo ECO's computation system geometry, with one change: the side face, the 2x3 end where Neo ECO wants its ME
 * interface and may take a network switch, holds casing and up to {@value #MAX_INTERFACES} Federation communication
 * interfaces instead, in any of its cells (user, 2026-10-20). So a Federation computation system has no way into an ME
 * network and never becomes an AE2 crafting CPU, and no switch pools its capacity with a Neo ECO system on ME. Neo ECO
 * routes every member's check to the controller's calculator, so registering this for the controller's and the
 * interface's block entity types covers the whole structure.
 */
final class EcoComputationClusterCalculator extends NEComputationClusterCalculator {
    static final int MAX_INTERFACES = 4;

    EcoComputationClusterCalculator(NEBlockEntity<NEComputationCluster, ?> blockEntity) {
        super(blockEntity);
    }

    /**
     * The side face here, then the rest as Neo ECO checks it. Neo ECO checks the side face's two columns as its
     * interface column and its casing-or-switch column; told that its interface side is its expand side, it checks
     * the casing columns there again instead and leaves the side face to this.
     */
    @Override
    protected boolean verifyStructure(ServerLevel level, BlockPos controllerPos, IECOTier tier, Direction front,
            Direction back, Direction top, Direction down, Direction interfaceSide, Direction expandSide,
            Direction networkSwitchSide, boolean mirrored) {
        int interfaces = 0;
        for (var cell : sideFace(controllerPos, back, top, down, interfaceSide)) {
            var state = level.getBlockState(cell);
            if (state.getBlock() instanceof EcoComputationInterfaceBlock) {
                interfaces++;
            } else if (!state.is(casing())) {
                return false;
            }
        }
        return interfaces <= MAX_INTERFACES && super.verifyStructure(level, controllerPos, tier, front, back, top,
                down, expandSide, expandSide, expandSide, mirrored);
    }

    /** Casing where Neo ECO's own walk looks for its interface, which is now the casing column beside the controller. */
    @Override
    protected boolean validateInterface(ServerLevel level, BlockPos interfacePos, Direction top, Direction down,
            Holder<Block> interfaceType, Holder<Block> casingType) {
        return super.validateInterface(level, interfacePos, top, down, casingType, casingType);
    }

    /**
     * The six cells of the side face toward {@code side} of the controller at {@code controller}: the controller's
     * column and the one behind it, bottom to top.
     */
    static List<BlockPos> sideFace(BlockPos controller, Direction back, Direction top, Direction down, Direction side) {
        var cells = new ArrayList<BlockPos>(6);
        for (var column : List.of(controller.relative(side), controller.relative(back).relative(side))) {
            cells.add(column.relative(down));
            cells.add(column);
            cells.add(column.relative(top));
        }
        return cells;
    }
}
