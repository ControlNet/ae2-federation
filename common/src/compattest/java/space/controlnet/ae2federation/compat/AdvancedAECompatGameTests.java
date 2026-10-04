package space.controlnet.ae2federation.compat;

import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Federation features with Advanced AE's own blocks. */
@PrefixGameTestTemplate(false)
public final class AdvancedAECompatGameTests {
    private AdvancedAECompatGameTests() {
    }

    /** The provider network's pattern sits in an Advanced Pattern Provider. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void advPatternProviderCrafting(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "advanced_ae:adv_pattern_provider", "ae2:molecular_assembler",
                List.of("ae2:1k_crafting_storage"));
        helper.succeedWhen(scene::tick);
    }
}
