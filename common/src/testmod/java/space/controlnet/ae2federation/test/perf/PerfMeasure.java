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

    private final String testId;
    private final Map<String, String> results = new LinkedHashMap<>();

    public PerfMeasure(String testId) {
        this.testId = testId;
    }

    /** Median ns/op of {@code operation}, at most {@code maxOps} calls and about {@code budgetMillis} per round. */
    public double nanosPerOp(String metric, int maxOps, long budgetMillis, Runnable operation) {
        var medians = new ArrayList<Double>();
        for (var round = 0; round < WARMUP_ROUNDS + ROUNDS; round++) {
            var budget = budgetMillis * 1_000_000L;
            var start = System.nanoTime();
            var ops = 0;
            var now = start;
            while (ops < maxOps && now - start < budget) {
                operation.run();
                ops++;
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
         * Mean nanoseconds of the ticks completed after the one that opened the window, at most the last 100. Called
         * during tick T, the completed ticks are T-1, T-2, ...; the opening tick is excluded.
         */
        public double meanTickNanos() {
            var times = server.getTickTimesNanos();
            var end = server.getTickCount();
            var count = Math.min(ticks() - 1, times.length);
            if (count <= 0) {
                throw new IllegalStateException("Tick window has no completed ticks");
            }
            var total = 0L;
            for (var index = 1; index <= count; index++) {
                total += times[Math.floorMod(end - index, times.length)];
            }
            return (double) total / count;
        }
    }
}
