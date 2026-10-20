package space.controlnet.ae2federation.automation.neoeco;

import cn.dancingsnow.neoecoae.blocks.ECOMachineInterface;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationSystem;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockContext;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the Structure Builder builds for a Federation controller: Neo ECO's computation structure of the same tier,
 * with the Federation controller in place of Neo ECO's and one communication interface where Neo ECO puts its ME
 * interface, the side face's back middle cell. Neo ECO's definition keeps its instructions to itself, so this runs
 * them into a context that swaps those two blocks.
 */
final class EcoComputationBuild {
    private static final Map<BlockState, MultiBlockDefinition> DEFINITIONS = new ConcurrentHashMap<>();

    private EcoComputationBuild() {
    }

    /** Neo ECO's {@code definition} with {@code controller}, the Federation controller's default state, swapped in. */
    static MultiBlockDefinition of(MultiBlockDefinition definition, BlockState controller) {
        return DEFINITIONS.computeIfAbsent(controller, state -> new MultiBlockDefinition(
                List.of(context -> definition.createLevel(new Swapping(context, state))),
                BuiltInRegistries.BLOCK.wrapAsHolder(state.getBlock()), state.getBlock().getName(),
                definition.getExpandMin(), definition.getExpandMax(), definition.getOnFormed()));
    }

    /**
     * Places into {@code target} what Neo ECO's instructions place, with the two blocks swapped. It reports itself
     * unformed, so Neo ECO's formed look is applied once, by the outer definition.
     */
    private static final class Swapping extends MultiBlockContext {
        private final MultiBlockContext target;
        private final BlockState controller;

        Swapping(MultiBlockContext target, BlockState controller) {
            this.target = target;
            this.controller = controller;
            this.repeats = target.getRepeats();
        }

        @Override
        public void setBlock(BlockPos pos, BlockState state) {
            if (state.getBlock() instanceof ECOComputationSystem) {
                state = controller;
            } else if (state.getBlock() instanceof ECOMachineInterface<?>) {
                // Neo ECO's structure grows west, so its side face looks east.
                state = NeoEcoComputing.INTERFACE.get().defaultBlockState()
                        .setValue(EcoComputationInterfaceBlock.FACING, Direction.EAST);
            }
            target.setBlock(pos, state);
        }

        @Override
        public Level getLevel() {
            return target.getLevel();
        }

        @Override
        public List<BlockPos> allBlocks() {
            return target.allBlocks();
        }

        @Override
        public boolean isFormed() {
            return false;
        }
    }
}
