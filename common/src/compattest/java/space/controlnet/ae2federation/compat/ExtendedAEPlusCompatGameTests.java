package space.controlnet.ae2federation.compat;

import appeng.api.config.Setting;
import appeng.api.config.YesNo;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.entity.BlockEntity;
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

    /**
     * The provider network's AE2 Pattern Provider has Smart Doubling on, so a plan runs the pattern as ExtendedAE-Plus'
     * scaled copies, fewer and larger. The consumer's plan must scale exactly when the provider network's own plan
     * does (other mods' planners can turn scaling off), the provider must take the copies and the job must finish
     * exactly.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void smartDoublingProcessing(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", "minecraft:chest",
                List.of("ae2:1k_crafting_storage"), true).requesting(16)
                .preparingProvider(entity -> enable(entity, "SMART_DOUBLING"))
                .checkingPlan((plan, local) -> helper.assertTrue(scaled(plan) == scaled(local),
                        "The consumer's plan must scale as the provider network's does: consumer " + plan.patternTimes()
                                + ", provider network " + local.patternTimes()));
        helper.succeedWhen(scene::tick);
    }

    private static boolean scaled(ICraftingPlan plan) {
        return plan.patternTimes().keySet().stream()
                .anyMatch(pattern -> pattern.getClass().getSimpleName().startsWith("Scaled"));
    }

    /** Turns on one of ExtendedAE-Plus' provider settings, as the provider's screen does. */
    private static void enable(BlockEntity entity, String setting) {
        try {
            @SuppressWarnings("unchecked")
            var key = (Setting<YesNo>) Class.forName("com.extendedae_plus.api.config.EAPSettings").getField(setting)
                    .get(null);
            ((PatternProviderLogicHost) entity).getLogic().getConfigManager().putSetting(key, YesNo.YES);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("ExtendedAE-Plus has no provider setting " + setting, exception);
        }
    }

    /** The provider network's storage is ExtendedAE-Plus' BigInteger cell, whose counts exceed a long. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void bigIntegerCellShared(GameTestHelper helper) {
        var scene = new AddonStorageScene(helper, "extendedae_plus:infinity_biginteger_cell");
        helper.succeedWhen(scene::tick);
    }
}
