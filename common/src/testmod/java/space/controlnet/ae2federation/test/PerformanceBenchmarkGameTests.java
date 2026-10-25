package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.helpers.PlayerSource;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.client.policy.FederationDomainPolicySession;
import space.controlnet.ae2federation.crafting.projection.CraftingProjectionService;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.energy.EnergySharingService;
import space.controlnet.ae2federation.observability.LevelObservabilityService;
import space.controlnet.ae2federation.observability.meter.OperationEventId;
import space.controlnet.ae2federation.observability.state.FlowState;
import space.controlnet.ae2federation.observability.state.ResourceUnit;
import space.controlnet.ae2federation.observability.statistics.FederationStatistics;
import space.controlnet.ae2federation.observability.statistics.StatisticsBook;
import space.controlnet.ae2federation.observability.statistics.StatisticsNames;
import space.controlnet.ae2federation.observability.statistics.StatisticsRange;
import space.controlnet.ae2federation.policy.AuthorityEpoch;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.storage.mount.StorageMountService;
import space.controlnet.ae2federation.test.perf.BulkItemStorage;
import space.controlnet.ae2federation.test.perf.BusFarmScene;
import space.controlnet.ae2federation.test.perf.BusScene;
import space.controlnet.ae2federation.test.perf.DomainFarmScene;
import space.controlnet.ae2federation.test.perf.FullTickClock;
import space.controlnet.ae2federation.test.perf.EnergyMeshScene;
import space.controlnet.ae2federation.test.perf.MenuBroadcasts;
import space.controlnet.ae2federation.test.perf.NativeEnergyMeshScene;
import space.controlnet.ae2federation.test.perf.PerfMeasure;
import space.controlnet.ae2federation.test.policy.PolicyBridgeFixtures;
import space.controlnet.ae2federation.test.router.RouterFixtures;

/**
 * TEST-ONLY, manual-only performance benchmarks. Each test builds a production topology, checks that the measured path
 * still does its job, and logs {@code AE2F_PERF} metrics for {@code tools/perf_benchmark.py}. The numbers are
 * wall-clock and machine dependent: compare runs only on the same host.
 */
@PrefixGameTestTemplate(false)
public final class PerformanceBenchmarkGameTests {
    private static final int MESH_SIZE = Integer.getInteger("ae2federation.perf.meshSize", 3);
    private static final int MESH_PADDING = Integer.getInteger("ae2federation.perf.padding", 256);
    private static final int STORAGE_TYPES = Integer.getInteger("ae2federation.perf.storageTypes", 1000);
    private static final int PLANE = Integer.getInteger("ae2federation.perf.plane", 30);
    /** Ticks per measured window; the reported median uses the last 100. A longer window only helps a profiler. */
    private static final int WINDOW_TICKS = Integer.getInteger("ae2federation.perf.windowTicks", 100);
    /**
     * Ticks an idle window lets pass before its median (of the last 100 ticks) counts: the ticks right after a
     * measured burst of operations still settle what the burst left behind.
     */
    private static final int IDLE_SETTLE_TICKS = 100;
    /** Item types on the main network of the bus benchmarks. */
    private static final int BUS_TYPES = 10_000;
    /**
     * Longest warm-up of the bus benchmarks. AE2 drops a bus that could not work yet to its slowest rate and speeds it
     * up by two ticks per operation, so a scene takes about a thousand ticks to reach its full rate.
     */
    private static final int BUS_WARMUP_TICKS = 3000;

    private PerformanceBenchmarkGameTests() {
    }

    /** Energy drawn from a pool that a SUPPLY mesh shares: per-call cost of demand, and of re-checking who shares. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 2400, required = true, manualOnly = true)
    public static void perfEnergyMesh(GameTestHelper helper) {
        var scene = new EnergyMeshScene(helper, MESH_SIZE, MESH_PADDING);
        var perf = new PerfMeasure("perfenergymesh");
        var state = new int[] {0};
        var idleReconciliations = new int[2];
        var window = new PerfMeasure.TickWindow[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(scene.ready(), "Waiting for energy mesh: " + scene.status());
            if (state[0] == 0) {
                perf.count("rules", scene.enableMesh());
                scene.chargeProviders(1.0e9);
                scene.drainConsumer();
                state[0] = 1;
                window[0] = new PerfMeasure.TickWindow(helper.getLevel().getServer());
                helper.assertTrue(false, "Waiting for the mesh pool");
            }
            if (state[0] == 1) {
                helper.assertTrue(window[0].ticks() >= 40, "Letting the consumer drain settle");
                helper.assertValueEqual(scene.sharedPairCount(), MESH_SIZE * (MESH_SIZE - 1) / 2,
                        "Every pair of mesh Grids must share energy");
                perf.count("sharedPairs", scene.sharedPairCount());
                perf.count("consumerNodes", scene.consumerNodeCount());
                var before = scene.providersStored();
                helper.assertValueEqual(scene.extractConsumer(1, Actionable.MODULATE), 1.0,
                        "Consumer demand must be supplied through the mesh");
                helper.assertTrue(scene.consumerCellStored() < 1, "Consumer cell must stay drained");
                helper.assertTrue(before - scene.providersStored() > 0.5, "Provider cells must pay for the demand");
                // Resolved once: the scene finds its Grids through block entities, which is not part of a demand.
                var energy = scene.consumerGrid().getEnergyService();
                perf.nanosPerOp("extractModulate", 2000, 300,
                        () -> energy.extractAEPower(1, Actionable.MODULATE, appeng.api.config.PowerMultiplier.ONE));
                perf.nanosPerOp("extractSimulate", 2000, 300,
                        () -> energy.extractAEPower(1, Actionable.SIMULATE, appeng.api.config.PowerMultiplier.ONE));
                // Re-deriving who shares from every rule and the domain topology; equal pools are left standing.
                var sharing = EnergySharingService.get(helper.getLevel());
                perf.nanosPerOp("reconcileSharing", 500, 300, sharing::reconcileAll);
                helper.assertValueEqual(scene.extractConsumer(1, Actionable.MODULATE), 1.0,
                        "Reconciling must leave the pool standing");
                idleReconciliations[0] = sharing.reconciliationCount();
                idleReconciliations[1] = sharing.dissolutionCount();
                state[0] = 2;
                window[0] = new PerfMeasure.TickWindow(helper.getLevel().getServer());
                helper.assertTrue(false, "Measuring idle ticks");
            }
            helper.assertTrue(window[0].ticks() > IDLE_SETTLE_TICKS + WINDOW_TICKS, "Measuring idle ticks");
            perf.record("idleTick", window[0].medianTickNanos(), "ns/tick");
            // Nothing changes while idle, so no tick may re-derive who shares or dissolve a pool.
            var idleSharing = EnergySharingService.get(helper.getLevel());
            perf.count("idleReconciliations", idleSharing.reconciliationCount() - idleReconciliations[0]);
            perf.count("idleDissolutions", idleSharing.dissolutionCount() - idleReconciliations[1]);
            helper.assertTrue(scene.consumerCellStored() < 1, "Consumer idle drain must still come from the mesh");
            scene.close();
        });
    }

    /**
     * Native AE2 baseline for {@link #perfEnergyMesh}: the same Grids share energy through Quartz Fibers instead of
     * Federation, and the same demand is measured.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 2400, required = true, manualOnly = true)
    public static void perfNativeEnergy(GameTestHelper helper) {
        var scene = new NativeEnergyMeshScene(helper, MESH_SIZE, MESH_PADDING);
        var perf = new PerfMeasure("perfnativeenergy");
        var state = new int[] {0};
        var window = new PerfMeasure.TickWindow[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(scene.ready(), "Waiting for native energy mesh: " + scene.status());
            if (state[0] == 0) {
                scene.chargeProviders(1.0e9);
                scene.drainConsumer();
                state[0] = 1;
                window[0] = new PerfMeasure.TickWindow(helper.getLevel().getServer());
                helper.assertTrue(false, "Letting the consumer drain settle");
            }
            if (state[0] == 1) {
                helper.assertTrue(window[0].ticks() >= 40, "Letting the consumer drain settle");
                perf.count("consumerNodes", scene.consumerNodeCount());
                var before = scene.providersStored();
                helper.assertValueEqual(scene.extractConsumer(1, Actionable.MODULATE), 1.0,
                        "Consumer demand must be supplied through the fibers");
                helper.assertTrue(scene.consumerCellStored() < 1, "Consumer cell must stay drained");
                helper.assertTrue(before - scene.providersStored() > 0.5, "Provider cells must pay for the demand");
                // Resolved once: the scene finds its Grids through block entities, which is not part of a demand.
                var energy = scene.consumerGrid().getEnergyService();
                perf.nanosPerOp("extractModulate", 2000, 300,
                        () -> energy.extractAEPower(1, Actionable.MODULATE, appeng.api.config.PowerMultiplier.ONE));
                perf.nanosPerOp("extractSimulate", 2000, 300,
                        () -> energy.extractAEPower(1, Actionable.SIMULATE, appeng.api.config.PowerMultiplier.ONE));
                state[0] = 2;
                window[0] = new PerfMeasure.TickWindow(helper.getLevel().getServer());
                helper.assertTrue(false, "Measuring idle ticks");
            }
            helper.assertTrue(window[0].ticks() > IDLE_SETTLE_TICKS + WINDOW_TICKS, "Measuring idle ticks");
            perf.record("idleTick", window[0].medianTickNanos(), "ns/tick");
            helper.assertTrue(scene.consumerCellStored() < 1, "Consumer idle drain must still come from the fibers");
            scene.close();
        });
    }

    /** Storage read through a mounted projection of a large native source, plus the reconcile paths that rebuild it. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1200, required = true, manualOnly = true)
    public static void perfStorageProjection(GameTestHelper helper) {
        var fixtures = new PolicyBridgeFixtures(helper, new BlockPos(5, 3, 5));
        fixtures.installStorageCells();
        var bulk = new BulkItemStorage(STORAGE_TYPES, 1_000_000L);
        var perf = new PerfMeasure("perfstorageprojection");
        var state = new int[] {0};
        var window = new PerfMeasure.TickWindow[1];
        var idleStart = new long[2];
        var iron = AEItemKey.of(Items.IRON_INGOT);
        helper.succeedWhen(() -> {
            if (state[0] == 0) {
                helper.assertTrue(fixtures.networksSettled(), "Waiting for storage networks");
                fixtures.addOuterStorageProvider(bulk);
                fixtures.placeFirstBridge();
                state[0] = 1;
                helper.assertTrue(false, "Waiting for the Bridge domain");
            }
            if (state[0] == 1) {
                helper.assertTrue(fixtures.networksSettled() && fixtures.firstBridgeReady(),
                        "Waiting for the Bridge domain");
                var key = PolicyLifecycleGameTests.storageKey(fixtures);
                PolicyService.get(helper.getLevel()).edit(new PolicyEdit(key, PolicyRevision.NONE,
                        PolicyRule.storageDefaults()));
                state[0] = 2;
                helper.assertTrue(false, "Waiting for the projection");
            }
            var key = PolicyLifecycleGameTests.storageKey(fixtures);
            var mounts = StorageMountService.get(helper.getLevel());
            var projection = mounts.projection(key);
            helper.assertTrue(projection != null, "Active Storage rule must mount one projection");
            if (state[0] == 2) {
                var visible = projection.getAvailableStacks();
                helper.assertTrue(visible.size() >= STORAGE_TYPES,
                        "Projection must show every bulk type, saw " + visible.size());
                perf.count("types", visible.size());
                var source = new PlayerSource(helper.makeMockPlayer(GameType.CREATIVE));
                helper.assertValueEqual(projection.insert(iron, 1, Actionable.MODULATE, source), 1L,
                        "Projection must insert into the native source");
                helper.assertValueEqual(projection.extract(iron, 1, Actionable.MODULATE, source), 1L,
                        "Projection must extract from the native source");
                perf.nanosPerOp("availableStacks", 2000, 300, () -> projection.getAvailableStacks(new KeyCounter()));
                perf.nanosPerOp("insertExtract", 5000, 300, () -> {
                    projection.insert(iron, 1, Actionable.MODULATE, source);
                    projection.extract(iron, 1, Actionable.MODULATE, source);
                });
                var consumer = fixtures.mainGrid().getStorageService().getInventory();
                perf.nanosPerOp("consumerSimulateExtract", 5000, 300,
                        () -> consumer.extract(iron, 1, Actionable.SIMULATE, source));
                measureNetworkStorage(helper, perf, consumer, source, bulk);
                perf.nanosPerOp("reconcileAll", 500, 300, () -> {
                    mounts.reconcileAll();
                    CraftingProjectionService.get(helper.getLevel()).reconcileAll();
                    EnergySharingService.get(helper.getLevel()).reconcileAll();
                });
                perf.nanosPerOp("bridgeNeighborChanged", 500, 300, fixtures::refreshFirstBridge);
                measureStatistics(helper, perf, fixtures);
                helper.assertTrue(mounts.projection(key) != null, "Bridge refresh must keep the projection");
                state[0] = 3;
                window[0] = new PerfMeasure.TickWindow(helper.getLevel().getServer());
                helper.assertTrue(false, "Measuring idle ticks");
            }
            // One operation per tick, as automation does: a held authorization stays on its fast path across ticks
            // only while nothing advances AuthorityEpoch or refreshes the dependency index in a static scene.
            var consumer = fixtures.mainGrid().getStorageService().getInventory();
            helper.assertValueEqual(consumer.extract(iron, 1, Actionable.SIMULATE, IActionSource.empty()), 1L,
                    "The idle scene must keep its projection");
            if (window[0].ticks() <= IDLE_SETTLE_TICKS) {
                idleStart[0] = AuthorityEpoch.current();
                idleStart[1] = mounts.dependencyRefreshCount();
            }
            helper.assertTrue(window[0].ticks() > IDLE_SETTLE_TICKS + WINDOW_TICKS, "Measuring idle ticks");
            perf.record("idleTick", window[0].medianTickNanos(), "ns/tick");
            perf.count("idleAuthorityEpochs", AuthorityEpoch.current() - idleStart[0]);
            perf.count("idleDependencyRefreshes", mounts.dependencyRefreshCount() - idleStart[1]);
            helper.assertValueEqual(bulk.amount(iron), 1_000_000L, "Every benchmark insert must be extracted again");
            fixtures.close();
        });
    }

    /** Ten subnets with a thousand buses each, all of them on the main network itself: the AE2 floor. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 12000, required = true, manualOnly = true)
    public static void perfBusesTenSubnetsDirect(GameTestHelper helper) {
        busFarm(helper, "perfbusestensubnetsdirect", BusFarmScene.Layout.DIRECT, 10, 1000);
    }

    /** Ten subnets with a thousand buses each, reaching the main network through a Storage Bus on an ME Interface. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 12000, required = true, manualOnly = true)
    public static void perfBusesTenSubnetsNative(GameTestHelper helper) {
        busFarm(helper, "perfbusestensubnetsnative", BusFarmScene.Layout.NATIVE, 10, 1000);
    }

    /** Ten subnets with a thousand buses each, reaching the main network through a Bridge and a storage rule. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 12000, required = true, manualOnly = true)
    public static void perfBusesTenSubnetsFederation(GameTestHelper helper) {
        busFarm(helper, "perfbusestensubnetsfederation", BusFarmScene.Layout.FEDERATION, 10, 1000);
    }

    /** A thousand subnets with ten buses each, all of them on the main network itself: the AE2 floor. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 12000, required = true, manualOnly = true)
    public static void perfBusesThousandSubnetsDirect(GameTestHelper helper) {
        busFarm(helper, "perfbusesthousandsubnetsdirect", BusFarmScene.Layout.DIRECT, 1000, 10);
    }

    /** A thousand subnets with ten buses each, reaching the main network through Storage Buses on ME Interfaces. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 12000, required = true, manualOnly = true)
    public static void perfBusesThousandSubnetsNative(GameTestHelper helper) {
        busFarm(helper, "perfbusesthousandsubnetsnative", BusFarmScene.Layout.NATIVE, 1000, 10);
    }

    /** A thousand subnets with ten buses each, reaching the main network through Bridges and storage rules. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 12000, required = true, manualOnly = true)
    public static void perfBusesThousandSubnetsFederation(GameTestHelper helper) {
        busFarm(helper, "perfbusesthousandsubnetsfederation", BusFarmScene.Layout.FEDERATION, 1000, 10);
    }

    /** Ten subnets with a thousand buses each, all in one Federation domain of Routers, bundles and Switches. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 12000, required = true, manualOnly = true)
    public static void perfBusesTenSubnetsDomain(GameTestHelper helper) {
        busFarm(helper, "perfbusestensubnetsdomain", new DomainFarmScene(helper, 10, 1000, BUS_TYPES), true, 10, 1000);
    }

    /** A thousand subnets with ten buses each, all in one Federation domain of Routers, bundles and Switches. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 12000, required = true, manualOnly = true)
    public static void perfBusesThousandSubnetsDomain(GameTestHelper helper) {
        busFarm(helper, "perfbusesthousandsubnetsdomain", new DomainFarmScene(helper, 1000, 10, BUS_TYPES), true, 1000,
                10);
    }

    /**
     * As {@link #perfBusesThousandSubnetsDomain}, with the Routers 52 blocks higher: the same networks and buses in a
     * domain of about ten thousand Federation blocks, which has no size limit.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 12000, required = true, manualOnly = true)
    public static void perfBusesThousandSubnetsLargeDomain(GameTestHelper helper) {
        busFarm(helper, "perfbusesthousandsubnetslargedomain", new DomainFarmScene(helper, 1000, 10, BUS_TYPES, 52),
                true, 1000, 10);
    }

    private static void busFarm(GameTestHelper helper, String testId, BusFarmScene.Layout layout, int subnets,
            int busesEach) {
        busFarm(helper, testId, new BusFarmScene(helper, layout, subnets, busesEach, BUS_TYPES),
                layout == BusFarmScene.Layout.FEDERATION, subnets, busesEach);
    }

    /**
     * Builds a bus scene and runs every bus until a hundred ticks reach 99% of AE2's fastest bus rate (or
     * {@link #BUS_WARMUP_TICKS} pass), then reports the median, mean and slowest whole server tick over the next
     * {@link #WINDOW_TICKS} and how many bus operations reached the main network's storage per tick, so systems are
     * compared at the same work. With Federation rules it then turns one rule off and on; in one domain it also breaks
     * and restores the cable to the subdomain farthest from the main network. The setup and each later step report
     * their slowest whole tick over every tick of the step.
     */
    private static void busFarm(GameTestHelper helper, String testId, BusScene scene, boolean federation, int subnets,
            int busesEach) {
        var perf = new PerfMeasure(testId);
        var server = helper.getLevel().getServer();
        var setupStarted = System.nanoTime();
        var mounts = StorageMountService.get(helper.getLevel());
        var stage = new int[] {0};
        var built = server.getTickCount();
        var window = new FullTickClock.Window[1];
        var start = new long[3];
        var full = (long) subnets * busesEach / 5 * 100;
        var warm = new long[] {-1, 0};
        // The slowest whole tick of the current step, and the tick it started.
        var slowest = new long[] {0, built};
        var domain = scene instanceof DomainFarmScene farm ? farm : null;
        var last = domain == null ? null : domain.lastSubdomain();
        Consumer<String> endStep = name -> {
            perf.record(name + "MaxTick", slowest[0] / 1e6, "ms/tick");
            perf.count(name + "Ticks", server.getTickCount() - slowest[1]);
            slowest[0] = 0;
            slowest[1] = server.getTickCount();
        };
        helper.succeedWhen(() -> {
            slowest[0] = Math.max(slowest[0], FullTickClock.lastTickNanos());
            if (stage[0] == 0) {
                helper.assertTrue(scene.build(), "Building the scene");
                perf.count("placements", scene.placements());
                perf.count("buildTicks", server.getTickCount() - built);
                if (domain != null) {
                    perf.count("routers", domain.routers());
                    perf.count("federationBlocks", domain.federationBlocks());
                }
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                helper.assertTrue(scene.ready(), "Waiting for every subnet to reach the main network");
                perf.count("readyTicks", server.getTickCount() - built);
                endStep.accept("setup");
                perf.record("setupSeconds", (System.nanoTime() - setupStarted) / 1e9, "s");
                if (federation) perf.record("allRulesEdit", scene.ruleEditNanos() / 1e6, "ms");
                window[0] = new FullTickClock.Window(server);
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                if (warm[0] < 0) {
                    if (window[0].ticks() % 100 == 0) {
                        var operations = scene.operations();
                        if (operations - warm[1] >= full * 99 / 100 || window[0].ticks() >= BUS_WARMUP_TICKS) {
                            warm[0] = window[0].ticks();
                            perf.count("warmupTicks", warm[0]);
                        }
                        warm[1] = operations;
                    }
                    start[0] = scene.operations();
                    start[1] = AuthorityEpoch.current();
                    start[2] = mounts.dependencyRefreshCount();
                    helper.assertTrue(false, "Running every bus up to its full rate");
                }
                helper.assertTrue(window[0].ticks() > warm[0] + WINDOW_TICKS, "Measuring with every bus working");
                var ticks = window[0].ticks() - warm[0];
                var operations = scene.operations() - start[0];
                helper.assertTrue(operations > 0, "The buses must move items to and from the main network");
                perf.count("subnets", subnets);
                perf.count("buses", (long) subnets * busesEach);
                perf.count("types", BUS_TYPES);
                perf.record("medianTick", window[0].medianTickNanos() / 1e6, "ms/tick");
                perf.record("meanTick", window[0].meanTickNanos() / 1e6, "ms/tick");
                perf.record("maxTick", window[0].maxTickNanos() / 1e6, "ms/tick");
                perf.record("busOperations", (double) operations / ticks, "ops/tick");
                // AE2 runs a working bus at most every 5 ticks: the share of that rate the buses reached.
                perf.record("busRate", 100.0 * operations / ticks / (subnets * busesEach / 5.0), "%");
                perf.count("authorityEpochs", AuthorityEpoch.current() - start[1]);
                perf.count("dependencyRefreshes", mounts.dependencyRefreshCount() - start[2]);
                perf.count("itemsInChests", scene.itemsInChests());
                // AE2 ticks every Grid on every level tick, used or not.
                perf.count("ae2Grids", appeng.hooks.ticking.TickHandler.instance().getGridList().size());
                endStep.accept("running");
                if (!federation) {
                    stage[0] = 6;
                } else {
                    var toggle = scene.toggleOneRule();
                    perf.record("oneRuleOff", toggle[0] / 1e6, "ms");
                    perf.record("oneRuleOn", toggle[1] / 1e6, "ms");
                    stage[0] = domain == null ? 6 : 3;
                    helper.assertTrue(false, "Waiting for the rule toggle to settle");
                }
            }
            if (stage[0] == 3) {
                helper.assertTrue(server.getTickCount() - slowest[1] > 40, "Waiting for the rule toggle to settle");
                helper.assertTrue(domain.reachMain(1, domain.networks()), "Waiting for every subnet to reach main again");
                endStep.accept("ruleToggle");
                domain.breakLastSubdomainCable();
                stage[0] = 4;
                helper.assertTrue(false, "Waiting for the cut subdomain to leave the domain");
            }
            if (stage[0] == 4) {
                helper.assertTrue(!domain.inMainDomain(last[0], last[1]) && domain.cutOff(last[0], last[1]),
                        "Waiting for the cut subdomain to leave the domain");
                helper.assertTrue(domain.reachMain(1, last[0]), "The rest of the domain must keep its storage");
                endStep.accept("cableBreak");
                domain.restoreLastSubdomainCable();
                stage[0] = 5;
                helper.assertTrue(false, "Waiting for the subdomain to rejoin");
            }
            if (stage[0] == 5) {
                helper.assertTrue(domain.reachMain(last[0], last[1]), "Waiting for the subdomain to rejoin");
                endStep.accept("cableRestore");
                stage[0] = 6;
            }
            perf.record("teardown", scene.clear() / 1e6, "ms");
        });
    }

    /**
     * Native AE2 baseline for {@link #perfStorageProjection}: the consumer Grid reaches the same bulk source through an
     * ME Storage Bus facing an ME Interface of the provider Grid instead of a Bridge.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1200, required = true, manualOnly = true)
    public static void perfNativeStorage(GameTestHelper helper) {
        var fixtures = new PolicyBridgeFixtures(helper, new BlockPos(5, 3, 5));
        fixtures.installStorageCells();
        var bulk = new BulkItemStorage(STORAGE_TYPES, 1_000_000L);
        var perf = new PerfMeasure("perfnativestorage");
        var state = new int[] {0};
        var window = new PerfMeasure.TickWindow[1];
        var iron = AEItemKey.of(Items.IRON_INGOT);
        helper.succeedWhen(() -> {
            if (state[0] == 0) {
                helper.assertTrue(fixtures.networksSettled(), "Waiting for storage networks");
                fixtures.addOuterStorageProvider(bulk);
                fixtures.placeNativeStorageBusToInterface();
                state[0] = 1;
                helper.assertTrue(false, "Waiting for the Storage Bus");
            }
            var consumer = fixtures.mainGrid().getStorageService().getInventory();
            var source = new PlayerSource(helper.makeMockPlayer(GameType.CREATIVE));
            if (state[0] == 1) {
                helper.assertValueEqual(consumer.extract(iron, 1, Actionable.SIMULATE, source), 1L,
                        "Waiting for the Storage Bus to reach the bulk source");
                perf.nanosPerOp("consumerSimulateExtract", 5000, 300,
                        () -> consumer.extract(iron, 1, Actionable.SIMULATE, source));
                measureNetworkStorage(helper, perf, consumer, source, bulk);
                state[0] = 2;
                window[0] = new PerfMeasure.TickWindow(helper.getLevel().getServer());
                helper.assertTrue(false, "Measuring idle ticks");
            }
            helper.assertTrue(window[0].ticks() > IDLE_SETTLE_TICKS + WINDOW_TICKS, "Measuring idle ticks");
            perf.record("idleTick", window[0].medianTickNanos(), "ns/tick");
            helper.assertValueEqual(bulk.amount(iron), 1_000_000L, "Every benchmark extract must be inserted again");
            fixtures.close();
        });
    }

    /**
     * Metrics shared by the Federation and native storage benchmarks, both taken through the consumer Grid's own
     * network inventory: listing every stack, and one item extracted from and put back into the bulk source.
     */
    private static void measureNetworkStorage(GameTestHelper helper, PerfMeasure perf, MEStorage consumer,
            PlayerSource source, BulkItemStorage bulk) {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var listed = new KeyCounter();
        consumer.getAvailableStacks(listed);
        helper.assertTrue(listed.size() >= STORAGE_TYPES, "The consumer network must list every bulk type, saw "
                + listed.size());
        perf.nanosPerOp("networkAvailableStacks", 2000, 300, () -> consumer.getAvailableStacks(new KeyCounter()));
        helper.assertValueEqual(consumer.extract(iron, 1, Actionable.MODULATE, source), 1L,
                "The consumer network must extract from the bulk source");
        helper.assertValueEqual(consumer.insert(iron, 1, Actionable.MODULATE, source), 1L,
                "The consumer network must insert into the bulk source");
        helper.assertValueEqual(bulk.amount(iron), 1_000_000L, "The insert must return to the bulk source");
        perf.nanosPerOp("networkExtractInsert", 5000, 300, () -> {
            consumer.extract(iron, 1, Actionable.MODULATE, source);
            consumer.insert(iron, 1, Actionable.MODULATE, source);
        });
        helper.assertValueEqual(bulk.amount(iron), 1_000_000L, "Every benchmark extract must return to the bulk source");
    }

    /**
     * One second of the statistics page's bookkeeping with both networks in a domain: on average, and in a second
     * that reads the provider's own stock of every bulk type. A book of its own, so the level's saved one is untouched.
     */
    private static void measureStatistics(GameTestHelper helper, PerfMeasure perf, PolicyBridgeFixtures fixtures) {
        var book = new StatisticsBook<AEKey>();
        var statistics = new FederationStatistics(helper.getLevel(), System::currentTimeMillis, book, second -> {
        });
        var provider = fixtures.outerNetwork();
        long phase = Math.floorMod(-(long) provider.value().hashCode(), FederationStatistics.STORAGE_PERIOD_SECONDS);
        var second = new long[] {1_000_000L * FederationStatistics.STORAGE_PERIOD_SECONDS + phase};
        statistics.finishSecond(second[0]);
        var items = StatisticsNames.stored(provider, AEKeyType.items().getId().toString());
        helper.assertTrue(book.latest(items, StatisticsRange.SECONDS, second[0]) >= STORAGE_TYPES * 1_000_000d,
                "Statistics must read the provider's own stock of every bulk type");
        perf.nanosPerOp("statisticsSecond", 2000, 300, () -> statistics.finishSecond(++second[0]));
        second[0] += FederationStatistics.STORAGE_PERIOD_SECONDS - Math.floorMod(second[0] - phase,
                FederationStatistics.STORAGE_PERIOD_SECONDS);
        perf.nanosPerOp("statisticsSecondReadingStock", 500, 300, () -> {
            statistics.finishSecond(second[0]);
            second[0] += FederationStatistics.STORAGE_PERIOD_SECONDS;
        });
    }

    /** A plane of Federation Cables: first publication, idle ticking, and a cable toggled at the edge every tick. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "scale_36_empty",
            timeoutTicks = 2400, required = true, manualOnly = true)
    public static void perfTopologyPlane(GameTestHelper helper) {
        var perf = new PerfMeasure("perftopologyplane");
        var routers = new RouterFixtures(helper);
        var router = new BlockPos(1, 2, 2);
        routers.nativePorts().placeChest(router.west());
        var corner = new BlockPos(2, 2, 2);
        var far = corner.offset(PLANE - 1, 0, PLANE - 1);
        var toggle = new BlockPos(2 + PLANE, 2, 2 + PLANE / 2);
        var state = new int[] {0};
        var window = new PerfMeasure.TickWindow[1];
        var placedAt = new int[1];
        helper.succeedWhen(() -> {
            var level = helper.getLevel();
            var registry = FederationDomainRegistryAccess.get(level);
            if (state[0] == 0) {
                helper.assertTrue(routers.nativePorts().chestNode(router.west()).getGrid() != null
                        && FederationDomainRegistryAccess.confirmedNetworkId(
                                routers.nativePorts().chestNode(router.west()).getGrid()).isPresent(),
                        "Waiting for the native network");
                routers.placeSwitch(router);
                for (var x = 0; x < PLANE; x++) {
                    for (var z = 0; z < PLANE; z++) {
                        routers.placeFederationCable(corner.offset(x, 0, z));
                    }
                }
                state[0] = 1;
                placedAt[0] = level.getServer().getTickCount();
                window[0] = new PerfMeasure.TickWindow(level.getServer());
                helper.assertTrue(false, "Waiting for the plane domain");
            }
            if (state[0] == 1) {
                var domain = registry.federationDomainOf(FederationDomainRegistryAccess.nodeId(level,
                        helper.absolutePos(far)));
                var routerDomain = registry.federationDomainOf(FederationDomainRegistryAccess.nodeId(level,
                        helper.absolutePos(router)));
                helper.assertTrue(domain.isPresent() && routerDomain.isPresent()
                                && domain.get().federationDomainId().equals(routerDomain.get().federationDomainId())
                                && domain.get().nodes().size() >= PLANE * PLANE + 1,
                        "Waiting for the plane domain");
                perf.count("domainNodes", domain.get().nodes().size());
                perf.count("settleTicks", level.getServer().getTickCount() - placedAt[0]);
                perf.record("settleTickTotal", window[0].totalTickNanos(), "ns");
                state[0] = 2;
                window[0] = new PerfMeasure.TickWindow(level.getServer());
                helper.assertTrue(false, "Measuring idle ticks");
            }
            if (state[0] == 2) {
                helper.assertTrue(window[0].ticks() > IDLE_SETTLE_TICKS + WINDOW_TICKS, "Measuring idle ticks");
                perf.record("idleTick", window[0].medianTickNanos(), "ns/tick");
                state[0] = 3;
                window[0] = new PerfMeasure.TickWindow(level.getServer());
                helper.assertTrue(false, "Measuring edge toggles");
            }
            if (window[0].ticks() <= WINDOW_TICKS) {
                var present = level.getBlockState(helper.absolutePos(toggle)).is(RouterRegistration.FEDERATION_CABLE.get());
                helper.setBlock(toggle, present ? Blocks.AIR.defaultBlockState()
                        : RouterRegistration.FEDERATION_CABLE.get().defaultBlockState());
                helper.assertTrue(false, "Measuring edge toggles");
            }
            perf.record("toggleTick", window[0].medianTickNanos(), "ns/tick");
            routers.close();
        });
    }

    /** Server side of the Federation Domain GUI and of accepted-transfer observation. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 2400, required = true, manualOnly = true)
    public static void perfDomainMenu(GameTestHelper helper) {
        ObservationRuntimeEvidence.useHeadlessGameTestTransport();
        var scene = new RealObservationScene(helper, true);
        var perf = new PerfMeasure("perfdomainmenu");
        var state = new int[] {0};
        var window = new PerfMeasure.TickWindow[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(scene.ready(), "Waiting for the observation scene: " + scene.status());
            var level = helper.getLevel();
            var observability = LevelObservabilityService.get(level);
            var scope = scene.processingScope();
            Runnable accepted = () -> observability.recordAccepted(List.of(scope), OperationEventId.create(),
                    "ae2:energy", 1, ResourceUnit.NANO_AE, FlowState.Attribution.EXACT_OPERATION);
            if (state[0] == 0) {
                perf.nanosPerOp("recordAcceptedClosed", 2000, 300, accepted);
                window[0] = new PerfMeasure.TickWindow(level.getServer());
                state[0] = 1;
                helper.assertTrue(false, "Measuring closed-menu ticks");
            }
            if (state[0] == 1) {
                helper.assertTrue(window[0].ticks() > IDLE_SETTLE_TICKS + WINDOW_TICKS, "Measuring closed-menu ticks");
                perf.record("closedTick", window[0].medianTickNanos(), "ns/tick");
                helper.assertTrue(scene.openFirstMenu() && scene.openSecondMenu(), "Both Domain menus must open");
                window[0] = new PerfMeasure.TickWindow(level.getServer());
                state[0] = 2;
                helper.assertTrue(false, "Warming up open menus");
            }
            if (state[0] == 2) {
                // The open-menu paths (domain projection, menu bindings) run only while a menu is open; without a
                // warm-up the window would measure them before the JIT compiled them.
                tickMenus(scene, level.getServer().getTickCount());
                helper.assertTrue(window[0].ticks() > WINDOW_TICKS, "Warming up open menus");
                window[0] = new PerfMeasure.TickWindow(level.getServer());
                state[0] = 3;
                helper.assertTrue(false, "Measuring open-menu ticks");
            }
            if (state[0] == 3) {
                tickMenus(scene, level.getServer().getTickCount());
                helper.assertTrue(window[0].ticks() > WINDOW_TICKS, "Measuring open-menu ticks");
                perf.record("openTick", window[0].medianTickNanos(), "ns/tick");
                perf.nanosPerOp("recordAcceptedOpen", 2000, 300, accepted);
                var session = FederationDomainPolicySession.forBridge(scene.player(), scene.firstBridgeContext());
                helper.assertTrue(!session.workspaceChoices().isEmpty() && !session.graphSnapshotText().isEmpty(),
                        "The Domain session must describe its domain");
                perf.nanosPerOp("workspaceChoices", 2000, 300, session::workspaceChoices);
                perf.nanosPerOp("graphSnapshotText", 2000, 300, session::graphSnapshotText);
                perf.nanosPerOp("labels", 2000, 300, () -> {
                    session.membersText();
                    session.networkOverviewText();
                    session.pairFlowText();
                });
                scene.close();
                return;
            }
        });
    }

    /**
     * Gives each open menu its server tick. A mock player ticks its menu only while its chunk is entity-ticking, which
     * depends on where the test was placed, so without this some runs would measure open menus that never update.
     */
    private static void tickMenus(RealObservationScene scene, int tick) {
        MenuBroadcasts.ensureBroadcast(scene.player().containerMenu, tick);
        MenuBroadcasts.ensureBroadcast(scene.secondPlayer().containerMenu, tick);
    }
}
