package space.controlnet.ae2federation.compat;

import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Federation features with Advanced AE's own blocks. */
@PrefixGameTestTemplate(false)
public final class AdvancedAECompatGameTests {
    private static final String QUANTUM_CORE = "advanced_ae:quantum_core";

    private AdvancedAECompatGameTests() {
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

}
