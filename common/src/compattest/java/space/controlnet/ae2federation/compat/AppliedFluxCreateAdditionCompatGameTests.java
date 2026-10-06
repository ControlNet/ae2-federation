package space.controlnet.ae2federation.compat;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Applied Flux's FE, sent on by the Federation Pattern Provider, driving Create through Create Crafts & Additions. */
@PrefixGameTestTemplate(false)
public final class AppliedFluxCreateAdditionCompatGameTests {
    private AppliedFluxCreateAdditionCompatGameTests() {
    }

    /** The guide's Induction Card example with an Electric Motor turning a Millstone ({@link InductionCardExample}). */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 1600)
    public static void inductionCardTurnsElectricMotor(GameTestHelper helper) {
        InductionCardExample.run(helper, new MotorDrivenMillstone());
    }

    /**
     * A Millstone above a Create Chute on the Endpoint, turned by an Electric Motor beside the Endpoint: the motor takes
     * FE from the Endpoint and drives a shaft up to a cogwheel meshing with the Millstone. The Storage Bus fills the
     * Millstone from its east side, and the Chute takes the gravel out of it and down into the Endpoint.
     */
    private static final class MotorDrivenMillstone implements EndpointMachineScene.Machine {
        @Override
        public String blockId() {
            return "create:millstone";
        }

        @Override
        public AEKey input() {
            return AEItemKey.of(Items.COBBLESTONE);
        }

        @Override
        public AEKey output() {
            return AEItemKey.of(Items.GRAVEL);
        }

        /** The Provider network's FE: an FE cell in the scene's second ME Chest. */
        @Override
        public String outputCell() {
            return "appflux:fe_1k_cell";
        }

        @Override
        public Direction inputFace() {
            return Direction.EAST;
        }

        @Override
        public net.minecraft.world.level.block.state.BlockState collector() {
            return AddonCraftingScene.block("create:chute").defaultBlockState();
        }

        /** The motor stands south of the Endpoint, two blocks under the Millstone, at its default speed. */
        @Override
        public void place(GameTestHelper helper, BlockPos position) {
            var motor = motor(position);
            helper.setBlock(motor, AddonCraftingScene.block("createaddition:electric_motor").defaultBlockState()
                    .setValue(BlockStateProperties.FACING, Direction.UP));
            helper.setBlock(motor.above(), AddonCraftingScene.block("create:shaft").defaultBlockState()
                    .setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
            helper.setBlock(motor.above(2), AddonCraftingScene.block("create:cogwheel").defaultBlockState()
                    .setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
            helper.setBlock(position, AddonCraftingScene.block(blockId()));
        }

        @Override
        public String state(GameTestHelper helper, BlockPos position) {
            var level = helper.getLevel();
            var energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(motor(position)),
                    Direction.NORTH);
            var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(position), Direction.DOWN);
            var held = new StringBuilder();
            for (int slot = 0; handler != null && slot < handler.getSlots(); slot++) {
                held.append(handler.getStackInSlot(slot)).append(';');
            }
            return "motorEnergy=" + (energy == null ? "-" : energy.getEnergyStored()) + " motorSpeed="
                    + speed(helper, motor(position)) + " millstoneSpeed=" + speed(helper, position) + " held=" + held;
        }

        private static BlockPos motor(BlockPos millstone) {
            return millstone.below(2).south();
        }

        private static String speed(GameTestHelper helper, BlockPos position) {
            var entity = helper.getLevel().getBlockEntity(helper.absolutePos(position));
            try {
                return String.valueOf(entity.getClass().getMethod("getSpeed").invoke(entity));
            } catch (ReflectiveOperationException | NullPointerException exception) {
                return "?";
            }
        }
    }
}
