package space.controlnet.ae2federation.test;

import space.controlnet.ae2federation.crafting.projection.CraftingReturnLedger;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.policy.RuleMode;
import space.controlnet.ae2federation.test.crafting.PatternProjectionFixture;

/** Independent provider test doubles exercise native registration and recipe ordering on real Grids. */
@PrefixGameTestTemplate(false)
public final class ProjectionReviewGameTests {
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 600)
    public static void projectionGlobalPriority(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper);
        var stage = new int[1];
        var providers = new Provider[2];
        var output = AEItemKey.of(Items.DIAMOND);
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] > 0 || fixture.ready(), "Waiting for native Grids");
            if (stage[0] == 0) {
                for (int index = 0; index < 2; index++) {
                    var stack = PatternDetailsHelper.encodeProcessingPattern(List.of(
                            new GenericStack(AEItemKey.of(index == 0 ? Items.IRON_INGOT : Items.GOLD_INGOT), 1)),
                            List.of(new GenericStack(output, 1)));
                    providers[index] = new Provider(PatternDetailsHelper.decodePattern(stack, helper.getLevel()));
                    fixture.providerGrid().getCraftingService().addGlobalCraftingProvider(providers[index]);
                }
                providers[0].priority = 10;
                fixture.providerGrid().getCraftingService().refreshGlobalCraftingProvider(providers[0]);
                fixture.setRule(fixture.crafting(), RuleMode.ENABLED);
                stage[0] = 1;
            }
            var consumer = fixture.consumerService();
            if (stage[0] <= 6) {
                helper.assertValueEqual(consumer.getCraftingFor(output).size(), 2,
                        "Both independent official global registrations must be projected");
            }
            if (stage[0] == 1 || stage[0] == 3 || stage[0] == 5) {
                int preferred = stage[0] == 3 ? 1 : 0;
                helper.assertTrue(consumer.getCraftingFor(output).iterator().next() == providers[preferred].pattern,
                        "Waiting for priority-only refresh of the native recipe order");
                fixture.putInConsumer(AEItemKey.of(Items.IRON_INGOT), 1);
                fixture.putInConsumer(AEItemKey.of(Items.GOLD_INGOT), 1);
                fixture.begin(output, 1);
                stage[0]++;
            }
            if (stage[0] == 2 || stage[0] == 4 || stage[0] == 6) {
                helper.assertTrue(fixture.planReady(), "Waiting for actual native crafting calculation");
                int preferred = stage[0] == 4 ? 1 : 0;
                helper.assertFalse(fixture.plan().simulation(), "Both candidate recipes have local input materials");
                helper.assertTrue(fixture.plan().patternTimes().containsKey(providers[preferred].pattern),
                        "The actual plan must use the newly preferred recipe");
                helper.assertFalse(fixture.plan().patternTimes().containsKey(providers[1 - preferred].pattern),
                        "The plan must not silently keep the previous priority");
                if (stage[0] < 6) {
                    int changed = stage[0] == 2 ? 1 : 0;
                    providers[changed].priority = stage[0] == 2 ? 20 : 30;
                    fixture.providerGrid().getCraftingService().refreshGlobalCraftingProvider(providers[changed]);
                    stage[0]++;
                    helper.fail("Waiting for the next priority edit to reach the native planner");
                }
                for (var provider : providers) fixture.providerGrid().getCraftingService().removeGlobalCraftingProvider(provider);
                stage[0] = 7;
            }
            helper.assertFalse(consumer.isCraftable(output), "Global removal must withdraw both projections");
            space.controlnet.ae2federation.test.policy.PolicyEvidence.write("projectionglobalpriority", 12,
                    java.util.Map.of("nativePlans", "3", "priorityEdits", "2", "withdrawn", "true"));
            fixture.close();
        });
    }

    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 600)
    public static void projectionRejectedArrival(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for native Grids");
            var key = PatternProjectionFixture.stone();
            var ledger = new CraftingReturnLedger();
            var executing = fixture.providerNetwork();
            var consumer = fixture.consumerNetwork();
            var mounted = new MEStorage[1];
            var mountedRouter = new appeng.api.storage.IStorageProvider[1];
            try {
                var add = CraftingReturnLedger.class.getDeclaredMethod("add",
                        space.controlnet.ae2federation.identity.NetworkId.class,
                        space.controlnet.ae2federation.identity.NetworkId.class, appeng.api.stacks.AEKey.class, long.class);
                add.setAccessible(true);
                add.invoke(ledger, executing, consumer, key, 8L);
                var constructor = Class.forName("space.controlnet.ae2federation.crafting.projection.CraftingReturnRouter")
                        .getDeclaredConstructors()[0];
                constructor.setAccessible(true);
                var router = (appeng.api.storage.IStorageProvider) constructor.newInstance(executing,
                        fixture.providerGrid(), ledger, (java.util.function.Function<Object, Object>) ignored -> null);
                fixture.providerGrid().getStorageService().addGlobalStorageProvider(router);
                mountedRouter[0] = router;
                mounted[0] = fixture.providerGrid().getStorageService().getInventory();
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure);
            }
            var cell = fixture.takeProviderCell();
            for (int retry = 0; retry < 12; retry++) {
                helper.assertValueEqual(mounted[0].insert(key, 1, Actionable.MODULATE, IActionSource.empty()), 0L,
                        "The machine retains the rejected single item");
                helper.assertValueEqual(ledger.held(executing, consumer, key), 0L,
                        "Rejected attempts are not distinct accepted arrivals");
            }
            fixture.restoreProviderCell(cell);
            helper.assertValueEqual(mounted[0].insert(key, 1, Actionable.SIMULATE, IActionSource.empty()), 1L,
                    "Simulation sees available native storage");
            helper.assertValueEqual(ledger.held(executing, consumer, key), 0L, "Simulation has no ledger effects");
            helper.assertValueEqual(mounted[0].insert(key, 1, Actionable.MODULATE, IActionSource.empty()), 1L,
                    "One actual accepted item leaves the machine");
            helper.assertValueEqual(ledger.held(executing, consumer, key), 1L, "Exactly one accepted arrival is held");
            helper.assertValueEqual(fixture.held(fixture.providerChest(), key), 1L, "The item really reached local storage");
            space.controlnet.ae2federation.test.policy.PolicyEvidence.write("projectionrejectedarrival", 30,
                    java.util.Map.of("rejectedRetries", "12", "heldAfterRetries", "0", "heldAfterAcceptance", "1"));
            fixture.providerGrid().getStorageService().removeGlobalStorageProvider(mountedRouter[0]);
            fixture.close();
        });
    }

    /** Manual machine output is fault injection; the CPU, planning, storage, topology and SavedData are native. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 1400)
    public static void projectionCompletedLateReturns(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper);
        var stage = new int[1];
        var sweeps = new long[1];
        var cell = new net.minecraft.world.item.ItemStack[1];
        var main = AEItemKey.of(Items.DIAMOND);
        var byproduct = AEItemKey.of(Items.GLASS_BOTTLE);
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] > 0 || fixture.ready(), "Waiting for native Grids");
            if (stage[0] == 0) {
                fixture.installProcessingPattern(PatternDetailsHelper.encodeProcessingPattern(
                        List.of(new GenericStack(PatternProjectionFixture.cobblestone(), 1)),
                        List.of(new GenericStack(main, 1), new GenericStack(byproduct, 3))));
                fixture.setRule(fixture.crafting(), RuleMode.ENABLED);
                fixture.putInConsumer(PatternProjectionFixture.cobblestone(), 1);
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                helper.assertTrue(fixture.consumerService().isCraftable(main), "Waiting for projection");
                fixture.begin(main, 1);
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                helper.assertTrue(fixture.planReady(), "Waiting for plan");
                helper.assertTrue(fixture.submit(), "Native CPU accepts the plan");
                stage[0] = 3;
            }
            if (stage[0] == 3) {
                helper.assertValueEqual(fixture.inMachine(Items.COBBLESTONE), 1L, "Waiting for machine input");
                helper.assertValueEqual(fixture.owed(byproduct), 3L, "Three real pattern byproducts are owed");
                helper.assertValueEqual(fixture.providerGrid().getStorageService().getInventory().insert(
                        main, 1, Actionable.MODULATE, IActionSource.empty()), 1L, "Main output reaches native CPU");
                stage[0] = 4;
            }
            if (stage[0] == 4) {
                helper.assertValueEqual(fixture.busyConsumerCpus(), 0L, "Main output completes native CPU before byproducts");
                fixture.disconnect();
                sweeps[0] = fixture.ledgerSweeps();
                stage[0] = 5;
            }
            if (stage[0] == 5) {
                helper.assertTrue(fixture.ledgerSweeps() >= sweeps[0] + 4, "Wait beyond the previous two-sweep cleanup");
                helper.assertValueEqual(fixture.owed(byproduct), 3L, "Unarrived byproducts remain owed after completion");
                helper.assertValueEqual(fixture.providerGrid().getStorageService().getInventory().insert(
                        byproduct, 1, Actionable.MODULATE, IActionSource.empty()), 1L, "First late arrival is accepted");
                cell[0] = fixture.takeConsumerCell();
                fixture.reconnect();
                stage[0] = 6;
            }
            if (stage[0] == 6) {
                helper.assertTrue(fixture.connected(), "Waiting for reconnection");
                sweeps[0] = fixture.ledgerSweeps();
                stage[0] = 7;
            }
            if (stage[0] == 7) {
                helper.assertTrue(fixture.ledgerSweeps() >= sweeps[0] + 4, "Several refused return sweeps");
                helper.assertValueEqual(fixture.owed(byproduct), 3L, "Refusal never retires responsibility");
                helper.assertValueEqual(fixture.heldForConsumer(byproduct), 1L, "Only one actual arrival is held");
                // Round-trip SavedData in this server. A separate production run is needed for a real process restart.
                var level = helper.getLevel();
                var saved = CraftingReturnLedger.get(level).save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
                space.controlnet.ae2federation.crafting.projection.CraftingProjectionService.closeServer(level.getServer());
                level.getServer().overworld().getDataStorage().set(CraftingReturnLedger.DATA_NAME,
                        CraftingReturnLedger.load(saved, level.registryAccess()));
                fixture.restoreConsumerCell(cell[0]);
                space.controlnet.ae2federation.crafting.projection.CraftingProjectionService.get(level)
                        .observeConnectedGrids(fixture.consumerGrid(), fixture.providerGrid());
                stage[0] = 8;
            }
            if (stage[0] == 8) {
                helper.assertValueEqual(fixture.held(fixture.consumerChest(), byproduct), 1L, "Saved held arrival returns after capacity is restored");
                helper.assertValueEqual(fixture.owed(byproduct), 2L, "Unarrived remainder still belongs to consumer");
                helper.assertValueEqual(fixture.providerGrid().getStorageService().getInventory().insert(
                        byproduct, 2, Actionable.MODULATE, IActionSource.empty()), 2L, "Second batch arrives after many sweeps");
                stage[0] = 9;
            }
            helper.assertValueEqual(fixture.owed(byproduct), 0L, "All three actual deliveries settle responsibility");
            helper.assertValueEqual(fixture.held(fixture.consumerChest(), byproduct), 3L, "No byproduct was lost or duplicated");
            helper.assertTrue(space.controlnet.ae2federation.policy.PolicyService.get(helper.getLevel())
                    .configured(fixture.storage()).isEmpty(), "Storage sharing was never configured");
            space.controlnet.ae2federation.test.policy.PolicyEvidence.write("projectioncompletedlatereturns", 16,
                    java.util.Map.of("storageEverEnabled", "false", "completedBeforeReturns", "true", "byproducts", "3",
                            "restart", "SavedData round trip only"));
            fixture.close();
        });
    }

    /** An independent ICraftingCPU and service view are explicit test doubles, never native CPU subclasses. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 900)
    public static void projectionIndependentCpuUnknown(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper);
        var stage = new int[1];
        var sweeps = new long[1];
        var cpu = new IndependentCpu();
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] > 0 || fixture.ready(), "Waiting for native consumer and executing Grids");
            var level = helper.getLevel();
            var service = space.controlnet.ae2federation.crafting.projection.CraftingProjectionService.get(level);
            var ledger = CraftingReturnLedger.get(level);
            if (stage[0] == 0) {
                service.observeConnectedGrids(fixture.consumerGrid(), fixture.providerGrid());
                invoke(ledger, "add", new Class<?>[] {space.controlnet.ae2federation.identity.NetworkId.class,
                        space.controlnet.ae2federation.identity.NetworkId.class, appeng.api.stacks.AEKey.class, long.class},
                        fixture.providerNetwork(), fixture.consumerNetwork(), PatternProjectionFixture.stone(), 8L);
                var crafting = java.lang.reflect.Proxy.newProxyInstance(getClassLoader(),
                        new Class<?>[] {appeng.api.networking.crafting.ICraftingService.class},
                        (object, method, arguments) -> switch (method.getName()) {
                            case "getCpus" -> com.google.common.collect.ImmutableSet.of(cpu);
                            default -> method.invoke(fixture.consumerService(), arguments);
                        });
                var grid = java.lang.reflect.Proxy.newProxyInstance(getClassLoader(),
                        new Class<?>[] {appeng.api.networking.IGrid.class},
                        (object, method, arguments) -> method.getName().equals("getCraftingService") ? crafting
                                : method.invoke(fixture.consumerGrid(), arguments));
                cpu.busy = false;
                var idle = invoke(service, "observeJobs", new Class<?>[] {appeng.api.networking.IGrid.class}, grid);
                invoke(service, "watchJobs", new Class<?>[] {CraftingReturnLedger.class,
                        space.controlnet.ae2federation.identity.NetworkId.class, idle.getClass()},
                        ledger, fixture.consumerNetwork(), idle);
                cpu.busy = true;
                var running = invoke(service, "observeJobs", new Class<?>[] {appeng.api.networking.IGrid.class}, grid);
                invoke(service, "watchJobs", new Class<?>[] {CraftingReturnLedger.class,
                        space.controlnet.ae2federation.identity.NetworkId.class, running.getClass()},
                        ledger, fixture.consumerNetwork(), running);
                helper.assertTrue(ledger.hasUnknownJobs(fixture.consumerNetwork()), "A second CPU observation in the same tick must persist a newly started independent job");
                helper.assertTrue(ledger.jobs(fixture.consumerNetwork()).isEmpty(), "No native CPU subclass supplied the lifecycle");
                // This view disappears while the real consumer Grid remains; no CPU job is observable on later sweeps.
                cpu.cancelJob();
                sweeps[0] = service.ledgerSweeps();
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                helper.assertTrue(service.ledgerSweeps() >= sweeps[0] + 4, "Four sweeps without observable independent CPU");
                helper.assertValueEqual(fixture.owed(PatternProjectionFixture.stone()), 8L, "Absence/cancel cannot erase debt");
                var saved = ledger.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
                var restored = CraftingReturnLedger.load(saved, level.registryAccess());
                helper.assertTrue(restored.hasUnknownJobs(fixture.consumerNetwork()), "Restart preserves unknown lifecycle");
                helper.assertValueEqual(restored.owed(fixture.providerNetwork(), fixture.consumerNetwork(),
                        PatternProjectionFixture.stone()), 8L, "Restart preserves all undelivered responsibility");
                space.controlnet.ae2federation.test.policy.PolicyEvidence.write("projectionindependentcpuunknown", 5,
                        java.util.Map.of("implementation", "independent ICraftingCPU test double", "unknownPersisted", "true"));
                fixture.close();
            }
        });
    }

    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 900)
    public static void projectionDiscoveryCost(GameTestHelper helper) {
        var fixture = new space.controlnet.ae2federation.test.crafting.ProjectionChainFixture(helper);
        var stage = new int[1];
        var providers = new java.util.ArrayList<Provider>();
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] > 0 || fixture.ready(), "Waiting for three native Grids");
            var source = fixture.grid(2).getCraftingService();
            var service = space.controlnet.ae2federation.crafting.projection.CraftingProjectionService.get(helper.getLevel());
            if (stage[0] == 0) {
                var stack = PatternDetailsHelper.encodeProcessingPattern(
                        List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)),
                        List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1)));
                var pattern = PatternDetailsHelper.decodePattern(stack, helper.getLevel());
                for (int i = 0; i < 64; i++) {
                    var provider = new Provider(pattern);
                    providers.add(provider);
                    source.addGlobalCraftingProvider(provider);
                }
                fixture.enableChain(RuleMode.REEXPORT);
                stage[0] = 1;
            }
            helper.assertValueEqual(fixture.projections(0, 2), 65, "Consumer sees all 64 distinct globals plus native provider");
            helper.assertValueEqual(fixture.projections(1, 2), 65, "Middle independently sees the same source providers");
            space.controlnet.ae2federation.crafting.projection.ProjectionDiagnostics.reset();
            service.reconcileAll();
            var counts = space.controlnet.ae2federation.crafting.projection.ProjectionDiagnostics.snapshot();
            helper.assertValueEqual(counts.discoveries(), 2L, "One discovery each for middle/source, not one per relationship");
            helper.assertValueEqual(counts.relationships(), 3L, "All three consumer/source relationships are still maintained");
            org.slf4j.LoggerFactory.getLogger(ProjectionReviewGameTests.class).info("AE2F_PROJECTION_COST {}", counts);
            var emerald = AEItemKey.of(Items.EMERALD);
            providers.getFirst().pattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)), List.of(new GenericStack(emerald, 1))), helper.getLevel());
            source.refreshGlobalCraftingProvider(providers.getFirst());
            service.reconcileAll();
            helper.assertTrue(fixture.canCraft(0, emerald) && fixture.canCraft(1, emerald),
                    "A native global pattern update reaches both consumers");
            fixture.enableMutual();
            service.reconcileAll();
            helper.assertValueEqual(fixture.projections(2, 0), 1, "Ring never reprojects its own globals through consumer projections");
            helper.assertValueEqual(fixture.projections(0, 2), 65, "Ring still contains exactly the 65 real source providers");
            for (var provider : providers) source.removeGlobalCraftingProvider(provider);
            service.reconcileAll();
            helper.assertFalse(fixture.canCraft(0, emerald) || fixture.canCraft(1, emerald),
                    "Native global withdrawal reaches both consumers even with the ring");
            space.controlnet.ae2federation.test.policy.PolicyEvidence.write("projectiondiscoverycost", 8,
                    java.util.Map.of("providers", "64", "sourceDiscoveries", "2", "relationships", "3",
                            "discoveryNanos", Long.toString(counts.discoveryNanos())));
            fixture.close();
        });
    }

    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 600)
    public static void projectionNativeFallbackOrder(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for native storage");
            var key = PatternProjectionFixture.stone();
            var action = IActionSource.empty();
            for (int order = 0; order < 2; order++) {
                for (int preferencePriority : new int[] {9, 10, 11}) {
                    var preferred = new space.controlnet.ae2federation.test.storage.SourceIndexFixtures.CountingStorage("preferred", 2);
                    var other = new space.controlnet.ae2federation.test.storage.SourceIndexFixtures.CountingStorage("other", 4);
                    var filtered = new appeng.me.storage.MEInventoryHandler(preferred);
                    filtered.setPartitionList(new appeng.util.prioritylist.IPartitionList() {
                        public boolean isListed(appeng.api.stacks.AEKey what) { return key.equals(what); }
                        public boolean isEmpty() { return false; }
                        public Iterable<appeng.api.stacks.AEKey> getItems() { return List.of(key); }
                    });
                    var first = order == 0 ? fixture.mountOnProvider(filtered, preferencePriority) : fixture.mountOnProvider(other, 10);
                    var second = order == 0 ? fixture.mountOnProvider(other, 10) : fixture.mountOnProvider(filtered, preferencePriority);
                    helper.assertValueEqual(fixture.providerGrid().getStorageService().getInventory().insert(key, 3,
                            Actionable.MODULATE, action), 3L, "Native reference accepts the residual");
                    long expectedPreferred = preferred.amount(key);
                    long expectedOther = other.amount(key);
                    preferred.extract(key, 4, Actionable.MODULATE, action);
                    other.extract(key, 4, Actionable.MODULATE, action);
                    var source = space.controlnet.ae2federation.test.crafting.FaultStorage.extractOnly();
                    source.put(key, 4);
                    var removeSource = fixture.mountOnProvider(source, -100);
                    var target = new space.controlnet.ae2federation.test.crafting.FaultStorage(4, 1);
                    var ledger = new CraftingReturnLedger();
                    helper.assertValueEqual(space.controlnet.ae2federation.crafting.projection.HeldReturnTransfer.handBack(
                            ledger, fixture.providerNetwork(), fixture.providerGrid(), fixture.consumerNetwork(), key, 4, target),
                            1L, "Real target accepts only one of four simulated units");
                    helper.assertValueEqual(preferred.amount(key), expectedPreferred, "Residual uses native preferred/priority order");
                    helper.assertValueEqual(other.amount(key), expectedOther, "Residual uses native non-preferred/mount order");
                    helper.assertValueEqual(preferred.amount(key) + other.amount(key) + target.held(key) + source.held(key),
                            4L, "Actual target plus all sources conserve four items");
                    first.run();
                    second.run();
                    removeSource.run();
                }
            }
            space.controlnet.ae2federation.test.policy.PolicyEvidence.write("projectionnativefallbackorder", 30,
                    java.util.Map.of("orders", "2", "priorityCombinations", "3", "reference", "native NetworkStorage"));
            fixture.close();
        });
    }

    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 600)
    public static void projectionOrphanTransitRecovery(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for a native recovery destination");
            var level = helper.getLevel();
            var ledger = CraftingReturnLedger.get(level);
            var orphan = space.controlnet.ae2federation.identity.NetworkId.create();
            var key = PatternProjectionFixture.stone();
            fixture.putInProvider(key, 3);
            long removed = fixture.providerChest().extract(key, 3, Actionable.MODULATE, IActionSource.empty());
            invoke(ledger, "addTransit", new Class<?>[] {space.controlnet.ae2federation.identity.NetworkId.class,
                    appeng.api.stacks.AEKey.class, long.class}, orphan, key, removed);
            var saved = ledger.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
            ledger = CraftingReturnLedger.load(saved, level.registryAccess());
            level.getServer().overworld().getDataStorage().set(CraftingReturnLedger.DATA_NAME, ledger);
            net.minecraft.core.BlockPos destination = null;
            for (var node : fixture.providerGrid().getNodes()) {
                if (node.getOwner() instanceof appeng.blockentity.storage.MEChestBlockEntity chest) {
                    destination = chest.getBlockPos();
                    break;
                }
            }
            helper.assertTrue(destination != null, "A real ME chest is the destination");
            var command = "ae2federation returns recover-transit " + orphan.value() + " "
                    + destination.getX() + " " + destination.getY() + " " + destination.getZ();
            var source = level.getServer().createCommandSourceStack().withLevel(level);
            var cell = fixture.takeProviderCell();
            level.getServer().getCommands().performPrefixedCommand(source, command);
            helper.assertValueEqual(ledger.transit(orphan, key), 3L, "A refusing destination leaves all saved transit");
            fixture.restoreProviderCell(cell);
            level.getServer().getCommands().performPrefixedCommand(source, command);
            helper.assertValueEqual(ledger.transit(orphan, key), 0L, "Accepted real stock leaves the orphan ledger");
            helper.assertValueEqual(fixture.held(fixture.providerChest(), key), 3L, "All three physical items are recovered");
            level.getServer().getCommands().performPrefixedCommand(source, command);
            helper.assertValueEqual(fixture.held(fixture.providerChest(), key), 3L, "Repeated recovery cannot duplicate stock");
            space.controlnet.ae2federation.test.policy.PolicyEvidence.write("projectionorphantransitrecovery", 5,
                    java.util.Map.of("orphanIdentity", "never assigned to a Grid", "recovered", "3", "repeated", "no duplicate"));
            fixture.close();
        });
    }

    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 100)
    public static void identityFreshSettlementEvent(GameTestHelper helper) {
        var notifications = new int[1];
        var node = appeng.api.networking.GridHelper.createManagedNode(new Object(),
                new appeng.api.networking.IGridNodeListener<Object>() {
                    @Override
                    public void onSaveChanges(Object owner, appeng.api.networking.IGridNode changed) { notifications[0]++; }
                }).setInWorldNode(false).setIdlePowerUsage(0);
        node.create(helper.getLevel(), helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1)));
        var identity = node.getGrid().getService(space.controlnet.ae2federation.identity.NetworkIdentityService.class);
        helper.assertValueEqual(identity.settlement().status(),
                space.controlnet.ae2federation.identity.IdentityStatus.PARTIAL_LOAD,
                "A wholly fresh identity cannot be persisted as a settled policy endpoint in its minting tick");
        int before = notifications[0];
        helper.runAfterDelay(2, () -> {
            helper.assertValueEqual(identity.settlement().status(),
                    space.controlnet.ae2federation.identity.IdentityStatus.SETTLED,
                    "Fresh identity settles after its adoption tick");
            helper.assertTrue(notifications[0] > before, "Tick settlement actively notifies native boundary listeners");
            node.destroy();
            space.controlnet.ae2federation.test.policy.PolicyEvidence.write("identityfreshsettlementevent", 3,
                    java.util.Map.of("mintingTick", "PARTIAL_LOAD", "later", "SETTLED", "nativeNotification", "true"));
            helper.succeed();
        });
    }

    /** Injected completed obligations isolate sweep cost; physical mounts and both consumer Grids are native. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 600)
    public static void projectionHeldSweepCost(GameTestHelper helper) {
        var fixture = new space.controlnet.ae2federation.test.crafting.ProjectionChainFixture(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for three native Grids");
            var level = helper.getLevel();
            var service = space.controlnet.ae2federation.crafting.projection.CraftingProjectionService.get(level);
            service.observeFederationDomainMembers(List.of(fixture.grid(0), fixture.grid(1), fixture.grid(2)));
            var keys = net.minecraft.core.registries.BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR)
                    .limit(32).map(AEItemKey::of).toList();
            var stock = new space.controlnet.ae2federation.test.storage.SourceIndexFixtures.CountingStorage("held backlog", 128)
                    .preload(keys, 2);
            appeng.api.storage.IStorageProvider mount = inventories -> inventories.mount(stock, -10);
            fixture.grid(2).getStorageService().addGlobalStorageProvider(mount);
            var ledger = CraftingReturnLedger.get(level);
            var networkType = space.controlnet.ae2federation.identity.NetworkId.class;
            for (int consumer = 0; consumer < 2; consumer++) {
                var id = fixture.network(consumer);
                var job = java.util.UUID.randomUUID();
                invoke(ledger, "watch", new Class<?>[] {networkType, java.util.UUID.class, String.class,
                        net.minecraft.core.BlockPos.class}, id, job, level.dimension().location().toString(),
                        helper.absolutePos(net.minecraft.core.BlockPos.ZERO));
                invoke(ledger, "end", new Class<?>[] {networkType, java.util.UUID.class, CraftingReturnLedger.JobEnd.class},
                        id, job, CraftingReturnLedger.JobEnd.DONE);
                for (var key : keys) {
                    var signature = new Class<?>[] {networkType, networkType, appeng.api.stacks.AEKey.class, long.class};
                    invoke(ledger, "add", signature, fixture.network(2), id, key, 1L);
                    invoke(ledger, "hold", signature, fixture.network(2), id, key, 1L);
                }
            }
            space.controlnet.ae2federation.crafting.projection.ProjectionDiagnostics.reset();
            long started = System.nanoTime();
            invoke(service, "sweepLedger", new Class<?>[0]);
            long nanos = System.nanoTime() - started;
            var counts = space.controlnet.ae2federation.crafting.projection.ProjectionDiagnostics.snapshot();
            helper.assertValueEqual(counts.mountViews(), 1L, "64 debts share one valid native mount view");
            helper.assertValueEqual(stock.total(), 0L, "All 64 real stocked items reach consumers");
            for (int consumer = 0; consumer < 2; consumer++) {
                for (var key : keys) {
                    helper.assertValueEqual(fixture.grid(consumer).getStorageService().getInventory().getAvailableStacks().get(key),
                            1L, "Each consumer receives one of each actual key");
                    helper.assertValueEqual(ledger.owed(fixture.network(2), fixture.network(consumer), key), 0L,
                            "Only actual delivery clears each debt");
                }
            }
            fixture.grid(2).getStorageService().removeGlobalStorageProvider(mount);
            org.slf4j.LoggerFactory.getLogger(ProjectionReviewGameTests.class).info("AE2F_HELD_COST debts=64 keys=32 consumers=2 sweepNanos={} {}", nanos, counts);
            space.controlnet.ae2federation.test.policy.PolicyEvidence.write("projectionheldsweepcost", 130,
                    java.util.Map.of("debts", "64", "keys", "32", "consumers", "2", "mountViews", "1", "sweepNanos", Long.toString(nanos)));
            fixture.close();
        });
    }

    /** Actual native access wrappers and a reentrant unmount; synthetic inventories hold exactly four test items. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 600)
    public static void projectionNativeAccessAndRemount(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper);
        int[] stage = {0};
        Runnable[] unmount = new Runnable[2];
        var key = PatternProjectionFixture.stone();
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] > 0 || fixture.ready(), "Waiting for native mounts");
            if (stage[0] == 0) {
                fixture.takeProviderCell();
                var readStock = new space.controlnet.ae2federation.test.storage.SourceIndexFixtures.CountingStorage("read-only", 8);
                var writeStock = new space.controlnet.ae2federation.test.storage.SourceIndexFixtures.CountingStorage("write-only", 8);
                readStock.insert(key, 2, Actionable.MODULATE, IActionSource.empty());
                writeStock.insert(key, 2, Actionable.MODULATE, IActionSource.empty());
                var read = new appeng.me.storage.MEInventoryHandler(readStock);
                read.setAllowInsertion(false);
                var write = new appeng.me.storage.MEInventoryHandler(writeStock);
                write.setAllowExtraction(false);
                write.setExtractFiltering(true, false);
                unmount[0] = fixture.mountOnProvider(read, 10);
                unmount[1] = fixture.mountOnProvider(write, 20);
                var before = ownView(fixture.providerGrid());
                helper.assertValueEqual(before.extract(key, 8, Actionable.SIMULATE, IActionSource.empty()), 2L,
                        "Native write-only wrapper cannot supply held returns");
                var ledger = new CraftingReturnLedger();
                MEStorage refusingTarget = new MEStorage() {
                    public long insert(appeng.api.stacks.AEKey what, long amount, Actionable mode, IActionSource source) {
                        if (mode == Actionable.SIMULATE) return amount;
                        unmount[0].run();
                        return 0;
                    }
                    public net.minecraft.network.chat.Component getDescription() {
                        return net.minecraft.network.chat.Component.literal("Reentrant test target");
                    }
                };
                helper.assertValueEqual(space.controlnet.ae2federation.crafting.projection.HeldReturnTransfer.handBack(ledger,
                        fixture.providerNetwork(), fixture.providerGrid(), fixture.consumerNetwork(), key, 2, refusingTarget),
                        0L, "Refusing target accepts no actual items");
                helper.assertValueEqual(readStock.amount(key), 0L, "Exactly two accessible items were extracted");
                helper.assertValueEqual(writeStock.amount(key), 4L, "Residual returns through the current write-only mount");
                helper.assertValueEqual(before.insert(key, 1, Actionable.MODULATE, IActionSource.empty()), 0L,
                        "The old view refuses operations after a reentrant unmount");
                helper.assertValueEqual(ledger.transit(fixture.providerNetwork(), key), 0L,
                        "Successful native fallback leaves no phantom transit");
                stage[0] = 1;
                helper.fail("Waiting for native storage monitor refresh without invalidateCache");
            }
            helper.assertValueEqual(fixture.providerGrid().getStorageService().getCachedInventory().get(key), 4L,
                    "Native end-of-tick inventory monitoring observes the four conserved items");
            unmount[1].run();
            space.controlnet.ae2federation.test.policy.PolicyEvidence.write("projectionnativeaccessandremount", 7,
                    java.util.Map.of("wrappers", "native read-only/write-only", "unmount", "reentrant", "conserved", "4"));
            fixture.close();
        });
    }

    private static MEStorage ownView(appeng.api.networking.IGrid grid) {
        try {
            var method = space.controlnet.ae2federation.crafting.projection.HeldReturnTransfer.class
                    .getDeclaredMethod("ownStorage", appeng.api.networking.IGrid.class);
            method.setAccessible(true);
            return (MEStorage) method.invoke(null, grid);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static ClassLoader getClassLoader() { return ProjectionReviewGameTests.class.getClassLoader(); }

    private static Object invoke(Object receiver, String name, Class<?>[] parameters, Object... arguments) {
        try {
            var method = receiver.getClass().getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method.invoke(receiver, arguments);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static final class IndependentCpu implements appeng.api.networking.crafting.ICraftingCPU {
        private boolean busy = true;
        public boolean isBusy() { return busy; }
        public appeng.api.networking.crafting.CraftingJobStatus getJobStatus() { return null; }
        public void cancelJob() { busy = false; }
        public long getAvailableStorage() { return 1024; }
        public int getCoProcessors() { return 0; }
        public net.minecraft.network.chat.Component getName() { return net.minecraft.network.chat.Component.literal("Independent test CPU"); }
        public appeng.api.config.CpuSelectionMode getSelectionMode() { return appeng.api.config.CpuSelectionMode.ANY; }
    }

    private static final class Provider implements ICraftingProvider {
        private IPatternDetails pattern;
        private int priority;
        private Provider(IPatternDetails pattern) { this.pattern = pattern; }
        public List<IPatternDetails> getAvailablePatterns() { return List.of(pattern); }
        public int getPatternPriority() { return priority; }
        public boolean pushPattern(IPatternDetails details, KeyCounter[] inputs) { return false; }
        public boolean isBusy() { return false; }
    }
}
