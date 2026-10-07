package space.controlnet.ae2federation.compat;

import appeng.api.inventories.InternalInventory;
import appeng.helpers.patternprovider.PatternContainer;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Federation features with UselessMod's own blocks. */
@PrefixGameTestTemplate(false)
public final class UselessModCompatGameTests {
    private static final String FURNACE = "useless_mod:advanced_alloy_furnace_block";

    private UselessModCompatGameTests() {
    }

    /**
     * The provider network's pattern sits in an Advanced Alloy Furnace, which is its own crafting provider and crafts
     * the pattern itself, with no Molecular Assembler; UselessMod also wraps every push of the consumer's CPU.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void alloyFurnaceCrafting(GameTestHelper helper) {
        var furnace = AddonCraftingScene.block(FURNACE);
        var scene = AddonCraftingScene.structure(helper, FURNACE,
                List.of("ae2:1k_crafting_storage"),
                place -> place.setBlock(AddonCraftingScene.PROVIDER, furnace), AddonCraftingScene.PROVIDER,
                entity -> entity != null && entity.getBlockState().is(furnace));
        helper.succeedWhen(scene::tick);
    }

    /**
     * The guide's example: network B is the furnace alone, with no storage and no power of its own, and network A
     * orders from it. Taking the pattern out of the furnace takes the recipe away from A; putting it back brings it
     * back, and A orders again.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void alloyFurnaceWorkshop(GameTestHelper helper) {
        var furnace = AddonCraftingScene.block(FURNACE);
        var taken = new ItemStack[] {ItemStack.EMPTY};
        var scene = AddonCraftingScene.structure(helper, FURNACE,
                List.of("ae2:1k_crafting_storage"),
                place -> place.setBlock(AddonCraftingScene.PROVIDER, furnace), AddonCraftingScene.PROVIDER,
                entity -> entity != null && entity.getBlockState().is(furnace))
                .providerWithoutStorage()
                .reorderingAfterwards(
                        change -> {
                            var patterns = patterns(change);
                            for (int slot = 0; slot < patterns.size() && taken[0].isEmpty(); slot++) {
                                taken[0] = patterns.extractItem(slot, 1, false);
                            }
                            change.assertFalse(taken[0].isEmpty(), "The furnace must give its pattern back");
                        },
                        restore -> restore.assertTrue(patterns(restore).addItems(taken[0]).isEmpty(),
                                "The furnace must take its pattern back"),
                        (order, cpu, provider) -> {
                        });
        helper.succeedWhen(scene::tick);
    }

    private static InternalInventory patterns(GameTestHelper helper) {
        return ((PatternContainer) helper.getLevel().getBlockEntity(helper.absolutePos(AddonCraftingScene.PROVIDER)))
                .getTerminalPatternInventory();
    }
}
