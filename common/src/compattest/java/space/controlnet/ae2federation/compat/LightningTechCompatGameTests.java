package space.controlnet.ae2federation.compat;

import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Federation features with AE2 Lightning Tech's own blocks. */
@PrefixGameTestTemplate(false)
public final class LightningTechCompatGameTests {
    private static final String MENTAL_MATH_UNIT = "ae2lt:pigmee_mentalmath_unit";

    private LightningTechCompatGameTests() {
    }

    /** AE2 Lightning Tech's Overloaded Pattern Provider serves the remote craft. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void overloadedPatternProviderCrafting(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2lt:overloaded_pattern_provider", "ae2:molecular_assembler", List.of("ae2:1k_crafting_storage"));
        helper.succeedWhen(scene::tick);
    }

    /** The same provider runs the remote request's processing pattern in a machine. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void overloadedPatternProviderProcessing(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2lt:overloaded_pattern_provider", "minecraft:chest",
                List.of("ae2:1k_crafting_storage"), true);
        helper.succeedWhen(scene::tick);
    }

    /** The Pigmee Pattern Provider, which has its own provider logic instead of AE2's, serves the remote craft. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void pigmeePatternProviderCrafting(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2lt:pigmee_pattern_provider", "ae2:molecular_assembler",
                List.of("ae2:1k_crafting_storage"));
        helper.succeedWhen(scene::tick);
    }

    /** The consumer's only CPU is a Pigmee Mental Math Unit, a one-block CPU that Thunderbolt runs, not AE2. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void mentalMathUnitCrafting(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", "ae2:molecular_assembler",
                List.of(MENTAL_MATH_UNIT));
        helper.succeedWhen(scene::tick);
    }

    /** The Pigmee Mental Math Unit's job is cancelled after the push. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void mentalMathUnitCancel(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", "minecraft:chest",
                List.of(MENTAL_MATH_UNIT), true).cancellingAfterPush();
        helper.succeedWhen(scene::tick);
    }

    /** A Pigmee Mental Math Unit is the Federation Pattern Provider network's CPU for a job through an Endpoint. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void mentalMathUnitEndpoint(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new CoreCompatGameTests.Furnace(), MENTAL_MATH_UNIT);
        helper.succeedWhen(scene::tick);
    }

    /**
     * The Pigmee provider is its own crafting provider with no AE2 logic, and the Overloaded one extends AE2's: both run
     * the Endpoint in Local mode.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void endpointLocalPigmeePatternProvider(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new CoreCompatGameTests.Furnace())
                .throughLocalProvider("ae2lt:pigmee_pattern_provider");
        helper.succeedWhen(scene::tick);
    }

    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void endpointLocalOverloadedPatternProvider(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new CoreCompatGameTests.Furnace())
                .throughLocalProvider("ae2lt:overloaded_pattern_provider");
        helper.succeedWhen(scene::tick);
    }


    /** The Bridge is removed while the Pigmee Mental Math Unit runs a job through the Pigmee provider. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void mentalMathUnitDisconnected(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2lt:pigmee_pattern_provider", "minecraft:chest",
                List.of(MENTAL_MATH_UNIT), true).disconnectingAfterPush();
        helper.succeedWhen(scene::tick);
    }

}
