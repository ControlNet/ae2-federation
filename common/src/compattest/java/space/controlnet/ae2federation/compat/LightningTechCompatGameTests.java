package space.controlnet.ae2federation.compat;

import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Federation features with AE2 Lightning Tech's own blocks. */
@PrefixGameTestTemplate(false)
public final class LightningTechCompatGameTests {
    private LightningTechCompatGameTests() {
    }

    /** AE2 Lightning Tech's Overloaded Pattern Provider serves the remote craft. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void overloadedPatternProviderCrafting(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2lt:overloaded_pattern_provider", "ae2:molecular_assembler", List.of("ae2:1k_crafting_storage"));
        helper.succeedWhen(scene::tick);
    }
}
