package space.controlnet.ae2federation.test.processing;

public record PatternParticipationReceipt(int scheduleIndex, int patternSlot, long patternRevision, int laneIndex,
        long plannerCalls, long submittedJobs, long cpuBusyTicks, long acceptedInput,
        long machineCompletions, String returnOwner, long primaryOutput, long byproductOutput, boolean warmup) {
    public PatternParticipationReceipt {
        if (scheduleIndex < 0 || patternSlot < 0 || patternRevision < 0 || laneIndex < 0
                || plannerCalls != 1 || submittedJobs != 1 || cpuBusyTicks < 1 || acceptedInput < 1
                || machineCompletions != 1 || returnOwner.isBlank() || primaryOutput < 1 || byproductOutput < 1) {
            throw new IllegalArgumentException("Pattern participation receipt is incomplete");
        }
    }

    String serialized() {
        return scheduleIndex + "," + patternSlot + "," + patternRevision + "," + laneIndex + ","
                + plannerCalls + "," + submittedJobs + "," + cpuBusyTicks + "," + acceptedInput + ","
                + machineCompletions + "," + returnOwner + "," + primaryOutput + "," + byproductOutput + ","
                + warmup;
    }
}
