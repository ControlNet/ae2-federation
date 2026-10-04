package space.controlnet.ae2federation.compat;

import appeng.api.stacks.AEItemKey;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
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

    /** ExtendedAE's Tag Storage Bus, filtered to iron ingots, shares a chest. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void tagStorageBus(GameTestHelper helper) {
        var scene = AddonStorageScene.storageBus(helper, "extendedae:tag_storage_bus", "minecraft:chest",
                AEItemKey.of(Items.IRON_INGOT), 9, 4, bus -> invoke(bus, "setTagFilter",
                        new Class<?>[] { String.class, boolean.class }, "c:ingots/iron", true));
        helper.succeedWhen(scene::tick);
    }

    /** ExtendedAE's Mod Storage Bus, filtered to Minecraft's items, shares a chest. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void modStorageBus(GameTestHelper helper) {
        var scene = AddonStorageScene.storageBus(helper, "extendedae:mod_storage_bus", "minecraft:chest",
                AEItemKey.of(Items.IRON_INGOT), 9, 4, bus -> invoke(bus, "setModNameFilter",
                        new Class<?>[] { String.class }, "minecraft"));
        helper.succeedWhen(scene::tick);
    }

    /** ExtendedAE's creative Infinity Cobblestone Cell: the consumer sees and takes endless cobblestone. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void infinityCellShared(GameTestHelper helper) {
        var scene = new AddonStorageScene(helper, "extendedae:infinity_cobblestone_cell",
                AEItemKey.of(Items.COBBLESTONE), 0, 64).infinite();
        helper.succeedWhen(scene::tick);
    }

    /** Sets a bus part's filter as its screen does. */
    private static void invoke(Object target, String name, Class<?>[] types, Object... arguments) {
        try {
            target.getClass().getMethod(name, types).invoke(target, arguments);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(target + " has no " + name, exception);
        }
    }
}
