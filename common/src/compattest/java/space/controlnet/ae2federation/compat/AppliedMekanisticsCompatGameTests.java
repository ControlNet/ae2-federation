package space.controlnet.ae2federation.compat;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Federation features with Applied Mekanistics' chemical storage. */
@PrefixGameTestTemplate(false)
public final class AppliedMekanisticsCompatGameTests {
    private AppliedMekanisticsCompatGameTests() {
    }

    /** The provider network's storage is a 1k ME Chemical Storage Cell holding hydrogen. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void chemicalCellShared(GameTestHelper helper) {
        var hydrogen = AddonStorageScene.key(helper, "appmek:chemical", "mekanism:hydrogen");
        var scene = new AddonStorageScene(helper, "appmek:chemical_storage_cell_1k", hydrogen, 1000, 250);
        helper.succeedWhen(scene::tick);
    }

    /** The same chemical cell under a rule without Insert: the consumer takes hydrogen but cannot store it back. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void chemicalCellExtractOnly(GameTestHelper helper) {
        var hydrogen = AddonStorageScene.key(helper, "appmek:chemical", "mekanism:hydrogen");
        var scene = new AddonStorageScene(helper, "appmek:chemical_storage_cell_1k", hydrogen, 1000, 250).extractOnly();
        helper.succeedWhen(scene::tick);
    }

    /**
     * The Federation Pattern Provider sends charcoal through an Endpoint into a Chemical Oxidizer standing on the
     * Endpoint; the oxidizer ejects the carbon it makes, a chemical, into the Endpoint, which returns it.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void endpointOxidizerChemical(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new Oxidizer(
                AddonStorageScene.key(helper, "appmek:chemical", "mekanism:carbon")));
        helper.succeedWhen(scene::tick);
    }

    /**
     * The consumer requests carbon; the provider network's AE2 Pattern Provider pushes charcoal into a Chemical
     * Oxidizer, which ejects the carbon back into the provider. The provider network stores it in a chemical cell.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void oxidizerChemicalProcessing(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", List.of("ae2:1k_crafting_storage"),
                new ProviderOxidizer(AddonStorageScene.key(helper, "appmek:chemical", "mekanism:carbon")));
        helper.succeedWhen(scene::tick);
    }

    /** An oxidizer east of the provider: charcoal in and carbon out on its west side, energy from its east. */
    private record ProviderOxidizer(AEKey carbon) implements AddonCraftingScene.Machine {
        @Override
        public String blockId() {
            return "mekanism:chemical_oxidizer";
        }

        @Override
        public AEItemKey input() {
            return AEItemKey.of(Items.CHARCOAL);
        }

        @Override
        public AEKey output() {
            return carbon;
        }

        @Override
        public long outputPerInput() {
            return 20;
        }

        @Override
        public String outputCell() {
            return "appmek:chemical_storage_cell_1k";
        }

        @Override
        public void placeAround(GameTestHelper helper, BlockPos position) {
            var cube = position.east();
            helper.setBlock(cube, AddonCraftingScene.block("mekanism:creative_energy_cube"));
            MekanismSetup.fill(helper, cube);
            MekanismSetup.configure(helper, cube, "ENERGY", "OUTPUT", Direction.WEST, true);
            MekanismSetup.configure(helper, position, "ITEM", "INPUT", Direction.WEST, false);
            MekanismSetup.configure(helper, position, "CHEMICAL", "OUTPUT", Direction.WEST, true);
            MekanismSetup.configure(helper, position, "ENERGY", "INPUT", Direction.EAST, false);
        }

        /** Nothing to move: the oxidizer ejects into the provider by itself. */
        @Override
        public void collect(GameTestHelper helper, BlockPos position, MEStorage network) {
        }
    }

    private record Oxidizer(AEKey carbon) implements EndpointMachineScene.Machine {
        @Override
        public String blockId() {
            return "mekanism:chemical_oxidizer";
        }

        @Override
        public AEKey input() {
            return AEItemKey.of(Items.CHARCOAL);
        }

        @Override
        public AEKey output() {
            return carbon;
        }

        @Override
        public String outputCell() {
            return "appmek:chemical_storage_cell_1k";
        }

        @Override
        public long outputPerInput() {
            return 20;
        }

        @Override
        public boolean ejectsIntoEndpoint() {
            return true;
        }

        @Override
        public Direction inputFace() {
            return Direction.EAST;
        }

        /** Items in on the Storage Bus side, carbon ejected down into the Endpoint, energy from the cube behind. */
        @Override
        public void prepare(GameTestHelper helper, BlockPos position) {
            var cube = position.west();
            helper.setBlock(cube, AddonCraftingScene.block("mekanism:creative_energy_cube"));
            MekanismSetup.fill(helper, cube);
            MekanismSetup.configure(helper, cube, "ENERGY", "OUTPUT", Direction.EAST, true);
            MekanismSetup.configure(helper, position, "ITEM", "INPUT", Direction.EAST, false);
            MekanismSetup.configure(helper, position, "CHEMICAL", "OUTPUT", Direction.DOWN, true);
            MekanismSetup.configure(helper, position, "ENERGY", "INPUT", Direction.WEST, false);
        }

        @Override
        public String state(GameTestHelper helper, BlockPos position) {
            return MekanismSetup.describe(helper, position) + " endpointChemicalHandler="
                    + MekanismSetup.chemicalHandler(helper, position.below(), Direction.UP);
        }
    }

    /**
     * The provider network shares hydrogen in a Basic Chemical Tank through AE2's Storage Bus, its side towards the bus
     * set to input and output as a player sets it with the Configurator.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void storageBusChemicalTank(GameTestHelper helper) {
        var hydrogen = AddonStorageScene.key(helper, "appmek:chemical", "mekanism:hydrogen");
        var scene = AddonStorageScene.storageBus(helper, "ae2:storage_bus", "mekanism:basic_chemical_tank", hydrogen,
                1000, 250, bus -> MekanismSetup.configure(helper, AddonStorageScene.BUS_TARGET, "CHEMICAL",
                        "INPUT_OUTPUT", Direction.WEST, false));
        helper.succeedWhen(scene::tick);
    }
}
