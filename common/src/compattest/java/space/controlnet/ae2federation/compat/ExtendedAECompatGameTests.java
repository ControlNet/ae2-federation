package space.controlnet.ae2federation.compat;

import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Federation features with ExtendedAE's own blocks. */
@PrefixGameTestTemplate(false)
public final class ExtendedAECompatGameTests {
    private ExtendedAECompatGameTests() {
    }

    /** ExtendedAE's Extended Pattern Provider serves the remote craft. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void exPatternProviderCrafting(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "extendedae:ex_pattern_provider", "ae2:molecular_assembler", List.of("ae2:1k_crafting_storage"));
        helper.succeedWhen(scene::tick);
    }

    /** ExtendedAE's Extended Molecular Assembler crafts for the remote request. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void exMolecularAssemblerCrafting(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", "extendedae:ex_molecular_assembler", List.of("ae2:1k_crafting_storage"));
        helper.succeedWhen(scene::tick);
    }
}
