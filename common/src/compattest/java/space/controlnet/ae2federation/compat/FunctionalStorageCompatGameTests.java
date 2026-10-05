package space.controlnet.ae2federation.compat;

import appeng.api.stacks.AEItemKey;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Storage sharing through AE2's Storage Bus on Functional Storage's drawers. */
@PrefixGameTestTemplate(false)
public final class FunctionalStorageCompatGameTests {
    private FunctionalStorageCompatGameTests() {
    }

    /** A Functional Storage oak drawer, which takes one item type. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void storageBusFunctionalDrawer(GameTestHelper helper) {
        var scene = AddonStorageScene.storageBus(helper, "ae2:storage_bus", "functionalstorage:oak_1",
                AEItemKey.of(Items.IRON_INGOT), 9, 4, bus -> {
                });
        helper.succeedWhen(scene::tick);
    }
}
