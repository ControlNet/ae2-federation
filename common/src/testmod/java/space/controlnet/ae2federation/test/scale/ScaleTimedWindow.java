package space.controlnet.ae2federation.test.scale;

public final class ScaleTimedWindow {
    private final long warmupNanos;
    private final long sampleNanos;
    private final int patternCount;
    private final int unitsPerJob;
    private long phaseStartNanos;
    private long phaseStartTick;
    private int phaseJobs;
    private Window warmup;
    private Window sample;

    public ScaleTimedWindow(int warmupSeconds, int sampleSeconds, int patternCount, int unitsPerJob,
            long startedNanos, long startedTick) {
        if (warmupSeconds <= 0 || sampleSeconds <= 0 || patternCount <= 0 || unitsPerJob <= 0) {
            throw new IllegalArgumentException("Timed native work and both windows must be positive");
        }
        warmupNanos = warmupSeconds * 1_000_000_000L;
        sampleNanos = sampleSeconds * 1_000_000_000L;
        this.patternCount = patternCount;
        this.unitsPerJob = unitsPerJob;
        phaseStartNanos = startedNanos;
        phaseStartTick = startedTick;
    }

    public boolean completedJob(long nowNanos, long nowTick) {
        phaseJobs++;
        long elapsed = nowNanos - phaseStartNanos;
        if (warmup == null && elapsed >= warmupNanos) {
            warmup = complete(elapsed, nowTick);
            phaseStartNanos = nowNanos;
            phaseStartTick = nowTick;
            phaseJobs = 0;
        } else if (warmup != null && elapsed >= sampleNanos) {
            sample = complete(elapsed, nowTick);
            return true;
        }
        return false;
    }

    private Window complete(long elapsed, long tick) {
        if (phaseJobs < patternCount || elapsed <= 0 || tick <= phaseStartTick) {
            throw new IllegalStateException("Timed window lacks complete native 256-Pattern work");
        }
        return new Window(phaseStartNanos, phaseStartNanos + elapsed, phaseStartTick, tick,
                phaseJobs, phaseJobs * (long) unitsPerJob);
    }

    public Window warmup() {
        return warmup;
    }

    public Window sample() {
        return sample;
    }

    public record Window(long startNanos, long endNanos, long startTick, long endTick, int jobs, long units) {
        public long wallNanos() {
            return endNanos - startNanos;
        }

        public long ticks() {
            return endTick - startTick;
        }
    }
}
