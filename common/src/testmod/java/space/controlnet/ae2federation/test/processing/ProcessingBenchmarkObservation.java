package space.controlnet.ae2federation.test.processing;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import java.util.IdentityHashMap;
import java.util.List;

public final class ProcessingBenchmarkObservation {
    public enum Scene { NATIVE, FEDERATION }

    private static boolean active;
    private static Scene scene;
    private static Counters nativeCounters = new Counters();
    private static Counters federationCounters = new Counters();
    private static ProcessingEndpointCohortTracker cohortTracker = new ProcessingEndpointCohortTracker();

    private ProcessingBenchmarkObservation() {}

    public static synchronized void reset() {
        active = true;
        scene = null;
        nativeCounters = new Counters();
        federationCounters = new Counters();
        cohortTracker = new ProcessingEndpointCohortTracker();
    }

    public static synchronized void select(Scene selected) {
        requireActive();
        scene = selected;
    }

    public static synchronized void reset(Scene selected) {
        requireActive();
        if (selected == Scene.NATIVE) {
            nativeCounters = new Counters();
        } else {
            federationCounters = new Counters();
        }
        scene = selected;
    }

    public static synchronized void registerLanes(Scene selected, List<?> lanes) {
        var counters = selected == Scene.NATIVE ? nativeCounters : federationCounters;
        counters.laneIndexes.clear();
        counters.laneDispatches = new long[lanes.size()];
        counters.resultLockedLanes = new boolean[lanes.size()];
        for (int index = 0; index < lanes.size(); index++) {
            counters.laneIndexes.put(lanes.get(index), index);
            if (lanes.get(index) instanceof appeng.helpers.patternprovider.PatternProviderLogic logic) counters.laneIndexes.put(logic.getReturnInv(), index);
        }
    }

    public static synchronized boolean recordPush(KeyCounter[] inputs) {
        if (!active) {
            return false;
        }
        var counters = current();
        counters.nativeInputCalls++;
        for (var holder : inputs) {
            for (var entry : holder) {
                counters.requestedInputUnits = Math.addExact(counters.requestedInputUnits, entry.getLongValue());
            }
        }
        return true;
    }

    public static synchronized boolean recordPushResult(Object owner, boolean accepted) {
        if (!active) {
            return false;
        }
        var counters = current();
        var lane = counters.laneIndexes.get(owner);
        cohortTracker.push(lane, accepted);
        if (accepted) {
            counters.acceptedPushes++;
            counters.machineDispatches++;
            if (lane != null) {
                counters.laneDispatches[lane]++;
                counters.lastAcceptedLane = lane;
            }
        } else {
            counters.rejectedPushes++;
        }
        return true;
    }

    public static synchronized boolean recordRemainder(AEKey key, long amount) {
        if (!active) {
            return false;
        }
        current().nativeRemainderUnits = Math.addExact(current().nativeRemainderUnits, amount);
        return true;
    }

    public static synchronized boolean recordReturn(Object owner, boolean changed) {
        if (!active) {
            return false;
        }
        var counters = current();
        cohortTracker.returned(counters.laneIndexes.get(owner), changed);
        counters.returnAttempts++;
        if (changed) {
            counters.returnAttemptsWithProgress++;
        }
        return true;
    }

    public static synchronized void recordPlannerCall() { current().plannerCalls++; }

    public static synchronized void recordSubmittedJob() { current().submittedJobs++; }

    public static synchronized void recordCpuBusyTick() {
        current().cpuBusyTicks++;
        cohortTracker.busy();
    }

    public static synchronized void recordMachineCompletion(int laneIndex) {
        current().machineCompletions++;
        cohortTracker.completed(laneIndex);
    }

    public static synchronized void recordResultLockedLane(int laneIndex) {
        current().resultLockedLanes[laneIndex] = true;
        cohortTracker.resultLocked(laneIndex);
    }

    public static synchronized void recordReload() { current().reloadCount++; }

    public static synchronized void observeRetainedResponsibility(long amount) {
        var counters = current();
        counters.finalRetainedResponsibility = amount;
        counters.peakRetainedResponsibility = Math.max(counters.peakRetainedResponsibility, amount);
    }

    public static synchronized void observeOwnerState(long returnInventoryUnits, long sendQueueUnits,
            int sendQueueEntries, int activeReturnOwners) {
        var counters = current();
        counters.finalReturnInventoryUnits = returnInventoryUnits;
        counters.finalSendQueueUnits = sendQueueUnits;
        counters.activeReturnOwners = activeReturnOwners;
        counters.peakReturnInventoryUnits = Math.max(counters.peakReturnInventoryUnits, returnInventoryUnits);
        counters.peakSendQueueUnits = Math.max(counters.peakSendQueueUnits, sendQueueUnits);
        counters.peakSendQueueEntries = Math.max(counters.peakSendQueueEntries, sendQueueEntries);
        counters.peakActiveReturnOwners = Math.max(counters.peakActiveReturnOwners, activeReturnOwners);
        observeRetainedResponsibility(Math.addExact(returnInventoryUnits, sendQueueUnits));
    }

    public static synchronized void observeLaneOwnerState(int laneIndex, long returnInventoryUnits,
            long sendQueueUnits) {
        cohortTracker.ownerState(laneIndex, returnInventoryUnits, sendQueueUnits);
    }

    public static synchronized void recordNotification(int laneIndex) { cohortTracker.notified(laneIndex); }

    public static synchronized void startCohort(int cohortId, EndpointCohortScenario scenario, List<Integer> lanes) {
        cohortTracker.start(cohortId, scenario, lanes, current().laneDispatches.length);
    }

    static synchronized ProcessingEndpointCohortTracker.Snapshot finishCohort() { return cohortTracker.finish(); }

    public static synchronized int lastAcceptedLane(Scene selected) {
        return (selected == Scene.NATIVE ? nativeCounters : federationCounters).lastAcceptedLane;
    }

    public static synchronized Snapshot snapshot(Scene selected) {
        var counters = selected == Scene.NATIVE ? nativeCounters : federationCounters;
        long dispatchedLanes = 0;
        long min = Long.MAX_VALUE;
        long max = 0;
        for (var count : counters.laneDispatches) {
            if (count > 0) {
                dispatchedLanes++;
                min = Math.min(min, count);
                max = Math.max(max, count);
            }
        }
        var fairnessSpread = dispatchedLanes == 0 ? 0 : max - min;
        var resultLockedLanes = 0L;
        for (var locked : counters.resultLockedLanes) {
            if (locked) {
                resultLockedLanes++;
            }
        }
        return new Snapshot(counters.nativeInputCalls, counters.requestedInputUnits, counters.acceptedPushes,
                counters.rejectedPushes, counters.nativeRemainderUnits, counters.returnAttempts,
                counters.returnAttemptsWithProgress, counters.peakRetainedResponsibility,
                counters.finalRetainedResponsibility, counters.plannerCalls, counters.submittedJobs,
                counters.cpuBusyTicks, counters.machineDispatches, counters.machineCompletions, counters.reloadCount,
                counters.peakReturnInventoryUnits, counters.finalReturnInventoryUnits,
                counters.peakSendQueueUnits, counters.finalSendQueueUnits, counters.peakSendQueueEntries,
                counters.peakActiveReturnOwners, counters.activeReturnOwners, dispatchedLanes, fairnessSpread,
                resultLockedLanes);
    }

    public static synchronized void finish() {
        active = false;
        scene = null;
    }

    private static Counters current() {
        if (scene == null) {
            throw new IllegalStateException("Processing benchmark scene is not selected");
        }
        return scene == Scene.NATIVE ? nativeCounters : federationCounters;
    }

    private static void requireActive() {
        if (!active) {
            throw new IllegalStateException("Processing benchmark observation is not active");
        }
    }

    private static final class Counters {
        private final IdentityHashMap<Object, Integer> laneIndexes = new IdentityHashMap<>();
        private long[] laneDispatches = new long[0];
        private boolean[] resultLockedLanes = new boolean[0];
        private long nativeInputCalls;
        private long requestedInputUnits;
        private long acceptedPushes;
        private long rejectedPushes;
        private long nativeRemainderUnits;
        private long returnAttempts;
        private long returnAttemptsWithProgress;
        private long peakRetainedResponsibility;
        private long finalRetainedResponsibility;
        private long plannerCalls;
        private long submittedJobs;
        private long cpuBusyTicks;
        private long machineDispatches;
        private long machineCompletions;
        private long reloadCount;
        private long peakReturnInventoryUnits;
        private long finalReturnInventoryUnits;
        private long peakSendQueueUnits;
        private long finalSendQueueUnits;
        private int peakSendQueueEntries;
        private int peakActiveReturnOwners;
        private int activeReturnOwners;
        private int lastAcceptedLane = -1;
    }

    public record Snapshot(long nativeInputCalls, long requestedInputUnits, long acceptedPushes, long rejectedPushes,
            long nativeRemainderUnits, long returnAttempts, long returnAttemptsWithProgress,
            long peakRetainedResponsibility, long finalRetainedResponsibility, long plannerCalls, long submittedJobs,
            long cpuBusyTicks, long machineDispatches, long machineCompletions, long reloadCount,
            long peakReturnInventoryUnits, long finalReturnInventoryUnits, long peakSendQueueUnits,
            long finalSendQueueUnits, int peakSendQueueEntries, int peakActiveReturnOwners, int activeReturnOwners,
            long dispatchedLanes, long fairnessSpread, long resultLockedLanes) {
    }
}
