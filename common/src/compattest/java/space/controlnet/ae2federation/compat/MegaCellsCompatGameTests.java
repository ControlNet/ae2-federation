package space.controlnet.ae2federation.compat;

import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Federation features with MEGA Cells' own blocks and cells. */
@PrefixGameTestTemplate(false)
public final class MegaCellsCompatGameTests {
    private MegaCellsCompatGameTests() {
    }

    /** The provider network's processing pattern sits in a MEGA Pattern Provider, which takes no other kind. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void megaPatternProviderProcessing(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "megacells:mega_pattern_provider", "minecraft:chest",
                List.of("ae2:1k_crafting_storage"), true);
        helper.succeedWhen(scene::tick);
    }

    /** The consumer's CPU is a MEGA 1M Crafting Storage. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void megaCellsCraftingStorageCrafting(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", "ae2:molecular_assembler",
                List.of("megacells:1m_crafting_storage"));
        helper.succeedWhen(scene::tick);
    }

    /** The provider network's storage is a MEGA 1M ME Storage Cell. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void megaCellsItemCellShared(GameTestHelper helper) {
        var scene = new AddonStorageScene(helper, "megacells:item_storage_cell_1m");
        helper.succeedWhen(scene::tick);
    }
}
