package space.controlnet.ae2federation.qa;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProcessingBenchmarkContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void processingSmallDeclaresDeterministicEqualResourceScene() throws IOException {
        var profile = ROOT.resolve("tests/benchmarks/processing/processing-small.json");

        assertTrue(Files.isRegularFile(profile), "processing-small profile must exist");
        var content = Files.readString(profile);
        assertTrue(content.contains("\"seed\": 20019006"));
        assertTrue(content.contains("\"comparison\": \"native-single-grid-vs-federation-multi-provider\""));
        assertTrue(content.contains("\"equalResources\": true"));
        assertTrue(content.contains("\"physicalPatterns\": 256"));
        assertTrue(content.contains("\"logicalPatterns\": 256"));
        assertTrue(content.contains("\"logicalLanes\": 15"));
        assertTrue(content.contains("\"routes\": 270"));
        assertTrue(content.contains("\"providerEntries\": 270"));
        assertTrue(content.contains("\"targetRelationships\": 15"));
        assertTrue(content.contains("\"measurementSamples\""));
        assertTrue(content.contains("\"gridCount\": 16"));
        assertTrue(content.contains("\"logicalPatterns\": 256"));
        assertTrue(content.contains("\"startingResourceUnits\": 4096"));
        assertTrue(content.contains("\"replayVariants\""));
        assertTrue(content.contains("\"large-batch-low-frequency\""));
        assertTrue(content.contains("\"small-batch-high-frequency\""));
        assertTrue(content.contains("\"busy\""));
        assertTrue(content.contains("\"result-locked\""));
        assertTrue(content.contains("\"rejecting\""));
        assertTrue(content.contains("\"return-congested\""));
    }

    @Test
    void processingSmallFreezesIdentityBoundBaselineAndBudgets() throws IOException {
        var baseline = ROOT.resolve("tests/benchmarks/processing/baseline.json");
        var budgets = ROOT.resolve("tests/benchmarks/processing/budgets.json");

        assertTrue(Files.isRegularFile(baseline), "frozen Processing baseline must exist");
        assertTrue(Files.isRegularFile(budgets), "frozen Processing budgets must exist");
        var baselineContent = Files.readString(baseline);
        var budgetContent = Files.readString(budgets);
        for (var field : new String[] { "schemaVersion", "profileVersion", "seed", "captureIdentitySha256",
                "dependencyLockSha256", "minecraftVersion", "neoForgeVersion", "ae2Version", "measurement" }) {
            assertTrue(baselineContent.contains("\"" + field + "\""), () -> "missing baseline identity " + field);
        }
        assertFalse(baselineContent.contains("\"sourceRevision\""));
        assertFalse(baselineContent.contains("\"dirtyDiffSha256\""));
        assertFalse(baselineContent.contains("\"benchmarkSourceSha256\""));
        assertTrue(budgetContent.contains("\"timingClassification\": \"environment-sensitive-secondary\""));
        assertTrue(budgetContent.contains("\"maxRetainedResponsibility\""));
        assertTrue(budgetContent.contains("\"resourceReconciliationRequired\": true"));
    }

    @Test
    void benchmarkHarnessRegistersRuntimeAndAdversarialAccounting() throws IOException {
        var manifest = Files.readString(ROOT.resolve("tests/scenarios/manifest.json"));
        var qa = Files.readString(ROOT.resolve("gradle/federation-qa.gradle"));
        var registration = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/FederationTestMod.java"));
        var gameTest = ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/ProcessingBenchmarkGameTests.java");

        assertTrue(manifest.contains("\"id\":\"processing-small\""));
        assertTrue(manifest.contains("benchmark.reject-empty-work"));
        assertTrue(manifest.contains("benchmark.resource-accounting"));
        assertTrue(manifest.contains("benchmark.evidence-identity"));
        assertTrue(manifest.contains("benchmark.execution-timeout"));
        assertTrue(manifest.contains("benchmark.shutdown-timeout"));
        assertTrue(registration.contains("ProcessingBenchmarkGameTests.class"));
        assertTrue(Files.isRegularFile(gameTest));
        assertTrue(qa.contains("verifyTaskTwentyBenchmarkEvidence"));
        assertTrue(qa.contains("federationTaskTwentyEvidenceSelfTest"));
        assertTrue(qa.contains("Task 20 resource reconciliation mismatch"));
        assertTrue(qa.contains("Task 20 benchmark capture identity is stale"));
        assertTrue(qa.contains("Task 20 seed-derived topology mismatch"));
        assertTrue(qa.contains("Task 20 planner/CPU lifecycle mismatch"));
        assertTrue(qa.contains("Task 20 deterministic reload mismatch"));
        assertTrue(qa.contains("Task 20 T-S04 equal-volume mismatch"));
        assertTrue(qa.contains("Task 20 T-S06 fairness or backpressure mismatch"));
    }

    @Test
    void generatedFactoryUsesNativePlannerCpuAndReloadInsteadOfDirectFixturePushes() throws IOException {
        var scene = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/processing/GeneratedProcessingFactoryScene.java"));
        var coordinator = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/processing/ProcessingCraftingCoordinator.java"));
        var requester = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/crafting/NativeCraftingRequester.java"));

        assertTrue(coordinator.contains("beginCraftingCalculation"));
        assertTrue(requester.contains("MultiCraftingTracker"));
        assertTrue(coordinator.contains("cpuBusy"));
        assertTrue(requester.contains("writeToNBT"));
        assertTrue(requester.contains("tracker.readFromNBT(persistedState)"));
        assertTrue(requester.contains("managedNode.loadFromNBT(persistedState)"));
        assertTrue(scene.contains("ControlledProcessingMachine"));
        assertFalse(scene.contains("nativeFixture.pushInputs"));
        assertFalse(scene.contains("federationFixture.pushLaneWithInputs"));
    }

    @Test
    void benchmarkEvidenceUsesAuthoritativeRuntimeStateInsteadOfLiteralBoundedMetrics() throws IOException {
        var evidence = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/processing/ProcessingBenchmarkEvidence.java"));
        var observation = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/processing/ProcessingBenchmarkObservation.java"));

        assertFalse(evidence.contains("facts.put(prefix + \".finalReturnInventory\", \"0\")"));
        assertFalse(evidence.contains("facts.put(prefix + \".retainedCollectionSize\", \"0\")"));
        assertFalse(evidence.contains("facts.put(prefix + \".retrySchedulerSize\", \"0\")"));
        assertTrue(observation.contains("plannerCalls"));
        assertTrue(observation.contains("submittedJobs"));
        assertTrue(observation.contains("cpuBusyTicks"));
        assertTrue(observation.contains("machineCompletions"));
        assertTrue(observation.contains("reloadCount"));
        assertTrue(observation.contains("peakReturnInventoryUnits"));
        assertTrue(observation.contains("peakSendQueueUnits"));
        assertTrue(observation.contains("logic.getReturnInv()"));
        assertTrue(evidence.contains("nativeResult.acceptedInput() == federationResult.acceptedInput()"));
        assertFalse(evidence.contains("nativeResult.observation().requestedInputUnits()"));
    }

    @Test
    void processingBenchmarkInstrumentationRemainsTestmodOnlyAndBounded() throws IOException {
        var observer = ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/processing/ProcessingBenchmarkObservation.java");
        var productionProbe = ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/processing/ProcessingBenchmarkObservation.java");

        assertTrue(Files.isRegularFile(observer));
        assertFalse(Files.exists(productionProbe));
        var content = Files.readString(observer);
        assertFalse(content.contains("ArrayList<"), "benchmark observation must not retain per-event lists");
        assertFalse(content.contains("Logger"), "benchmark observation must not emit per-event logs");
        assertTrue(content.contains("peakRetainedResponsibility"));
        assertTrue(content.contains("reset"));
    }

    @Test
    void generatedWorkloadRequiresRuntimePatternParticipationAndSeedAuthority() throws IOException {
        var scene = source("processing/GeneratedProcessingFactoryScene.java");
        var fixture = source("processing/GeneratedSceneFixture.java");
        var topology = source("processing/GeneratedFactoryTopology.java");

        assertTrue(scene.contains("PatternParticipationReceipt"));
        assertTrue(scene.contains("topology.patternSchedule()"));
        assertTrue(fixture.contains("topology.patternLaneAssignments()"));
        assertTrue(fixture.contains("topology.targetPositions()"));
        assertFalse(scene.contains("selectSharedLane"));
        assertFalse(fixture.contains("logicalPatterns() - 1"));
        assertTrue(topology.contains("scheduleDigest"));
    }

    @Test
    void taskTwentyEvidenceRequiresFiveIndependentEndpointCohorts() throws IOException {
        var evidence = source("processing/ProcessingBenchmarkEvidence.java");
        var tracker = source("processing/ProcessingEndpointCohortTracker.java");
        var cohort = ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/processing/EndpointCohortObservation.java");

        assertTrue(Files.isRegularFile(cohort));
        assertTrue(evidence.contains("result.endpointCohorts()"));
        assertTrue(tracker.contains("resetCounters();"));
        assertFalse(evidence.contains("addEndpointCohort(facts, federationResult)"));
        assertFalse(evidence.contains("facts.put(\"tS06.cohortSize\", \"3\")"));
    }

    @Test
    void taskTwentyUsesRepositoryIdentityWithoutManualSourceAuthority() throws IOException {
        var qa = Files.readString(ROOT.resolve("gradle/federation-qa.gradle"));
        assertFalse(Files.exists(ROOT.resolve("tests/benchmarks/processing/source-authority.json")));
        assertTrue(qa.contains("def currentSourceIdentity"));
        assertTrue(qa.contains("--untracked-files=all"));
        assertTrue(qa.contains(":(exclude).omo/**"));
        assertTrue(qa.contains("dirtyDiffSha256 != identity.dirtySha256"));
        assertFalse(qa.contains("processingBenchmarkSourceAuthority"));
        assertFalse(qa.contains("verifyProcessingBenchmarkPhysicalLines"));
    }

    @Test
    void taskTwentyPersistsStartingResourceEvidenceWithoutTimingGates() throws IOException {
        var evidence = source("processing/ProcessingBenchmarkEvidence.java");
        var baseline = Files.readString(ROOT.resolve("tests/benchmarks/processing/baseline.json"));
        var budgets = Files.readString(ROOT.resolve("tests/benchmarks/processing/budgets.json"));

        assertTrue(evidence.contains("startingInput=acceptedInput+nativeRemainder+finalRetained+finalInputInventory"));
        assertTrue(evidence.contains("prefix + \".startingInput\""));
        assertTrue(evidence.contains("prefix + \".offeredInput\""));
        assertFalse(evidence.contains("prefix + \".initialInput\""));
        assertTrue(baseline.contains("\"startingInputPerScene\": 4096"));
        assertTrue(baseline.contains("\"offeredInputPerScene\": 3840"));
        assertTrue(baseline.contains("\"acceptedInputPerScene\": 3840"));
        assertTrue(baseline.contains("\"finalInputInventoryPerScene\": 256"));
        assertFalse(baseline.contains("\"performanceMedians\""));
        assertFalse(budgets.contains("\"regressionFactor\""));
        assertTrue(budgets.contains("\"timingClassification\": \"environment-sensitive-secondary\""));
    }

    private static String source(String relative) throws IOException {
        return Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/" + relative));
    }
}
