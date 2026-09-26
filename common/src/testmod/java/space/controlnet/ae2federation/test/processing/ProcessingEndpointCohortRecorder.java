package space.controlnet.ae2federation.test.processing;

import java.util.List;

final class ProcessingEndpointCohortRecorder {
    private ProcessingEndpointCohortRecorder() {}

    static void finishIfComplete(boolean cohortScene, int measuredJobs, GeneratedFactoryTopology topology,
            GeneratedSceneFixture fixture, List<EndpointCohortObservation> cohorts) {
        if (!cohortScene || measuredJobs % 3 != 0) {
            return;
        }
        var definition = topology.endpointCohorts().get(measuredJobs / 3 - 1);
        var observed = ProcessingBenchmarkObservation.finishCohort();
        cohorts.add(new EndpointCohortObservation(definition.cohortId(), definition.scenario(),
                definition.laneIndexes().stream().map(fixture::endpointIdentity).toList(), definition.laneIndexes(),
                observed.offered(), observed.accepted(), observed.completed(), observed.rejected(),
                observed.noProgress(), observed.busyTicks(), observed.resultLockTicks(), observed.returnAttempts(),
                observed.returnProgress(), observed.peakRetained(), observed.finalRetained(),
                observed.peakReturnDepth(), observed.finalReturnDepth(), observed.peakQueueDepth(),
                observed.finalQueueDepth(), observed.notifications(), observed.eligibleCompletions(),
                observed.fairnessSpread(), observed.starvationBound()));
    }
}
