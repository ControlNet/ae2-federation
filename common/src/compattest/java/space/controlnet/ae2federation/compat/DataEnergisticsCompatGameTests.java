package space.controlnet.ae2federation.compat;

import appeng.core.definitions.AEBlocks;
import appeng.util.inv.AppEngInternalInventory;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Federation features with Data Energistics' own blocks. */
@PrefixGameTestTemplate(false)
public final class DataEnergisticsCompatGameTests {
    private static final String ADAPTIVE = "data_energistics:adaptive_pattern_provider";

    private DataEnergisticsCompatGameTests() {
    }

    /** The guide's Data Energistics example; see {@link SolarObservatoryScene}. */
    @GameTest(templateNamespace = "ae2federation_test", template = "scale_36_empty", timeoutTicks = 1200)
    public static void solarObservatory(GameTestHelper helper) {
        var scene = new SolarObservatoryScene(helper, new net.minecraft.core.BlockPos(18, 1, 18));
        helper.succeedWhen(scene::tick);
    }

    /** Data Energistics' Adaptive Pattern Provider, holding an AE2 Pattern Provider, serves the remote craft. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void adaptivePatternProviderCrafting(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, ADAPTIVE, "ae2:molecular_assembler",
                List.of("ae2:1k_crafting_storage")).preparingProvider(entity -> fitPatternProvider(helper, entity));
        helper.succeedWhen(scene::tick);
    }

    /**
     * The guide's Data Energistics example and its exercise: after the job, the provider network's AE2 Pattern Provider
     * is upgraded in place with an Adaptive Pattern Provider Upgrade, as its player does. With no provider fitted, the
     * Adaptive Pattern Provider offers no patterns, so its recipe leaves the consumer. Once an AE2 Pattern Provider is
     * fitted, the kept pattern returns and the consumer's next order runs through it.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void adaptiveUpgradeInPlace(GameTestHelper helper) {
        var provider = AddonCraftingScene.PROVIDER;
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", "ae2:molecular_assembler",
                List.of("ae2:1k_crafting_storage")).reorderingAfterwards(test -> {
                    // Sneaking, so the item is used instead of the provider's screen opening.
                    var player = test.makeMockPlayer(GameType.SURVIVAL);
                    player.setShiftKeyDown(true);
                    var stack = new ItemStack(AddonCraftingScene.item("data_energistics:adaptive_pattern_provider_upgrade"));
                    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                    var absolute = test.absolutePos(provider);
                    var hit = new BlockHitResult(Vec3.atCenterOf(absolute).relative(Direction.UP, 0.5), Direction.UP,
                            absolute, false);
                    var result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
                    test.assertTrue(result.consumesAction(), "The upgrade must take the AE2 Pattern Provider: " + result);
                    test.assertTrue(test.getBlockState(provider).is(AddonCraftingScene.block(ADAPTIVE)),
                            "The upgrade must leave an Adaptive Pattern Provider, not " + test.getBlockState(provider));
                }, test -> fitPatternProvider(test, test.getLevel().getBlockEntity(test.absolutePos(provider))),
                (order, cpu, network) -> helper.assertTrue(helper.getBlockState(provider)
                        .is(AddonCraftingScene.block(order == 1 ? "ae2:pattern_provider" : ADAPTIVE)),
                        "Order " + order + " must run through " + helper.getBlockState(provider)));
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

    /** Data Energistics' Adaptive Pattern Provider runs the Endpoint in Local mode once an AE2 provider is fitted. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void endpointLocalAdaptivePatternProvider(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new CoreCompatGameTests.Furnace()).throughLocalProvider(ADAPTIVE)
                .installingLocalPatternWith((provider, pattern) -> {
                    fitPatternProvider(helper, (BlockEntity) provider);
                    return ((appeng.helpers.patternprovider.PatternContainer) provider).getTerminalPatternInventory()
                            .addItems(pattern).isEmpty();
                });
        helper.succeedWhen(scene::tick);
    }

}
