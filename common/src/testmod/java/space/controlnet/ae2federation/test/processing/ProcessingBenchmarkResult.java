package space.controlnet.ae2federation.test.processing;

import java.util.Arrays;
import java.util.List;

public record ProcessingBenchmarkResult(int gridCount, int physicalPatterns, int logicalPatterns, int logicalLanes,
        int routes, int providerEntries, int targetRelationships, int cpuLimit, long startingInput, long acceptedInput,
        long rejectedTargetMutation, long deliveredPrimary, long deliveredByproduct, long finalInputInventory,
        String generationDigest, String scheduleDigest, boolean deterministicReload, String originalLinkId,
        String reloadedLinkId, ProcessingBenchmarkObservation.Snapshot observation,
        List<PatternParticipationReceipt> participationReceipts,
        List<EndpointCohortObservation> endpointCohorts, long[] timingSamples) {
    public ProcessingBenchmarkResult {
        participationReceipts = List.copyOf(participationReceipts);
        endpointCohorts = List.copyOf(endpointCohorts);
        timingSamples = timingSamples.clone();
    }

    public String samples() {
        return Arrays.stream(timingSamples).mapToObj(Long::toString).reduce((left, right) -> left + "," + right)
                .orElse("");
    }
}
