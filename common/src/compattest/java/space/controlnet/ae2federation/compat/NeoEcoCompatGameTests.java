package space.controlnet.ae2federation.compat;

import appeng.api.config.Actionable;
import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEParts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.me.cluster.IAEMultiBlock;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.observability.statistics.StatisticsNames;
import space.controlnet.ae2federation.observability.statistics.StatisticsRange;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.policy.RuleMode;
import space.controlnet.ae2federation.storage.mount.StorageMountService;

/**
 * Federation with Neo ECO AE Extension's multiblocks, each the smallest its controller accepts: 5 long, 3 high and 2
 * deep, with the controller facing north and its interface on the west, towards the provider network.
 */
@PrefixGameTestTemplate(false)
public final class NeoEcoCompatGameTests {
    /** The computation system's interface, at the end of the consumer's cable. */
    private static final BlockPos COMPUTATION = AddonCraftingScene.FIRST_CPU.south(3).east(2);
    /** The consumer's storage system, from its bottom north-west corner, three blocks south of the computation system. */
    private static final BlockPos CONSUMER_STORAGE = COMPUTATION.south(3).offset(0, -1, -1);
    private static final BlockPos CONSUMER_DRIVE = CONSUMER_STORAGE.offset(3, 1, 0);

    private NeoEcoCompatGameTests() {
    }

    /**
     * The block and item ids of one tier of the ECO systems, by the names of Neo ECO's own L4 blocks: {@link #L4}, or
     * an addon's tier built the same way, whose ids {@code renamed} gives.
     */
    record Tier(String namespace, Map<String, String> renamed) {
        static final Tier L4 = new Tier("neoecoae", Map.of());

        String id(String name) {
            return namespace + ":" + renamed.getOrDefault(name, name);
        }
    }

    /** Neo ECO's crafting system serves the remote craft: its pattern bus provides the pattern, its worker crafts it. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void craftingSystemCrafting(GameTestHelper helper) {
        helper.succeedWhen(crafting(helper, Tier.L4)::tick);
    }

    static AddonCraftingScene crafting(GameTestHelper helper, Tier tier) {
        var start = AddonCraftingScene.PROVIDER.offset(0, -1, -1);
        return AddonCraftingScene.structure(helper, tier.id("crafting_system_l4"), List.of("ae2:1k_crafting_storage"),
                test -> placeCraftingSystem(test, tier, start), start.offset(3, 2, 1));
    }

    /** Two real jobs share one threading-core anchor without sharing a lifecycle. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void computationSharedAnchorLifecycle(GameTestHelper helper) {
        CpuLifecycleAuditScene.sharedAnchor(helper);
    }

    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void computationCompletedLifecycle(GameTestHelper helper) {
        CpuLifecycleAuditScene.completed(helper);
    }

    /** The consumer's real computation system orders from the provider's crafting system. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void computationSystemOrdering(GameTestHelper helper) {
        helper.succeedWhen(computationOrdering(helper, Tier.L4)::tick);
    }

    /** Real independent ECO CPU cancellation, with delayed output from the explicitly hand-run test machine. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void computationSystemCancel(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", "minecraft:chest", List.of(), true)
                .consumerCpuStructure(test -> placeComputationSystem(test, Tier.L4), () -> fitComputationCell(helper, Tier.L4))
                .craftingOnly().cancellingAfterPush();
        helper.succeedWhen(scene::tick);
    }

    /** Real independent ECO CPU waits for a separately delayed declared byproduct (test-operated machine). */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void computationSystemDelayedByproduct(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", "minecraft:chest", List.of(), true)
                .consumerCpuStructure(test -> placeComputationSystem(test, Tier.L4), () -> fitComputationCell(helper, Tier.L4))
                .craftingOnly().lateByproduct();
        helper.succeedWhen(scene::tick);
    }

    /** Real ECO CPU keeps its return debt with the Federation link removed after dispatch. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void computationSystemDisconnect(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", "minecraft:chest", List.of(), true)
                .consumerCpuStructure(test -> placeComputationSystem(test, Tier.L4), () -> fitComputationCell(helper, Tier.L4))
                .craftingOnly().disconnectingAfterPush();
        helper.succeedWhen(scene::tick);
    }

    /**
     * The real ECO CPU runs a projected processing job whose output is a fluid: the hand-run test machine turns each
     * cobblestone into a bucket of lava on the provider network, and the return router hands it to the consumer.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void computationSystemFluidReturn(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", List.of(), AddonCraftingScene.lavaMachine())
                .consumerCpuStructure(test -> placeComputationSystem(test, Tier.L4), () -> fitComputationCell(helper, Tier.L4))
                .craftingOnly().outputStoredOnConsumer();
        helper.succeedWhen(scene::tick);
    }

    /** As {@link #computationSystemFluidReturn}, with the ECO job cancelled before the lava comes back. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void computationSystemFluidCancel(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", List.of(), AddonCraftingScene.lavaMachine())
                .consumerCpuStructure(test -> placeComputationSystem(test, Tier.L4), () -> fitComputationCell(helper, Tier.L4))
                .craftingOnly().outputStoredOnConsumer().cancellingAfterPush();
        helper.succeedWhen(scene::tick);
    }

    /**
     * Neo ECO's smart doubling: its CPU pushes many runs of a processing pattern as one scaled batch, to the providers
     * it recognises; here through a projected AE2 Pattern Provider ({@link #smartDoubling}).
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void computationSystemSmartDoubling(GameTestHelper helper) {
        helper.succeedWhen(smartDoubling(helper, "ae2:pattern_provider")::tick);
    }

    /**
     * The consumer's ECO CPU orders sixteen stone through a projection of {@code providerId} on the provider network:
     * Neo ECO must batch to that provider on its own network, batch through the projection too, and every scaled
     * push's output must come back.
     */
    static AddonCraftingScene smartDoubling(GameTestHelper helper, String providerId) {
        var scene = new AddonCraftingScene(helper, providerId, "minecraft:chest", List.of(), true)
                .consumerCpuStructure(test -> placeComputationSystem(test, Tier.L4), () -> fitComputationCell(helper, Tier.L4))
                .craftingOnly().requesting(16);
        return scene.checkingAfterJob((order, cpu, provider) -> {
            if (scene.joined()) return; // One Grid has no projection to batch through.
            var bridge = scene.bridge();
            var consumer = scene.consumerGrid();
            var stone = appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.STONE);
            var pattern = consumer.getCraftingService().getCraftingFor(stone).iterator().next();
            var real = NeoEcoScaledDispatch.providers(provider, pattern);
            var projected = NeoEcoScaledDispatch.providers(consumer, pattern);
            helper.assertTrue(real.size() == 1 && projected.size() == 1,
                    "One provider on each network: real " + real + ", projected " + projected);
            boolean local = NeoEcoScaledDispatch.wouldScale(pattern, real.getFirst(), helper.getLevel());
            boolean remote = NeoEcoScaledDispatch.wouldScale(pattern, projected.getFirst(), helper.getLevel());
            var statistics = space.controlnet.ae2federation.observability.LevelObservabilityService
                    .get(helper.getLevel()).statistics();
            long now = statistics.nowSecond();
            statistics.finishSecond(now);
            double pushes = statistics.book().total(StatisticsNames.pair(StatisticsNames.PUSHED,
                    bridge.mainNetwork(), bridge.outerNetwork()), StatisticsRange.MINUTES, now);
            var observed = providerId + ": Neo ECO batches to the real provider: " + local + ", to the projection: "
                    + remote + "; pushes through the projection for 16 runs: " + pushes;
            helper.assertTrue(local, "Neo ECO must batch to the provider on its own network. " + observed);
            helper.assertTrue(remote, "Neo ECO must batch through the projection as it does locally. " + observed);
            helper.assertTrue(pushes < 16, "The projection must carry fewer, larger pushes. " + observed);
            helper.assertValueEqual(space.controlnet.ae2federation.crafting.projection.CraftingReturnLedger
                    .get(helper.getLevel()).owed(bridge.outerNetwork(), bridge.mainNetwork(), stone), 0L,
                    "The scaled pushes' outputs must all be returned");
            com.mojang.logging.LogUtils.getLogger().info("Smart doubling: {}", observed);
        });
    }

    /**
     * Neo ECO's smart doubling to a Federation Pattern Provider's Lane on the CPU's own network: a Lane is AE2 provider
     * logic, so Neo ECO must batch to it as to a plain AE2 Pattern Provider.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 2400)
    public static void laneSmartDoubling(GameTestHelper helper) {
        var provider = new java.util.concurrent.atomic.AtomicReference<
                space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity>();
        var scene = new EndpointMachineScene(helper, new CoreCompatGameTests.Furnace()).requesting(4)
                .preparingProvider(provider::set)
                .checkingPlan(plan -> {
                    var lanes = provider.get();
                    helper.assertTrue(lanes.laneCount() > 0, "The provider must have a Lane for the Endpoint");
                    for (int lane = 0; lane < lanes.laneCount(); lane++) {
                        for (var pattern : lanes.lane(lane).getAvailablePatterns()) {
                            helper.assertTrue(NeoEcoScaledDispatch.wouldScale(pattern, lanes.lane(lane),
                                    helper.getLevel()), "Neo ECO must batch to Lane " + lane);
                        }
                    }
                });
        helper.succeedWhen(scene::tick);
    }

    /** How many planks the throughput scenes turn into sticks: two make four, in 256 crafts. */
    private static final long THROUGHPUT_PLANKS = 512;
    /**
     * The most game ticks the projected job may take: with the same batches as on one network it takes about 100
     * (2026-10-25); one craft at a time takes 10 ticks a craft per worker, about 2600 here.
     */
    private static final long THROUGHPUT_TICKS = 300;

    /**
     * The consumer's computation system crafts sticks with the provider's crafting system through Federation: Neo ECO
     * must send the crafts in batches through the projection, as on one network
     * ({@link #craftingSystemThroughputJoined}), and every batch's output must come back.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 2400)
    public static void craftingSystemThroughput(GameTestHelper helper) {
        helper.succeedWhen(throughput(helper, false)::tick);
    }

    /** As {@link #craftingSystemThroughput}, with both networks joined into one Grid: the time to match. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 2400)
    public static void craftingSystemThroughputJoined(GameTestHelper helper) {
        helper.succeedWhen(throughput(helper, true)::tick);
    }

    static AddonCraftingScene throughput(GameTestHelper helper, boolean joined) {
        var scene = computationOrdering(helper, Tier.L4).requesting(THROUGHPUT_PLANKS);
        if (joined) scene.joinedBeforeOrder();
        return scene.checkingAfterJob(batches(helper, scene, appeng.api.stacks.AEItemKey.of(
                net.minecraft.world.item.Items.STICK), THROUGHPUT_PLANKS / 2, THROUGHPUT_TICKS, "fast path true"));
    }

    /**
     * As {@link #craftingSystemThroughput}, submitted as Neo ECO's exact plan straight to the computation system, as
     * Neo ECO's crafting menu submits exact orders: the projection offers no exact batches, so Neo ECO must fall back to
     * the ordinary batches.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 2400)
    public static void craftingSystemExactOrder(GameTestHelper helper) {
        var scene = computationOrdering(helper, Tier.L4).requesting(THROUGHPUT_PLANKS).submittingWith((grid, plan) -> {
            var cpu = grid.getCraftingService().getCpus().stream()
                    .filter(candidate -> candidate.getClass().getName().equals(ECO_CPU))
                    .findFirst().orElseThrow(() -> new IllegalStateException("The consumer has no ECO CPU"));
            try {
                var exact = Class.forName("cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan")
                        .getConstructor(appeng.api.networking.crafting.ICraftingPlan.class,
                                java.math.BigInteger.class)
                        .newInstance(plan, java.math.BigInteger.valueOf(plan.bytes()));
                var cluster = cpu.getClass().getMethod("getCluster").invoke(cpu);
                return (appeng.api.networking.crafting.ICraftingSubmitResult) cluster.getClass()
                        .getMethod("submitJob", appeng.api.networking.IGrid.class,
                                appeng.api.networking.crafting.ICraftingPlan.class,
                                appeng.api.networking.security.IActionSource.class,
                                appeng.api.networking.crafting.ICraftingRequester.class)
                        .invoke(cluster, grid, exact, appeng.api.networking.security.IActionSource.empty(), null);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Neo ECO's exact submission is not available", exception);
            }
        });
        var sticks = appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.STICK);
        helper.succeedWhen(scene.checkingAfterJob(batches(helper, scene, sticks, THROUGHPUT_PLANKS / 2,
                THROUGHPUT_TICKS, "fast path true"))::tick);
    }

    private static final String ECO_CPU = "cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU";

    /** How many crafts the workstation scenes order: one batch within the workstation's normal limit of 256. */
    private static final long WORKSTATION_CRAFTS = 64;
    /** The most game ticks the projected workstation job may take. */
    private static final long WORKSTATION_TICKS = 300;

    /**
     * The consumer's computation system orders energized crystals from the provider's Large Integrated Working Station
     * through Federation: Neo ECO must send the crafts in batches through the projection, as on one network
     * ({@link #largeWorkstationBatchingJoined}), and every batch's output must come back.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 2400)
    public static void largeWorkstationBatching(GameTestHelper helper) {
        helper.succeedWhen(largeWorkstation(helper, false)::tick);
    }

    /** As {@link #largeWorkstationBatching}, with both networks joined into one Grid: the time to match. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 2400)
    public static void largeWorkstationBatchingJoined(GameTestHelper helper) {
        helper.succeedWhen(largeWorkstation(helper, true)::tick);
    }

    static AddonCraftingScene largeWorkstation(GameTestHelper helper, boolean joined) {
        var scene = AddonCraftingScene.processingStructure(helper, "neoecoae:large_integrated_working_station",
                        List.of(), new EnergizedCrystal(), NeoEcoCompatGameTests::placeLargeWorkstation,
                        AddonCraftingScene.PROVIDER,
                        entity -> entity instanceof IAEMultiBlock<?> part && part.getCluster() != null)
                .consumerCpuStructure(test -> placeComputationSystem(test, Tier.L4),
                        () -> fitComputationCell(helper, Tier.L4))
                .craftingOnly().requesting(WORKSTATION_CRAFTS);
        if (joined) scene.joinedBeforeOrder();
        return scene.checkingAfterJob(batches(helper, scene, EnergizedCrystal.CRYSTAL, WORKSTATION_CRAFTS,
                WORKSTATION_TICKS, "parallel true"));
    }

    /**
     * Logs how long the job took, the pushes through a projection and which batch paths Neo ECO takes; through
     * Federation, Neo ECO must take the projection on {@code path} and send {@code crafts} in batches within
     * {@code maxTicks}, and the ledger must be settled.
     */
    private static AddonCraftingScene.CpuCheck batches(GameTestHelper helper, AddonCraftingScene scene,
            appeng.api.stacks.AEKey output, long crafts, long maxTicks, String path) {
        return (order, cpu, provider) -> {
            boolean joined = scene.joined();
            var consumer = scene.consumerGrid();
            var pattern = consumer.getCraftingService().getCraftingFor(output).iterator().next();
            var paths = NeoEcoScaledDispatch.providers(consumer, pattern).stream()
                    .map(each -> each.getClass().getSimpleName() + ": " + NeoEcoScaledDispatch.batchPaths(each))
                    .toList();
            var pushes = 0.0;
            if (!joined) {
                var statistics = space.controlnet.ae2federation.observability.LevelObservabilityService
                        .get(helper.getLevel()).statistics();
                long now = statistics.nowSecond();
                statistics.finishSecond(now);
                pushes = statistics.book().total(StatisticsNames.pair(StatisticsNames.PUSHED,
                        scene.bridge().mainNetwork(), scene.bridge().outerNetwork()), StatisticsRange.MINUTES, now);
            }
            var observed = (joined ? "joined" : "Federation") + ": " + crafts + " crafts of " + output + " in "
                    + scene.jobTicks() + " ticks, " + pushes + " pushes through a projection; " + paths;
            com.mojang.logging.LogUtils.getLogger().info("Neo ECO batches ({})", observed);
            if (joined) return;
            helper.assertTrue(paths.size() == 1 && paths.getFirst().contains(path),
                    "Neo ECO must take the projection on its batch path (" + path + "). " + observed);
            helper.assertTrue(pushes > 0 && pushes < crafts / 4,
                    "The crafts must cross the projection in batches. " + observed);
            helper.assertTrue(scene.jobTicks() <= maxTicks,
                    "The job must take about as long as on one network. " + observed);
            helper.assertValueEqual(space.controlnet.ae2federation.crafting.projection.CraftingReturnLedger
                    .get(helper.getLevel()).owed(scene.bridge().outerNetwork(), scene.bridge().mainNetwork(), output),
                    0L, "Every batch's output must be returned. " + observed);
        };
    }

    /**
     * The Large Integrated Working Station's recipe for energized crystals: four charged certus quartz, four
     * energized crystal dust and 250 mB of water make eight. The workstation runs it itself and stores the crystals.
     */
    private record EnergizedCrystal() implements AddonCraftingScene.Machine {
        static final appeng.api.stacks.AEItemKey CRYSTAL = item("neoecoae:energized_crystal");

        private static appeng.api.stacks.AEItemKey item(String id) {
            return appeng.api.stacks.AEItemKey.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    net.minecraft.resources.ResourceLocation.parse(id)));
        }

        @Override
        public String blockId() {
            return "neoecoae:large_integrated_working_station_interface";
        }

        @Override
        public Map<appeng.api.stacks.AEItemKey, Long> inputs() {
            return Map.of(appeng.api.stacks.AEItemKey.of(appeng.core.definitions.AEItems.CERTUS_QUARTZ_CRYSTAL_CHARGED),
                    4L, item("neoecoae:energized_crystal_dust"), 4L);
        }

        @Override
        public Map<appeng.api.stacks.AEKey, Long> otherInputs() {
            return Map.of(appeng.api.stacks.AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER), 250L);
        }

        @Override
        public String inputCell() {
            return "ae2:fluid_storage_cell_4k";
        }

        @Override
        public appeng.api.stacks.AEKey output() {
            return CRYSTAL;
        }

        @Override
        public long outputPerInput() {
            return 8;
        }

        @Override
        public void collect(GameTestHelper helper, BlockPos position, MEStorage network) {
        }
    }

    /**
     * Neo ECO's Large Integrated Working Station with its interface at {@link AddonCraftingScene#PROVIDER}, towards the
     * provider network's cable in the west: the blocks Neo ECO's own auto-build places around its controller, which
     * stands behind and above the interface, formed as that auto-build forms them.
     */
    static void placeLargeWorkstation(GameTestHelper helper) {
        var controller = AddonCraftingScene.PROVIDER.east().above();
        var interfaceId = "neoecoae:large_integrated_working_station_interface";
        try {
            var definition = Class.forName("cn.dancingsnow.neoecoae.all.NEMultiBlocks")
                    .getField("LARGE_INTEGRATED_WORKING_STATION").get(null);
            Method preview = null;
            for (var method : Class.forName("cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementService")
                    .getMethods()) {
                if (method.getName().equals("preview")) preview = method;
            }
            for (var direction : Direction.Plane.HORIZONTAL) {
                var state = facing(Tier.L4, "integrated_working_station", direction);
                var plan = preview.invoke(null, helper.getLevel(), helper.absolutePos(controller), state, definition, 1,
                        false);
                var planned = (List<?>) plan.getClass().getMethod("getAllBlocks").invoke(plan);
                var placements = new ArrayList<Map.Entry<BlockPos, BlockState>>();
                boolean interfaceAtProvider = false;
                for (var block : planned) {
                    var at = (BlockPos) block.getClass().getMethod("worldPos").invoke(block);
                    var target = (BlockState) block.getClass().getMethod("targetState").invoke(block);
                    placements.add(Map.entry(at, target));
                    interfaceAtProvider |= at.equals(helper.absolutePos(AddonCraftingScene.PROVIDER))
                            && BuiltInRegistries.BLOCK.getKey(target.getBlock()).toString().equals(interfaceId);
                }
                if (!interfaceAtProvider) continue;
                // The plan's positions are absolute.
                for (var placement : placements) {
                    helper.getLevel().setBlock(placement.getKey(), placement.getValue(), Block.UPDATE_ALL);
                }
                helper.setBlock(controller, state);
                // As Neo ECO's auto-build does once it has placed the blocks.
                var entity = helper.getBlockEntity(controller);
                entity.getClass().getMethod("rebuildMultiblock").invoke(entity);
                return;
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Neo ECO's multiblock placement is not available", exception);
        }
        throw new IllegalStateException("No facing puts the workstation's interface at the provider");
    }

    static AddonCraftingScene computationOrdering(GameTestHelper helper, Tier tier) {
        var start = AddonCraftingScene.PROVIDER.offset(0, -1, -1);
        return AddonCraftingScene.structure(helper, tier.id("crafting_system_l4"), List.of(),
                test -> placeCraftingSystem(test, tier, start), start.offset(3, 2, 1))
                .consumerCpuStructure(test -> placeComputationSystem(test, tier), () -> fitComputationCell(helper, tier));
    }

    /**
     * The guide's district and its exercise: the consumer keeps its items in an ECO storage system and runs its jobs on
     * an ECO computation system, and orders from the provider network's ECO crafting system, which has no power of its
     * own. Breaking a casing of the crafting system then takes its recipes away from the consumer, and putting it back
     * returns them.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 1200)
    public static void ecoDistrictOrdering(GameTestHelper helper) {
        helper.succeedWhen(district(helper, Tier.L4)::tick);
    }

    static AddonCraftingScene district(GameTestHelper helper, Tier tier) {
        var start = AddonCraftingScene.PROVIDER.offset(0, -1, -1);
        return computationOrdering(helper, tier)
                .consumerStorageStructure(test -> placeConsumerStorageSystem(test, tier),
                        () -> fitCell(helper, tier, CONSUMER_DRIVE) ? mountedStorage(helper, CONSUMER_DRIVE) : null)
                .dismantlingAfterwards(start.offset(4, 1, 0));
    }

    /** Neo ECO's storage system, with a cell in one of its drives, is the provider network's storage the rule shares. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void storageSystemStorage(GameTestHelper helper) {
        helper.succeedWhen(storage(helper, Tier.L4)::tick);
    }

    static AddonStorageScene storage(GameTestHelper helper, Tier tier) {
        var start = AddonStorageScene.BESIDE_CHEST.offset(0, -1, -1);
        var drive = start.offset(3, 1, 0);
        return AddonStorageScene.structure(helper, tier.id("storage_system_l4"),
                test -> placeStorageSystem(test, tier, start), () -> fitCell(helper, tier, drive));
    }

    /**
     * The guide's former storage-system example and its exercise: after the round trip, breaking the tail casing takes
     * the shared items away from the consumer, and putting it back returns them.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void storageSystemDismantled(GameTestHelper helper) {
        helper.succeedWhen(storageDismantled(helper, Tier.L4)::tick);
    }

    static AddonStorageScene storageDismantled(GameTestHelper helper, Tier tier) {
        return storage(helper, tier).dismantlingAfterwards(AddonStorageScene.BESIDE_CHEST.offset(4, 0, -1));
    }

    /**
     * A regional warehouse, once a guide example: an ECO storage system on the warehouse network, with the only energy
     * cell; a hub network that uses the warehouse's storage with re-export; and two districts, each with no storage and
     * no power of its own and a Bridge of its own to the hub. A district is two Bridges from the warehouse, shares no
     * domain and has no rule with it, yet sees, takes and stores into it. Stepping the hub's rule back to
     * plain Enabled takes the warehouse away from both districts but not from the hub; re-export brings it back.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 1200)
    public static void ecoWarehouseChain(GameTestHelper helper) {
        helper.succeedWhen(new Warehouse(helper, Tier.L4)::run);
    }

    /**
     * Neo ECO's own planner, as its crafting screen starts it with cycle planning on, chains a seed pattern that only
     * the provider network has; see {@link CyclePlanningScene}.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void ecoPlannerCycle(GameTestHelper helper) {
        // Neo ECO gives the plans its planner makes only to its own computation systems.
        helper.succeedWhen(new CyclePlanningScene(helper, "Neo ECO's planner", NeoEcoCompatGameTests::ecoPlannerRequest,
                NeoEcoCompatGameTests::ecoOwnedPlan)
                .consumerCpuStructure(test -> placeComputationSystem(test, Tier.L4),
                        () -> fitComputationCell(helper, Tier.L4))::tick);
    }

    /** Wraps {@code plain} as Neo ECO's screens do: ECOPlannerRequester with cycle planning on and nothing fuzzy. */
    private static appeng.api.networking.crafting.ICraftingSimulationRequester ecoPlannerRequest(
            appeng.api.networking.crafting.ICraftingSimulationRequester plain) {
        try {
            var options = Class.forName("cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions")
                    .getConstructor(boolean.class, boolean.class, java.util.Set.class)
                    .newInstance(true, false, java.util.Set.of());
            return (appeng.api.networking.crafting.ICraftingSimulationRequester) Class.forName(
                    "cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerRequester")
                    .getConstructor(appeng.api.networking.crafting.ICraftingSimulationRequester.class,
                            options.getClass())
                    .newInstance(plain, options);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Neo ECO's planner request is not available", exception);
        }
    }

    /** Whether Neo ECO's planner made {@code plan}, as its registry of planning results records it. */
    private static boolean ecoOwnedPlan(appeng.api.networking.crafting.ICraftingPlan plan) {
        try {
            return (boolean) Class.forName("cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry")
                    .getMethod("isECOOwnedPlan", appeng.api.networking.crafting.ICraftingPlan.class)
                    .invoke(null, plan);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Neo ECO's planning registry is not available", exception);
        }
    }

    /** The warehouse chain's stages; see {@link #ecoWarehouseChain}. */
    static final class Warehouse {
        private static final appeng.api.stacks.AEItemKey IRON =
                appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.IRON_INGOT);
        /** The storage system's bottom north-west corner; its interface touches the warehouse's cable at (9, 4, 3). */
        private static final BlockPos STORAGE = new BlockPos(10, 3, 2);
        private static final BlockPos DRIVE = STORAGE.offset(3, 1, 0);
        private final GameTestHelper helper;
        private final Tier tier;
        private final BridgeChainFixture chain;
        private final appeng.api.networking.security.IActionSource source =
                appeng.api.networking.security.IActionSource.empty();
        private int stage;

        Warehouse(GameTestHelper helper, Tier tier) {
            this.helper = helper;
            this.tier = tier;
            chain = new BridgeChainFixture(helper, List.of(
                    new BridgeChainFixture.Network("district1", 7, 1, 3, AEColor.PURPLE),
                    new BridgeChainFixture.Network("district2", 7, 7, 9, AEColor.YELLOW),
                    new BridgeChainFixture.Network("hub", 8, 1, 9, AEColor.LIGHT_BLUE),
                    new BridgeChainFixture.Network("warehouse", 9, 3, 7, AEColor.GREEN)),
                    List.of(new BridgeChainFixture.Link("district1", "hub", 2),
                            new BridgeChainFixture.Link("hub", "warehouse", 5),
                            new BridgeChainFixture.Link("district2", "hub", 8)));
            // The warehouse's energy cell is the only power source; it touches no other network's cable.
            helper.setBlock(chain.cable("warehouse", 7).below(),
                    appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
            placeStorageSystem(helper, tier, STORAGE);
        }

        void run() {
            switch (stage) {
                case 0 -> {
                    helper.assertTrue(chain.built(), "Waiting for the networks and Bridges: " + chain.readiness());
                    helper.assertTrue(chain.powered(), "Waiting for the ME power rules to power every network");
                    helper.assertTrue(fitCell(helper, tier, DRIVE) && mountedStorage(helper, DRIVE) != null,
                            "Waiting for the ECO storage system to form and take its cell");
                    helper.assertFalse(chain.shareDomain("district1", "warehouse"),
                            "A district must share no domain with the warehouse");
                    chain.rule("hub", "warehouse", PolicyCapability.STORAGE, reexport());
                    chain.rule("district1", "hub", PolicyCapability.STORAGE, PolicyRule.storageDefaults());
                    chain.rule("district2", "hub", PolicyCapability.STORAGE, PolicyRule.storageDefaults());
                    helper.assertValueEqual(inventory("warehouse").insert(IRON, 9, Actionable.MODULATE, source), 9L,
                            "The warehouse's storage system must store the iron");
                    stage = 1;
                    helper.fail("Set the rules and stocked the warehouse");
                }
                case 1 -> {
                    helper.assertValueEqual(seen("district1"), 9L, "Waiting for district 1 to see the warehouse: "
                            + mountStatus("district1"));
                    helper.assertValueEqual(seen("district2"), 9L, "Waiting for district 2 to see the warehouse: "
                            + mountStatus("district2"));
                    helper.assertTrue(PolicyService.get(helper.getLevel())
                            .configured(chain.key("district1", "warehouse", PolicyCapability.STORAGE)).isEmpty(),
                            "No rule joins a district to the warehouse");
                    helper.assertValueEqual(inventory("district1").extract(IRON, 4, Actionable.MODULATE, source), 4L,
                            "District 1 must take iron out of the warehouse");
                    helper.assertValueEqual(stored(), 5L, "What district 1 took must leave the storage system");
                    helper.assertValueEqual(inventory("district2").insert(IRON, 4, Actionable.MODULATE, source), 4L,
                            "District 2, with no storage of its own, must store into the warehouse");
                    helper.assertValueEqual(stored(), 9L, "What district 2 stored must reach the storage system");
                    // The exercise: the hub's rule steps back from re-export to plain Enabled.
                    chain.rule("hub", "warehouse", PolicyCapability.STORAGE, PolicyRule.storageDefaults());
                    stage = 2;
                    helper.fail("Stepped the hub's rule back to Enabled");
                }
                case 2 -> {
                    helper.assertValueEqual(seen("district1") + seen("district2"), 0L,
                            "Waiting for the warehouse to leave both districts");
                    helper.assertValueEqual(seen("hub"), 9L, "The hub keeps the warehouse under its own rule");
                    chain.rule("hub", "warehouse", PolicyCapability.STORAGE, reexport());
                    stage = 3;
                    helper.fail("Stepped the hub's rule to re-export again");
                }
                default -> {
                    helper.assertValueEqual(seen("district1"), 9L, "Waiting for the warehouse to return to district 1");
                    helper.assertValueEqual(seen("district2"), 9L, "Waiting for the warehouse to return to district 2");
                    chain.close();
                }
            }
        }

        private static PolicyRule reexport() {
            return PolicyRule.storageDefaults().withMode(RuleMode.REEXPORT);
        }

        private MEStorage inventory(String network) {
            return chain.grid(network).getStorageService().getInventory();
        }

        private long seen(String network) {
            return inventory(network).getAvailableStacks().get(IRON);
        }

        /** What the ECO drive's own cell holds, so nothing is counted twice through a mount. */
        private long stored() {
            return mountedStorage(helper, DRIVE).extract(IRON, Long.MAX_VALUE, Actionable.SIMULATE, source);
        }

        private String mountStatus(String district) {
            return "hub " + StorageMountService.status(helper.getLevel(),
                    chain.key("hub", "warehouse", PolicyCapability.STORAGE)) + ", district "
                    + StorageMountService.status(helper.getLevel(), chain.key(district, "hub", PolicyCapability.STORAGE));
        }
    }

    /**
     * From the west, at {@code start} (bottom, north): the interface side with the interface between its input and
     * output hatches behind, the controller, a casing column, the worker between parallel cores with the vent between
     * pattern buses behind, and the end casing.
     */
    static void placeCraftingSystem(GameTestHelper helper, Tier tier, BlockPos start) {
        for (int y = 0; y < 3; y++) {
            helper.setBlock(start.offset(0, y, 0), block(tier, "crafting_casing"));
            helper.setBlock(start.offset(1, y, 1), block(tier, "crafting_casing"));
            helper.setBlock(start.offset(2, y, 0), block(tier, "crafting_casing"));
            helper.setBlock(start.offset(2, y, 1), block(tier, "crafting_casing"));
            helper.setBlock(start.offset(4, y, 0), block(tier, "crafting_casing"));
            helper.setBlock(start.offset(4, y, 1), block(tier, "crafting_casing"));
        }
        helper.setBlock(start.offset(0, 2, 1), block(tier, "input_hatch"));
        helper.setBlock(start.offset(0, 1, 1), block(tier, "crafting_interface"));
        helper.setBlock(start.offset(0, 0, 1), block(tier, "output_hatch"));
        helper.setBlock(start.offset(1, 2, 0), block(tier, "crafting_casing"));
        helper.setBlock(start.offset(1, 0, 0), block(tier, "crafting_casing"));
        helper.setBlock(start.offset(3, 2, 0), facing(tier, "crafting_parallel_core_l4", Direction.NORTH));
        helper.setBlock(start.offset(3, 1, 0), facing(tier, "crafting_worker", Direction.NORTH));
        helper.setBlock(start.offset(3, 0, 0), facing(tier, "crafting_parallel_core_l4", Direction.NORTH));
        helper.setBlock(start.offset(3, 2, 1), facing(tier, "crafting_pattern_bus", Direction.SOUTH));
        helper.setBlock(start.offset(3, 1, 1), facing(tier, "crafting_vent", Direction.SOUTH));
        helper.setBlock(start.offset(3, 0, 1), facing(tier, "crafting_pattern_bus", Direction.SOUTH));
        helper.setBlock(start.offset(1, 1, 0), facing(tier, "crafting_system_l4", Direction.NORTH));
    }

    /**
     * From the west, at {@code start} (bottom, north): the interface side with the interface behind, the controller, a
     * casing transition, the drives with the vent between energy cells behind, and the tail casing.
     */
    static void placeStorageSystem(GameTestHelper helper, Tier tier, BlockPos start) {
        for (int y = 0; y < 3; y++) {
            helper.setBlock(start.offset(0, y, 0), block(tier, "storage_casing"));
            helper.setBlock(start.offset(1, y, 1), block(tier, "storage_casing"));
            helper.setBlock(start.offset(2, y, 0), block(tier, "storage_casing"));
            helper.setBlock(start.offset(2, y, 1), block(tier, "storage_casing"));
            helper.setBlock(start.offset(3, y, 0), facing(tier, "eco_drive", Direction.NORTH));
            helper.setBlock(start.offset(4, y, 0), block(tier, "storage_casing"));
            helper.setBlock(start.offset(4, y, 1), block(tier, "storage_casing"));
        }
        helper.setBlock(start.offset(0, 2, 1), block(tier, "storage_casing"));
        helper.setBlock(start.offset(0, 1, 1), block(tier, "storage_interface"));
        helper.setBlock(start.offset(0, 0, 1), block(tier, "storage_casing"));
        helper.setBlock(start.offset(1, 2, 0), block(tier, "storage_casing"));
        helper.setBlock(start.offset(1, 0, 0), block(tier, "storage_casing"));
        helper.setBlock(start.offset(3, 2, 1), facing(tier, "energy_cell_l4", Direction.SOUTH));
        helper.setBlock(start.offset(3, 1, 1), facing(tier, "storage_vent", Direction.SOUTH));
        helper.setBlock(start.offset(3, 0, 1), facing(tier, "energy_cell_l4", Direction.SOUTH));
        helper.setBlock(start.offset(1, 1, 0), facing(tier, "storage_system_l4", Direction.NORTH));
    }

    /**
     * The consumer's cable runs from west of its first cable south and east to the computation system's interface,
     * which is on the system's west end, behind its controller; from there, as {@link #placeCraftingSystem}: the
     * controller, a casing column, the transmitter between drives with the threading core between parallel cores
     * behind, and the cooling controller at the end.
     */
    static void placeComputationSystem(GameTestHelper helper, Tier tier) {
        var cable = AddonCraftingScene.FIRST_CPU;
        consumerCable(helper, cable);
        for (int step = 1; step <= 3; step++) consumerCable(helper, cable.south(step));
        consumerCable(helper, cable.south(3).east());
        var start = COMPUTATION.offset(0, -1, -1);
        for (int y = 0; y < 3; y++) {
            helper.setBlock(start.offset(0, y, 0), block(tier, "computation_casing"));
            helper.setBlock(start.offset(1, y, 1), block(tier, "computation_casing"));
            helper.setBlock(start.offset(2, y, 0), block(tier, "computation_casing"));
            helper.setBlock(start.offset(2, y, 1), block(tier, "computation_casing"));
        }
        helper.setBlock(start.offset(0, 2, 1), block(tier, "computation_casing"));
        helper.setBlock(start.offset(0, 0, 1), block(tier, "computation_casing"));
        helper.setBlock(start.offset(1, 2, 0), block(tier, "computation_casing"));
        helper.setBlock(start.offset(1, 0, 0), block(tier, "computation_casing"));
        helper.setBlock(start.offset(3, 2, 0), facing(tier, "computation_drive", Direction.NORTH));
        helper.setBlock(start.offset(3, 1, 0), facing(tier, "computation_transmitter", Direction.NORTH));
        helper.setBlock(start.offset(3, 0, 0), facing(tier, "computation_drive", Direction.NORTH));
        helper.setBlock(start.offset(3, 2, 1), facing(tier, "computation_parallel_core_l4", Direction.SOUTH));
        helper.setBlock(start.offset(3, 1, 1), facing(tier, "computation_threading_core_l4", Direction.SOUTH));
        helper.setBlock(start.offset(3, 0, 1), facing(tier, "computation_parallel_core_l4", Direction.SOUTH));
        helper.setBlock(start.offset(4, 2, 0), block(tier, "computation_casing"));
        helper.setBlock(start.offset(4, 1, 0), facing(tier, "computation_cooling_controller_l4", Direction.EAST));
        helper.setBlock(start.offset(4, 0, 0), block(tier, "computation_casing"));
        for (int y = 0; y < 3; y++) helper.setBlock(start.offset(4, y, 1), block(tier, "computation_casing"));
        helper.setBlock(COMPUTATION, block(tier, "computation_interface"));
        helper.setBlock(start.offset(1, 1, 0), facing(tier, "computation_system_l4", Direction.NORTH));
    }

    /**
     * Past the computation system's cable, the consumer's cable runs on south and east to an ECO storage system laid
     * out as in {@link #placeStorageSystem}, its interface on its west end.
     */
    static void placeConsumerStorageSystem(GameTestHelper helper, Tier tier) {
        var cable = AddonCraftingScene.FIRST_CPU;
        for (int step = 4; step <= 6; step++) consumerCable(helper, cable.south(step));
        consumerCable(helper, cable.south(6).east());
        placeStorageSystem(helper, tier, CONSUMER_STORAGE);
    }

    /** The storage an ECO drive mounts into its network, as AE2 asks it for: the drive's cell inventory. */
    static MEStorage mountedStorage(GameTestHelper helper, BlockPos drive) {
        var mounted = new ArrayList<MEStorage>();
        ((IStorageProvider) helper.getLevel().getBlockEntity(helper.absolutePos(drive)))
                .mountInventories((inventory, priority) -> mounted.add(inventory));
        return mounted.isEmpty() ? null : mounted.getFirst();
    }

    private static void consumerCable(GameTestHelper helper, BlockPos position) {
        helper.assertTrue(PartHelper.setPart(helper.getLevel(), helper.absolutePos(position), null, null,
                AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT)) != null, "A cable must go at " + position);
    }

    /** Once the computation system has formed, puts a flash array into its upper drive as its player does. */
    static boolean fitComputationCell(GameTestHelper helper, Tier tier) {
        return fitCell(helper, COMPUTATION.offset(3, 1, -1), tier.id("eco_computation_cell_l4"));
    }

    /** Once the storage system has formed, puts an ECO item cell into the drive as its player does. */
    static boolean fitCell(GameTestHelper helper, Tier tier, BlockPos drive) {
        return fitCell(helper, drive, tier.id("eco_item_storage_cell_16m"));
    }

    /** Once the storage system has formed, puts {@code cell} into the drive at {@code drive} as its player does. */
    static boolean fitCell(GameTestHelper helper, BlockPos drive, String cell) {
        var entity = helper.getLevel().getBlockEntity(helper.absolutePos(drive));
        if (!(entity instanceof IAEMultiBlock<?> part) || part.getCluster() == null) return false;
        try {
            // An empty drive holds no stack at all.
            var held = (ItemStack) entity.getClass().getMethod("getCellStack").invoke(entity);
            if (held == null || held.isEmpty()) {
                entity.getClass().getMethod("setCellStack", ItemStack.class)
                        .invoke(entity, new ItemStack(AddonCraftingScene.item(cell)));
            }
            held = (ItemStack) entity.getClass().getMethod("getCellStack").invoke(entity);
            return held != null && !held.isEmpty();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("The ECO drive at " + drive + " cannot take a cell", exception);
        }
    }

    /**
     * Puts {@code cell} (or nothing) into the ECO drive at {@code drive} as its player does, and gives back the cell it
     * held. Neo ECO Prototype's L1 drive has its own {@code removeCell} and {@code insertCell}, which its players use.
     */
    static ItemStack swapCell(GameTestHelper helper, BlockPos drive, ItemStack cell) {
        var entity = helper.getLevel().getBlockEntity(helper.absolutePos(drive));
        try {
            var type = entity.getClass();
            var held = (ItemStack) type.getMethod("getCellStack").invoke(entity);
            held = held == null ? ItemStack.EMPTY : held.copy();
            if (hasMethod(type, "removeCell") && hasMethod(type, "insertCell", ItemStack.class)) {
                if (!held.isEmpty()) type.getMethod("removeCell").invoke(entity);
                if (!cell.isEmpty()) type.getMethod("insertCell", ItemStack.class).invoke(entity, cell);
                return held;
            }
            type.getMethod("setCellStack", ItemStack.class).invoke(entity, cell);
            return held;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("The ECO drive at " + drive + " cannot swap its cell", exception);
        }
    }

    private static boolean hasMethod(Class<?> type, String name, Class<?>... parameters) {
        try {
            type.getMethod(name, parameters);
            return true;
        } catch (NoSuchMethodException absent) {
            return false;
        }
    }

    private static BlockState block(Tier tier, String name) {
        return AddonCraftingScene.block(tier.id(name)).defaultBlockState();
    }

    /** Some of its blocks have a facing property of their own, so it is found by name. */
    private static BlockState facing(Tier tier, String name, Direction direction) {
        var state = block(tier, name);
        for (var property : state.getProperties()) {
            if (property.getName().equals("facing") && property instanceof DirectionProperty facing) {
                return state.setValue(facing, direction);
            }
        }
        throw new IllegalStateException(tier.id(name) + " has no facing");
    }

    /**
     * As {@link #computationSystemFluidReturn}, merged mid-job: only the Bridge part is removed while the job waits, so
     * the fluix cable under it joins both networks into one AE2 Grid; the CPU takes the lava natively, the debt
     * settles, and after the split the provider keeps its next lava.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void computationSystemMergedFluidReturn(GameTestHelper helper) {
        var tier = Tier.L4;
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", List.of(), AddonCraftingScene.lavaMachine())
                .consumerCpuStructure(test -> placeComputationSystem(test, tier),
                        () -> fitComputationCell(helper, tier))
                .craftingOnly().outputStoredOnConsumer().mergingAfterPush();
        helper.succeedWhen(scene::tick);
    }
}
