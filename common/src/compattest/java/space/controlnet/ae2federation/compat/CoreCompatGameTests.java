package space.controlnet.ae2federation.compat;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.bridge.BridgeRegistration;
import space.controlnet.ae2federation.material.MaterialRegistration;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.test.EndpointFederationFaceGameTests;
import space.controlnet.ae2federation.test.PatternProjectionGameTests;
import space.controlnet.ae2federation.test.ProductionProviderGameTests;

/**
 * What every profile checks: Federation can be crafted, and its features work with the profile's mods loaded. Most
 * scenes are the development GameTests' own, run here without the development test mod's mixins.
 */
@PrefixGameTestTemplate(false)
public final class CoreCompatGameTests {
    private static final String TEMPLATES = "ae2federation_test";

    private CoreCompatGameTests() {
    }

    /** A pack's recipe scripts may change Federation recipes, but every device must still have one. */
    @GameTest(templateNamespace = TEMPLATES, template = "harness_native_smoke", timeoutTicks = 100)
    public static void federationCraftable(GameTestHelper helper) {
        var level = helper.getLevel();
        var made = new HashSet<Item>();
        for (var holder : level.getRecipeManager().getRecipes()) {
            made.add(holder.value().getResultItem(level.registryAccess()).getItem());
        }
        var missing = List.of(MaterialRegistration.FEDERATION_LOGIC_PROCESSOR.get(), BridgeRegistration.BRIDGE.get(),
                        RouterRegistration.ROUTER_ITEM.get(), RouterRegistration.FEDERATION_CABLE_ITEM.get(),
                        ProcessingRegistration.PROVIDER_ITEM.get(), ProcessingRegistration.ENDPOINT_ITEM.get())
                .stream().filter(item -> !made.contains(item)).map(Item::toString).toList();
        helper.assertTrue(missing.isEmpty(), "No recipe makes " + missing);
        helper.succeed();
    }

    /** Two networks joined by Routers and Federation Cable share storage through a Storage rule. */
    @GameTest(templateNamespace = TEMPLATES, template = "harness_native_smoke", timeoutTicks = 400)
    public static void storageShare(GameTestHelper helper) {
        var scene = new AddonStorageScene(helper, "ae2:item_storage_cell_1k");
        helper.succeedWhen(scene::tick);
    }

    /** A rule that allows viewing and taking but not storing: the consumer's store-back is refused. */
    @GameTest(templateNamespace = TEMPLATES, template = "harness_native_smoke", timeoutTicks = 400)
    public static void extractOnlyRule(GameTestHelper helper) {
        var scene = new AddonStorageScene(helper, "ae2:item_storage_cell_1k").extractOnly();
        helper.succeedWhen(scene::tick);
    }

    /** The consumer's own CPU crafts sticks with the provider network's Molecular Assembler pattern. */
    @GameTest(templateNamespace = TEMPLATES, template = "harness_native_smoke", timeoutTicks = 600)
    public static void remoteCrafting(GameTestHelper helper) {
        PatternProjectionGameTests.projectionRequest(helper);
    }

    /** The same craft with the materials only in the provider network's storage. */
    @GameTest(templateNamespace = TEMPLATES, template = "harness_native_smoke", timeoutTicks = 600)
    public static void remoteCraftingMaterials(GameTestHelper helper) {
        PatternProjectionGameTests.projectionRemoteMaterials(helper);
    }

    /** One Federation Pattern Provider runs four processing jobs through three Endpoints, with Blocking mode. */
    @GameTest(templateNamespace = TEMPLATES, template = "harness_native_smoke", timeoutTicks = 1200)
    public static void processingThreeEndpoints(GameTestHelper helper) {
        ProductionProviderGameTests.productionProviderThreeWay(helper);
    }

    /** A claimed Endpoint powers its subnet from the Provider's network, and stops when told to or released. */
    @GameTest(templateNamespace = TEMPLATES, template = "harness_native_smoke", timeoutTicks = 600)
    public static void endpointSharedEnergy(GameTestHelper helper) {
        EndpointFederationFaceGameTests.endpointSubnetEnergy(helper);
    }

    /**
     * The Federation Pattern Provider runs a processing job through an Endpoint into a vanilla furnace and gets the
     * smelted stone back through a hopper: the control for every mod machine's variant of it.
     */
    @GameTest(templateNamespace = TEMPLATES, template = "harness_native_smoke", timeoutTicks = 900)
    public static void endpointFurnace(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new Furnace());
        helper.succeedWhen(scene::tick);
    }

    /**
     * After a job through an Endpoint, AE2's Pattern Access Terminal lists the Federation Pattern Provider once, with
     * its pattern, and none of the lanes it runs inside.
     */
    @GameTest(templateNamespace = TEMPLATES, template = "harness_native_smoke", timeoutTicks = 900)
    public static void patternAccessTerminal(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new Furnace()).checkingPatternAccessTerminal();
        helper.succeedWhen(scene::tick);
    }

    /**
     * Another network's AE2 Pattern Provider touches the Endpoint's Federation face and runs it in Local mode: the same
     * furnace job without a Federation Pattern Provider. This is the control for each addon provider's variant.
     */
    @GameTest(templateNamespace = TEMPLATES, template = "harness_native_smoke", timeoutTicks = 900)
    public static void endpointLocalFurnace(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new Furnace()).throughLocalProvider("ae2:pattern_provider");
        helper.succeedWhen(scene::tick);
    }

    static final class Furnace implements EndpointMachineScene.Machine {
        @Override
        public String blockId() {
            return "minecraft:furnace";
        }

        @Override
        public AEKey input() {
            return AEItemKey.of(Items.COBBLESTONE);
        }

        @Override
        public AEKey output() {
            return AEItemKey.of(Items.STONE);
        }

        /** Fuel, as a player puts it in. */
        @Override
        public void prepare(GameTestHelper helper, BlockPos position) {
            helper.<FurnaceBlockEntity>getBlockEntity(position).setItem(1, new ItemStack(Items.COAL_BLOCK));
        }

        @Override
        public String state(GameTestHelper helper, BlockPos position) {
            var furnace = helper.<FurnaceBlockEntity>getBlockEntity(position);
            return "input=" + furnace.getItem(0) + " fuel=" + furnace.getItem(1) + " result=" + furnace.getItem(2);
        }
    }

    /**
     * The addon crafting scene with AE2's own blocks, placed as a player places them: the control for every addon's
     * variant of it.
     */
    @GameTest(templateNamespace = TEMPLATES, template = "harness_native_smoke", timeoutTicks = 600)
    public static void remoteCraftingPlacedBlocks(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", "ae2:molecular_assembler",
                List.of("ae2:1k_crafting_storage"));
        helper.succeedWhen(scene::tick);
    }

    /** The processing variant of the placed-block scene, with AE2's own Pattern Provider: its control. */
    @GameTest(templateNamespace = TEMPLATES, template = "harness_native_smoke", timeoutTicks = 600)
    public static void remoteProcessingPlacedBlocks(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", "minecraft:chest",
                List.of("ae2:1k_crafting_storage"), true);
        helper.succeedWhen(scene::tick);
    }

    /**
     * The consumer cancels the job after the provider pushed its inputs: the late output stays on the provider network.
     * This is the control for each addon CPU's variant.
     */
    @GameTest(templateNamespace = TEMPLATES, template = "harness_native_smoke", timeoutTicks = 600)
    public static void remoteProcessingCancel(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", "minecraft:chest",
                List.of("ae2:1k_crafting_storage"), true).cancellingAfterPush();
        helper.succeedWhen(scene::tick);
    }

    /** The provider network shares a chest through AE2's Storage Bus: the control for every container's variant. */
    @GameTest(templateNamespace = TEMPLATES, template = "harness_native_smoke", timeoutTicks = 400)
    public static void storageBusChest(GameTestHelper helper) {
        var scene = AddonStorageScene.storageBus(helper, "ae2:storage_bus", "minecraft:chest",
                AEItemKey.of(Items.IRON_INGOT), 9, 4, bus -> {
                });
        helper.succeedWhen(scene::tick);
    }

    /** The provider network shares water in an AE2 Sky Stone Tank through a Storage Bus. */
    @GameTest(templateNamespace = TEMPLATES, template = "harness_native_smoke", timeoutTicks = 400)
    public static void storageBusTank(GameTestHelper helper) {
        var scene = AddonStorageScene.storageBus(helper, "ae2:storage_bus", "ae2:sky_stone_tank",
                AEFluidKey.of(Fluids.WATER), 4 * AEFluidKey.AMOUNT_BUCKET, AEFluidKey.AMOUNT_BUCKET, bus -> {
                });
        helper.succeedWhen(scene::tick);
    }

    /** The provider network shares water in a 1k ME Fluid Storage Cell. */
    @GameTest(templateNamespace = TEMPLATES, template = "harness_native_smoke", timeoutTicks = 400)
    public static void fluidCellShared(GameTestHelper helper) {
        var scene = new AddonStorageScene(helper, "ae2:fluid_storage_cell_1k", AEFluidKey.of(Fluids.WATER),
                4 * AEFluidKey.AMOUNT_BUCKET, AEFluidKey.AMOUNT_BUCKET);
        helper.succeedWhen(scene::tick);
    }
}
