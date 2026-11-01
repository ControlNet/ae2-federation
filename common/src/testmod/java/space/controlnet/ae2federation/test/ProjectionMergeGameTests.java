package space.controlnet.ae2federation.test;

import static space.controlnet.ae2federation.test.crafting.PatternProjectionFixture.cobblestone;
import static space.controlnet.ae2federation.test.crafting.PatternProjectionFixture.stone;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.test.crafting.NativeCraftingRequester;
import space.controlnet.ae2federation.test.crafting.PatternProjectionFixture;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;

/**
 * Two linked networks that become one AE2 Grid while a projected job waits: the machine's output then reaches the
 * consumer's CPU natively, through AE2's own crafting storage. Once the Grids split again, that delivery must have
 * settled the debt, and stock the provider network stores later stays its own.
 */
@PrefixGameTestTemplate(false)
public final class ProjectionMergeGameTests {
    private ProjectionMergeGameTests() {
    }

    /** How the two networks become one Grid. */
    private enum Merge {
        /** Only the Bridge part is removed, so the fluix cable it covered joins the provider's cable, then restored. */
        BRIDGE_PART,
        /** A player places a cable between the networks, then breaks it. */
        JOINING_CABLE
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 1200)
    public static void projectionMergedBridgeReturn(GameTestHelper helper) {
        mergedMidJob(helper, Merge.BRIDGE_PART, false, "projectionmergedbridgereturn");
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 1200)
    public static void projectionMergedCableReturn(GameTestHelper helper) {
        mergedMidJob(helper, Merge.JOINING_CABLE, false, "projectionmergedcablereturn");
    }

    /** As {@link #projectionMergedBridgeReturn}, with a bucket of lava as the output and a fluid cell on each side. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 1200)
    public static void projectionMergedBridgeFluidReturn(GameTestHelper helper) {
        mergedMidJob(helper, Merge.BRIDGE_PART, true, "projectionmergedbridgefluidreturn");
    }

    /**
     * The joined Grid also runs a native job for the same output on the consumer's other CPU, so both CPUs wait for one
     * stone each when the machine delivers one. AE2 does not say which job took it, and the native job's wait explains
     * it, so the debt must stay: settled there, the projected job's own stone, arriving after the split, would stay on
     * the provider network while a job still waits. Both jobs have a requester, so one stone ends one job's wait only;
     * whichever CPU took it, the router's hand-back after the split feeds the other job and settles the debt.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 1600)
    public static void projectionMergedMixedDemandReturn(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper, PatternProjectionFixture.ConsumerCpus.BOTH);
        var requester = new NativeCraftingRequester[1];
        var stage = new int[1];
        var sweeps = new long[1];
        var whileMerged = new String[1];
        var delivered = new long[1];
        var waiting = new long[1];
        var owedWhileMerged = new long[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] > 0 || fixture.ready(), "Waiting for both networks and their providers");
            if (stage[0] == 0) {
                fixture.fluixCableUnderBridge();
                fixture.preferConsumerChest();
                fixture.enableRules();
                fixture.putInConsumer(cobblestone(), 2);
                requester[0] = new NativeCraftingRequester(helper.getLevel(), helper.absolutePos(BlockPos.ZERO),
                        fixture.consumerChest(), fixture.consumerNetwork(), null, false);
                requester[0].connect(fixture.consumerNode());
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                helper.assertTrue(requester[0].isReady(fixture.consumerNode()),
                        "Waiting for the requester to join the consumer network");
                helper.assertTrue(fixture.consumerService().isCraftable(stone()), "Waiting for the projection");
                fixture.begin(stone(), 1);
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                helper.assertTrue(fixture.planReady(), "Waiting for the consumer's plan");
                helper.assertTrue(fixture.submitOn(fixture.overworldCpu(), requester[0]),
                        "The consumer's overworld CPU must take the projected job");
                stage[0] = 3;
            }
            if (stage[0] == 3) {
                helper.assertValueEqual(fixture.inMachine(Items.COBBLESTONE), 1L, "Waiting for the push");
                helper.assertValueEqual(fixture.owed(stone()), 1L, "The provider network owes the consumer one");
                fixture.disconnect();
                stage[0] = 4;
            }
            if (stage[0] == 4) {
                helper.assertTrue(fixture.merged(), "Waiting for the networks to become one Grid");
                helper.assertValueEqual(fixture.visibleConsumerCpus(), 2, "Waiting for the merged Grid to list both CPUs");
                helper.assertFalse(fixture.providerRouted(), "Waiting for Federation to stop routing the merged Grid");
                helper.assertValueEqual(fixture.projections(), 0, "Waiting for the projection to be withdrawn");
                fixture.begin(stone(), 1);
                stage[0] = 5;
            }
            if (stage[0] == 5) {
                helper.assertTrue(fixture.planReady(), "Waiting for the merged Grid's own plan");
                helper.assertTrue(fixture.submitOn(fixture.netherCpu(), requester[0]),
                        "The other CPU must take the native job");
                stage[0] = 6;
            }
            if (stage[0] == 6) {
                helper.assertValueEqual(fixture.inMachine(Items.COBBLESTONE), 2L, "Waiting for the native push");
                helper.assertValueEqual(fixture.owed(stone()), 1L, "A native push owes nothing");
                helper.assertValueEqual(fixture.consumerService().getRequestedAmount(stone()), 2L,
                        "Both jobs wait for one stone each");
                delivered[0] = fixture.runMachine(stone(), 1);
                waiting[0] = fixture.consumerService().getRequestedAmount(stone());
                owedWhileMerged[0] = fixture.owed(stone());
                whileMerged[0] = fixture.returns(stone());
                stage[0] = 7;
            }
            if (stage[0] == 7) {
                // Checked in a stage of its own, so a failure keeps its message after the machine was emptied.
                helper.assertValueEqual(delivered[0], 1L, "One of the jobs takes the stone");
                helper.assertValueEqual(waiting[0], 1L, "One job still waits");
                helper.assertValueEqual(owedWhileMerged[0], 1L,
                        "Stone the native job's wait explains settles nothing; while merged: " + whileMerged[0]);
                fixture.reconnect();
                stage[0] = 8;
            }
            if (stage[0] == 8) {
                helper.assertFalse(fixture.merged(), "Waiting for the Grids to split");
                helper.assertTrue(fixture.connected(), "Waiting for the Bridge to link the networks again");
                helper.assertTrue(fixture.status().map(status -> status.active()).orElse(false),
                        "Waiting for the projection to come back");
                // The machine was given two cobblestones and has made one stone so far.
                helper.assertValueEqual(fixture.runMachine(stone(), 1), 1L, "The provider network takes the stone");
                stage[0] = 9;
            }
            if (stage[0] == 9) {
                helper.assertValueEqual(requester[0].acceptedAmount(stone()), 2L,
                        "Waiting for both jobs' stone; while merged: " + whileMerged[0] + "; now: "
                                + fixture.returns(stone()));
                helper.assertValueEqual(fixture.busyConsumerCpus(), 0L, "Both jobs finish");
                sweeps[0] = fixture.ledgerSweeps();
                stage[0] = 10;
            }
            if (stage[0] == 10) {
                helper.assertTrue(fixture.ledgerSweeps() - sweeps[0] >= 3, "Letting three ledger sweeps pass");
                // Unrelated output that a player's import bus stores on the provider network after the jobs.
                helper.assertValueEqual(fixture.providerGrid().getStorageService().getInventory().insert(stone(), 1,
                        Actionable.MODULATE, IActionSource.empty()), 1L, "The provider network stores a player's stone");
                sweeps[0] = fixture.ledgerSweeps();
                stage[0] = 11;
            }
            helper.assertTrue(fixture.ledgerSweeps() - sweeps[0] >= 3, "Letting three more ledger sweeps pass");
            var context = "; while merged: " + whileMerged[0] + "; now: " + fixture.returns(stone());
            helper.assertValueEqual(fixture.owed(stone()), 0L, "The hand-back settled the debt" + context);
            helper.assertValueEqual(fixture.heldForConsumer(stone()), 0L, "Nothing is held for the consumer" + context);
            helper.assertValueEqual(fixture.transit(stone()), 0L, "Nothing is in transit" + context);
            helper.assertValueEqual(fixture.watchedJobs(), 0, "The finished job retires" + context);
            helper.assertValueEqual(fixture.held(fixture.consumerChest(), stone()), 2L,
                    "The consumer keeps both jobs' stone" + context);
            helper.assertValueEqual(fixture.held(fixture.providerChest(), stone()), 1L,
                    "Only the player's stone is on the provider network" + context);
            PolicyEvidence.write("projectionmergedmixeddemandreturn", 34, Map.of("owedWhileMerged", "1",
                    "owedAfter", "0", "requesterReceived", "2", "playerOutputKept", "1"));
            requester[0].close();
            fixture.close();
        });
    }

    /**
     * With {@code fluid}, the hand-run machine makes one bucket of lava from the cobblestone instead of one stone, and
     * each network stores it in its fluid cell.
     */
    private static void mergedMidJob(GameTestHelper helper, Merge merge, boolean fluid, String testId) {
        var fixture = new PatternProjectionFixture(helper);
        if (fluid) fixture.withFluidCells();
        AEKey output = fluid ? AEFluidKey.of(Fluids.LAVA) : stone();
        long unit = fluid ? AEFluidKey.AMOUNT_BUCKET : 1;
        Supplier<MEStorage> consumerStorage = fluid ? fixture::consumerFluids : fixture::consumerChest;
        Supplier<MEStorage> providerStorage = fluid ? fixture::providerFluids : fixture::providerChest;
        var stage = new int[1];
        var sweeps = new long[1];
        var whileMerged = new String[1];
        var afterSplit = new String[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] > 0 || fixture.ready(), "Waiting for both networks and their providers");
            if (stage[0] == 0) {
                if (merge == Merge.BRIDGE_PART) fixture.fluixCableUnderBridge();
                if (fluid) {
                    fixture.installProcessingPattern(PatternDetailsHelper.encodeProcessingPattern(
                            List.of(new GenericStack(cobblestone(), 1)), List.of(new GenericStack(output, unit))));
                }
                fixture.preferConsumerChest();
                fixture.enableRules();
                fixture.putInConsumer(cobblestone(), 1);
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                helper.assertFalse(fixture.merged(), "The Bridge part keeps the networks apart");
                helper.assertTrue(fixture.consumerService().isCraftable(output), "Waiting for the projection");
                fixture.begin(output, unit);
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                helper.assertTrue(fixture.planReady(), "Waiting for the consumer's plan");
                helper.assertTrue(fixture.submit(), "The consumer's own CPU must take the job");
                stage[0] = 3;
            }
            if (stage[0] == 3) {
                helper.assertValueEqual(fixture.inMachine(Items.COBBLESTONE), 1L, "Waiting for the push");
                helper.assertValueEqual(fixture.owed(output), unit, "The provider network owes the consumer one");
                if (merge == Merge.BRIDGE_PART) fixture.disconnect();
                else fixture.placeJoiningCable();
                stage[0] = 4;
            }
            if (stage[0] == 4) {
                helper.assertTrue(fixture.merged(), "Waiting for the networks to become one Grid");
                helper.assertValueEqual(fixture.busyConsumerCpus(), 1L, "Waiting for the merged Grid to list the CPU");
                helper.assertFalse(fixture.providerRouted(), "Waiting for Federation to stop routing the merged Grid");
                helper.assertValueEqual(fixture.runMachine(output, unit), unit,
                        "The merged Grid takes the machine's output");
                stage[0] = 5;
            }
            if (stage[0] == 5) {
                helper.assertValueEqual(fixture.visibleConsumerCpus(), 1, "The merged Grid lists the CPU");
                helper.assertValueEqual(fixture.busyConsumerCpus(), 0L,
                        "Waiting for the CPU to take its output natively");
                whileMerged[0] = fixture.settlement() + "; " + fixture.returns(output);
                if (merge == Merge.BRIDGE_PART) fixture.reconnect();
                else fixture.removeJoiningCable();
                stage[0] = 6;
            }
            if (stage[0] == 6) {
                helper.assertFalse(fixture.merged(), "Waiting for the Grids to split");
                helper.assertTrue(merge != Merge.BRIDGE_PART || fixture.connected(),
                        "Waiting for the Bridge to link the networks again");
                helper.assertTrue(fixture.status().map(status -> status.active()).orElse(false),
                        "Waiting for the projection to come back");
                sweeps[0] = fixture.ledgerSweeps();
                stage[0] = 7;
            }
            if (stage[0] == 7) {
                helper.assertTrue(fixture.ledgerSweeps() - sweeps[0] >= 3, "Letting three ledger sweeps pass");
                afterSplit[0] = fixture.returns(output);
                helper.assertValueEqual(fixture.held(consumerStorage.get(), output), unit,
                        "The job's output reaches the consumer; after the split: " + afterSplit[0]);
                helper.assertValueEqual(fixture.held(providerStorage.get(), output), 0L,
                        "The provider network keeps none of it; after the split: " + afterSplit[0]);
                // Unrelated output that a player's import bus stores on the provider network after the job.
                GameTestChecks.once(helper, () -> {
                    helper.assertValueEqual(fixture.providerGrid().getStorageService().getInventory().insert(output, unit,
                            Actionable.MODULATE, IActionSource.empty()), unit,
                            "The provider network stores a player's output");
                });
                sweeps[0] = fixture.ledgerSweeps();
                stage[0] = 8;
            }
            helper.assertTrue(fixture.ledgerSweeps() - sweeps[0] >= 3, "Letting three more ledger sweeps pass");
            var context = "; while merged: " + whileMerged[0] + "; after the split: " + afterSplit[0] + "; now: "
                    + fixture.returns(output);
            helper.assertValueEqual(fixture.owed(output), 0L, "The native delivery settled the debt" + context);
            helper.assertValueEqual(fixture.heldForConsumer(output), 0L, "Nothing is held for the consumer" + context);
            helper.assertValueEqual(fixture.transit(output), 0L, "Nothing is in transit" + context);
            helper.assertValueEqual(fixture.watchedJobs(), 0, "The finished job retires" + context);
            helper.assertValueEqual(fixture.held(providerStorage.get(), output), unit,
                    "The player's output stays on the provider network" + context);
            helper.assertValueEqual(fixture.held(consumerStorage.get(), output), unit,
                    "The consumer keeps only its job's output" + context);
            helper.assertTrue(fixture.consumerCpuEmpty(), "The CPU holds nothing" + context);
            PolicyEvidence.write(testId, 19, Map.of("merge", merge.name(), "output", fluid ? "lava" : "stone",
                    "owedAfter", "0", "watchedJobs", "0", "playerOutputKept", "1"));
            fixture.close();
        });
    }
}
