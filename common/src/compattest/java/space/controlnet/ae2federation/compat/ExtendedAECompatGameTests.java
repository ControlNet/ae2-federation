package space.controlnet.ae2federation.compat;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.util.IConfigManager;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Federation features with ExtendedAE's own blocks. */
@PrefixGameTestTemplate(false)
public final class ExtendedAECompatGameTests {
    private ExtendedAECompatGameTests() {
    }

    /** ExtendedAE's Extended Pattern Provider serves the remote craft. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void exPatternProviderCrafting(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "extendedae:ex_pattern_provider", "ae2:molecular_assembler", List.of("ae2:1k_crafting_storage"));
        helper.succeedWhen(scene::tick);
    }

    /**
     * ExtendedAE's Assembler Matrix crafts the remote request: a multiblock whose pattern core provides the pattern
     * and whose crafter core crafts it, joined to the provider network through its frame.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void assemblerMatrixCrafting(GameTestHelper helper) {
        var scene = AddonCraftingScene.structure(helper, "extendedae:assembler_matrix", List.of("ae2:1k_crafting_storage"),
                ExtendedAECompatGameTests::placeAssemblerMatrix, AddonCraftingScene.PROVIDER.offset(1, 1, -1));
        helper.succeedWhen(scene::tick);
    }

    /**
     * The smallest Assembler Matrix, 4 long, 3 high and 3 deep from the provider's spot: frames on its edges, walls on
     * its faces, and inside a pattern core and then a crafter core.
     */
    private static void placeAssemblerMatrix(GameTestHelper helper) {
        var start = AddonCraftingScene.PROVIDER.offset(0, 0, -2);
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    int outer = (x == 0 || x == 3 ? 1 : 0) + (y == 1 ? 0 : 1) + (z == 1 ? 0 : 1);
                    var part = outer >= 2 ? "frame" : outer == 1 ? "wall" : x == 1 ? "pattern" : "crafter";
                    helper.setBlock(start.offset(x, y, z), AddonCraftingScene.block("extendedae:assembler_matrix_" + part));
                }
            }
        }
    }

    /** The same provider runs the remote request's processing pattern in a machine. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void exPatternProviderProcessing(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "extendedae:ex_pattern_provider", "minecraft:chest",
                List.of("ae2:1k_crafting_storage"), true);
        helper.succeedWhen(scene::tick);
    }

    /**
     * The Federation Pattern Provider sends a gold block through an Endpoint into ExtendedAE's Circuit Slicer, which
     * joins the Endpoint's subnet for power and exports its prints down into the Endpoint.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void endpointCircuitCutter(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new CircuitCutter());
        helper.succeedWhen(scene::tick);
    }

    private static final class CircuitCutter implements EndpointMachineScene.Machine {
        @Override
        public String blockId() {
            return "extendedae:circuit_cutter";
        }

        @Override
        public AEKey input() {
            return AEItemKey.of(Items.GOLD_BLOCK);
        }

        @Override
        public AEKey output() {
            return AEItemKey.of(AddonCraftingScene.item("ae2:printed_logic_processor"));
        }

        @Override
        public long outputPerInput() {
            return 9;
        }

        @Override
        public boolean ejectsIntoEndpoint() {
            return true;
        }

        /** Auto-export on, down into the Endpoint, as its screen sets it. */
        @Override
        public void prepare(GameTestHelper helper, BlockPos position) {
            var cutter = helper.getLevel().getBlockEntity(helper.absolutePos(position));
            try {
                var settings = (IConfigManager) cutter.getClass().getMethod("getConfigManager").invoke(cutter);
                settings.putSetting(Settings.AUTO_EXPORT, YesNo.YES);
                @SuppressWarnings("unchecked")
                var sides = (Set<Direction>) cutter.getClass().getMethod("getOutputSides").invoke(cutter);
                sides.add(Direction.DOWN);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("The Circuit Slicer's export could not be set", exception);
            }
        }

        @Override
        public String state(GameTestHelper helper, BlockPos position) {
            var cutter = helper.getLevel().getBlockEntity(helper.absolutePos(position));
            try {
                return "input=" + cutter.getClass().getMethod("getInput").invoke(cutter) + " output="
                        + ((appeng.api.inventories.InternalInventory) cutter.getClass().getMethod("getOutput")
                        .invoke(cutter)).getStackInSlot(0) + " progress="
                        + cutter.getClass().getMethod("getProgress").invoke(cutter) + " powered="
                        + ((appeng.blockentity.grid.AENetworkedPoweredBlockEntity) cutter).getMainNode().isActive();
            } catch (ReflectiveOperationException exception) {
                return exception.toString();
            }
        }
    }

    /** An Extended Pattern Provider runs an Endpoint in Local mode, as AE2's own provider does. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void endpointLocalExPatternProvider(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new CoreCompatGameTests.Furnace())
                .throughLocalProvider("extendedae:ex_pattern_provider");
        helper.succeedWhen(scene::tick);
    }

    /** ExtendedAE's Extended Molecular Assembler crafts for the remote request. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void exMolecularAssemblerCrafting(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", "extendedae:ex_molecular_assembler", List.of("ae2:1k_crafting_storage"));
        helper.succeedWhen(scene::tick);
    }

    /** ExtendedAE's Tag Storage Bus, filtered to iron ingots, shares a chest. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void tagStorageBus(GameTestHelper helper) {
        var scene = AddonStorageScene.storageBus(helper, "extendedae:tag_storage_bus", "minecraft:chest",
                AEItemKey.of(Items.IRON_INGOT), 9, 4, bus -> invoke(bus, "setTagFilter",
                        new Class<?>[] { String.class, boolean.class }, "c:ingots/iron", true));
        helper.succeedWhen(scene::tick);
    }

    /** ExtendedAE's Mod Storage Bus, filtered to Minecraft's items, shares a chest. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void modStorageBus(GameTestHelper helper) {
        var scene = AddonStorageScene.storageBus(helper, "extendedae:mod_storage_bus", "minecraft:chest",
                AEItemKey.of(Items.IRON_INGOT), 9, 4, bus -> invoke(bus, "setModNameFilter",
                        new Class<?>[] { String.class }, "minecraft"));
        helper.succeedWhen(scene::tick);
    }

    /** ExtendedAE's creative Infinity Cobblestone Cell: the consumer sees and takes endless cobblestone. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void infinityCellShared(GameTestHelper helper) {
        var scene = new AddonStorageScene(helper, "extendedae:infinity_cobblestone_cell",
                AEItemKey.of(Items.COBBLESTONE), 0, 64).infinite();
        helper.succeedWhen(scene::tick);
    }

    /** Sets a bus part's filter as its screen does. */
    private static void invoke(Object target, String name, Class<?>[] types, Object... arguments) {
        try {
            target.getClass().getMethod(name, types).invoke(target, arguments);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(target + " has no " + name, exception);
        }
    }

    /** ExtendedAE's cable-part Pattern Provider runs the Endpoint in Local mode. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void endpointLocalExPatternProviderPart(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new CoreCompatGameTests.Furnace())
                .throughLocalProviderPart("extendedae:ex_pattern_provider_part");
        helper.succeedWhen(scene::tick);
    }


    /** The Bridge is removed after ExtendedAE's Pattern Provider pushed its inputs. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void exPatternProviderDisconnected(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "extendedae:ex_pattern_provider", "minecraft:chest",
                List.of("ae2:1k_crafting_storage"), true).disconnectingAfterPush();
        helper.succeedWhen(scene::tick);
    }

}
