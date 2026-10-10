package space.controlnet.ae2federation.compat;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Federation features with Advanced AE's own blocks. */
@PrefixGameTestTemplate(false)
public final class AdvancedAECompatGameTests {
    private static final String QUANTUM_CORE = "advanced_ae:quantum_core";

    private AdvancedAECompatGameTests() {
    }

    /**
     * The Nexus Press and Advanced AE's Quantum Press both press the Engineering and Logic Presses together; the middle
     * input decides which one comes out.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 100)
    public static void nexusAndQuantumPressesShareTheirPresses(GameTestHelper helper) {
        var level = helper.getLevel();
        var engineering = appeng.core.definitions.AEItems.ENGINEERING_PROCESSOR_PRESS.stack();
        var logic = appeng.core.definitions.AEItems.LOGIC_PROCESSOR_PRESS.stack();
        var nexus = appeng.blockentity.misc.InscriberRecipes.findRecipe(level,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_PEARL), engineering, logic, false);
        helper.assertTrue(nexus != null && nexus.getResultItem().is(
                space.controlnet.ae2federation.material.MaterialRegistration.NEXUS_PROCESSOR_PRESS.get()),
                "An Ender Pearl between the two presses must make the Nexus Press, not " + nexus);
        var quantum = appeng.blockentity.misc.InscriberRecipes.findRecipe(level,
                AddonCraftingScene.item("advanced_ae:shattered_singularity").getDefaultInstance(), engineering, logic, false);
        helper.assertTrue(quantum != null && quantum.getResultItem().is(AddonCraftingScene.item("advanced_ae:quantum_processor_press")),
                "A Shattered Singularity between the two presses must still make the Quantum Press, not " + quantum);
        helper.succeed();
    }

    /** The provider network's pattern sits in an Advanced Pattern Provider. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void advPatternProviderCrafting(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "advanced_ae:adv_pattern_provider", "ae2:molecular_assembler",
                List.of("ae2:1k_crafting_storage"));
        helper.succeedWhen(scene::tick);
    }

    /** The same provider runs the remote request's processing pattern in a machine. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void advPatternProviderProcessing(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "advanced_ae:adv_pattern_provider", "minecraft:chest",
                List.of("ae2:1k_crafting_storage"), true);
        helper.succeedWhen(scene::tick);
    }

    /**
     * The consumer's only CPU is a lone Quantum Computer Core, which Advanced AE runs with its own CPU logic instead of
     * AE2's; it drives the provider network's pattern.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void quantumCoreCrafting(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", "ae2:molecular_assembler",
                List.of(QUANTUM_CORE)).cpuOnCable();
        helper.succeedWhen(scene::tick);
    }

    /** The Quantum Computer Core's job is cancelled after the push, as the core's own CPU logic cancels it. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void quantumCoreCancel(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", "minecraft:chest", List.of(QUANTUM_CORE),
                true).cpuOnCable().cancellingAfterPush();
        helper.succeedWhen(scene::tick);
    }

    /** A lone Quantum Computer Core is the Federation Pattern Provider network's CPU for a job through an Endpoint. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void quantumCoreEndpoint(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new CoreCompatGameTests.Furnace(), QUANTUM_CORE);
        helper.succeedWhen(scene::tick);
    }

    /**
     * Advanced AE's providers have their own logic, not AE2's, and still run the Endpoint in Local mode: the block and
     * the cable part.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void endpointLocalAdvPatternProvider(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new CoreCompatGameTests.Furnace())
                .throughLocalProvider("advanced_ae:adv_pattern_provider");
        helper.succeedWhen(scene::tick);
    }

    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void endpointLocalAdvPatternProviderPart(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new CoreCompatGameTests.Furnace())
                .throughLocalProviderPart("advanced_ae:adv_pattern_provider_part");
        helper.succeedWhen(scene::tick);
    }


    /** The Bridge is removed while the Quantum Core runs a job through an Advanced Pattern Provider. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void quantumCoreDisconnected(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "advanced_ae:adv_pattern_provider", "minecraft:chest",
                List.of(QUANTUM_CORE), true).cpuOnCable().disconnectingAfterPush();
        helper.succeedWhen(scene::tick);
    }

    /**
     * The guide's quantum lab: a network whose only CPU is a formed 7x7x7 Quantum Computer, built as Advanced AE's own
     * guide shows it and holding the only energy cell, uses a sawmill network's planks pattern and a joinery network's
     * sticks pattern on one Router. It orders eight sticks from a log, which needs both networks in turn, and four
     * planks from another log at the same time: the Quantum Computer runs both jobs at once, which a single AE2
     * crafting CPU cannot. The exercise switches the lab's Crafting rule on the joinery off: sticks leave the lab's
     * craftables, and planks stay.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "scale_36_empty", timeoutTicks = 1200)
    public static void quantumComputerLab(GameTestHelper helper) {
        var scene = new RouterCraftingScene(helper, new BlockPos(18, 1, 18));
        scene.provider("sawmill", Direction.EAST, RouterCraftingScene::placeAssemblyProvider,
                        scene.start(Direction.EAST), RouterCraftingScene::patternProvider,
                        RouterCraftingScene.planksPattern(helper))
                .provider("joinery", Direction.WEST, RouterCraftingScene::placeAssemblyProvider,
                        scene.start(Direction.WEST), RouterCraftingScene::patternProvider,
                        RouterCraftingScene.sticksPattern(helper))
                .consumer("lab", Direction.SOUTH, AdvancedAECompatGameTests::placeQuantumComputer,
                        RouterCraftingScene::formed, RouterCraftingScene.STICKS, 8, RouterCraftingScene.LOG, 1,
                        "sawmill", "joinery")
                .alsoOrdering(RouterCraftingScene.PLANKS, 4, RouterCraftingScene.LOG, 1)
                .poweredBy("lab")
                .thenSwitchingOff("lab", "joinery", RouterCraftingScene.STICKS, RouterCraftingScene.PLANKS);
        helper.succeedWhen(scene::tick);
    }

    /**
     * Advanced AE's guide Quantum Computer, 7 blocks each way from {@code start}, the middle of one bottom edge: Quantum
     * Structure on the outside, Quantum Accelerators inside, and up the middle a Multi-Threader, a Data Entangler, the
     * Quantum Core and a 256M Quantum Storage.
     */
    private static void placeQuantumComputer(GameTestHelper helper, BlockPos start, Direction outward) {
        var across = outward.getClockWise();
        for (int along = 0; along < 7; along++) {
            for (int y = 0; y < 7; y++) {
                for (int side = -3; side <= 3; side++) {
                    var shell = along == 0 || along == 6 || y == 0 || y == 6 || side == -3 || side == 3;
                    var block = shell ? "quantum_structure"
                            : along == 3 && side == 0 && y == 1 ? "quantum_multi_threader"
                            : along == 3 && side == 0 && y == 2 ? "data_entangler"
                            : along == 3 && side == 0 && y == 3 ? "quantum_core"
                            : along == 3 && side == 0 && y == 4 ? "quantum_storage_256"
                            : "quantum_accelerator";
                    helper.setBlock(start.relative(outward, along).relative(across, side).above(y),
                            AddonCraftingScene.block("advanced_ae:" + block));
                }
            }
        }
    }
}
