package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.me.helpers.PlayerSource;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.client.policy.FederationDomainPolicySession;
import space.controlnet.ae2federation.crafting.binding.CraftingBindingService;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.energy.EnergyBindingService;
import space.controlnet.ae2federation.observability.LevelObservabilityService;
import space.controlnet.ae2federation.observability.meter.OperationEventId;
import space.controlnet.ae2federation.observability.state.FlowState;
import space.controlnet.ae2federation.observability.state.ResourceUnit;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.storage.mount.StorageMountService;
import space.controlnet.ae2federation.test.perf.BulkItemStorage;
import space.controlnet.ae2federation.test.perf.EnergyMeshScene;
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
    private static final int WINDOW_TICKS = 60;

    private PerformanceBenchmarkGameTests() {
    }

    /** Energy borrowed across a SUPPLY mesh: per-call cost of MODULATE demand and of AE2's stored-power probe. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 2400, required = true, manualOnly = true)
    public static void perfEnergyMesh(GameTestHelper helper) {
        var scene = new EnergyMeshScene(helper, MESH_SIZE, MESH_PADDING);
        var perf = new PerfMeasure("perfenergymesh");
        var state = new int[] {0};
        var window = new PerfMeasure.TickWindow[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(scene.ready(), "Waiting for energy mesh: " + scene.status());
            if (state[0] == 0) {
                perf.count("rules", scene.enableMesh());
                scene.chargeProviders(1.0e9);
                scene.drainConsumer();
                state[0] = 1;
                window[0] = new PerfMeasure.TickWindow(helper.getLevel().getServer());
                helper.assertTrue(false, "Waiting for mesh bindings");
            }
            if (state[0] == 1) {
                helper.assertTrue(window[0].ticks() >= 40, "Letting the consumer drain settle");
                helper.assertTrue(scene.bindingCount() >= MESH_SIZE * (MESH_SIZE - 1),
                        "Every ordered pair must hold an energy binding, found " + scene.bindingCount());
                perf.count("bindings", scene.bindingCount());
                perf.count("consumerNodes", scene.consumerNodeCount());
                var before = scene.providersStored();
                helper.assertValueEqual(scene.extractConsumer(1, Actionable.MODULATE), 1.0,
                        "Consumer demand must be supplied through the mesh");
                helper.assertTrue(scene.consumerCellStored() < 1, "Consumer cell must stay drained");
                helper.assertTrue(before - scene.providersStored() > 0.5, "Provider cells must pay for the demand");
                perf.nanosPerOp("extractModulate", 2000, 300, () -> scene.extractConsumer(1, Actionable.MODULATE));
                perf.nanosPerOp("extractSimulate", 2000, 300, () -> scene.extractConsumer(1, Actionable.SIMULATE));
                var source = scene.consumerSource();
                helper.assertTrue(source.getAECurrentPower() > 1.0e8, "Stored-power probe must see the mesh cells");
                perf.nanosPerOp("storedPowerProbe", 500, 300, source::getAECurrentPower);
                state[0] = 2;
                window[0] = new PerfMeasure.TickWindow(helper.getLevel().getServer());
                helper.assertTrue(false, "Measuring idle ticks");
            }
            helper.assertTrue(window[0].ticks() > WINDOW_TICKS, "Measuring idle ticks");
            perf.record("idleTick", window[0].meanTickNanos(), "ns/tick");
            helper.assertTrue(scene.consumerCellStored() < 1, "Consumer idle drain must still come from the mesh");
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
                perf.nanosPerOp("reconcileAll", 500, 300, () -> {
                    mounts.reconcileAll();
                    CraftingBindingService.get(helper.getLevel()).reconcileAll();
                    EnergyBindingService.get(helper.getLevel()).reconcileAll();
                });
                perf.nanosPerOp("bridgeNeighborChanged", 500, 300, fixtures::refreshFirstBridge);
                helper.assertTrue(mounts.projection(key) != null, "Bridge refresh must keep the projection");
                state[0] = 3;
                window[0] = new PerfMeasure.TickWindow(helper.getLevel().getServer());
                helper.assertTrue(false, "Measuring idle ticks");
            }
            helper.assertTrue(window[0].ticks() > WINDOW_TICKS, "Measuring idle ticks");
            perf.record("idleTick", window[0].meanTickNanos(), "ns/tick");
            helper.assertValueEqual(bulk.amount(iron), 1_000_000L, "Every benchmark insert must be extracted again");
            fixtures.close();
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
                routers.placeRouter(router);
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
                var ticks = Math.min(window[0].ticks() - 1, 100);
                perf.record("settleTickTotal", window[0].meanTickNanos() * ticks, "ns");
                state[0] = 2;
                window[0] = new PerfMeasure.TickWindow(level.getServer());
                helper.assertTrue(false, "Measuring idle ticks");
            }
            if (state[0] == 2) {
                helper.assertTrue(window[0].ticks() > WINDOW_TICKS, "Measuring idle ticks");
                perf.record("idleTick", window[0].meanTickNanos(), "ns/tick");
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
            perf.record("toggleTick", window[0].meanTickNanos(), "ns/tick");
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
                helper.assertTrue(window[0].ticks() > WINDOW_TICKS, "Measuring closed-menu ticks");
                perf.record("closedTick", window[0].meanTickNanos(), "ns/tick");
                helper.assertTrue(scene.openFirstMenu() && scene.openSecondMenu(), "Both Domain menus must open");
                window[0] = new PerfMeasure.TickWindow(level.getServer());
                state[0] = 2;
                helper.assertTrue(false, "Measuring open-menu ticks");
            }
            if (state[0] == 2) {
                helper.assertTrue(window[0].ticks() > WINDOW_TICKS, "Measuring open-menu ticks");
                perf.record("openTick", window[0].meanTickNanos(), "ns/tick");
                perf.nanosPerOp("recordAcceptedOpen", 2000, 300, accepted);
                var session = FederationDomainPolicySession.forBridge(scene.player(), scene.firstBridgeContext());
                helper.assertTrue(!session.workspaceChoices().isEmpty() && !session.graphSnapshotText().isEmpty(),
                        "The Domain session must describe its domain");
                perf.nanosPerOp("workspaceChoices", 2000, 300, session::workspaceChoices);
                perf.nanosPerOp("graphSnapshotText", 2000, 300, session::graphSnapshotText);
                perf.nanosPerOp("labels", 2000, 300, () -> {
                    session.membersText();
                    session.revisionsText();
                    session.networkOverviewText();
                    session.pairFlowText();
                });
                scene.close();
                return;
            }
        });
    }
}
