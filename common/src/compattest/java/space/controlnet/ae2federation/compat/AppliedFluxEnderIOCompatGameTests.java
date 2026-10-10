package space.controlnet.ae2federation.compat;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Applied Flux's FE, sent on by the Federation Pattern Provider, running an Ender IO machine. */
@PrefixGameTestTemplate(false)
public final class AppliedFluxEnderIOCompatGameTests {
    private AppliedFluxEnderIOCompatGameTests() {
    }

    /** The guide's Induction Card example with a SAG Mill on the Provider's Endpoint ({@link InductionCardExample}). */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 1600)
    public static void inductionCardPowersSagMill(GameTestHelper helper) {
        InductionCardExample.run(helper, new FluxPoweredSagMill());
    }

    /**
     * A SAG Mill on the Endpoint with no energy source but the Endpoint below it: stone in from the subnet's Storage Bus
     * on its east face, cobblestone pushed down into the Endpoint, energy taken from below. A player fits a Basic
     * Capacitor, without which the mill holds no energy, and sets the bottom face to push with the Yeta Wrench.
     */
    private static final class FluxPoweredSagMill implements EndpointMachineScene.Machine {
        @Override
        public String blockId() {
            return "enderio:sag_mill";
        }

        @Override
        public AEKey input() {
            return AEItemKey.of(Items.STONE);
        }

        @Override
        public AEKey output() {
            return AEItemKey.of(Items.COBBLESTONE);
        }

        /** The Provider network's FE: an FE cell in the scene's second ME Chest. */
        @Override
        public String outputCell() {
            return "appflux:fe_1k_cell";
        }

        @Override
        public boolean ejectsIntoEndpoint() {
            return true;
        }

        @Override
        public Direction inputFace() {
            return Direction.EAST;
        }

        @Override
        public void prepare(GameTestHelper helper, BlockPos position) {
            var mill = helper.getBlockEntity(position);
            try {
                var slot = (int) mill.getClass().getMethod("getCapacitorSlotIndex").invoke(mill);
                var inventory = (ItemStackHandler) mill.getClass().getMethod("getInventory").invoke(mill);
                inventory.setStackInSlot(slot, new ItemStack(AddonCraftingScene.item("enderio:basic_capacitor")));
                setIOMode(mill, Direction.DOWN, "PUSH");
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("The SAG Mill could not be set up", exception);
            }
        }

        @Override
        public String state(GameTestHelper helper, BlockPos position) {
            var level = helper.getLevel();
            var energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(position), Direction.DOWN);
            var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(position), Direction.EAST);
            var held = new StringBuilder();
            for (int slot = 0; handler != null && slot < handler.getSlots(); slot++) {
                held.append(handler.getStackInSlot(slot)).append(';');
            }
            return "energy=" + (energy == null ? "-" : energy.getEnergyStored() + "/" + energy.getMaxEnergyStored())
                    + " held=" + held;
        }

        private static void setIOMode(BlockEntity mill, Direction side, String mode) throws ReflectiveOperationException {
            var type = Class.forName("com.enderio.enderio.api.io.IOMode", true, mill.getClass().getClassLoader());
            Object value = null;
            for (var constant : type.getEnumConstants()) {
                if (((Enum<?>) constant).name().equals(mode)) value = constant;
            }
            mill.getClass().getMethod("setIOMode", Direction.class, type).invoke(mill, side, value);
        }
    }
}
