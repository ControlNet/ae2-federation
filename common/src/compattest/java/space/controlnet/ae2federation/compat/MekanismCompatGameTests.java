package space.controlnet.ae2federation.compat;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Federation processing through Mekanism's own machines, powered as players power them. */
@PrefixGameTestTemplate(false)
public final class MekanismCompatGameTests {
    private MekanismCompatGameTests() {
    }

    /**
     * The Federation Pattern Provider sends cobblestone through an Endpoint into a Crusher that a Creative Energy Cube
     * powers, and a hopper under the Crusher pushes the gravel back into the Endpoint.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void endpointCrusher(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new Crusher());
        helper.succeedWhen(scene::tick);
    }

    /**
     * A fluid comes back through the Endpoint: the Federation Pattern Provider sends apples into a Nutritional Liquifier
     * standing on the Endpoint, which ejects the nutritional paste it makes, a fluid, down into the Endpoint.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void endpointLiquifierFluid(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new Liquifier());
        helper.succeedWhen(scene::tick);
    }

    /** The same fluid return in Local mode, into the return inventory of another network's AE2 Pattern Provider. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void endpointLocalLiquifierFluid(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new Liquifier()).throughLocalProvider("ae2:pattern_provider");
        helper.succeedWhen(scene::tick);
    }

    /**
     * The consumer requests gravel; the provider network's AE2 Pattern Provider pushes cobblestone into a Crusher,
     * which ejects the gravel back into the provider, as a player sets its side to input and output.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void crusherProcessing(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", List.of("ae2:1k_crafting_storage"),
                new ProviderCrusher());
        helper.succeedWhen(scene::tick);
    }

    /** A Crusher east of the provider, taking and returning items on its west side, powered from its east. */
    private static final class ProviderCrusher implements AddonCraftingScene.Machine {
        @Override
        public String blockId() {
            return "mekanism:crusher";
        }

        @Override
        public AEItemKey output() {
            return AEItemKey.of(Items.GRAVEL);
        }

        @Override
        public void placeAround(GameTestHelper helper, BlockPos position) {
            var cube = position.east();
            helper.setBlock(cube, AddonCraftingScene.block("mekanism:creative_energy_cube"));
            MekanismSetup.fill(helper, cube);
            MekanismSetup.configure(helper, cube, "ENERGY", "OUTPUT", Direction.WEST, true);
            MekanismSetup.configure(helper, position, "ITEM", "INPUT_OUTPUT", Direction.WEST, true);
            MekanismSetup.configure(helper, position, "ENERGY", "INPUT", Direction.EAST, false);
        }

        /** Nothing to move: the Crusher ejects into the provider by itself. */
        @Override
        public void collect(GameTestHelper helper, BlockPos position, MEStorage network) {
        }
    }

    private static final class Crusher implements EndpointMachineScene.Machine {
        @Override
        public String blockId() {
            return "mekanism:crusher";
        }

        @Override
        public AEKey input() {
            return AEItemKey.of(Items.COBBLESTONE);
        }

        @Override
        public AEKey output() {
            return AEItemKey.of(Items.GRAVEL);
        }

        /**
         * A filled Creative Energy Cube beside the Crusher, ejecting energy into it; the Crusher takes items on top,
         * gives its product at the bottom and takes energy from the cube's side, as a player configures them.
         */
        @Override
        public void prepare(GameTestHelper helper, BlockPos position) {
            var cube = position.west();
            helper.setBlock(cube, AddonCraftingScene.block("mekanism:creative_energy_cube"));
            MekanismSetup.fill(helper, cube);
            MekanismSetup.configure(helper, cube, "ENERGY", "OUTPUT", Direction.EAST, true);
            MekanismSetup.configure(helper, position, "ITEM", "INPUT", Direction.UP, false);
            MekanismSetup.configure(helper, position, "ITEM", "OUTPUT", Direction.DOWN, false);
            MekanismSetup.configure(helper, position, "ENERGY", "INPUT", Direction.WEST, false);
        }

        @Override
        public String state(GameTestHelper helper, BlockPos position) {
            var level = helper.getLevel();
            var absolute = helper.absolutePos(position);
            var text = new StringBuilder();
            for (var side : net.minecraft.core.Direction.values()) {
                var items = level.getCapability(Capabilities.ItemHandler.BLOCK, absolute, side);
                var energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, absolute, side);
                text.append(side).append(":items=").append(items == null ? "-" : describe(items))
                        .append(",energy=").append(energy == null ? "-" : energy.getEnergyStored()).append(' ');
            }
            var cube = level.getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(position.west()), null);
            var entity = level.getBlockEntity(absolute);
            var cubeEntity = level.getBlockEntity(helper.absolutePos(position.west()));
            text.append("config=").append(MekanismSetup.describe(helper, position));
            return text + "cube=" + (cube == null ? "-" : cube.getEnergyStored()) + " entity="
                    + (entity == null ? null : entity.getClass().getName()) + " state=" + level.getBlockState(absolute)
                    + " cubeEntity=" + (cubeEntity == null ? null : cubeEntity.getClass().getName())
                    + " cubeState=" + level.getBlockState(helper.absolutePos(position.west()));
        }

        private static String describe(net.neoforged.neoforge.items.IItemHandler handler) {
            var text = new StringBuilder("[");
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                text.append(handler.getStackInSlot(slot)).append(slot + 1 < handler.getSlots() ? "," : "");
            }
            return text.append(']').toString();
        }
    }

    /** The provider network shares iron in a Basic Bin through a Storage Bus. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void storageBusBin(GameTestHelper helper) {
        var scene = AddonStorageScene.storageBus(helper, "ae2:storage_bus", "mekanism:basic_bin",
                AEItemKey.of(Items.IRON_INGOT), 9, 4, bus -> {
                });
        helper.succeedWhen(scene::tick);
    }

    /** The same bin under a rule without Insert: the consumer takes iron but cannot store it back. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void storageBusBinExtractOnly(GameTestHelper helper) {
        var scene = AddonStorageScene.storageBus(helper, "ae2:storage_bus", "mekanism:basic_bin",
                AEItemKey.of(Items.IRON_INGOT), 9, 4, bus -> {
                }).extractOnly();
        helper.succeedWhen(scene::tick);
    }

    /** The provider network shares water in a Basic Fluid Tank through a Storage Bus. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void storageBusMekanismFluidTank(GameTestHelper helper) {
        var scene = AddonStorageScene.storageBus(helper, "ae2:storage_bus", "mekanism:basic_fluid_tank",
                AEFluidKey.of(Fluids.WATER), 4 * AEFluidKey.AMOUNT_BUCKET, AEFluidKey.AMOUNT_BUCKET, bus -> {
                });
        helper.succeedWhen(scene::tick);
    }

    private static final class Liquifier implements EndpointMachineScene.Machine {
        @Override
        public String blockId() {
            return "mekanism:nutritional_liquifier";
        }

        @Override
        public AEKey input() {
            return AEItemKey.of(Items.APPLE);
        }

        @Override
        public AEKey output() {
            var paste = net.minecraft.core.registries.BuiltInRegistries.FLUID.get(
                    net.minecraft.resources.ResourceLocation.parse("mekanism:nutritional_paste"));
            return AEFluidKey.of(paste);
        }

        @Override
        public String outputCell() {
            return "ae2:fluid_storage_cell_1k";
        }

        /** An apple feeds 4, and the Liquifier makes 50 mB of paste per point. */
        @Override
        public long outputPerInput() {
            return 200;
        }

        @Override
        public boolean ejectsIntoEndpoint() {
            return true;
        }

        @Override
        public Direction inputFace() {
            return Direction.EAST;
        }

        /** Apples in on the Storage Bus side, paste ejected down into the Endpoint, energy from the cube behind. */
        @Override
        public void prepare(GameTestHelper helper, BlockPos position) {
            var cube = position.west();
            helper.setBlock(cube, AddonCraftingScene.block("mekanism:creative_energy_cube"));
            MekanismSetup.fill(helper, cube);
            MekanismSetup.configure(helper, cube, "ENERGY", "OUTPUT", Direction.EAST, true);
            MekanismSetup.configure(helper, position, "ITEM", "INPUT", Direction.EAST, false);
            MekanismSetup.configure(helper, position, "FLUID", "OUTPUT", Direction.DOWN, true);
            MekanismSetup.configure(helper, position, "ENERGY", "INPUT", Direction.WEST, false);
        }

        @Override
        public String state(GameTestHelper helper, BlockPos position) {
            var fluids = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                    helper.absolutePos(position.below()), Direction.UP);
            return MekanismSetup.describe(helper, position) + " endpointFluidHandler=" + fluids;
        }
    }

}
