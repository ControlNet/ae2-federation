package space.controlnet.ae2federation.compat;

import appeng.api.config.Setting;
import appeng.api.config.YesNo;
import appeng.api.crafting.IPatternDetails;
import appeng.core.definitions.AEBlocks;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity;

/** Federation features with ExtendedAE-Plus's own blocks. */
@PrefixGameTestTemplate(false)
public final class ExtendedAEPlusCompatGameTests {
    /** Off every network of the Endpoint scene. */
    private static final BlockPos NATIVE_PROVIDER = new BlockPos(1, 1, 1);

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

    /**
     * Smart Doubling set on the Federation Pattern Provider reaches its Lanes, which refresh their patterns through
     * AE2's own updatePatterns, where ExtendedAE-Plus marks them as allowed to scale. Each Lane pattern must carry the
     * mark exactly when the same pattern in a plain AE2 Pattern Provider with Smart Doubling does: AE All Pattern
     * replaces AE2's updatePatterns outright, so with it loaded neither gets the mark. Whether a plan then uses the
     * scaled copies is the planner's choice. The job must finish exactly through the Endpoint.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 2400)
    public static void smartDoublingEndpoint(GameTestHelper helper) {
        var provider = new AtomicReference<FederationPatternProviderBlockEntity>();
        var reference = new AtomicReference<Boolean>();
        var scene = new EndpointMachineScene(helper, new CoreCompatGameTests.Furnace()).requesting(4)
                .preparingProvider(entity -> {
                    provider.set(entity);
                    entity.getConfigManager().putSetting(setting("SMART_DOUBLING"), YesNo.YES);
                })
                .checkingPlan(plan -> {
                    var lanes = provider.get();
                    // The stage retries until it passes, so the reference provider is built only once.
                    boolean nativeMarked = reference.updateAndGet(marked -> marked != null ? marked
                            : nativeMark(helper, lanes.getTerminalPatternInventory().getStackInSlot(0)));
                    helper.assertTrue(nativeMarked || ModList.get().isLoaded("aeallpattern"),
                            "ExtendedAE-Plus must mark the pattern in a plain AE2 Pattern Provider");
                    helper.assertTrue(lanes.laneCount() > 0, "The provider must have a Lane for the Endpoint");
                    for (int lane = 0; lane < lanes.laneCount(); lane++) {
                        var patterns = lanes.lane(lane).getAvailablePatterns();
                        helper.assertFalse(patterns.isEmpty(), "Lane " + lane + " must offer the pattern");
                        for (var pattern : patterns) {
                            helper.assertValueEqual(allowsScaling(pattern), nativeMarked, "Lane " + lane
                                    + "'s pattern must carry ExtendedAE-Plus' mark as a native provider's does; plan "
                                    + plan.patternTimes());
                        }
                    }
                });
        helper.succeedWhen(scene::tick);
    }

    /**
     * Without Federation in the way: a plain AE2 Pattern Provider with Smart Doubling on marks its pattern exactly when
     * AE All Pattern is not loaded. AE All Pattern cancels AE2's updatePatterns at its start and refreshes the patterns
     * itself, so ExtendedAE-Plus' mark, added at the end of that method, is never set.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 100)
    public static void smartDoublingNativeProvider(GameTestHelper helper) {
        var pattern = appeng.api.crafting.PatternDetailsHelper.encodeProcessingPattern(
                List.of(new appeng.api.stacks.GenericStack(appeng.api.stacks.AEItemKey.of(Items.COBBLESTONE), 1)),
                List.of(new appeng.api.stacks.GenericStack(appeng.api.stacks.AEItemKey.of(Items.STONE), 1)));
        var aeAllPattern = ModList.get().isLoaded("aeallpattern");
        helper.assertValueEqual(nativeMark(helper, pattern), !aeAllPattern,
                "ExtendedAE-Plus' mark on a plain AE2 Pattern Provider with AE All Pattern loaded=" + aeAllPattern);
        helper.succeed();
    }

    /** Whether a plain AE2 Pattern Provider, off any network, with Smart Doubling on, marks {@code pattern}. */
    private static boolean nativeMark(GameTestHelper helper, ItemStack pattern) {
        helper.assertFalse(pattern.isEmpty(), "The Federation Pattern Provider must hold the pattern");
        helper.setBlock(NATIVE_PROVIDER, AEBlocks.PATTERN_PROVIDER.block());
        var logic = ((PatternProviderLogicHost) helper.getBlockEntity(NATIVE_PROVIDER)).getLogic();
        logic.getConfigManager().putSetting(setting("SMART_DOUBLING"), YesNo.YES);
        helper.assertTrue(logic.getPatternInv().addItems(pattern.copy()).isEmpty(),
                "The AE2 Pattern Provider refused the pattern");
        var patterns = logic.getAvailablePatterns();
        helper.assertValueEqual(patterns.size(), 1, "The AE2 Pattern Provider must offer the pattern");
        return allowsScaling(patterns.getFirst());
    }

    /** ExtendedAE-Plus' Smart Doubling mark, which its hook on AE2's updatePatterns sets on each decoded pattern. */
    private static boolean allowsScaling(IPatternDetails pattern) {
        try {
            var aware = Class.forName("com.extendedae_plus.api.smartDoubling.ISmartDoublingAwarePattern");
            return aware.isInstance(pattern) && (boolean) aware.getMethod("eap$allowScaling").invoke(pattern);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("ExtendedAE-Plus has no Smart Doubling pattern mark", exception);
        }
    }

    private static boolean scaled(ICraftingPlan plan) {
        return plan.patternTimes().keySet().stream()
                .anyMatch(pattern -> pattern.getClass().getSimpleName().startsWith("Scaled"));
    }

    /** Turns on one of ExtendedAE-Plus' provider settings, as the provider's screen does. */
    private static void enable(BlockEntity entity, String setting) {
        ((PatternProviderLogicHost) entity).getLogic().getConfigManager().putSetting(setting(setting), YesNo.YES);
    }

    @SuppressWarnings("unchecked")
    private static Setting<YesNo> setting(String name) {
        try {
            return (Setting<YesNo>) Class.forName("com.extendedae_plus.api.config.EAPSettings").getField(name).get(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("ExtendedAE-Plus has no provider setting " + name, exception);
        }
    }

    /** The provider network's storage is ExtendedAE-Plus' BigInteger cell, whose counts exceed a long. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void bigIntegerCellShared(GameTestHelper helper) {
        var scene = new AddonStorageScene(helper, "extendedae_plus:infinity_biginteger_cell");
        helper.succeedWhen(scene::tick);
    }
}
