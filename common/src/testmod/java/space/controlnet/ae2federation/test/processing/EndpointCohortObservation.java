package space.controlnet.ae2federation.test.processing;

import java.util.List;

public record EndpointCohortObservation(int cohortId, EndpointCohortScenario scenario, List<String> endpointIdentities,
        List<Integer> laneIdentities, long offered, long accepted, long completed, long rejected,
        long noProgress, long busyTicks, long resultLockTicks, long returnAttempts, long returnProgress,
        long peakRetained, long finalRetained, long peakReturnDepth, long finalReturnDepth,
        long peakQueueDepth, long finalQueueDepth, long notifications, long eligibleCompletions,
        long fairnessSpread, long starvationBound) {
    public EndpointCohortObservation {
        endpointIdentities = List.copyOf(endpointIdentities);
        laneIdentities = List.copyOf(laneIdentities);
        if (cohortId < 0 || endpointIdentities.size() != 3 || endpointIdentities.stream().distinct().count() != 3
                || laneIdentities.size() != 3 || laneIdentities.stream().distinct().count() != 3
                || scenario == null) {
            throw new IllegalArgumentException("Endpoint cohort identity is incomplete");
        }
    }

    String serialized() {
        return cohortId + "|" + scenario.serialized() + "|" + String.join(",", endpointIdentities) + "|"
                + laneIdentities.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")) + "|"
                + offered + "," + accepted + "," + completed + "," + rejected + "," + noProgress + ","
                + busyTicks + "," + resultLockTicks + "," + returnAttempts + "," + returnProgress + ","
                + peakRetained + "," + finalRetained + "," + peakReturnDepth + "," + finalReturnDepth + ","
                + peakQueueDepth + "," + finalQueueDepth + "," + notifications + "," + eligibleCompletions + ","
                + fairnessSpread + "," + starvationBound;
    }
}
