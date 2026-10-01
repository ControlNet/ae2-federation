package space.controlnet.ae2federation.test.perf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TEST-ONLY wall-clock measurement for the performance benchmark GameTests. Each metric runs warm-up rounds, then
 * measured rounds, each bounded by an operation count and a time budget, and reports the median nanoseconds per
 * operation. Results are logged as {@code AE2F_PERF test=<id> metric=<name> value=<v> unit=<u>} lines that
 * {@code tools/perf_benchmark.py} parses.
 */
public final class PerfMeasure {
    private static final Logger LOGGER = LoggerFactory.getLogger("ae2federation-perf");
    private static final int WARMUP_ROUNDS = 2;
    private static final int ROUNDS = 5;
    /** Operations run between two clock reads. */
    private static final int CLOCK_BATCH = 32;
    /** Multiplies every metric's operation count and time budget, so a profiler run collects enough samples. */
    private static final int OPS_SCALE = Integer.getInteger("ae2federation.perf.opsScale", 1);
    /**
     * Comma-separated per-operation metrics to measure, or empty for all: the 1000-type listings take minutes at a
     * high opsScale, which an A/B of one operation does not need.
     */
    private static final java.util.Set<String> ONLY = java.util.Arrays.stream(
                    System.getProperty("ae2federation.perf.only", "").split(","))
            .map(String::trim).filter(name -> !name.isEmpty()).collect(java.util.stream.Collectors.toUnmodifiableSet());

    private final String testId;
    private final Map<String, String> results = new LinkedHashMap<>();

    public PerfMeasure(String testId) {
        this.testId = testId;
    }

    /**
     * Median ns/op of {@code operation}, at most {@code maxOps} calls and about {@code budgetMillis} per round; NaN,
     * without running or recording it, when {@code ae2federation.perf.only} names other metrics only.
     */
    public double nanosPerOp(String metric, int maxOps, long budgetMillis, Runnable operation) {
        if (!ONLY.isEmpty() && !ONLY.contains(metric)) {
            return Double.NaN;
        }
        var medians = new ArrayList<Double>();
        for (var round = 0; round < WARMUP_ROUNDS + ROUNDS; round++) {
            var budget = budgetMillis * 1_000_000L * OPS_SCALE;
            var limit = (long) maxOps * OPS_SCALE;
            var start = System.nanoTime();
            var ops = 0;
            var now = start;
            while (ops < limit && now - start < budget) {
                // The clock is read once per batch: on a kvm-clock host one System.nanoTime() costs ~40 ns, which
                // read per call would be most of a fast operation's measured time.
                var batch = (int) Math.min(CLOCK_BATCH, limit - ops);
                for (var call = 0; call < batch; call++) {
                    operation.run();
                }
                ops += batch;
                now = System.nanoTime();
            }
            if (round >= WARMUP_ROUNDS) {
                medians.add((double) (now - start) / ops);
            }
        }
        medians.sort(Double::compare);
        var median = medians.get(medians.size() / 2);
        record(metric, median, "ns/op");
        return median;
    }

    public void record(String metric, double value, String unit) {
        var text = String.format(java.util.Locale.ROOT, "%.1f", value);
        results.put(metric, text);
        LOGGER.info("AE2F_PERF test={} metric={} value={} unit={}", testId, metric, text, unit);
    }

    public void count(String metric, long value) {
        results.put(metric, Long.toString(value));
        LOGGER.info("AE2F_PERF test={} metric={} value={} unit=count", testId, metric, value);
    }

    public Map<String, String> results() {
        return Map.copyOf(results);
    }

    /** A window over the server's own per-tick wall time, which includes every block entity, Grid and GameTest tick. */
    public static final class TickWindow {
        private final MinecraftServer server;
        private final int startTick;

        public TickWindow(MinecraftServer server) {
            this.server = server;
            startTick = server.getTickCount();
        }

        public int ticks() {
            return server.getTickCount() - startTick;
        }

        /**
         * Median nanoseconds of the ticks completed after the one that opened the window, at most the last 100: the
         * median keeps a garbage collection or JIT pause from moving the result. Called during tick T, the completed
         * ticks are T-1, T-2, ...; the opening tick is excluded.
         */
        public double medianTickNanos() {
            var times = server.getTickTimesNanos();
            var end = server.getTickCount();
            var count = Math.min(ticks() - 1, times.length);
            if (count <= 0) {
                throw new IllegalStateException("Tick window has no completed ticks");
            }
            var window = new long[count];
            for (var index = 1; index <= count; index++) {
                window[index - 1] = times[Math.floorMod(end - index, times.length)];
            }
            java.util.Arrays.sort(window);
            return count % 2 == 1 ? window[count / 2] : (window[count / 2 - 1] + window[count / 2]) / 2.0;
        }

        /** Sum of the completed ticks after the opening one, at most the last 100. */
        public double totalTickNanos() {
            var times = server.getTickTimesNanos();
            var end = server.getTickCount();
            var count = Math.min(ticks() - 1, times.length);
            var total = 0L;
            for (var index = 1; index <= count; index++) {
                total += times[Math.floorMod(end - index, times.length)];
            }
            return total;
        }
    }
}
