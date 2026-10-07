package space.controlnet.ae2federation.compat;

import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
}
