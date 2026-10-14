package space.controlnet.ae2federation.observability.statistics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every statistic the server keeps, by name, over the last day: gauges (a value sampled each second, such as stored
 * energy), counters (what moved in each second) and "most moved" rankings with keys {@code K}.
 *
 * <p>The book also records which seconds the server observed at all. A counter's point is what it gathered divided by
 * the observed seconds of the point, so a quiet second counts as zero and a second the server was stopped or paused
 * does not count; a point with no observed second is missing ({@code NaN}) for every series, which a chart draws as a
 * gap rather than as zero.
 */
public final class StatisticsBook<K> {
    /** The keys a point of a ranking keeps at least: enough for the page's list, few enough to save. */
    public static final int RANKING_CAP = 16;

    private final Timeline observed;
    private final Map<String, Timeline> gauges;
    private final Map<String, Timeline> counters;
    private final Map<String, TopKeys<K>> rankings;

    public StatisticsBook() {
        this(new Timeline(), new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    private StatisticsBook(Timeline observed, Map<String, Timeline> gauges, Map<String, Timeline> counters,
            Map<String, TopKeys<K>> rankings) {
        this.observed = observed;
        this.gauges = gauges;
        this.counters = counters;
        this.rankings = rankings;
    }

    /** Marks {@code second} as observed; call it once per second the server runs, before or after its records. */
    public void observe(long second) {
        observed.add(second, 1);
    }

    /** Records a gauge's value in {@code second}; once per gauge per second. */
    public void gauge(String name, long second, double value) {
        gauges.computeIfAbsent(name, ignored -> new Timeline()).add(second, value);
    }

    /** Records what a counter gathered in {@code second}; once per counter per second, and only when it is not 0. */
    public void count(String name, long second, double amount) {
        if (amount == 0) return;
        counters.computeIfAbsent(name, ignored -> new Timeline()).add(second, amount);
    }

    /** Adds {@code amount} of {@code key} to the ranking {@code name} in {@code second}. */
    public void moved(String name, long second, K key, double amount) {
        rankings.computeIfAbsent(name, ignored -> new TopKeys<>(RANKING_CAP)).add(second, key, amount);
    }

    /** Whether the server observed each point of the range, oldest first. */
    public boolean[] observed(StatisticsRange range, long nowSecond) {
        var counts = observed.readCounts(range, nowSecond);
        var out = new boolean[counts.length];
        for (int index = 0; index < counts.length; index++) out[index] = counts[index] > 0;
        return out;
    }

    /** How many seconds the server observed in each point of the range, oldest first. */
    public int[] observedSeconds(StatisticsRange range, long nowSecond) {
        return observed.readCounts(range, nowSecond);
    }

    /** What a counter gathered in each point of the range, oldest first; 0 where it gathered nothing. */
    public double[] sums(String name, StatisticsRange range, long nowSecond) {
        var timeline = counters.get(name);
        return timeline == null ? new double[range.buckets()] : timeline.readSums(range, nowSecond);
    }

    /** A gauge's points, oldest first; {@code NaN} where it was not sampled. */
    public double[] gauge(String name, StatisticsRange range, long nowSecond) {
        var timeline = gauges.get(name);
        if (timeline != null) return timeline.read(range, nowSecond);
        var out = new double[range.buckets()];
        java.util.Arrays.fill(out, Double.NaN);
        return out;
    }

    /** A counter's rate per observed second at each point, oldest first; {@code NaN} where nothing was observed. */
    public double[] rate(String name, StatisticsRange range, long nowSecond) {
        var seconds = observed.readCounts(range, nowSecond);
        var timeline = counters.get(name);
        var sums = timeline == null ? new double[range.buckets()] : timeline.readSums(range, nowSecond);
        var out = new double[range.buckets()];
        for (int index = 0; index < out.length; index++) {
            out[index] = seconds[index] > 0 ? sums[index] / seconds[index] : Double.NaN;
        }
        return out;
    }

    /** Everything a counter gathered over the range. */
    public double total(String name, StatisticsRange range, long nowSecond) {
        var timeline = counters.get(name);
        if (timeline == null) return 0;
        double total = 0;
        for (double sum : timeline.readSums(range, nowSecond)) total += sum;
        return total;
    }

    /** A gauge's newest point in the range, or {@code NaN} when the range holds none. */
    public double latest(String name, StatisticsRange range, long nowSecond) {
        var points = gauge(name, range, nowSecond);
        for (int index = points.length - 1; index >= 0; index--) {
            if (!Double.isNaN(points[index])) return points[index];
        }
        return Double.NaN;
    }

    public List<TopKeys.Ranked<K>> ranking(String name, StatisticsRange range, long nowSecond, int limit) {
        var top = rankings.get(name);
        return top == null ? List.of() : top.read(range, nowSecond, limit);
    }

    public Set<String> gaugeNames() {
        return gauges.keySet();
    }

    public Set<String> counterNames() {
        return counters.keySet();
    }

    public Set<String> rankingNames() {
        return rankings.keySet();
    }

    /** Forgets every series and ranking with nothing left in the last day. */
    public void prune(long nowSecond) {
        gauges.values().removeIf(timeline -> timeline.idle(nowSecond));
        counters.values().removeIf(timeline -> timeline.idle(nowSecond));
        rankings.values().removeIf(top -> top.idle(nowSecond));
    }

    /** The book as saved: each timeline's tiers and each ranking's points still inside their range. */
    public record Saved<K>(Timeline.SavedTier[] observed, Map<String, Timeline.SavedTier[]> gauges,
            Map<String, Timeline.SavedTier[]> counters, Map<String, List<TopKeys.SavedPoint<K>>> rankings) {
    }

    public Saved<K> save(long nowSecond) {
        var savedGauges = new HashMap<String, Timeline.SavedTier[]>();
        gauges.forEach((name, timeline) -> savedGauges.put(name, timeline.save()));
        var savedCounters = new HashMap<String, Timeline.SavedTier[]>();
        counters.forEach((name, timeline) -> savedCounters.put(name, timeline.save()));
        var savedRankings = new HashMap<String, List<TopKeys.SavedPoint<K>>>();
        rankings.forEach((name, top) -> {
            var points = top.save(nowSecond);
            if (!points.isEmpty()) savedRankings.put(name, points);
        });
        return new Saved<>(observed.save(), savedGauges, savedCounters, savedRankings);
    }

    public static <K> StatisticsBook<K> restore(Saved<K> saved) {
        var gauges = new HashMap<String, Timeline>();
        saved.gauges().forEach((name, tiers) -> gauges.put(name, Timeline.restore(tiers)));
        var counters = new HashMap<String, Timeline>();
        saved.counters().forEach((name, tiers) -> counters.put(name, Timeline.restore(tiers)));
        var rankings = new HashMap<String, TopKeys<K>>();
        saved.rankings().forEach((name, points) -> rankings.put(name, TopKeys.restore(RANKING_CAP, points)));
        return new StatisticsBook<>(Timeline.restore(saved.observed()), gauges, counters, rankings);
    }
}
