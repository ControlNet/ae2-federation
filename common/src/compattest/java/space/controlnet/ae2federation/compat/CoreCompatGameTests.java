package space.controlnet.ae2federation.compat;

import java.util.HashSet;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
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
}
