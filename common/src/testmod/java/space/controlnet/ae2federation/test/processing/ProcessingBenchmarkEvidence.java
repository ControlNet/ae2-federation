package space.controlnet.ae2federation.test.processing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProcessingBenchmarkEvidence {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessingBenchmarkEvidence.class);

    private ProcessingBenchmarkEvidence() {
    }

    public static void write(ProcessingBenchmarkProfile profile, ProcessingBenchmarkResult nativeResult,
            ProcessingBenchmarkResult federationResult, ProcessingBenchmarkResult nativeSmall,
            ProcessingBenchmarkResult federationSmall, int assertions) {
        var facts = new TreeMap<String, String>();
        facts.put("schemaVersion", "1");
        facts.put("status", "passed");
        facts.put("kind", "benchmark");
        facts.put("testId", "processingbenchmarksmall");
        facts.put("structure", "ae2federation_test:harness_native_smoke");
        facts.put("assertions", Integer.toString(assertions));
        facts.put("operations", Integer.toString((profile.largeVariant().measuredInputCallsPerScene()
                + profile.smallVariant().measuredInputCallsPerScene()) * 2));
        facts.put("inserted", Long.toString(nativeResult.acceptedInput() + federationResult.acceptedInput()
                + nativeSmall.acceptedInput() + federationSmall.acceptedInput()));
        facts.put("extracted", facts.get("inserted"));
        facts.put("elapsedNanos", Long.toString(elapsed(nativeResult) + elapsed(federationResult)
                + elapsed(nativeSmall) + elapsed(federationSmall)));
        facts.put("profileVersion", profile.profileVersion());
        facts.put("seed", Long.toString(profile.seed()));
        facts.put("profileSha256", profile.profileSha256());
        facts.put("comparison", "native-single-grid-vs-federation-multi-provider");
        facts.put("equalResources", Boolean.toString(equalResources(nativeResult, federationResult)));
        addScene(facts, "native", profile, nativeResult);
        addScene(facts, "federation", profile, federationResult);
        addVariant(facts, "large", profile.largeBatchCalls(), profile.largeBatchUnits(), nativeResult,
                federationResult);
        addVariant(facts, "small", profile.smallBatchCalls(), profile.smallBatchUnits(), nativeSmall,
                federationSmall);
        facts.put("tS04.equalVolume", Boolean.toString(
                (long) profile.largeBatchCalls() * profile.largeBatchUnits()
                        == (long) profile.smallBatchCalls() * profile.smallBatchUnits()
                        && nativeResult.acceptedInput() == nativeSmall.acceptedInput()
                        && federationResult.acceptedInput() == federationSmall.acceptedInput()));
        requireCohorts(federationResult);
        ProcessingBenchmarkRuntimeEvidence.addSeededRuntime(facts, nativeResult, federationResult,
                nativeSmall, federationSmall);
        facts.put("reconciliation.inputEquation",
                "startingInput=acceptedInput+nativeRemainder+finalRetained+finalInputInventory");
        facts.put("reconciliation.outputEquation", "generatedOutput=deliveredOutput+finalReturnInventory");
        facts.put("reconciliation.currentRun", "true");
        facts.put("reconciliation.crossRunCarryover", "0");
        facts.put("timing.classification", "environment-sensitive-secondary");
        facts.put("timing.nativeSamplesNanos", nativeResult.samples());
        facts.put("timing.federationSamplesNanos", federationResult.samples());
        facts.put("timing.smallNativeSamplesNanos", nativeSmall.samples());
        facts.put("timing.smallFederationSamplesNanos", federationSmall.samples());
        var configured = System.getProperty("ae2federation.nativeEvidenceFile", "");
        if (configured.isBlank()) {
            return;
        }
        var path = Path.of(configured).toAbsolutePath().normalize();
        var temporary = path.resolveSibling(path.getFileName() + ".tmp");
        var lines = new ArrayList<String>(facts.size());
        facts.forEach((name, value) -> lines.add(name + "=" + value));
        try {
            Files.createDirectories(path.getParent());
            Files.write(temporary, lines);
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write Processing benchmark evidence " + path, exception);
        }
        LOGGER.info("AE2F_PROCESSING_BENCHMARK_SUMMARY profile={} seed={} nativeCalls={} federationCalls={}"
                        + " acceptedInput={} deliveredOutput={} rejectedAttempts={} peakRetained={}",
                profile.profileVersion(), profile.seed(), nativeResult.observation().nativeInputCalls(),
                federationResult.observation().nativeInputCalls(),
                nativeResult.acceptedInput() + federationResult.acceptedInput(),
                nativeResult.deliveredPrimary() + nativeResult.deliveredByproduct()
                        + federationResult.deliveredPrimary() + federationResult.deliveredByproduct(),
                nativeResult.observation().rejectedPushes() + federationResult.observation().rejectedPushes(),
                Math.max(nativeResult.observation().peakRetainedResponsibility(),
                        federationResult.observation().peakRetainedResponsibility()));
    }

    private static void addVariant(Map<String, String> facts, String variant, int calls, int units,
            ProcessingBenchmarkResult nativeResult, ProcessingBenchmarkResult federationResult) {
        var prefix = "tS04." + variant;
        facts.put(prefix + ".calls", Integer.toString(calls));
        facts.put(prefix + ".unitsPerCall", Integer.toString(units));
        facts.put(prefix + ".inputVolume", Long.toString((long) calls * units));
        facts.put(prefix + ".nativeAcceptedInput", Long.toString(nativeResult.acceptedInput()));
        facts.put(prefix + ".federationAcceptedInput", Long.toString(federationResult.acceptedInput()));
        facts.put(prefix + ".nativePlannerCalls", Long.toString(nativeResult.observation().plannerCalls()));
        facts.put(prefix + ".federationPlannerCalls", Long.toString(federationResult.observation().plannerCalls()));
        facts.put(prefix + ".nativeSubmittedJobs", Long.toString(nativeResult.observation().submittedJobs()));
        facts.put(prefix + ".federationSubmittedJobs", Long.toString(federationResult.observation().submittedJobs()));
        facts.put(prefix + ".nativeDispatchedLanes", Long.toString(nativeResult.observation().dispatchedLanes()));
        facts.put(prefix + ".federationDispatchedLanes", Long.toString(federationResult.observation().dispatchedLanes()));
    }

    private static void addScene(Map<String, String> facts, String prefix, ProcessingBenchmarkProfile profile,
            ProcessingBenchmarkResult result) {
        var observation = result.observation();
        var offeredInput = (long) profile.measurementSamples() * profile.batchUnits();
        var generatedPrimary = (long) profile.measurementSamples() * profile.primaryOutputUnits();
        var generatedByproduct = (long) profile.measurementSamples() * profile.byproductOutputUnits();
        facts.put(prefix + ".gridCount", Integer.toString(result.gridCount()));
        facts.put(prefix + ".physicalPatterns", Integer.toString(result.physicalPatterns()));
        facts.put(prefix + ".logicalPatterns", Integer.toString(result.logicalPatterns()));
        facts.put(prefix + ".logicalLanes", Integer.toString(result.logicalLanes()));
        facts.put(prefix + ".routes", Integer.toString(result.routes()));
        facts.put(prefix + ".providerEntries", Integer.toString(result.providerEntries()));
        facts.put(prefix + ".targetRelationships", Integer.toString(result.targetRelationships()));
        facts.put(prefix + ".cpuLimit", Integer.toString(result.cpuLimit()));
        facts.put(prefix + ".startingInput", Long.toString(result.startingInput()));
        facts.put(prefix + ".offeredInput", Long.toString(offeredInput));
        facts.put(prefix + ".nativeInputCalls", Long.toString(observation.nativeInputCalls()));
        facts.put(prefix + ".requestedInputUnits", Long.toString(observation.requestedInputUnits()));
        facts.put(prefix + ".acceptedPushes", Long.toString(observation.acceptedPushes()));
        facts.put(prefix + ".acceptedInput", Long.toString(result.acceptedInput()));
        facts.put(prefix + ".nativeRemainder", Long.toString(observation.nativeRemainderUnits()));
        facts.put(prefix + ".finalRetained", Long.toString(observation.finalRetainedResponsibility()));
        facts.put(prefix + ".returnAttempts", Long.toString(observation.returnAttempts()));
        facts.put(prefix + ".returnProgressAttempts", Long.toString(observation.returnAttemptsWithProgress()));
        facts.put(prefix + ".returnRetries", Long.toString(observation.returnAttempts() - profile.measurementSamples()));
        facts.put(prefix + ".returnedResources", Long.toString(generatedPrimary + generatedByproduct));
        facts.put(prefix + ".deliveredPrimary", Long.toString(result.deliveredPrimary()));
        facts.put(prefix + ".deliveredByproduct", Long.toString(result.deliveredByproduct()));
        facts.put(prefix + ".finalInputInventory", Long.toString(result.finalInputInventory()));
        facts.put(prefix + ".finalReturnInventory", Long.toString(observation.finalReturnInventoryUnits()));
        facts.put(prefix + ".rejectedTargetAttempts", Long.toString(observation.rejectedPushes()));
        facts.put(prefix + ".rejectedTargetMutation", Long.toString(result.rejectedTargetMutation()));
        facts.put(prefix + ".plannerCalls", Long.toString(observation.plannerCalls()));
        facts.put(prefix + ".submittedJobs", Long.toString(observation.submittedJobs()));
        facts.put(prefix + ".cpuBusyTicks", Long.toString(observation.cpuBusyTicks()));
        facts.put(prefix + ".machineDispatches", Long.toString(observation.machineDispatches()));
        facts.put(prefix + ".machineCompletions", Long.toString(observation.machineCompletions()));
        facts.put(prefix + ".reloadCount", Long.toString(observation.reloadCount()));
        facts.put(prefix + ".deterministicReload", Boolean.toString(result.deterministicReload()));
        facts.put(prefix + ".dispatchedLanes", Long.toString(observation.dispatchedLanes()));
        facts.put(prefix + ".fairnessSpread", Long.toString(observation.fairnessSpread()));
        facts.put(prefix + ".peakRetainedResponsibility",
                Long.toString(observation.peakRetainedResponsibility()));
        facts.put(prefix + ".retainedCollectionSize", Long.toString(observation.finalRetainedResponsibility()));
        facts.put(prefix + ".peakActiveReturnOwners", Integer.toString(observation.peakActiveReturnOwners()));
        facts.put(prefix + ".activeReturnOwners", Integer.toString(observation.activeReturnOwners()));
        facts.put(prefix + ".retrySchedulerPresent", "false");
        facts.put(prefix + ".inputReconciled", Boolean.toString(result.startingInput() == result.acceptedInput()
                + observation.nativeRemainderUnits() + observation.finalRetainedResponsibility()
                + result.finalInputInventory()));
        facts.put(prefix + ".outputReconciled", Boolean.toString(generatedPrimary == result.deliveredPrimary()
                && generatedByproduct == result.deliveredByproduct()));
    }

    private static boolean equalResources(ProcessingBenchmarkResult nativeResult,
            ProcessingBenchmarkResult federationResult) {
        return nativeResult.physicalPatterns() == federationResult.physicalPatterns()
                && nativeResult.logicalPatterns() == federationResult.logicalPatterns()
                && nativeResult.logicalLanes() == federationResult.logicalLanes()
                && nativeResult.routes() == federationResult.routes()
                && nativeResult.providerEntries() == federationResult.providerEntries()
                && nativeResult.targetRelationships() == federationResult.targetRelationships()
                && nativeResult.cpuLimit() == federationResult.cpuLimit()
                && nativeResult.startingInput() == federationResult.startingInput()
                && nativeResult.acceptedInput() == federationResult.acceptedInput();
    }

    private static void requireCohorts(ProcessingBenchmarkResult result) {
        if (result.endpointCohorts().size() != 5) {
            throw new IllegalStateException("Task 20 requires five independently observed Endpoint cohorts");
        }
    }

    private static long elapsed(ProcessingBenchmarkResult result) {
        return java.util.Arrays.stream(result.timingSamples()).sum();
    }
}
