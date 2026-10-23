package space.controlnet.ae2federation.test;

import static space.controlnet.ae2federation.test.crafting.PatternProjectionFixture.cobblestone;
import static space.controlnet.ae2federation.test.crafting.PatternProjectionFixture.stone;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
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
        mergedMidJob(helper, Merge.BRIDGE_PART, "projectionmergedbridgereturn");
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 1200)
    public static void projectionMergedCableReturn(GameTestHelper helper) {
        mergedMidJob(helper, Merge.JOINING_CABLE, "projectionmergedcablereturn");
    }

    private static void mergedMidJob(GameTestHelper helper, Merge merge, String testId) {
        var fixture = new PatternProjectionFixture(helper);
        var stage = new int[1];
        var sweeps = new long[1];
        var whileMerged = new String[1];
        var afterSplit = new String[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] > 0 || fixture.ready(), "Waiting for both networks and their providers");
            if (stage[0] == 0) {
                if (merge == Merge.BRIDGE_PART) fixture.fluixCableUnderBridge();
                fixture.preferConsumerChest();
                fixture.enableRules();
                fixture.putInConsumer(cobblestone(), 1);
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                helper.assertFalse(fixture.merged(), "The Bridge part keeps the networks apart");
                helper.assertTrue(fixture.consumerService().isCraftable(stone()), "Waiting for the projection");
                fixture.begin(stone(), 1);
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                helper.assertTrue(fixture.planReady(), "Waiting for the consumer's plan");
                helper.assertTrue(fixture.submit(), "The consumer's own CPU must take the job");
                stage[0] = 3;
            }
            if (stage[0] == 3) {
                helper.assertValueEqual(fixture.inMachine(Items.COBBLESTONE), 1L, "Waiting for the push");
                helper.assertValueEqual(fixture.owed(stone()), 1L, "The provider network owes the consumer one stone");
                if (merge == Merge.BRIDGE_PART) fixture.disconnect();
                else fixture.placeJoiningCable();
                stage[0] = 4;
            }
            if (stage[0] == 4) {
                helper.assertTrue(fixture.merged(), "Waiting for the networks to become one Grid");
                helper.assertValueEqual(fixture.busyConsumerCpus(), 1L, "Waiting for the merged Grid to list the CPU");
                helper.assertFalse(fixture.providerRouted(), "Waiting for Federation to stop routing the merged Grid");
                helper.assertValueEqual(fixture.runMachine(1), 1L, "The merged Grid takes the machine's stone");
                stage[0] = 5;
            }
            if (stage[0] == 5) {
                helper.assertValueEqual(fixture.visibleConsumerCpus(), 1, "The merged Grid lists the CPU");
                helper.assertValueEqual(fixture.busyConsumerCpus(), 0L, "Waiting for the CPU to take its stone natively");
                whileMerged[0] = fixture.settlement() + "; " + fixture.returns(stone());
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
                afterSplit[0] = fixture.returns(stone());
                helper.assertValueEqual(fixture.held(fixture.consumerChest(), stone()), 1L,
                        "The job's stone reaches the consumer; after the split: " + afterSplit[0]);
                helper.assertValueEqual(fixture.held(fixture.providerChest(), stone()), 0L,
                        "The provider network keeps no stone; after the split: " + afterSplit[0]);
                // Unrelated stone that a player's import bus stores on the provider network after the job.
                helper.assertValueEqual(fixture.providerGrid().getStorageService().getInventory().insert(stone(), 1,
                        Actionable.MODULATE, IActionSource.empty()), 1L, "The provider network stores a player's stone");
                sweeps[0] = fixture.ledgerSweeps();
                stage[0] = 8;
            }
            helper.assertTrue(fixture.ledgerSweeps() - sweeps[0] >= 3, "Letting three more ledger sweeps pass");
            var context = "; while merged: " + whileMerged[0] + "; after the split: " + afterSplit[0] + "; now: "
                    + fixture.returns(stone());
            helper.assertValueEqual(fixture.owed(stone()), 0L, "The native delivery settled the debt" + context);
            helper.assertValueEqual(fixture.heldForConsumer(stone()), 0L, "Nothing is held for the consumer" + context);
            helper.assertValueEqual(fixture.transit(stone()), 0L, "Nothing is in transit" + context);
            helper.assertValueEqual(fixture.watchedJobs(), 0, "The finished job retires" + context);
            helper.assertValueEqual(fixture.held(fixture.providerChest(), stone()), 1L,
                    "The player's stone stays on the provider network" + context);
            helper.assertValueEqual(fixture.held(fixture.consumerChest(), stone()), 1L,
                    "The consumer keeps only its job's stone" + context);
            helper.assertTrue(fixture.consumerCpuEmpty(), "The CPU holds nothing" + context);
            PolicyEvidence.write(testId, 19, Map.of("merge", merge.name(), "owedAfter", "0", "watchedJobs", "0",
                    "playerStoneKept", "1"));
            fixture.close();
        });
    }
}
