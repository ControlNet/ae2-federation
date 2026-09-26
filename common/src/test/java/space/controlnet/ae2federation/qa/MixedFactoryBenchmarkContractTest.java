package space.controlnet.ae2federation.qa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class MixedFactoryBenchmarkContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void profileDrivesFourIsolatedEqualVolumeNativeRuns() throws IOException {
        var profile = Files.readString(ROOT.resolve("tests/benchmarks/mixed/mixed-small.json"));

        assertEquals(4, value(profile, "warmupIterations") + value(profile, "measuredIterations"));
        assertEquals(value(profile, "largeBatchCalls") * value(profile, "largeBatchUnits"),
                value(profile, "smallBatchCalls") * value(profile, "smallBatchUnits"));
        assertEquals(11, value(profile, "resourceKeyCount"));
    }

    @Test
    void benchmarkUsesNativeReceiptsAndHasNoSelfAuthoredPhaseCounters() throws IOException {
        var scene = source("mixed/MixedFactoryScene.java");
        var observation = source("mixed/MixedFactoryObservation.java");
        var evidence = source("mixed/MixedFactoryEvidence.java");

        for (var forbidden : Set.of("MixedFactoryObservation.planning()", "MixedFactoryObservation.submitted(",
                "MixedFactoryObservation.waiting()", "MixedFactoryObservation.executing()",
                "MixedFactoryObservation.processingHandler(", "MixedFactoryObservation.stockingCycle(",
                "MixedFactoryObservation.largeBatch(", "MixedFactoryObservation.smallBatch(")) {
            assertFalse(scene.contains(forbidden), () -> "self-authored native metric remains: " + forbidden);
        }
        assertTrue(observation.contains("TerminalNativeObservation.snapshot()"));
        assertTrue(observation.contains("ProcessingNativeObservation.snapshot()"));
        assertTrue(observation.contains("AutomationNativeObservation.snapshot()"));
        assertTrue(evidence.contains("projectionInserted"));
        assertTrue(evidence.contains("handlerProduced"));
        assertTrue(evidence.contains("callbackInserted"));
        assertFalse(evidence.contains("initialSource=storageExtracted+finalSource"));
        assertTrue(source("mixed/MixedMachineBlockEntity.java").contains("implements IItemHandler"));
        assertFalse(source("mixed/MixedProcessingMachine.java").contains("new ItemStack"));
        assertFalse(source("mixed/MixedProcessingMachine.java").contains("extractTargetItem"));
        assertTrue(source("mixed/MixedFactoryProfile.java").contains("verifyAxisVariations()"));
    }

    @Test
    void exactConsumersCoverEveryReportedForgeryClass() throws IOException {
        var qa = Files.readString(ROOT.resolve("gradle/federation-qa.gradle"));
        for (var probe : Set.of("fabricated-accounting", "fake-handler-attribution", "fake-stocking-attribution",
                "call-quantity-conflation", "changed-iteration-count", "extra-metric", "copied-iteration-facts",
                "empty-native-work", "empty-physical-work", "silent-drop", "no-recovery", "hidden-queue",
                "duplicate-job")) {
            assertTrue(qa.contains(probe), () -> "missing fully rebound Task 30 probe: " + probe);
        }
        assertTrue(qa.contains("metrics.keySet() as Set != expectedFields"));
        assertTrue(qa.contains("Task 30 per-key physical equation mismatch"));
        assertTrue(qa.contains("coordinated-accounting"));
        assertTrue(qa.contains("coordinated-handler-owner"));
        assertTrue(qa.contains("coordinated-stocking-owner"));
        assertTrue(qa.contains("unbound-handler-quantity"));
        assertTrue(qa.contains("unbound-peak-in-flight"));
        assertTrue(qa.contains("numeric(\"${prefix}.handlerQuantity\") != handlerQuantity"));
        assertTrue(qa.contains("numeric(\"${prefix}.peakInFlight\") != peakInFlight"));
        assertTrue(qa.contains("independent runtime authority mismatch"));
    }

    @Test
    void runtimeAuthorityIsAppendedAtNativeOperationsInsteadOfProjectedAtCompletion() throws IOException {
        var receipt = source("mixed/MixedFactoryRuntimeReceipt.java");
        var evidence = source("mixed/MixedFactoryEvidence.java");
        var scene = source("mixed/MixedFactoryScene.java");
        var automation = source("automation/AutomationNativeObservation.java");
        var processing = source("processing/ProcessingNativeObservation.java");
        var terminal = source("crafting/TerminalNativeObservation.java");
        var requester = source("crafting/NativeCraftingRequester.java");
        var qa = Files.readString(ROOT.resolve("gradle/federation-qa.gradle"));

        assertFalse(receipt.contains("List<MixedFactoryIteration>"));
        assertFalse(evidence.contains("MixedFactoryRuntimeReceipt.write(profile, runs)"));
        assertTrue(receipt.contains("StandardOpenOption.APPEND"));
        assertTrue(scene.contains("MixedFactoryRuntimeReceipt.inventory"));
        assertTrue(automation.contains("MixedFactoryRuntimeReceipt.projection"));
        assertTrue(processing.contains("MixedFactoryRuntimeReceipt.processing"));
        assertTrue(terminal.contains("MixedFactoryRuntimeReceipt.terminal"));
        assertTrue(requester.contains("MixedFactoryRuntimeReceipt.callback"));
        assertTrue(qa.contains("benchmark-runtime.receipts"));
        assertTrue(qa.contains("Task 30 raw receipt sequence mismatch"));
        assertTrue(qa.contains("coordinated-projections"));
        assertTrue(qa.contains("duplicate-raw-receipt"));
        assertTrue(qa.contains("replace-raw-receipt"));
        assertTrue(qa.contains("missing-raw-receipt"));
        assertTrue(qa.contains("extra-raw-receipt"));
        assertTrue(qa.contains("conflicting-raw-receipt"));
        assertTrue(qa.contains("out-of-order-raw-receipt"));
        assertTrue(qa.contains("wrong-phase-raw-receipt"));
        assertTrue(qa.contains("wrong-iteration-raw-receipt"));
    }

    private static String source(String relative) throws IOException {
        return Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/" + relative));
    }

    private static long value(String json, String field) {
        var matcher = java.util.regex.Pattern.compile("\\\"" + field + "\\\"\\s*:\\s*([0-9]+)").matcher(json);
        assertTrue(matcher.find(), () -> "missing numeric profile field: " + field);
        return Long.parseLong(matcher.group(1));
    }
}
