package space.controlnet.ae2federation.compat;

import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Federation features with ExtendedAE-Plus's own blocks. */
@PrefixGameTestTemplate(false)
public final class ExtendedAEPlusCompatGameTests {
    private ExtendedAEPlusCompatGameTests() {
    }

    /** The consumer's CPU has an ExtendedAE-Plus 4x Crafting Accelerator. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void acceleratedCpuCrafting(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", "ae2:molecular_assembler", List.of("ae2:1k_crafting_storage", "extendedae_plus:4x_crafting_accelerator"));
        helper.succeedWhen(scene::tick);
    }

    /** The provider network's storage is ExtendedAE-Plus' BigInteger cell, whose counts exceed a long. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void bigIntegerCellShared(GameTestHelper helper) {
        var scene = new AddonStorageScene(helper, "extendedae_plus:infinity_biginteger_cell");
        helper.succeedWhen(scene::tick);
    }
}
