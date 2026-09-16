package space.controlnet.ae2federation.test.processing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

final class ProcessingBenchmarkRuntimeEvidence {
    private ProcessingBenchmarkRuntimeEvidence() {
    }

    static void addSeededRuntime(Map<String, String> facts, ProcessingBenchmarkResult nativeLarge,
            ProcessingBenchmarkResult federationLarge, ProcessingBenchmarkResult nativeSmall,
            ProcessingBenchmarkResult federationSmall) {
        addRuntimeIdentity(facts, "native.large", nativeLarge);
        addRuntimeIdentity(facts, "federation.large", federationLarge);
        addRuntimeIdentity(facts, "native.small", nativeSmall);
        addRuntimeIdentity(facts, "federation.small", federationSmall);
        addParticipation(facts, "native", nativeLarge, nativeSmall);
        addParticipation(facts, "federation", federationLarge, federationSmall);
        addCohorts(facts, federationLarge.endpointCohorts());
    }

    private static void addRuntimeIdentity(Map<String, String> facts, String prefix,
            ProcessingBenchmarkResult result) {
        facts.put("runtime." + prefix + ".topologyDigest", result.generationDigest());
        facts.put("runtime." + prefix + ".scheduleDigest", result.scheduleDigest());
    }

    private static void addParticipation(Map<String, String> facts, String kind,
            ProcessingBenchmarkResult large, ProcessingBenchmarkResult small) {
        var receipts = new ArrayList<PatternParticipationReceipt>(256);
        receipts.addAll(large.participationReceipts());
        receipts.addAll(small.participationReceipts());
        if (receipts.size() != 256 || receipts.stream().map(PatternParticipationReceipt::patternSlot)
                .distinct().count() != 256) {
            throw new IllegalStateException("Runtime Pattern participation must contain exactly 256 identities");
        }
        var lanes = new HashSet<Integer>();
        receipts.forEach(receipt -> lanes.add(receipt.laneIndex()));
        if (lanes.size() != 15) {
            throw new IllegalStateException("Runtime Pattern participation must use all 15 Lanes");
        }
        receipts.sort(Comparator.comparingInt(PatternParticipationReceipt::patternSlot));
        facts.put("patterns." + kind + ".count", Integer.toString(receipts.size()));
        facts.put("patterns." + kind + ".distinct", "256");
        facts.put("patterns." + kind + ".lanes", Integer.toString(lanes.size()));
        var runtimeOrder = new ArrayList<PatternParticipationReceipt>(256);
        runtimeOrder.addAll(large.participationReceipts());
        runtimeOrder.addAll(small.participationReceipts());
        var serializedOrder = runtimeOrder.stream().map(PatternParticipationReceipt::serialized)
                .reduce((left, right) -> left + ";" + right).orElseThrow();
        facts.put("patterns." + kind + ".runtimeScheduleDigest", sha256(serializedOrder));
        for (var receipt : receipts) {
            facts.put("patterns." + kind + ".receipt." + String.format("%03d", receipt.patternSlot()),
                    receipt.serialized());
        }
    }

    private static void addCohorts(Map<String, String> facts, List<EndpointCohortObservation> cohorts) {
        if (cohorts.size() != 5) {
            throw new IllegalStateException("T-S06 requires exactly five independently observed cohorts");
        }
        facts.put("tS06.cohortCount", Integer.toString(cohorts.size()));
        facts.put("tS06.retrySchedulerPresent", "false");
        for (var cohort : cohorts) {
            facts.put("tS06.cohort." + cohort.cohortId(), cohort.serialized());
        }
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
