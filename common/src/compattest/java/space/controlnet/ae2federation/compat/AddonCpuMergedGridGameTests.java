package space.controlnet.ae2federation.compat;

import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * An independent addon CPU waits for a projected output while only the Bridge part is removed, so the fluix cable under
 * it joins both networks into one AE2 Grid. The output reaches the addon CPU natively, through AE2's crafting service,
 * which each addon extends to feed its own CPUs; Federation reads what all CPUs wait for around that call, so the
 * receipt settles the debt, and after the split the next output the provider network stores stays its own. The class
 * spans several addons, so each profile names its addon's test by id, with the AE2 CPU control beside it.
 */
@PrefixGameTestTemplate(false)
public final class AddonCpuMergedGridGameTests {
    private AddonCpuMergedGridGameTests() {
    }

    /** AE2 Lightning Tech's Pigmee Mental Math Unit (profile {@code ae2-lightning-tech}). */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void mentalMathUnitMergedFluidReturn(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", List.of("ae2lt:pigmee_mentalmath_unit"),
                AddonCraftingScene.lavaMachine()).craftingOnly().outputStoredOnConsumer().mergingAfterPush();
        helper.succeedWhen(scene::tick);
    }

    /** Advanced AE's lone Quantum Computer Core (profile {@code advanced-ae}). */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void quantumCoreMergedFluidReturn(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", List.of("advanced_ae:quantum_core"),
                AddonCraftingScene.lavaMachine()).cpuOnCable().craftingOnly().outputStoredOnConsumer()
                .mergingAfterPush();
        helper.succeedWhen(scene::tick);
    }

    /** Neo ECO's computation system (profile {@code neoecoae}). */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void computationSystemMergedFluidReturn(GameTestHelper helper) {
        var tier = NeoEcoCompatGameTests.Tier.L4;
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", List.of(), AddonCraftingScene.lavaMachine())
                .consumerCpuStructure(test -> NeoEcoCompatGameTests.placeComputationSystem(test, tier),
                        () -> NeoEcoCompatGameTests.fitComputationCell(helper, tier))
                .craftingOnly().outputStoredOnConsumer().mergingAfterPush();
        helper.succeedWhen(scene::tick);
    }

    /** Control: the same scene with AE2's own crafting CPU, beside the addon's hooks into the crafting service. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void nativeCpuMergedFluidReturn(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", List.of("ae2:1k_crafting_storage"),
                AddonCraftingScene.lavaMachine()).craftingOnly().outputStoredOnConsumer().mergingAfterPush();
        helper.succeedWhen(scene::tick);
    }
}
