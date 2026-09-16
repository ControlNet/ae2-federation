package space.controlnet.ae2federation.test.processing;

import java.util.List;
import java.util.Set;

final class ProcessingEndpointCohortTracker {
    private int cohortId = -1;
    private EndpointCohortScenario scenario;
    private Set<Integer> lanes = Set.of();
    private long offered;
    private long accepted;
    private long completed;
    private long rejected;
    private long noProgress;
    private long busyTicks;
    private long resultLocks;
    private long returnAttempts;
    private long returnProgress;
    private long peakRetained;
    private long finalRetained;
    private long peakReturnDepth;
    private long finalReturnDepth;
    private long peakQueueDepth;
    private long finalQueueDepth;
    private long notifications;
    private long eventWindow;
    private long eligibleCompletions;
    private long completionWindow;
    private long[] laneCompletions = new long[0];
    private long[] offeredAt = new long[0];

    void start(int id, EndpointCohortScenario selectedScenario, List<Integer> laneIndexes, int laneCount) {
        if (cohortId >= 0 || laneIndexes.size() != 3 || laneIndexes.stream().distinct().count() != 3) {
            throw new IllegalStateException("Endpoint cohort lifecycle is invalid");
        }
        resetCounters();
        cohortId = id;
        scenario = java.util.Objects.requireNonNull(selectedScenario);
        lanes = Set.copyOf(laneIndexes);
        laneCompletions = new long[laneCount];
        offeredAt = new long[laneCount];
        java.util.Arrays.fill(offeredAt, -1);
    }

    private void resetCounters() {
        offered = 0;
        accepted = 0;
        completed = 0;
        rejected = 0;
        noProgress = 0;
        busyTicks = 0;
        resultLocks = 0;
        returnAttempts = 0;
        returnProgress = 0;
        peakRetained = 0;
        finalRetained = 0;
        peakReturnDepth = 0;
        finalReturnDepth = 0;
        peakQueueDepth = 0;
        finalQueueDepth = 0;
        notifications = 0;
        eventWindow = 0;
        eligibleCompletions = 0;
        completionWindow = 0;
    }

    boolean active() {
        return cohortId >= 0;
    }

    void push(Integer lane, boolean wasAccepted) {
        if (!active() || lane == null || !lanes.contains(lane)) {
            return;
        }
        eventWindow++;
        offered++;
        if (wasAccepted) {
            accepted++;
            if (offeredAt[lane] < 0) {
                offeredAt[lane] = eventWindow;
            }
        } else {
            rejected++;
        }
    }

    void returned(Integer lane, boolean progressed) {
        if (!active() || lane == null || !lanes.contains(lane)) {
            return;
        }
        returnAttempts++;
        if (progressed) {
            returnProgress++;
        } else {
            noProgress++;
        }
    }

    void busy() {
        if (active()) {
            busyTicks++;
        }
    }

    void completed(int lane) {
        if (active() && lanes.contains(lane)) {
            eventWindow++;
            completed++;
            laneCompletions[lane]++;
            if (scenario == EndpointCohortScenario.ELIGIBLE) {
                if (offeredAt[lane] < 0) {
                    throw new IllegalStateException("Eligible Lane completed without an accepted offer");
                }
                eligibleCompletions++;
                completionWindow = Math.max(completionWindow, eventWindow - offeredAt[lane]);
            }
        }
    }

    void resultLocked(int lane) {
        if (active() && lanes.contains(lane)) {
            resultLocks++;
        }
    }

    void ownerState(int lane, long returnDepth, long queueDepth) {
        if (!active() || !lanes.contains(lane)) {
            return;
        }
        finalReturnDepth = returnDepth;
        finalQueueDepth = queueDepth;
        finalRetained = Math.addExact(returnDepth, queueDepth);
        peakReturnDepth = Math.max(peakReturnDepth, returnDepth);
        peakQueueDepth = Math.max(peakQueueDepth, queueDepth);
        peakRetained = Math.max(peakRetained, finalRetained);
    }

    void notified(int lane) {
        if (active() && lanes.contains(lane)) {
            notifications++;
        }
    }

    Snapshot finish() {
        if (!active()) {
            throw new IllegalStateException("Endpoint cohort is not active");
        }
        var counts = lanes.stream().mapToLong(lane -> laneCompletions[lane]).toArray();
        var min = java.util.Arrays.stream(counts).min().orElse(0);
        var max = java.util.Arrays.stream(counts).max().orElse(0);
        requireScenarioEvidence(max - min);
        var snapshot = new Snapshot(cohortId, offered, accepted, completed, rejected, noProgress, busyTicks,
                resultLocks, returnAttempts, returnProgress, peakRetained, finalRetained, peakReturnDepth,
                finalReturnDepth, peakQueueDepth, finalQueueDepth, notifications, eligibleCompletions, max - min,
                completionWindow);
        cohortId = -1;
        scenario = null;
        lanes = Set.of();
        return snapshot;
    }

    private void requireScenarioEvidence(long fairnessSpread) {
        var valid = switch (scenario) {
            case BUSY -> busyTicks > 0;
            case RESULT_LOCKED -> resultLocks == lanes.size();
            case REJECTING -> rejected > 0;
            case RETURN_CONGESTED -> noProgress > 0 && peakRetained > 0;
            case ELIGIBLE -> eligibleCompletions == lanes.size() && fairnessSpread == 0 && completionWindow > 0;
        };
        if (!valid) {
            throw new IllegalStateException("Endpoint cohort did not execute its " + scenario.serialized() + " mechanism");
        }
    }

    record Snapshot(int cohortId, long offered, long accepted, long completed, long rejected, long noProgress,
            long busyTicks, long resultLockTicks, long returnAttempts, long returnProgress, long peakRetained,
            long finalRetained, long peakReturnDepth, long finalReturnDepth, long peakQueueDepth,
            long finalQueueDepth, long notifications, long eligibleCompletions, long fairnessSpread,
            long starvationBound) {
    }
}
