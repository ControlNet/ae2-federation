package space.controlnet.ae2federation.compat;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.cells.ICellWorkbenchItem;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
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

    /** A MEGA Pattern Provider runs an Endpoint in Local mode, as AE2's own provider does. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void endpointLocalMegaPatternProvider(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new CoreCompatGameTests.Furnace())
                .throughLocalProvider("megacells:mega_pattern_provider");
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

    /** A MEGA Bulk Item Cell, partitioned to iron as its player does in the Cell Workbench. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void megaBulkCellShared(GameTestHelper helper) {
        var cell = new ItemStack(AddonCraftingScene.item("megacells:bulk_item_cell"));
        var iron = AEItemKey.of(Items.IRON_INGOT);
        helper.assertTrue(cell.getItem() instanceof ICellWorkbenchItem, "The Bulk Item Cell has no workbench config");
        ((ICellWorkbenchItem) cell.getItem()).getConfigInventory(cell).setStack(0, new GenericStack(iron, 1));
        var scene = new AddonStorageScene(helper, cell, iron, 9, 4);
        helper.succeedWhen(scene::tick);
    }

    /** A MEGA 1M Fluid Storage Cell holding water. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void megaFluidCellShared(GameTestHelper helper) {
        var scene = new AddonStorageScene(helper, "megacells:fluid_storage_cell_1m", AEFluidKey.of(Fluids.WATER),
                4 * AEFluidKey.AMOUNT_BUCKET, AEFluidKey.AMOUNT_BUCKET);
        helper.succeedWhen(scene::tick);
    }
}
