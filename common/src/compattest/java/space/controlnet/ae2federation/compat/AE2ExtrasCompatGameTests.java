package space.controlnet.ae2federation.compat;

import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Federation features with AE2 Extras's own blocks. */
@PrefixGameTestTemplate(false)
public final class AE2ExtrasCompatGameTests {
    private AE2ExtrasCompatGameTests() {
    }

    /** The consumer's CPU is AE2 Extras' 1M Crafting Storage. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void megaCraftingStorageCrafting(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", "ae2:molecular_assembler", List.of("ae2extras:1m_crafting_storage"));
        helper.succeedWhen(scene::tick);
    }

    /** The provider network's storage is an AE2 Extras 1m ME Storage Cell. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void megaStorageCellShared(GameTestHelper helper) {
        var scene = new AddonStorageScene(helper, "ae2extras:item_storage_cell_1m");
        helper.succeedWhen(scene::tick);
    }
}
