package space.controlnet.ae2federation.compat;

import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Federation features with OmniSequence's own blocks, which batch crafting jobs. */
@PrefixGameTestTemplate(false)
public final class OmniSequenceCompatGameTests {
    private static final String NEXUS = "molecularmanipulator:transfinite_compute_nexus";

    private OmniSequenceCompatGameTests() {
    }

    /** The consumer's only CPU is a Transfinite Compute Nexus, whose virtual CPUs drive the provider's pattern. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void nexusCrafting(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", "ae2:molecular_assembler", List.of(NEXUS));
        helper.succeedWhen(scene::tick);
    }

    /** The Transfinite Compute Nexus' job is cancelled after the push. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void nexusCancel(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", "minecraft:chest", List.of(NEXUS), true)
                .cancellingAfterPush();
        helper.succeedWhen(scene::tick);
    }

    /** A Transfinite Compute Nexus is the Federation Pattern Provider network's CPU for a job through an Endpoint. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void nexusEndpoint(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new CoreCompatGameTests.Furnace(), NEXUS);
        helper.succeedWhen(scene::tick);
    }

    /**
     * The provider network's pattern sits in a Molecular Sequence Rewrite Array, which crafts inside itself in batches
     * instead of pushing to an assembler.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void molecularManipulatorCrafting(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "molecularmanipulator:molecular_manipulator", "minecraft:air",
                List.of("ae2:1k_crafting_storage"));
        helper.succeedWhen(scene::tick);
    }
}
