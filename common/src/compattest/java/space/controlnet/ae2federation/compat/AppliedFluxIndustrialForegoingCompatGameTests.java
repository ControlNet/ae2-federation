package space.controlnet.ae2federation.compat;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Applied Flux's FE, sent on by the Federation Pattern Provider, running an Industrial Foregoing machine. */
@PrefixGameTestTemplate(false)
public final class AppliedFluxIndustrialForegoingCompatGameTests {
    private AppliedFluxIndustrialForegoingCompatGameTests() {
    }

    /**
     * The guide's Induction Card example with a Resourceful Furnace on the Provider's Endpoint
     * ({@link InductionCardExample}).
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 1600)
    public static void inductionCardPowersResourcefulFurnace(GameTestHelper helper) {
        InductionCardExample.run(helper, new FluxPoweredFurnace());
    }

    /**
     * A Resourceful Furnace on the Endpoint with no energy source but the Endpoint below it: cobblestone in from the
     * subnet's Storage Bus on its east face, stone pushed down into the Endpoint, energy taken from below. A player sets
     * the output inventory's bottom to Push in the furnace's side configuration.
     */
    private static final class FluxPoweredFurnace implements EndpointMachineScene.Machine {
        @Override
        public String blockId() {
            return "industrialforegoing:resourceful_furnace";
        }

        @Override
        public AEKey input() {
            return AEItemKey.of(Items.COBBLESTONE);
        }

        @Override
        public AEKey output() {
            return AEItemKey.of(Items.STONE);
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
        @SuppressWarnings("unchecked")
        public void prepare(GameTestHelper helper, BlockPos position) {
            var furnace = helper.getBlockEntity(position);
            try {
                var field = furnace.getClass().getDeclaredField("output");
                field.setAccessible(true);
                var output = field.get(furnace);
                var loader = furnace.getClass().getClassLoader();
                var modes = (Map<Object, Object>) output.getClass().getMethod("getFacingModes").invoke(output);
                modes.put(constant(loader, "com.hrznstudio.titanium.util.FacingUtil$Sideness", "BOTTOM"),
                        constant(loader, "com.hrznstudio.titanium.component.sideness.IFacingComponent$FaceMode", "PUSH"));
                furnace.setChanged();
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("The Resourceful Furnace could not be set up", exception);
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

        private static Object constant(ClassLoader loader, String type, String name) throws ClassNotFoundException {
            for (var constant : Class.forName(type, true, loader).getEnumConstants()) {
                if (((Enum<?>) constant).name().equals(name)) return constant;
            }
            throw new ClassNotFoundException(type + "." + name);
        }
    }
}
