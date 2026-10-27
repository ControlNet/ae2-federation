package space.controlnet.ae2federation.compat;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.inv.AppEngInternalInventory;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.observability.statistics.StatisticsNames;
import space.controlnet.ae2federation.observability.statistics.StatisticsRange;

/** Federation features with Data Energistics' own blocks. */
@PrefixGameTestTemplate(false)
public final class DataEnergisticsCompatGameTests {
    private static final String ADAPTIVE = "data_energistics:adaptive_pattern_provider";

    private DataEnergisticsCompatGameTests() {
    }

    /**
     * Data Energistics turns any tunnel of its {@code ae2:p2p} tag back into an ME P2P tunnel by hand. The Federation P2P
     * tunnel joins that tag, as AE2's own tunnels and Applied Mekanistics' chemical tunnel do.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 100)
    public static void federationTunnelTurnsBackIntoAnMeTunnel(GameTestHelper helper) {
        var level = helper.getLevel();
        var tunnel = space.controlnet.ae2federation.p2p.FederationP2PRegistration.TUNNEL.get().getDefaultInstance();
        var p2p = net.minecraft.tags.TagKey.create(Registries.ITEM,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ae2", "p2p"));
        helper.assertTrue(tunnel.is(p2p), "The Federation P2P tunnel must be in Data Energistics' ae2:p2p tag");
        var input = net.minecraft.world.item.crafting.CraftingInput.of(1, 1, List.of(tunnel));
        var made = level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level)
                .map(holder -> holder.value().assemble(input, level.registryAccess()));
        helper.assertTrue(made.isPresent() && made.get().is(AddonCraftingScene.item("ae2:me_p2p_tunnel")),
                "A Federation P2P tunnel alone in the grid must make an ME P2P tunnel, not " + made);
        helper.succeed();
    }

    /** The guide's Data Energistics example; see {@link SolarObservatoryScene}. It sets the time of day to night. */
    @RunsAlone
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

    /**
     * Data Energistics' own crafting planner, the Trinity planner, plans with a pattern that only the provider network
     * has. The consumer's only CPU is a Trinity Data Core, built from the mod's own guide structures; its pattern cores
     * hold no pattern. A network with a formed Trinity CPU hands its calculations to the Trinity planner, so the
     * consumer's plan for four sticks must be a Trinity plan that fires the provider's pattern as often as the provider
     * network's own plan does; the Trinity CPU then pushes through the projection and the sticks are stored exactly.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "matter_fabrication_well", timeoutTicks = 1200)
    public static void trinityPlanning(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", "ae2:molecular_assembler", List.of());
        var trinity = new TrinityDataCore(helper, false);
        scene.consumerCpuStructure(test -> trinity.place(), () -> trinity.ready(scene.consumerGrid()))
                .checkingPlan((plan, local) -> {
                    helper.assertTrue(plan.getClass().getName().equals(TrinityDataCore.PLAN),
                            "The Trinity planner must make the consumer's plan, not " + TrinityDataCore.describe(plan));
                    var fired = TrinityDataCore.firings(plan);
                    var remote = TrinityDataCore.identities(helper, local);
                    helper.assertTrue(!fired.isEmpty() && fired.equals(remote),
                            "The Trinity plan must craft with the provider's own pattern as often as the provider network"
                                    + " does: " + fired.values() + " against " + remote.values());
                    trinity.assertOnlyTrinityCpus(scene.consumerGrid());
                });
        helper.succeedWhen(scene::tick);
    }

    /**
     * A projected lava job on a Trinity Data Core, merged mid-job: only the Bridge part is removed while the job waits,
     * so the fluix cable under it joins both networks into one AE2 Grid; the Trinity CPU takes the lava natively
     * through Data Energistics' own hook on the crafting service, the debt settles, and after the split the provider
     * keeps its next lava.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "matter_fabrication_well", timeoutTicks = 1200)
    public static void trinityDataCoreMergedFluidReturn(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", List.of(), AddonCraftingScene.lavaMachine());
        var trinity = new TrinityDataCore(helper, false);
        scene.consumerCpuStructure(test -> trinity.place(), () -> trinity.ready(scene.consumerGrid()))
                .checkingPlan((plan, local) -> helper.assertTrue(plan.getClass().getName().equals(TrinityDataCore.PLAN),
                        "The Trinity planner must make the consumer's plan, not " + TrinityDataCore.describe(plan)))
                .craftingOnly().outputStoredOnConsumer().mergingAfterPush();
        helper.succeedWhen(scene::tick);
    }

    /**
     * The consumer's Trinity Data Core runs sixteen runs of a projected cobblestone -> stone pattern from an AE2
     * Pattern Provider on the provider network. The Trinity CPU dispatches counted batches to providers Data Energistics can
     * batch, an AE2 Pattern Provider among them; the test records whether it can batch to the projection too and how
     * many pushes cross it, and checks every run's stone comes back.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "matter_fabrication_well", timeoutTicks = 1500)
    public static void trinityCpuSixteenRemoteRuns(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", "minecraft:chest", List.of(), true);
        var trinity = new TrinityDataCore(helper, false);
        scene.consumerCpuStructure(test -> trinity.place(), () -> trinity.ready(scene.consumerGrid()))
                .checkingPlan((plan, local) -> helper.assertTrue(plan.getClass().getName().equals(TrinityDataCore.PLAN),
                        "The Trinity planner must make the consumer's plan, not " + TrinityDataCore.describe(plan)))
                .craftingOnly().requesting(16)
                .checkingAfterJob((order, cpu, provider) -> {
                    var bridge = scene.bridge();
                    var consumer = scene.consumerGrid();
                    var stone = AEItemKey.of(Items.STONE);
                    var pattern = consumer.getCraftingService().getCraftingFor(stone).iterator().next();
                    var real = NeoEcoScaledDispatch.providers(provider, pattern);
                    var projected = NeoEcoScaledDispatch.providers(consumer, pattern);
                    helper.assertTrue(real.size() == 1 && projected.size() == 1,
                            "One provider on each network: real " + real + ", projected " + projected);
                    var statistics = space.controlnet.ae2federation.observability.LevelObservabilityService
                            .get(helper.getLevel()).statistics();
                    long now = statistics.nowSecond();
                    statistics.finishSecond(now);
                    double pushes = statistics.book().total(StatisticsNames.pair(StatisticsNames.PUSHED,
                            bridge.mainNetwork(), bridge.outerNetwork()), StatisticsRange.MINUTES, now);
                    var observed = "Trinity batches to the real provider: " + TrinityDataCore.counted(real.getFirst())
                            + ", to the projection: " + TrinityDataCore.counted(projected.getFirst())
                            + "; pushes through the projection for 16 runs: " + pushes;
                    helper.assertTrue(TrinityDataCore.counted(real.getFirst()),
                            "Data Energistics must batch to an AE2 Pattern Provider on its own network. " + observed);
                    helper.assertValueEqual(space.controlnet.ae2federation.crafting.projection.CraftingReturnLedger
                            .get(helper.getLevel()).owed(bridge.outerNetwork(), bridge.mainNetwork(), stone), 0L,
                            "Every run's stone must be returned. " + observed);
                    com.mojang.logging.LogUtils.getLogger().info("Trinity remote runs: {}", observed);
                });
        helper.succeedWhen(scene::tick);
    }

    /**
     * A Trinity Data Core's pattern core as the provider of an AE2 CPU, with both networks joined into one AE2 Grid
     * before the order. There the Trinity plans the order itself, Data Energistics refuses that plan to the AE2 CPU
     * (only Trinity CPUs take Trinity plans), and a Trinity CPU runs it. So an AE2 CPU using a Trinity pattern core is a
     * pairing only Federation makes: natively, on a Grid with the Trinity, orders through its pattern cores are planned
     * by the Trinity and run on its own CPUs.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "matter_fabrication_well", timeoutTicks = 1200)
    public static void trinityPatternCoreProviderJoined(GameTestHelper helper) {
        var trinity = new TrinityDataCore(helper, true);
        var scene = AddonCraftingScene.structure(helper, "the Trinity Data Core", List.of("ae2:1k_crafting_storage"),
                        test -> trinity.place(), TrinityDataCore.DEPOT, entity -> trinity.formed())
                .withoutPatternContainer().installingPatternsWith((entity, pattern) -> trinity.installPattern(pattern))
                .joinedBeforeOrder()
                .submittingWith((grid, plan) -> {
                    var crafting = grid.getCraftingService();
                    var nativeCpu = crafting.getCpus().stream().filter(cpu -> cpu instanceof CraftingCPUCluster)
                            .findFirst().orElseThrow();
                    var refused = crafting.submitJob(plan, null, nativeCpu, true, IActionSource.empty());
                    helper.assertFalse(refused.successful(), "The AE2 CPU must refuse the joined Grid's Trinity plan");
                    return crafting.submitJob(plan, null, null, true, IActionSource.empty());
                })
                .checkingAfterJob((order, cpu, provider) -> helper.assertTrue(TrinityDataCore.trinityCpu(cpu),
                        "A Trinity CPU must run the joined Grid's order: " + cpu));
        helper.succeedWhen(scene::tick);
    }

    /**
     * A Trinity Data Core facing north in the {@code matter_fabrication_well} template, east and south of the networks
     * of {@link AddonCraftingScene}. Its main structure and its CPU and crafting child structures are placed block by
     * block from the guide structures Data Energistics ships, the children sharing the main structure's controller. The
     * main structure's Information Exchange Depot goes where the guide shows quartz vibrant glass under the controller,
     * its cable bus spots get covered cables, and the controller goes in last, as a player builds it. A glass cable runs
     * from west of the consumer's cable, or on the provider's side from {@link AddonCraftingScene#PROVIDER}, to the
     * depot through the open channel in front of it.
     *
     * <p>The crafting child is needed even when the Trinity crafts nothing itself: until its pattern catalog is
     * active, the depot's own crafting provider reports its custody incomplete, and the Trinity CPU dispatches no
     * pattern at all, its job waiting forever with the inputs taken.
     */
    private static final class TrinityDataCore {
        static final String PLAN = "com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan";
        private static final String DIAGNOSED =
                "com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway.TrinityDiagnosedCraftingPlan";
        private static final String VIRTUAL_CPU =
                "com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu.TrinityDataCoreVirtualCpu";
        private static final String CORE = "data_energistics:trinity_data_core";
        private static final String PATTERN_CORE = "data_energistics:me_digital_pattern_processing_core";
        private static final String COUNTED = "com.fish_dan_.data_energistics.common.crafting.trinity.dispatch."
                + "provider.CountedCraftingProviderAdapters";
        /** The main structure's (0, 0, 0). */
        private static final BlockPos ORIGIN = new BlockPos(10, 1, 12);
        /** The controller in each guide structure, where the three overlap. */
        private static final BlockPos MAIN_CONTROLLER = new BlockPos(13, 2, 7);
        private static final BlockPos CPU_CONTROLLER = new BlockPos(0, 2, 0);
        private static final BlockPos CRAFTING_CONTROLLER = new BlockPos(8, 2, 0);
        private static final BlockPos CONTROLLER = ORIGIN.offset(MAIN_CONTROLLER);
        /** The main structure's replaceable compartment right under the controller. */
        private static final BlockPos DEPOT = CONTROLLER.below();

        private final GameTestHelper helper;
        /** Whether the Trinity joins the provider network instead of the consumer's. */
        private final boolean onProvider;
        /** The first pattern core of the crafting child, once placed. */
        private BlockPos patternCore;

        TrinityDataCore(GameTestHelper helper, boolean onProvider) {
            this.helper = helper;
            this.onProvider = onProvider;
        }

        /** The cable to the depot, the main structure, the two children, and then the controller. */
        void place() {
            var start = onProvider ? AddonCraftingScene.PROVIDER : AddonCraftingScene.FIRST_CPU;
            // Four blocks short of the structure in front of the depot; from there down and south in.
            var corner = new BlockPos(DEPOT.getX(), start.getY(), ORIGIN.getZ() - 4);
            int x0 = corner.getX();
            int y0 = corner.getY();
            int z0 = corner.getZ();
            if (onProvider) {
                // East along the provider network's row, then south.
                for (int x = start.getX(); x <= x0; x++) cable(new BlockPos(x, y0, start.getZ()));
                for (int z = start.getZ() + 1; z <= z0; z++) cable(new BlockPos(x0, y0, z));
            } else {
                // West of the consumer's cable, south, then east.
                for (int z = start.getZ(); z <= z0; z++) cable(new BlockPos(start.getX(), y0, z));
                for (int x = start.getX() + 1; x <= x0; x++) cable(new BlockPos(x, y0, z0));
            }
            for (int y = y0 - 1; y >= DEPOT.getY(); y--) cable(new BlockPos(x0, y, z0));
            for (int z = z0 + 1; z < DEPOT.getZ(); z++) cable(new BlockPos(x0, DEPOT.getY(), z));
            var controller = placeGuideStructure("trinity_data_core_main.snbt", ORIGIN);
            placeGuideStructure("trinity_data_core_cpu.snbt", CONTROLLER.subtract(CPU_CONTROLLER));
            placeGuideStructure("trinity_data_core_crafting.snbt", CONTROLLER.subtract(CRAFTING_CONTROLLER));
            helper.setBlock(CONTROLLER, controller);
        }

        /**
         * Places one of the mod's guide structures with its {@code (0, 0, 0)} at {@code origin}, all but its
         * controller, whose state it returns.
         */
        private BlockState placeGuideStructure(String file,
                BlockPos origin) {
            CompoundTag structure;
            try {
                structure = TagParser.parseTag(Files.readString(
                        ModList.get().getModFileById("data_energistics").getFile()
                                .findResource("assets", "data_energistics", "ae2guide", "structure", file)));
            } catch (IOException | CommandSyntaxException exception) {
                throw new IllegalStateException("Data Energistics' structure " + file + " is unreadable", exception);
            }
            var blocks = helper.getLevel().holderLookup(Registries.BLOCK);
            var palette = structure.getList("palette", Tag.TAG_COMPOUND);
            BlockState controller = null;
            for (var tag : structure.getList("blocks", Tag.TAG_COMPOUND)) {
                var entry = (CompoundTag) tag;
                var pos = entry.getList("pos", Tag.TAG_INT);
                var position = origin.offset(pos.getInt(0), pos.getInt(1), pos.getInt(2));
                var state = NbtUtils.readBlockState(blocks, palette.getCompound(entry.getInt("state")));
                if (state.is(AddonCraftingScene.block(CORE))) {
                    helper.assertTrue(position.equals(CONTROLLER), "The controller of " + file + " is not at " + CONTROLLER);
                    controller = state;
                } else if (position.equals(DEPOT)) {
                    helper.assertTrue(state.is(AddonCraftingScene.block("ae2:quartz_vibrant_glass")),
                            "The guide must show quartz vibrant glass under the controller, not " + state);
                    helper.setBlock(position, AddonCraftingScene.block("data_energistics:trinity_information_exchange_depot"));
                } else if (state.is(AddonCraftingScene.block(PATTERN_CORE))) {
                    if (patternCore == null) patternCore = position;
                    helper.setBlock(position, state);
                } else if (state.is(AddonCraftingScene.block("ae2:cable_bus"))) {
                    helper.assertTrue(PartHelper.setPart(helper.getLevel(), helper.absolutePos(position),
                            null, null, AEParts.COVERED_CABLE.item(
                                    AEColor.TRANSPARENT)) != null, "A covered cable must go at " + position);
                } else {
                    helper.setBlock(position, state);
                }
            }
            helper.assertTrue(controller != null, "The structure " + file + " has no controller");
            return controller;
        }

        /**
         * True once the Trinity is formed and the consumer network has the Trinity's CPUs and no others; until then,
         * what the controller reports.
         */
        boolean ready(IGrid consumer) {
            formed();
            assertOnlyTrinityCpus(consumer);
            return true;
        }

        /**
         * True once all three structures have formed, the depot is online and the pattern catalog is active; until
         * then, what the controller reports.
         */
        boolean formed() {
            var core = helper.getLevel().getBlockEntity(helper.absolutePos(CONTROLLER));
            helper.assertTrue(core != null && core.getBlockState().is(AddonCraftingScene.block(CORE)),
                    "No Trinity Data Core at " + CONTROLLER);
            helper.assertTrue((boolean) invoke(core, "isStructureFormed"), "Waiting for the main structure: "
                    + invoke(core, "getLastFailureReason") + " at " + invoke(core, "getLastFailurePosition"));
            helper.assertTrue((boolean) invoke(core, "isCpuStructureFormed"), "Waiting for the CPU child structure: "
                    + invoke(core, "getCpuLastFailureReason") + " at " + invoke(core, "getCpuLastFailurePosition"));
            helper.assertTrue((boolean) invoke(core, "isCraftingStructureFormed"), "Waiting for the crafting child structure: "
                    + invoke(core, "getCraftingLastFailureReason") + " at " + invoke(core, "getCraftingLastFailurePosition"));
            helper.assertTrue((boolean) invoke(core, "hasActiveInformationExchangeDepot"),
                    "Waiting for the Information Exchange Depot to come online");
            helper.assertTrue((boolean) invoke(core, "isCpuProviderAvailable"), "Waiting for the Trinity's CPUs");
            helper.assertTrue((boolean) invoke(core, "isPatternProviderAvailable"), "Waiting for the pattern catalog");
            return true;
        }

        /** Puts {@code pattern} into the first slot of the crafting child's first pattern core, as its screen does. */
        boolean installPattern(ItemStack pattern) {
            var core = helper.getLevel().getBlockEntity(helper.absolutePos(patternCore));
            try {
                return (boolean) core.getClass().getMethod("trySetPattern", int.class, ItemStack.class)
                        .invoke(core, 0, pattern);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("The pattern core at " + patternCore + " takes no pattern", exception);
            }
        }

        /** Whether a Trinity CPU can dispatch counted batches to {@code provider}, not only one run per push. */
        static boolean counted(appeng.api.networking.crafting.ICraftingProvider provider) {
            try {
                return (boolean) Class.forName(COUNTED).getMethod("supportsCountedDispatch",
                        appeng.api.networking.crafting.ICraftingProvider.class).invoke(null, provider);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Data Energistics' counted dispatch is unavailable", exception);
            }
        }

        static boolean trinityCpu(appeng.api.networking.crafting.ICraftingCPU cpu) {
            return cpu.getClass().getName().equals(VIRTUAL_CPU);
        }

        void assertOnlyTrinityCpus(IGrid consumer) {
            var cpus = consumer.getCraftingService().getCpus();
            helper.assertTrue(!cpus.isEmpty() && cpus.stream().allMatch(TrinityDataCore::trinityCpu),
                    "The consumer's only CPUs must be the Trinity's: "
                            + cpus.stream().map(cpu -> cpu.getClass().getSimpleName()).toList());
        }

        /**
         * How often a Trinity plan fires each pattern. A Trinity plan leaves AE2's pattern times empty and keeps its
         * firings by Data Energistics' own pattern identity, made from the pattern's definition, inputs and outputs.
         */
        static Map<Object, Long> firings(ICraftingPlan plan) {
            var firings = new HashMap<Object, Long>();
            ((Map<?, ?>) invoke(plan, "patternFirings")).forEach((identity, count) ->
                    firings.put(identity, ((BigInteger) count).longValueExact()));
            return firings;
        }

        /** An AE2 plan's pattern times, each pattern by the identity Data Energistics gives it. */
        static Map<Object, Long> identities(GameTestHelper helper,
                ICraftingPlan plan) {
            try {
                var signature = Class.forName("com.fish_dan_.data_energistics.common.trinity.pattern."
                        + "TrinityPatternPublicationSignature");
                var capture = signature.getMethod("capture", IPatternDetails.class);
                var identity = Class.forName("com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph."
                        + "TrinityPatternIdentity").getMethod("capture", signature,
                        HolderLookup.Provider.class);
                var identities = new HashMap<Object, Long>();
                for (var entry : plan.patternTimes().entrySet()) {
                    identities.merge(identity.invoke(null, capture.invoke(null, entry.getKey()),
                            helper.getLevel().registryAccess()), entry.getValue(), Long::sum);
                }
                return identities;
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Data Energistics' pattern identity is unavailable", exception);
            }
        }

        /** A plan's class, and for a plan the Trinity planner refused, its diagnostic. */
        static String describe(ICraftingPlan plan) {
            var name = plan.getClass().getName();
            return name.equals(DIAGNOSED) ? name + " " + invoke(plan, "diagnostic") : name;
        }

        private void cable(BlockPos position) {
            helper.assertTrue(PartHelper.setPart(helper.getLevel(), helper.absolutePos(position), null,
                    null, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT)) != null,
                    "A cable must go at " + position);
        }

        private static Object invoke(Object target, String name) {
            try {
                return target.getClass().getMethod(name).invoke(target);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(target.getClass().getName() + " has no usable " + name, exception);
            }
        }
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
