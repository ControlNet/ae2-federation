package space.controlnet.ae2federation.compat;

import appeng.core.definitions.AEBlocks;
import appeng.util.inv.AppEngInternalInventory;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Federation features with Data Energistics' own blocks. */
@PrefixGameTestTemplate(false)
public final class DataEnergisticsCompatGameTests {
    private static final String ADAPTIVE = "data_energistics:adaptive_pattern_provider";

    private DataEnergisticsCompatGameTests() {
    }

    /** Data Energistics' Adaptive Pattern Provider, holding an AE2 Pattern Provider, serves the remote craft. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void adaptivePatternProviderCrafting(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, ADAPTIVE, "ae2:molecular_assembler",
                List.of("ae2:1k_crafting_storage")).preparingProvider(entity -> fitPatternProvider(helper, entity));
        helper.succeedWhen(scene::tick);
    }

    /** The same provider runs the remote request's processing pattern in a machine. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void adaptivePatternProviderProcessing(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, ADAPTIVE, "minecraft:chest", List.of("ae2:1k_crafting_storage"), true)
                .preparingProvider(entity -> fitPatternProvider(helper, entity));
        helper.succeedWhen(scene::tick);
    }

    /**
     * An Adaptive Pattern Provider has pattern slots only for the providers fitted into it, which a player does in its
     * screen. This loads one AE2 Pattern Provider into its provider slot through its saved data.
     */
    private static void fitPatternProvider(GameTestHelper helper, BlockEntity entity) {
        var registries = helper.getLevel().registryAccess();
        var slot = new AppEngInternalInventory(1);
        slot.setItemDirect(0, AEBlocks.PATTERN_PROVIDER.stack());
        var tag = entity.saveWithoutMetadata(registries);
        slot.writeToNBT(tag, "provider_slot", registries);
        entity.loadWithComponents(tag, registries);
    }
}
