package space.controlnet.ae2federation.observability.statistics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;

/**
 * Heap a ranking or a timeline keeps, at the 1000-subnet bus farm's scale (about 2000 routes, each with its ranking,
 * kept for a day). Measured as the used heap after {@link System#gc()} with many copies alive, so the bounds are
 * approximate and set well above what the structures need, and well below what a map per point needed (87 KB per
 * route at one key a point, 187 KB at five, 919 KB at 31; 9.2 KB per timeline, written or not).
 */
class StatisticsFootprintTest {
    private static final long DAY = 86_400;
    private static final long NOW = 10 * DAY;
    private static final String[] KEYS = new String[64];

    static {
        for (int index = 0; index < KEYS.length; index++) KEYS[index] = "key" + index;
    }

    private static long used() {
        var runtime = Runtime.getRuntime();
        long least = Long.MAX_VALUE;
        for (int round = 0; round < 5; round++) {
            System.gc();
            try {
                Thread.sleep(20);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            least = Math.min(least, runtime.totalMemory() - runtime.freeMemory());
        }
        return least;
    }

    /** About how many bytes each of {@code count} objects made by {@code make} keeps. */
    private static double bytesEach(int count, IntFunction<Object> make) {
        var kept = new ArrayList<Object>(count);
        long before = used();
        for (int index = 0; index < count; index++) kept.add(make.apply(index));
        long after = used();
        assertTrue(kept.size() == count);
        return (after - before) / (double) count;
    }

    /** One second in each point of every range of the day ending at {@link #NOW}, oldest first. */
    private static List<Long> everyPoint(long now) {
        var seconds = new TreeSet<Long>();
        for (var range : StatisticsRange.values()) {
            long last = Math.floorDiv(now, range.seconds());
            for (int index = 0; index < range.buckets(); index++) seconds.add((last - index) * range.seconds());
        }
        return new ArrayList<>(seconds);
    }

    private static TopKeys<String> ranking(int keysPerPoint) {
        var top = new TopKeys<String>(StatisticsBook.RANKING_CAP);
        for (long second : everyPoint(NOW)) {
            for (int key = 0; key < keysPerPoint; key++) top.add(second, KEYS[key], 1 + key);
        }
        return top;
    }

    @Test
    void aRouteRankingMovingOneKeyKeepsUnder24Kb() {
        double each = bytesEach(400, index -> ranking(1));
        assertTrue(each < 24_000, "one key a point: " + each + " bytes a route");
    }

    @Test
    void aRouteRankingMovingFiveKeysKeepsUnder64Kb() {
        double each = bytesEach(300, index -> ranking(5));
        assertTrue(each < 64_000, "five keys a point: " + each + " bytes a route");
    }

    @Test
    void aRouteRankingAtItsCapKeepsUnder320Kb() {
        // 64 keys a point: each point is trimmed and holds up to 31.
        double each = bytesEach(60, index -> ranking(64));
        assertTrue(each < 320_000, "keys over the cap: " + each + " bytes a route");
    }

    @Test
    void aRankingShrinksBackOnceABusyDayHasPassed() {
        double each = bytesEach(200, index -> {
            var top = ranking(64);
            long now = NOW + DAY + 600;
            for (long second : everyPoint(now)) {
                if (second <= NOW) continue;
                for (int key = 0; key < 5; key++) top.add(second, KEYS[key], 1 + key);
            }
            top.prune(now);
            return top;
        });
        assertTrue(each < 64_000, "five keys a point a day after 64: " + each + " bytes a route");
    }

    @Test
    void aTimelineAllocatesNothingUntilWritten() {
        double each = bytesEach(5000, index -> new Timeline());
        assertTrue(each < 1000, "an empty timeline: " + each + " bytes");
    }

    @Test
    void aTimelineNotWrittenForAnHourKeepsOnlyItsDayPoints() {
        double each = bytesEach(3000, index -> {
            var timeline = new Timeline();
            for (long second = NOW - 120; second <= NOW; second++) timeline.add(second, second);
            timeline.prune(NOW + 3600 + 60);
            return timeline;
        });
        assertTrue(each < 4_500, "a timeline idle for an hour: " + each + " bytes");
    }
}
