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
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
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
                helper.assertValueEqual(fixture.providerGrid().getStorageService().getInventory().insert(output, unit,
                        Actionable.MODULATE, IActionSource.empty()), unit,
                        "The provider network stores a player's output");
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
