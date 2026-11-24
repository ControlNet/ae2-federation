package space.controlnet.ae2federation.observability.statistics;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What moved most, per key, over each {@link StatisticsRange}, for the "Most moved" list. Each point of each range
 * keeps the amounts of its keys; once a point holds twice its cap of keys it keeps only the largest {@code cap}, so
 * a list read over many points is exact while fewer keys than the cap move, and close to it otherwise.
 *
 * <p>There is one ranking per route, and a thousand subnets make about 2000 routes, each with 444 points over the
 * day. A map per point cost about 200 bytes even for one key (175 MB for 2000 routes moving one key each); a range's
 * points are kept in flat arrays instead, 12 bytes per kept key, and a range nothing was added to for longer than it
 * shows is released by {@link #prune}.
 */
public final class TopKeys<K> {
    private static final StatisticsRange[] RANGES = StatisticsRange.values();

    public record Ranked<K>(K key, double amount) {
    }

    /**
     * One range's points: point {@code slot} holds {@code sizes[slot]} keys at {@code keys[slot * width]} onwards, with
     * their amounts at the same places of {@code amounts}. The width is the most keys a point holds, at most twice the
     * cap; {@link #prune} narrows it again once the wider points are no longer shown.
     */
    private static final class Tier {
        private final long[] buckets;
        private final int[] sizes;
        private Object[] keys;
        private double[] amounts;
        private int width;
        /** The newest bucket any point holds, so a range with nothing left to show is found without a scan. */
        private long newest = Long.MIN_VALUE;

        private Tier(int points, int width) {
            buckets = new long[points];
            Arrays.fill(buckets, Long.MIN_VALUE);
            sizes = new int[points];
            keys = new Object[points * width];
            amounts = new double[points * width];
            this.width = width;
        }

        /** Lays the points out {@code width} keys apart; every point must fit. */
        private void resize(int width) {
            var keys = new Object[sizes.length * width];
            var amounts = new double[sizes.length * width];
            for (int slot = 0; slot < sizes.length; slot++) {
                System.arraycopy(this.keys, slot * this.width, keys, slot * width, sizes[slot]);
                System.arraycopy(this.amounts, slot * this.width, amounts, slot * width, sizes[slot]);
            }
            this.keys = keys;
            this.amounts = amounts;
            this.width = width;
        }

        private void clear(int slot) {
            Arrays.fill(keys, slot * width, slot * width + sizes[slot], null);
            sizes[slot] = 0;
        }

        /** The slot of {@code bucket}, emptied first when it still holds an older point. */
        private int open(long bucket) {
            int slot = (int) Math.floorMod(bucket, (long) sizes.length);
            if (buckets[slot] != bucket) {
                clear(slot);
                buckets[slot] = bucket;
                newest = Math.max(newest, bucket);
            }
            return slot;
        }
    }

    private final int cap;
    /** By {@link StatisticsRange#ordinal()}; {@code null} for a range that holds nothing. */
    private final Tier[] tiers = new Tier[RANGES.length];

    public TopKeys(int cap) {
        if (cap < 1) throw new IllegalArgumentException("cap must be positive");
        this.cap = cap;
    }

    public void add(long second, K key, double amount) {
        if (!(amount > 0)) return;
        for (var range : RANGES) {
            var tier = tier(range);
            add(tier, tier.open(Math.floorDiv(second, range.seconds())), key, amount);
        }
    }

    /**
     * Adds every key's amount in {@code amounts}, as {@link #add} does for each key in the map's order; each point
     * is looked up once for the whole map.
     */
    public void addAll(long second, Object2LongMap<K> amounts) {
        for (var range : RANGES) {
            Tier tier = null;
            int slot = 0;
            for (var entry : Object2LongMaps.fastIterable(amounts)) {
                double amount = entry.getLongValue();
                if (!(amount > 0)) continue;
                if (tier == null) {
                    tier = tier(range);
                    slot = tier.open(Math.floorDiv(second, range.seconds()));
                }
                add(tier, slot, entry.getKey(), amount);
            }
        }
    }

    private Tier tier(StatisticsRange range) {
        var tier = tiers[range.ordinal()];
        if (tier == null) tier = tiers[range.ordinal()] = new Tier(range.buckets(), 1);
        return tier;
    }

    /** Adds {@code amount} to {@code key}'s amount in the point; a point a few dozen keys long is searched in order. */
    private void add(Tier tier, int slot, K key, double amount) {
        int base = slot * tier.width;
        int size = tier.sizes[slot];
        int at = base;
        while (at < base + size && !key.equals(tier.keys[at])) at++;
        if (at < base + size) {
            tier.amounts[at] += amount;
        } else {
            if (size == tier.width) {
                tier.resize(tier.width + 1);
                base = slot * tier.width;
                at = base + size;
            }
            tier.keys[at] = key;
            tier.amounts[at] = amount;
            tier.sizes[slot] = ++size;
        }
        if (size >= cap * 2) trim(tier, slot);
    }

    /** Keeps the point's {@code cap} largest keys; of equal amounts, the one added first. */
    private void trim(Tier tier, int slot) {
        int base = slot * tier.width;
        int size = tier.sizes[slot];
        var keys = tier.keys;
        var amounts = tier.amounts;
        for (int next = base + 1; next < base + size; next++) {
            var key = keys[next];
            double amount = amounts[next];
            int at = next - 1;
            for (; at >= base && Double.compare(amounts[at], amount) < 0; at--) {
                keys[at + 1] = keys[at];
                amounts[at + 1] = amounts[at];
            }
            keys[at + 1] = key;
            amounts[at + 1] = amount;
        }
        if (size > cap) {
            Arrays.fill(keys, base + cap, base + size, null);
            tier.sizes[slot] = cap;
        }
    }

    @SuppressWarnings("unchecked")
    private static <K> K key(Tier tier, int at) {
        return (K) tier.keys[at];
    }

    /** The {@code limit} keys that moved most over the range ending at {@code nowSecond}, largest first. */
    public List<Ranked<K>> read(StatisticsRange range, long nowSecond, int limit) {
        var tier = tiers[range.ordinal()];
        if (tier == null) return List.of();
        int size = range.buckets();
        long last = Math.floorDiv(nowSecond, range.seconds());
        var totals = new HashMap<K, double[]>();
        for (int index = 0; index < size; index++) {
            long bucket = last - index;
            int slot = (int) Math.floorMod(bucket, (long) size);
            if (tier.buckets[slot] != bucket) continue;
            int base = slot * tier.width;
            for (int at = base; at < base + tier.sizes[slot]; at++) {
                totals.computeIfAbsent(key(tier, at), ignored -> new double[1])[0] += tier.amounts[at];
            }
        }
        return totals.entrySet().stream()
                .sorted((left, right) -> Double.compare(right.getValue()[0], left.getValue()[0]))
                .limit(limit)
                .map(entry -> new Ranked<>(entry.getKey(), entry.getValue()[0]))
                .toList();
    }

    /** What each of {@code keys} moved in each point of the range, oldest first; 0 where it moved nothing. */
    public Map<K, double[]> series(StatisticsRange range, long nowSecond, java.util.Collection<K> keys) {
        int size = range.buckets();
        long last = Math.floorDiv(nowSecond, range.seconds());
        var out = new HashMap<K, double[]>();
        for (var key : keys) out.put(key, new double[size]);
        var tier = tiers[range.ordinal()];
        if (tier == null) return out;
        for (int index = 0; index < size; index++) {
            long bucket = last - (size - 1 - index);
            int slot = (int) Math.floorMod(bucket, (long) size);
            if (tier.buckets[slot] != bucket) continue;
            int base = slot * tier.width;
            for (int at = base; at < base + tier.sizes[slot]; at++) {
                var points = out.get(tier.keys[at]);
                if (points != null) points[index] = tier.amounts[at];
            }
        }
        return out;
    }

    /** Whether the longest range holds nothing at {@code nowSecond}. */
    public boolean idle(long nowSecond) {
        // Off the slots' bucket numbers, as Timeline#idle: reading the range would sum and sort every key it holds.
        var range = StatisticsRange.LONGEST;
        var tier = tiers[range.ordinal()];
        if (tier == null) return true;
        long last = Math.floorDiv(nowSecond, range.seconds());
        for (int index = 0; index < range.buckets(); index++) {
            long bucket = last - index;
            int slot = (int) Math.floorMod(bucket, (long) range.buckets());
            if (tier.buckets[slot] == bucket && tier.sizes[slot] > 0) return false;
        }
        return true;
    }

    /**
     * Releases what no range shows from {@code nowSecond} on: the points older than their range, a range holding no
     * newer point, and the room of keys only those points needed. True once the ranking holds nothing to show.
     */
    public boolean prune(long nowSecond) {
        for (var range : RANGES) {
            var tier = tiers[range.ordinal()];
            if (tier == null) continue;
            long oldest = Math.floorDiv(nowSecond, range.seconds()) - range.buckets() + 1;
            if (tier.newest < oldest) {
                tiers[range.ordinal()] = null;
                continue;
            }
            int widest = 1;
            for (int slot = 0; slot < range.buckets(); slot++) {
                if (tier.buckets[slot] < oldest) tier.clear(slot);
                else widest = Math.max(widest, tier.sizes[slot]);
            }
            if (widest < tier.width) tier.resize(widest);
        }
        return idle(nowSecond);
    }

    /** One saved point: its range, bucket number and the amounts of the keys it kept. */
    public record SavedPoint<K>(StatisticsRange range, long bucket, Map<K, Double> amounts) {
    }

    /** The points still inside their range as of {@code nowSecond}. */
    public List<SavedPoint<K>> save(long nowSecond) {
        var out = new ArrayList<SavedPoint<K>>();
        for (var range : RANGES) {
            var tier = tiers[range.ordinal()];
            if (tier == null) continue;
            long last = Math.floorDiv(nowSecond, range.seconds());
            for (int slot = 0; slot < range.buckets(); slot++) {
                long bucket = tier.buckets[slot];
                int size = tier.sizes[slot];
                if (size == 0 || bucket > last || last - bucket >= range.buckets()) continue;
                var amounts = new HashMap<K, Double>();
                int base = slot * tier.width;
                for (int at = base; at < base + size; at++) amounts.put(key(tier, at), tier.amounts[at]);
                out.add(new SavedPoint<>(range, bucket, amounts));
            }
        }
        return out;
    }

    /** A ranking holding the saved points. */
    public static <K> TopKeys<K> restore(int cap, List<SavedPoint<K>> saved) {
        var top = new TopKeys<K>(cap);
        for (var point : saved) {
            var tier = top.tier(point.range());
            int slot = (int) Math.floorMod(point.bucket(), (long) point.range().buckets());
            tier.clear(slot);
            tier.buckets[slot] = point.bucket();
            tier.newest = Math.max(tier.newest, point.bucket());
            int size = point.amounts().size();
            if (size > tier.width) tier.resize(size);
            int at = slot * tier.width;
            for (var entry : point.amounts().entrySet()) {
                tier.keys[at] = entry.getKey();
                tier.amounts[at++] = entry.getValue();
            }
            tier.sizes[slot] = size;
        }
        return top;
    }
}
