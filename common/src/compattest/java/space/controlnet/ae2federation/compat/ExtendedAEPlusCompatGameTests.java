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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity;

/** Federation features with ExtendedAE-Plus's own blocks. */
@PrefixGameTestTemplate(false)
public final class ExtendedAEPlusCompatGameTests {
    /** Off every network of the Endpoint scene. */
    private static final BlockPos NATIVE_PROVIDER = new BlockPos(1, 1, 1);
    private static final String ACCELERATOR = "extendedae_plus:4x_crafting_accelerator";

    private ExtendedAEPlusCompatGameTests() {
    }

    /**
     * The guide's hub example: one factory network's Super Assembler Matrix serves two districts on the same Router,
     * each ordering sticks at once on its own CPU, while the factory runs on the first district's power.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "scale_36_empty", timeoutTicks = 900)
    public static void superAssemblerMatrixHub(GameTestHelper helper) {
        var center = new BlockPos(18, 2, 18);
        var scene = new RouterCraftingScene(helper, center);
        // The matrix's frame that touches the factory's chest: the matrix's outer blocks join the network and take
        // patterns for the Hybrid Cores inside, which have no network connection of their own.
        var core = center.east(2);
        scene.provider("factory", net.minecraft.core.Direction.EAST, ExtendedAEPlusCompatGameTests::placeSuperAssemblerMatrix,
                        core, ExtendedAEPlusCompatGameTests::superMatrixFormed, RouterCraftingScene.sticksPattern(helper))
                .installingPatternsWith(ExtendedAEPlusCompatGameTests::installInSuperMatrix)
                .consumer("first district", net.minecraft.core.Direction.WEST, RouterCraftingScene::placeCpu,
                        RouterCraftingScene::formed, RouterCraftingScene.STICKS, 4, RouterCraftingScene.PLANKS, 2, "factory")
                .consumer("second district", net.minecraft.core.Direction.NORTH, RouterCraftingScene::placeCpu,
                        RouterCraftingScene::formed, RouterCraftingScene.STICKS, 4, RouterCraftingScene.PLANKS, 2, "factory");
        helper.succeedWhen(scene::tick);
    }

    /** Whether {@code entity} belongs to a formed Super Assembler Matrix, which keeps its own cluster, not AE2's. */
    private static boolean superMatrixFormed(BlockEntity entity) {
        if (entity == null) return false;
        try {
            return entity.getClass().getMethod("eap$getSuperMatrixCluster").invoke(entity) != null;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    /**
     * Puts a pattern into a formed Super Assembler Matrix as its own screen does: into the pattern slots of its Hybrid
     * Cores, which the matrix gathers.
     */
    private static boolean installInSuperMatrix(BlockEntity entity, ItemStack pattern) {
        try {
            var cluster = entity.getClass().getMethod("eap$getSuperMatrixCluster").invoke(entity);
            var inventories = (appeng.api.inventories.InternalInventory[]) cluster.getClass()
                    .getMethod("getPatternInventories").invoke(cluster);
            for (var inventory : inventories) {
                if (inventory.addItems(pattern.copy()).isEmpty()) {
                    cluster.getClass().getMethod("refreshCraftingProvider").invoke(cluster);
                    return true;
                }
            }
            return false;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("The Super Assembler Matrix's pattern slots are unavailable", exception);
        }
    }

    /**
     * The smallest Super Assembler Matrix, 3 blocks each way from {@code start}: frames on its edges, walls on its
     * faces and a Hybrid Core inside. {@code start}, touching the network's chest, is the middle of one bottom edge.
     */
    private static void placeSuperAssemblerMatrix(GameTestHelper helper, BlockPos start, net.minecraft.core.Direction outward) {
        var across = outward.getClockWise();
        for (int along = 0; along < 3; along++) {
            for (int y = 0; y < 3; y++) {
                for (int side = -1; side <= 1; side++) {
                    int outer = (along == 1 ? 0 : 1) + (y == 1 ? 0 : 1) + (side == 0 ? 0 : 1);
                    var block = outer >= 2 ? "extendedae_plus:super_assembler_matrix_frame"
                            : outer == 1 ? "extendedae_plus:super_assembler_matrix_wall"
                            : "extendedae_plus:assembler_matrix_hybrid_plus";
                    helper.setBlock(start.relative(outward, along).above(y).relative(across, side),
                            AddonCraftingScene.block(block));
                }
            }
        }
    }

    /** The consumer's CPU has an ExtendedAE-Plus 4x Crafting Accelerator. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void acceleratedCpuCrafting(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", "ae2:molecular_assembler", List.of("ae2:1k_crafting_storage", ACCELERATOR));
        helper.succeedWhen(scene::tick);
    }

    /**
     * A consumer orders from an assembly workshop: its CPU runs the order with the accelerator's four
     * co-processors. Moved to a crafting storage on the provider network, the accelerator speeds up that network's CPU
     * only, and the consumer's next order runs on its own CPU without co-processors.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void acceleratorMovedToWorkshop(GameTestHelper helper) {
        var accelerator = AddonCraftingScene.FIRST_CPU.west();
        var workshopCpu = AddonCraftingScene.PROVIDER_CABLE.above();
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", "ae2:molecular_assembler",
                List.of("ae2:1k_crafting_storage", ACCELERATOR)).reorderingAfterwards(test -> {
                    test.setBlock(accelerator, Blocks.AIR);
                    test.setBlock(workshopCpu, AEBlocks.CRAFTING_STORAGE_1K.block());
                    test.setBlock(workshopCpu.above(), AddonCraftingScene.block(ACCELERATOR));
                }, (order, cpu, provider) -> {
                    if (order == 1) {
                        helper.assertValueEqual(cpu.getCoProcessors(), 4, "The accelerated CPU's co-processors");
                        return;
                    }
                    helper.assertTrue(provider.getCraftingService().getCpus().stream()
                            .anyMatch(workshop -> workshop.getCoProcessors() == 4),
                            "The accelerator must speed up the provider network's CPU");
                    helper.assertValueEqual(cpu.getCoProcessors(), 0,
                            "The consumer's own CPU, without the accelerator, runs the order");
                });
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
