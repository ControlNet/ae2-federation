package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.IStorageProvider;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.crafting.projection.CraftingProjectionService;
import space.controlnet.ae2federation.crafting.projection.CraftingReturnLedger;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.*;
import space.controlnet.ae2federation.test.crafting.PatternProjectionFixture;
import space.controlnet.ae2federation.test.crafting.ProjectionChainFixture;
import space.controlnet.ae2federation.test.crafting.FaultStorage;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;

/** Native planning and topology; hand-operated processing and explicitly injected storage capacity faults. */
@PrefixGameTestTemplate(false)
public final class ReturnCoverageGameTests {
    /** The existing hand-operated processor returns one of three stones before cancellation, then the other two. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 1000)
    public static void projectionPartialCancelledReturnPromptly(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper);
        var stone = PatternProjectionFixture.stone();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(fixture.ready(), "Waiting for native networks"))
                .thenExecute(() -> {
                    fixture.setRule(fixture.crafting(), RuleMode.ENABLED);
                    fixture.putInConsumer(PatternProjectionFixture.cobblestone(), 3);
                })
                .thenWaitUntil(() -> helper.assertTrue(fixture.consumerService().isCraftable(stone), "Waiting for projection"))
                .thenExecute(() -> fixture.begin(stone, 3))
                .thenWaitUntil(() -> helper.assertTrue(fixture.planReady(), "Waiting for plan"))
                .thenExecute(() -> helper.assertTrue(fixture.submit(), "Native CPU accepts the job"))
                .thenWaitUntil(() -> helper.assertValueEqual(fixture.inMachine(Items.COBBLESTONE), 3L, "Three inputs dispatched"))
                .thenExecute(() -> {
                    helper.assertValueEqual(fixture.runMachine(1), 1L, "Processor returns its first stone");
                    helper.assertValueEqual(fixture.owed(stone), 2L, "Partial delivery leaves two owed");
                    fixture.cancelConsumerJob();
                    helper.assertValueEqual(fixture.owed(stone), 2L, "Cancellation preserves undelivered output");
                    helper.assertValueEqual(fixture.runMachine(2), 2L, "Processor releases its remaining two stones");
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    helper.assertValueEqual(fixture.held(fixture.consumerChest(), stone), 3L,
                            "All output returns within one sweep after cancellation: " + fixture.returns(stone));
                    helper.assertValueEqual(fixture.onProviderNetwork(stone), 0L, "Factory keeps no output");
                    helper.assertValueEqual(fixture.owed(stone), 0L, "Delivery settles debt");
                    helper.assertValueEqual(fixture.watchedJobs(), 0, "Cancelled job retires");
                    helper.assertTrue(fixture.consumerCpuEmpty(), "Cancelled CPU is empty");
                    PolicyEvidence.write("projectionpartialcancelledreturnpromptly", 12,
                            Map.of("partial", "1", "late", "2", "idleTicks", "25"));
                    fixture.close();
                })
                .thenSucceed();
    }

    /**
     * A projected processing pattern whose output is a fluid: the hand-operated processor returns two buckets of lava,
     * one at a time, into the provider network. Both fluid cells show where each bucket ended.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 1000)
    public static void projectionFluidReturn(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper).withFluidCells();
        var lava = AEFluidKey.of(Fluids.LAVA);
        long bucket = AEFluidKey.AMOUNT_BUCKET;
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(fixture.ready(), "Waiting for native networks"))
                .thenExecute(() -> {
                    fixture.installProcessingPattern(PatternDetailsHelper.encodeProcessingPattern(
                            List.of(new GenericStack(PatternProjectionFixture.cobblestone(), 1)),
                            List.of(new GenericStack(lava, bucket))));
                    fixture.setRule(fixture.crafting(), RuleMode.ENABLED);
                    fixture.putInConsumer(PatternProjectionFixture.cobblestone(), 2);
                })
                .thenWaitUntil(() -> helper.assertTrue(fixture.consumerService().isCraftable(lava), "Waiting for projection"))
                .thenExecute(() -> fixture.begin(lava, 2 * bucket))
                .thenWaitUntil(() -> helper.assertTrue(fixture.planReady(), "Waiting for plan"))
                .thenExecute(() -> helper.assertTrue(fixture.submit(), "Native CPU accepts the job"))
                .thenWaitUntil(() -> helper.assertValueEqual(fixture.inMachine(Items.COBBLESTONE), 2L, "Two inputs dispatched"))
                .thenExecute(() -> {
                    helper.assertValueEqual(fixture.owed(lava), 2 * bucket, "Two pushes owe two buckets");
                    helper.assertValueEqual(fixture.runMachine(lava, bucket), bucket, "Processor returns its first bucket");
                    helper.assertValueEqual(fixture.owed(lava), bucket, "The waiting CPU takes the first bucket");
                    helper.assertValueEqual(fixture.heldForConsumer(lava), 0L, "Nothing is held while the CPU waits");
                    helper.assertValueEqual(fixture.runMachine(lava, bucket), bucket, "Processor returns its second bucket");
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    assertFluidSettled(helper, fixture, lava, 2 * bucket);
                    PolicyEvidence.write("projectionfluidreturn", 16, Map.of("output", "lava", "buckets", "2"));
                    fixture.close();
                })
                .thenSucceed();
    }

    /**
     * One pattern makes flint and a bucket of water. The job ends with its flint while a bucket of water is still out;
     * that bucket arrives afterwards, is held on the provider network and handed to the consumer's storage.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 1000)
    public static void projectionMixedFluidReturn(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper).withFluidCells();
        var flint = AEItemKey.of(Items.FLINT);
        var water = AEFluidKey.of(Fluids.WATER);
        long bucket = AEFluidKey.AMOUNT_BUCKET;
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(fixture.ready(), "Waiting for native networks"))
                .thenExecute(() -> {
                    fixture.installProcessingPattern(PatternDetailsHelper.encodeProcessingPattern(
                            List.of(new GenericStack(PatternProjectionFixture.cobblestone(), 1)),
                            List.of(new GenericStack(flint, 1), new GenericStack(water, bucket))));
                    fixture.setRule(fixture.crafting(), RuleMode.ENABLED);
                    fixture.putInConsumer(PatternProjectionFixture.cobblestone(), 2);
                })
                .thenWaitUntil(() -> helper.assertTrue(fixture.consumerService().isCraftable(flint), "Waiting for projection"))
                .thenExecute(() -> fixture.begin(flint, 2))
                .thenWaitUntil(() -> helper.assertTrue(fixture.planReady(), "Waiting for plan"))
                .thenExecute(() -> helper.assertTrue(fixture.submit(), "Native CPU accepts the job"))
                .thenWaitUntil(() -> helper.assertValueEqual(fixture.inMachine(Items.COBBLESTONE), 2L, "Two inputs dispatched"))
                .thenExecute(() -> {
                    helper.assertValueEqual(fixture.owed(flint), 2L, "Two pushes owe two flint");
                    helper.assertValueEqual(fixture.owed(water), 2 * bucket, "Two pushes owe two buckets");
                    helper.assertValueEqual(fixture.runMachine(water, bucket), bucket, "Processor returns one bucket");
                    helper.assertValueEqual(fixture.owed(water), bucket, "The waiting CPU takes the first bucket");
                    helper.assertValueEqual(fixture.runMachine(flint, 2), 2L, "Processor returns both flint");
                    helper.assertValueEqual(fixture.busyConsumerCpus(), 0L, "The job ends with its flint");
                    helper.assertValueEqual(fixture.owed(water), bucket, "The job's end leaves the second bucket owed");
                    helper.assertValueEqual(fixture.runMachine(water, bucket), bucket, "Processor returns the late bucket");
                    helper.assertValueEqual(fixture.heldForConsumer(water), bucket, "The late bucket is held for the consumer");
                    helper.assertValueEqual(fixture.held(fixture.providerFluids(), water), bucket,
                            "The late bucket waits in the provider's fluid cell");
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    helper.assertValueEqual(fixture.held(fixture.consumerChest(), flint), 2L, "The flint reaches the consumer");
                    helper.assertValueEqual(fixture.onProviderNetwork(flint), 0L, "Factory keeps no flint");
                    helper.assertValueEqual(fixture.owed(flint), 0L, "Flint delivery settles its debt");
                    assertFluidSettled(helper, fixture, water, 2 * bucket);
                    PolicyEvidence.write("projectionmixedfluidreturn", 25,
                            Map.of("outputs", "flint,water", "lateBuckets", "1"));
                    fixture.close();
                })
                .thenSucceed();
    }

    /**
     * As {@link #projectionPartialCancelledReturnPromptly}, with lava: one bucket returns before the job is cancelled,
     * the other two after, and all three reach the consumer.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 1000)
    public static void projectionFluidCancelledReturn(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper).withFluidCells();
        var lava = AEFluidKey.of(Fluids.LAVA);
        long bucket = AEFluidKey.AMOUNT_BUCKET;
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(fixture.ready(), "Waiting for native networks"))
                .thenExecute(() -> {
                    fixture.installProcessingPattern(PatternDetailsHelper.encodeProcessingPattern(
                            List.of(new GenericStack(PatternProjectionFixture.cobblestone(), 1)),
                            List.of(new GenericStack(lava, bucket))));
                    fixture.setRule(fixture.crafting(), RuleMode.ENABLED);
                    fixture.putInConsumer(PatternProjectionFixture.cobblestone(), 3);
                })
                .thenWaitUntil(() -> helper.assertTrue(fixture.consumerService().isCraftable(lava), "Waiting for projection"))
                .thenExecute(() -> fixture.begin(lava, 3 * bucket))
                .thenWaitUntil(() -> helper.assertTrue(fixture.planReady(), "Waiting for plan"))
                .thenExecute(() -> helper.assertTrue(fixture.submit(), "Native CPU accepts the job"))
                .thenWaitUntil(() -> helper.assertValueEqual(fixture.inMachine(Items.COBBLESTONE), 3L, "Three inputs dispatched"))
                .thenExecute(() -> {
                    helper.assertValueEqual(fixture.runMachine(lava, bucket), bucket, "Processor returns its first bucket");
                    helper.assertValueEqual(fixture.owed(lava), 2 * bucket, "Partial delivery leaves two buckets owed");
                    fixture.cancelConsumerJob();
                    helper.assertValueEqual(fixture.owed(lava), 2 * bucket, "Cancellation preserves undelivered output");
                    helper.assertValueEqual(fixture.runMachine(lava, 2 * bucket), 2 * bucket,
                            "Processor releases its remaining two buckets");
                    helper.assertValueEqual(fixture.heldForConsumer(lava), 2 * bucket,
                            "The late buckets are held for the cancelled consumer");
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    assertFluidSettled(helper, fixture, lava, 3 * bucket);
                    PolicyEvidence.write("projectionfluidcancelledreturn", 17,
                            Map.of("output", "lava", "partial", "1", "late", "2", "idleTicks", "25"));
                    fixture.close();
                })
                .thenSucceed();
    }

    /**
     * Removing the Bridge after the push: two buckets of lava that arrive while the networks are apart stay in the
     * provider's fluid cell, held for the consumer, whose job keeps waiting; once the Bridge is back the job gets them.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 1200)
    public static void projectionFluidDisconnectedReturn(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper).withFluidCells();
        var lava = AEFluidKey.of(Fluids.LAVA);
        long bucket = AEFluidKey.AMOUNT_BUCKET;
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(fixture.ready(), "Waiting for native networks"))
                .thenExecute(() -> {
                    fixture.installProcessingPattern(PatternDetailsHelper.encodeProcessingPattern(
                            List.of(new GenericStack(PatternProjectionFixture.cobblestone(), 1)),
                            List.of(new GenericStack(lava, bucket))));
                    fixture.setRule(fixture.crafting(), RuleMode.ENABLED);
                    fixture.putInConsumer(PatternProjectionFixture.cobblestone(), 2);
                })
                .thenWaitUntil(() -> helper.assertTrue(fixture.consumerService().isCraftable(lava), "Waiting for projection"))
                .thenExecute(() -> fixture.begin(lava, 2 * bucket))
                .thenWaitUntil(() -> helper.assertTrue(fixture.planReady(), "Waiting for plan"))
                .thenExecute(() -> helper.assertTrue(fixture.submit(), "Native CPU accepts the job"))
                .thenWaitUntil(() -> helper.assertValueEqual(fixture.inMachine(Items.COBBLESTONE), 2L, "Two inputs dispatched"))
                .thenExecute(fixture::disconnect)
                .thenWaitUntil(() -> helper.assertValueEqual(fixture.projections(), 0, "Waiting for the patterns to be withdrawn"))
                .thenExecute(() -> {
                    helper.assertValueEqual(fixture.runMachine(lava, 2 * bucket), 2 * bucket,
                            "The provider network takes both buckets");
                    helper.assertValueEqual(fixture.held(fixture.providerFluids(), lava), 2 * bucket,
                            "The lava stays in the provider's fluid cell");
                    helper.assertValueEqual(fixture.held(fixture.consumerFluids(), lava), 0L,
                            "Nothing crosses the removed Bridge");
                    helper.assertValueEqual(fixture.heldForConsumer(lava), 2 * bucket, "The lava is held for the consumer");
                    helper.assertValueEqual(fixture.owed(lava), 2 * bucket, "The debt stays while the consumer still waits");
                    helper.assertValueEqual(fixture.busyConsumerCpus(), 1L, "The consumer's job keeps waiting");
                    fixture.reconnect();
                })
                .thenWaitUntil(() -> helper.assertTrue(fixture.connected(), "Waiting for the Bridge to link again"))
                .thenWaitUntil(() -> helper.assertValueEqual(fixture.busyConsumerCpus(), 0L,
                        "Waiting for the held lava to reach the job: " + fixture.returns(lava)))
                .thenIdle(25)
                .thenExecute(() -> {
                    assertFluidSettled(helper, fixture, lava, 2 * bucket);
                    PolicyEvidence.write("projectionfluiddisconnectedreturn", 17,
                            Map.of("output", "lava", "heldWhileApart", "2", "handedBackAfterReconnect", "2"));
                    fixture.close();
                })
                .thenSucceed();
    }

    /** Where a projected fluid ended: all of it in the consumer's fluid cell, nothing at the factory or in the ledger. */
    private static void assertFluidSettled(GameTestHelper helper, PatternProjectionFixture fixture, AEKey fluid,
            long made) {
        helper.assertValueEqual(fixture.held(fixture.consumerFluids(), fluid), made,
                "The fluid reaches the consumer's storage: " + fixture.returns(fluid));
        helper.assertValueEqual(fixture.held(fixture.providerFluids(), fluid), 0L, "The provider's fluid cell keeps none");
        helper.assertValueEqual(fixture.onProviderNetwork(fluid), 0L, "Factory keeps no fluid");
        helper.assertValueEqual(fixture.owed(fluid), 0L, "Delivery settles the fluid debt");
        helper.assertValueEqual(fixture.heldForConsumer(fluid), 0L, "Nothing is held afterwards");
        helper.assertValueEqual(fixture.transit(fluid), 0L, "No fluid is in transit");
        helper.assertValueEqual(fixture.watchedJobs(), 0, "The job retires");
        helper.assertTrue(fixture.consumerCpuEmpty(), "The consumer's CPU is idle and empty");
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 1400)
    public static void projectionMixedJobResponsibilities(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper);
        var stage = new int[1];
        var sweep = new long[1];
        var diamond = AEItemKey.of(Items.DIAMOND);
        var emerald = AEItemKey.of(Items.EMERALD);
        var bottle = AEItemKey.of(Items.GLASS_BOTTLE);
        var bowl = AEItemKey.of(Items.BOWL);
        var stone = PatternProjectionFixture.stone();
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] > 0 || fixture.ready(), "Waiting for native fixture");
            var ledger = CraftingReturnLedger.get(helper.getLevel());
            if (stage[0] == 0) {
                fixture.installProcessingPattern(PatternDetailsHelper.encodeProcessingPattern(
                        List.of(new GenericStack(PatternProjectionFixture.cobblestone(), 1)),
                        List.of(new GenericStack(diamond, 1), new GenericStack(bottle, 3))));
                fixture.installProcessingPattern(PatternDetailsHelper.encodeProcessingPattern(
                        List.of(new GenericStack(PatternProjectionFixture.cobblestone(), 1)),
                        List.of(new GenericStack(emerald, 1), new GenericStack(bowl, 2))));
                fixture.setRule(fixture.crafting(), RuleMode.ENABLED);
                fixture.putInConsumer(PatternProjectionFixture.cobblestone(), 3);
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                helper.assertTrue(fixture.consumerService().isCraftable(diamond)
                        && fixture.consumerService().isCraftable(emerald), "Waiting for both processing projections");
                fixture.begin(diamond, 1);
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                helper.assertTrue(fixture.planReady(), "Waiting for first native plan");
                helper.assertTrue(fixture.submit(), "First native job submitted");
                stage[0] = 3;
            }
            if (stage[0] == 3) {
                helper.assertValueEqual(fixture.inMachine(Items.COBBLESTONE), 1L, "First real input pushed");
                insert(helper, fixture, diamond, 1);
                stage[0] = 4;
            }
            if (stage[0] == 4) {
                helper.assertValueEqual(fixture.busyConsumerCpus(), 0L, "First CPU completed before its containers");
                helper.assertValueEqual(fixture.owed(bottle), 3L, "Completed job still owns three containers");
                fixture.begin(emerald, 1);
                stage[0] = 5;
            }
            if (stage[0] == 5) {
                helper.assertTrue(fixture.planReady(), "Waiting for second native plan");
                helper.assertTrue(fixture.submit(), "Second native job submitted");
                stage[0] = 6;
            }
            if (stage[0] == 6) {
                helper.assertValueEqual(fixture.inMachine(Items.COBBLESTONE), 2L, "Second real input pushed");
                fixture.consumerCpu().cancelJob();
                fixture.begin(stone, 1);
                stage[0] = 7;
            }
            if (stage[0] == 7) {
                helper.assertTrue(fixture.planReady(), "Waiting for third native plan");
                helper.assertTrue(fixture.submit(), "Third native job submitted");
                stage[0] = 8;
            }
            if (stage[0] == 8) {
                helper.assertValueEqual(fixture.inMachine(Items.COBBLESTONE), 3L, "All three jobs actually dispatched");
                helper.assertValueEqual(fixture.watchedJobs(), 3, "Three distinct real crafting UUIDs remain watched");
                fixture.runMachine(0); // Hand-operated processor consumes the three dispatched ingredients.
                insert(helper, fixture, bottle, 1);
                insert(helper, fixture, emerald, 1);
                insert(helper, fixture, bowl, 2);
                sweep[0] = fixture.ledgerSweeps();
                stage[0] = 9;
            }
            if (stage[0] == 9) {
                helper.assertTrue(fixture.ledgerSweeps() >= sweep[0] + 4, "Observe mixed jobs across four cleanup sweeps");
                var jobs = ledger.jobs(fixture.consumerNetwork());
                helper.assertValueEqual(jobs.stream().filter(job -> job.end() == CraftingReturnLedger.JobEnd.DONE).count(), 1L,
                        "One completed job is recorded independently");
                helper.assertValueEqual(jobs.stream().filter(job -> job.end() == CraftingReturnLedger.JobEnd.CANCELLED).count(), 1L,
                        "One cancelled job is recorded independently");
                helper.assertValueEqual(jobs.stream().filter(job -> job.end() == CraftingReturnLedger.JobEnd.RUNNING).count(), 1L,
                        "The third job remains running");
                helper.assertValueEqual(fixture.owed(bottle), 3L, "Completed debt is not dropped by mixed summary");
                helper.assertValueEqual(fixture.owed(bowl), 2L, "Cancelled debt is not dropped by mixed summary");
                helper.assertValueEqual(fixture.heldForConsumer(bottle), 1L, "Only the first actual container batch is held");
                helper.assertValueEqual(fixture.heldForConsumer(bowl), 2L, "Cancelled late containers remain owned");
                helper.assertValueEqual(fixture.held(fixture.consumerChest(), bottle), 0L, "Running task conservatively delays ordinary returns");
                insert(helper, fixture, bottle, 2);
                insert(helper, fixture, stone, 1);
                stage[0] = 10;
            }
            helper.assertValueEqual(fixture.busyConsumerCpus(), 0L, "Third native CPU completes");
            for (var output : List.of(new GenericStack(diamond, 1), new GenericStack(emerald, 1),
                    new GenericStack(bottle, 3), new GenericStack(bowl, 2), new GenericStack(stone, 1))) {
                helper.assertValueEqual(fixture.held(fixture.consumerChest(), output.what()), output.amount(), "Every real output reaches its owner");
                helper.assertValueEqual(fixture.owed(output.what()), 0L, "Actual delivery settles each key");
                helper.assertValueEqual(fixture.heldForConsumer(output.what()), 0L, "No duplicate held stock remains");
                helper.assertValueEqual(fixture.onProviderNetwork(output.what()), 0L, "No output remains at factory");
                helper.assertValueEqual(fixture.transit(output.what()), 0L, "No transit remains");
            }
            helper.assertTrue(PolicyService.get(helper.getLevel()).configured(fixture.storage()).isEmpty(), "Storage was never configured");
            helper.assertValueEqual(fixture.watchedJobs(), 0, "All three jobs retire only after actual return");
            helper.assertTrue(fixture.consumerCpuEmpty(), "The actual native CPU retains no output or task inventory");
            PolicyEvidence.write("projectionmixedjobresponsibilities", 42,
                    Map.of("nativeJobs", "completed,cancelled,running", "storageEverEnabled", "false", "mixedSweeps", "4"));
            fixture.close();
        });
    }

    /** Explicit debt/job and capacity fault injection, with actual native mounts and a conserved machine ItemStack. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 1000)
    public static void projectionPartialRetryRecovery(GameTestHelper helper) {
        var fixture = new ProjectionChainFixture(helper);
        var stage = new int[1];
        var sweep = new long[1];
        var key = AEItemKey.of(Items.STONE);
        var machine = new net.minecraft.world.item.ItemStack(Items.STONE, 1);
        var source = new FaultStorage(0, 0);
        var firstCapacity = new FaultStorage(1, 1);
        var secondCapacity = new FaultStorage(2, 2);
        IStorageProvider firstMount = mounts -> { mounts.mount(source, 5); mounts.mount(firstCapacity, 10); };
        IStorageProvider secondMount = mounts -> mounts.mount(secondCapacity, 10);
        var cell = new net.minecraft.world.item.ItemStack[1];
        var chest = new appeng.blockentity.storage.MEChestBlockEntity[1];
        var ids = new NetworkId[3];
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] > 0 || fixture.ready(), "Waiting for three actual native Grids");
            var ledger = CraftingReturnLedger.get(helper.getLevel());
            var service = CraftingProjectionService.get(helper.getLevel());
            if (stage[0] == 0) {
                for (int index = 0; index < 3; index++) ids[index] = fixture.network(index);
                fixture.craftingOnly(0, 2, RuleMode.ENABLED);
                fixture.craftingOnly(1, 2, RuleMode.ENABLED);
                for (int index = 0; index < 2; index++) {
                    var job = UUID.randomUUID();
                    call(ledger, "watch", new Class<?>[] {NetworkId.class, UUID.class, String.class, BlockPos.class},
                            ids[index], job, helper.getLevel().dimension().location().toString(), helper.absolutePos(BlockPos.ZERO));
                    call(ledger, "end", new Class<?>[] {NetworkId.class, UUID.class, CraftingReturnLedger.JobEnd.class},
                            ids[index], job, CraftingReturnLedger.JobEnd.DONE);
                    call(ledger, "add", new Class<?>[] {NetworkId.class, NetworkId.class, appeng.api.stacks.AEKey.class, long.class},
                            ids[2], ids[index], key, index == 0 ? 1L : 8L);
                }
                service.observeFederationDomainMembers(fixture.grids());
                fixture.removeSwitch();
                for (var node : fixture.grid(2).getNodes()) {
                    if (node.getOwner() instanceof appeng.blockentity.storage.MEChestBlockEntity found) chest[0] = found;
                }
                helper.assertTrue(chest[0] != null, "Real factory storage chest found");
                cell[0] = chest[0].getCell().copy();
                chest[0].setCell(net.minecraft.world.item.ItemStack.EMPTY);
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                helper.assertTrue(service.routes(fixture.grid(2)), "Debt keeps the native return router mounted after disconnect");
                var inventory = fixture.grid(2).getStorageService().getInventory();
                for (int attempt = 0; attempt < 12; attempt++) {
                    helper.assertValueEqual(inventory.insert(key, machine.getCount(), Actionable.MODULATE, IActionSource.empty()),
                            0L, "Rejected single item remains in machine");
                    helper.assertValueEqual(totalHeld(ledger, ids, key), 0L, "Retry attempts never create held stock");
                }
                helper.assertValueEqual(machine.getCount(), 1, "Machine still owns exactly its original item");
                // Two additional real processor outputs, distinct from retries of the existing item.
                machine.grow(2);
                fixture.grid(2).getStorageService().addGlobalStorageProvider(firstMount);
                helper.assertValueEqual(inventory.insert(key, 3, Actionable.SIMULATE, IActionSource.empty()), 1L,
                        "Simulation sees only one unit of actual capacity");
                helper.assertValueEqual(totalHeld(ledger, ids, key), 0L, "Simulation has no held side effects");
                long accepted = inventory.insert(key, machine.getCount(), Actionable.MODULATE, IActionSource.empty());
                helper.assertValueEqual(accepted, 1L, "Only one of three machine items is actually accepted");
                machine.shrink((int) accepted);
                for (int attempt = 0; attempt < 12; attempt++) {
                    helper.assertValueEqual(inventory.insert(key, machine.getCount(), Actionable.MODULATE, IActionSource.empty()),
                            0L, "Partially refused remainder stays in machine on every retry");
                    helper.assertValueEqual(totalHeld(ledger, ids, key), 1L, "Repeated residual never increases held");
                }
                source.put(key, 7); // Existing factory stock is deliberately available for extraction, never an arrival.
                helper.assertValueEqual(machine.getCount() + source.held(key) + firstCapacity.held(key), 10L,
                        "Three produced items plus seven pre-existing items are conserved before reconnect");
                fixture.restoreSwitch();
                sweep[0] = service.ledgerSweeps();
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                helper.assertTrue(service.ledgerSweeps() >= sweep[0] + 4, "Observe several sweeps after real Switch reconnection");
                helper.assertValueEqual(fixture.amount(0, key) + fixture.amount(1, key), 1L,
                        "Reconnection returns one actual arrival, never retry-inflated debt from unrelated stock");
                helper.assertValueEqual(source.held(key) + firstCapacity.held(key), 7L, "Seven factory items remain after one actual return");
                helper.assertValueEqual(totalHeld(ledger, ids, key), 0L, "The one held arrival was delivered once");
                helper.assertValueEqual(ledger.owed(ids[2], ids[0], key) + ledger.owed(ids[2], ids[1], key), 8L,
                        "Unarrived obligations remain without authorizing extra withdrawals");
                fixture.grid(2).getStorageService().addGlobalStorageProvider(secondMount);
                long accepted = fixture.grid(2).getStorageService().getInventory().insert(key, machine.getCount(),
                        Actionable.MODULATE, IActionSource.empty());
                helper.assertValueEqual(accepted, 2L, "Restored local capacity accepts the actual remainder");
                machine.shrink((int) accepted);
                stage[0] = 3;
            }
            if (stage[0] == 3) {
                helper.assertValueEqual(fixture.amount(0, key) + fixture.amount(1, key), 3L, "Only three actual outputs reached consumers");
                helper.assertValueEqual(source.held(key) + firstCapacity.held(key) + secondCapacity.held(key), 7L,
                        "Partial retry and multiple consumers cannot consume additional factory stock");
                helper.assertValueEqual(totalHeld(ledger, ids, key), 0L, "No retry credit remains");
                helper.assertValueEqual(ledger.transit(ids[2], key), 0L, "No hidden transit stock");
                helper.assertValueEqual(machine.getCount(), 0, "Machine buffer is empty only after acceptance");
                helper.assertTrue(fixture.grids().stream().flatMap(grid -> grid.getCraftingService().getCpus().stream())
                        .allMatch(cpu -> !cpu.isBusy() && ((appeng.me.cluster.implementations.CraftingCPUCluster) cpu)
                                .craftingLogic.getInventory().list.isEmpty()), "No native CPU hides extra stock in the conservation equation");
                chest[0].setCell(cell[0]);
                // Complete the remaining injected obligations with six explicitly new processor outputs.
                helper.assertValueEqual(fixture.grid(2).getStorageService().getInventory().insert(key, 6,
                        Actionable.MODULATE, IActionSource.empty()), 6L, "Six distinct new outputs are accepted");
                stage[0] = 4;
            }
            helper.assertValueEqual(fixture.amount(0, key), 1L, "First consumer receives exactly its one owed output");
            helper.assertValueEqual(fixture.amount(1, key), 8L, "Second consumer receives exactly its eight owed outputs");
            helper.assertValueEqual(ledger.owed(ids[2], ids[0], key) + ledger.owed(ids[2], ids[1], key), 0L, "Both debts settle by delivery");
            helper.assertValueEqual(fixture.amount(2, key) + source.held(key) + firstCapacity.held(key) + secondCapacity.held(key),
                    7L, "Nine real outputs were returned and seven initial factory items remain");
            for (int index = 0; index < 2; index++) {
                helper.assertTrue(PolicyService.get(helper.getLevel()).configured(
                        new PolicyKey(ids[index], ids[2], PolicyCapability.STORAGE)).isEmpty(), "Storage was never enabled for either consumer");
            }
            fixture.grid(2).getStorageService().removeGlobalStorageProvider(firstMount);
            fixture.grid(2).getStorageService().removeGlobalStorageProvider(secondMount);
            PolicyEvidence.write("projectionpartialretryrecovery", 70, Map.of("consumers", "2", "rejectedAttempts", "24",
                    "produced", "9", "factoryInitialStock", "7", "faultInjection", "debts,terminal jobs,capacity", "storageEverEnabled", "false"));
            fixture.close();
        });
    }

    private static long totalHeld(CraftingReturnLedger ledger, NetworkId[] ids, AEItemKey key) {
        return ledger.held(ids[2], ids[0], key) + ledger.held(ids[2], ids[1], key);
    }

    private static void call(Object target, String method, Class<?>[] signature, Object... arguments) {
        try {
            var reflected = target.getClass().getDeclaredMethod(method, signature);
            reflected.setAccessible(true);
            reflected.invoke(target, arguments);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void insert(GameTestHelper helper, PatternProjectionFixture fixture, AEItemKey key, long amount) {
        helper.assertValueEqual(fixture.providerGrid().getStorageService().getInventory().insert(key, amount,
                Actionable.MODULATE, IActionSource.empty()), amount, "Hand-operated processor emits actual output");
    }
}
