package space.controlnet.ae2federation.observability.statistics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What moved most, per key, over each {@link StatisticsRange}, for the "Most moved" list. Each point of each range
 * keeps the amounts of its keys; once a point holds twice its cap of keys it keeps only the largest {@code cap}, so
 * a list read over many points is exact while fewer keys than the cap move, and close to it otherwise.
 */
public final class TopKeys<K> {
    private static final StatisticsRange[] RANGES = StatisticsRange.values();

    public record Ranked<K>(K key, double amount) {
    }

    private final int cap;
    private final List<List<Map<K, double[]>>> points = new ArrayList<>();
    private final long[][] buckets = new long[RANGES.length][];

    public TopKeys(int cap) {
        if (cap < 1) throw new IllegalArgumentException("cap must be positive");
        this.cap = cap;
        for (var range : RANGES) {
            var slots = new ArrayList<Map<K, double[]>>(range.buckets());
            for (int index = 0; index < range.buckets(); index++) slots.add(null);
            points.add(slots);
            buckets[range.ordinal()] = new long[range.buckets()];
            java.util.Arrays.fill(buckets[range.ordinal()], Long.MIN_VALUE);
        }
    }

    public void add(long second, K key, double amount) {
        if (!(amount > 0)) return;
        for (var range : RANGES) {
            int tier = range.ordinal();
            long bucket = Math.floorDiv(second, range.seconds());
            int slot = (int) Math.floorMod(bucket, (long) range.buckets());
            var slots = points.get(tier);
            var point = slots.get(slot);
            if (point == null || buckets[tier][slot] != bucket) {
                point = new HashMap<>();
                slots.set(slot, point);
                buckets[tier][slot] = bucket;
            }
            point.computeIfAbsent(key, ignored -> new double[1])[0] += amount;
            if (point.size() >= cap * 2) trim(point);
        }
    }

    private void trim(Map<K, double[]> point) {
        var entries = new ArrayList<>(point.entrySet());
        entries.sort((left, right) -> Double.compare(right.getValue()[0], left.getValue()[0]));
        for (int index = cap; index < entries.size(); index++) point.remove(entries.get(index).getKey());
    }

    /** The {@code limit} keys that moved most over the range ending at {@code nowSecond}, largest first. */
    public List<Ranked<K>> read(StatisticsRange range, long nowSecond, int limit) {
        int tier = range.ordinal();
        int size = range.buckets();
        long last = Math.floorDiv(nowSecond, range.seconds());
        var totals = new HashMap<K, double[]>();
        for (int index = 0; index < size; index++) {
            long bucket = last - index;
            int slot = (int) Math.floorMod(bucket, (long) size);
            var point = points.get(tier).get(slot);
            if (point == null || buckets[tier][slot] != bucket) continue;
            point.forEach((key, amount) -> totals.computeIfAbsent(key, ignored -> new double[1])[0] += amount[0]);
        }
        return totals.entrySet().stream()
                .sorted((left, right) -> Double.compare(right.getValue()[0], left.getValue()[0]))
                .limit(limit)
                .map(entry -> new Ranked<>(entry.getKey(), entry.getValue()[0]))
                .toList();
    }

    /** Whether the longest range holds nothing at {@code nowSecond}. */
    public boolean idle(long nowSecond) {
        return read(StatisticsRange.LONGEST, nowSecond, 1).isEmpty();
    }

    /** One saved point: its range, bucket number and the amounts of the keys it kept. */
    public record SavedPoint<K>(StatisticsRange range, long bucket, Map<K, Double> amounts) {
    }

    /** The points still inside their range as of {@code nowSecond}. */
    public List<SavedPoint<K>> save(long nowSecond) {
        var out = new ArrayList<SavedPoint<K>>();
        for (var range : RANGES) {
            int tier = range.ordinal();
            long last = Math.floorDiv(nowSecond, range.seconds());
            for (int slot = 0; slot < range.buckets(); slot++) {
                var point = points.get(tier).get(slot);
                long bucket = buckets[tier][slot];
                if (point == null || point.isEmpty() || bucket > last || last - bucket >= range.buckets()) continue;
                var amounts = new HashMap<K, Double>();
                point.forEach((key, amount) -> amounts.put(key, amount[0]));
                out.add(new SavedPoint<>(range, bucket, amounts));
            }
        }
        return out;
    }

    /** A ranking holding the saved points. */
    public static <K> TopKeys<K> restore(int cap, List<SavedPoint<K>> saved) {
        var top = new TopKeys<K>(cap);
        for (var point : saved) {
            int tier = point.range().ordinal();
            int slot = (int) Math.floorMod(point.bucket(), (long) point.range().buckets());
            var amounts = new HashMap<K, double[]>();
            point.amounts().forEach((key, amount) -> amounts.put(key, new double[] {amount}));
            top.points.get(tier).set(slot, amounts);
            top.buckets[tier][slot] = point.bucket();
        }
        return top;
    }
}
