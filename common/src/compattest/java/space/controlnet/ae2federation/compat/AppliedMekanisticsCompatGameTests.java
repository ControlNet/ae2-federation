package space.controlnet.ae2federation.compat;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Federation features with Applied Mekanistics' chemical storage. */
@PrefixGameTestTemplate(false)
public final class AppliedMekanisticsCompatGameTests {
    private AppliedMekanisticsCompatGameTests() {
    }

    /** The provider network's storage is a 1k ME Chemical Storage Cell holding hydrogen. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void chemicalCellShared(GameTestHelper helper) {
        var hydrogen = AddonStorageScene.key(helper, "appmek:chemical", "mekanism:hydrogen");
        var scene = new AddonStorageScene(helper, "appmek:chemical_storage_cell_1k", hydrogen, 1000, 250);
        helper.succeedWhen(scene::tick);
    }
}
