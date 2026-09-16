package space.controlnet.ae2federation.qa;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class TaskTwentyClosureContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void repositoryAndCaptureIdentityReplaceManualSourceClosure() throws IOException {
        var qa = Files.readString(ROOT.resolve("gradle/federation-qa.gradle"));
        var baseline = Files.readString(ROOT.resolve("tests/benchmarks/processing/baseline.json"));
        assertFalse(Files.exists(ROOT.resolve("tests/benchmarks/processing/source-authority.json")));
        assertTrue(qa.contains("federationTaskTwentyRepositoryIdentityCharacterization"));
        assertTrue(qa.contains("federationTaskTwentyOldEvidenceContract"));
        assertTrue(qa.contains("federationTaskTwentyShutdownTimeoutContract"));
        assertFalse(qa.contains("minusSeconds(900)"));
        assertTrue(baseline.contains("\"captureIdentitySha256\""));
        assertFalse(baseline.contains("\"benchmarkSourceSha256\""));
    }

    @Test
    void endpointCohortsUseFiveTypedScenarioOwnedEquations() throws IOException {
        var topology = source("processing/GeneratedFactoryTopology.java");
        var recorder = source("processing/ProcessingEndpointCohortRecorder.java");
        var tracker = source("processing/ProcessingEndpointCohortTracker.java");
        var profile = Files.readString(ROOT.resolve("tests/benchmarks/processing/processing-small.json"));
        assertTrue(topology.contains("EndpointCohortScenario.values()"));
        assertTrue(recorder.contains("definition.scenario()"));
        assertFalse(recorder.contains("busy-result-lock-reject-return-congestion-eligible"));
        assertTrue(tracker.contains("completionWindow"));
        assertFalse(tracker.contains("completed, max - min, busyTicks"));
        for (var type : Set.of("busy", "result-locked", "rejecting", "return-congested", "eligible")) {
            assertTrue(profile.contains("\"" + type + "\""), () -> "missing typed cohort: " + type);
        }
    }

    @Test
    void adversarialSelfTestRetainsCompleteSemanticMatrix() throws IOException {
        var qa = Files.readString(ROOT.resolve("gradle/federation-qa.gradle"));
        var requiredProbes = Set.of("pattern-omission", "pattern-duplicate", "registered-unused-pattern",
                "marker-only-participation", "metadata-only-seed", "fixed-alternate-schedule", "cohort-omission",
                "cohort-duplicate", "cohort-reorder", "cohort-overlap", "cohort-wrong-size",
                "cohort-wrong-identity", "cohort-wrong-type", "cohort-ineligible-completion",
                "cohort-fabricated-fairness", "cohort-fabricated-starvation", "cohort-fabricated-bounds",
                "lane-owner-swap", "lane-stale-generation", "unequal-workload",
                "starting-input", "offered-input", "final-input-inventory",
                "lifecycle-collection-missing", "lifecycle-entry-missing", "lifecycle-deadline-zero",
                "lifecycle-deadline-wrong", "lifecycle-grace-zero", "lifecycle-grace-unbounded",
                "broken-ts04-totals", "stale-baseline", "loosened-budget", "cleanup-omission",
                "cleanup-replacement");
        for (var probe : requiredProbes) {
            assertTrue(qa.contains(probe), () -> "missing Task 20 adversarial probe: " + probe);
        }
    }

    @Test
    void repositoryAndPersistedLifecycleContractsFailClosed() throws IOException {
        var qa = Files.readString(ROOT.resolve("gradle/federation-qa.gradle"));

        assertTrue(qa.contains("Task 20 rename destination identity characterization failed"));
        assertTrue(qa.contains("Process isolation is unavailable"));
        assertTrue(qa.contains("report.childLifecycles instanceof Map"));
        assertTrue(qa.contains("parsePositiveInteger(lifecycle.executionTimeoutSeconds"));
        assertTrue(qa.contains("parsePositiveInteger(lifecycle.shutdownGraceSeconds"));
        assertTrue(qa.contains("report.sourceRevision = 'stale-source-identity'"));
        assertFalse(qa.contains("report.startedAt = Instant.now().minusSeconds(3600)"));
    }

    private static String source(String relative) throws IOException {
        return Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/" + relative));
    }
}
