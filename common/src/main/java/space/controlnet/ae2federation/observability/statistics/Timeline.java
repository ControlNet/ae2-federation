package space.controlnet.ae2federation.observability.statistics;

import java.util.Arrays;

/**
 * One measured value over the last day, kept at each {@link StatisticsRange}'s resolution at once. It lives in memory;
 * {@link #save()} and {@link #restore} carry it across a restart.
 *
 * <p>Each second a caller records the second's value once: a sample (stored energy) or what a counter gathered in that
 * second (items moved). A point is the mean of the seconds it holds, so for a counter it is a rate per second. A
 * second nobody recorded is not zero: a point with no recorded second reads as {@code NaN}, which a chart draws as a
 * gap. Points are addressed by absolute bucket number, so nothing has to advance the timeline while it is idle; a slot
 * whose stored bucket number is not the one asked for holds an older point and reads as missing.
 */
public final class Timeline {
    private static final StatisticsRange[] RANGES = StatisticsRange.values();

    private final double[][] sums = new double[RANGES.length][];
    private final int[][] counts = new int[RANGES.length][];
    private final long[][] buckets = new long[RANGES.length][];

    public Timeline() {
        for (var range : RANGES) {
            int tier = range.ordinal();
            sums[tier] = new double[range.buckets()];
            counts[tier] = new int[range.buckets()];
            buckets[tier] = new long[range.buckets()];
            Arrays.fill(buckets[tier], Long.MIN_VALUE);
        }
    }

    /** Records the value of {@code second}; call it at most once per second. */
    public void add(long second, double value) {
        for (var range : RANGES) {
            int tier = range.ordinal();
            long bucket = Math.floorDiv(second, range.seconds());
            int slot = (int) Math.floorMod(bucket, (long) range.buckets());
            if (buckets[tier][slot] != bucket) {
                buckets[tier][slot] = bucket;
                sums[tier][slot] = 0;
                counts[tier][slot] = 0;
            }
            sums[tier][slot] += value;
            counts[tier][slot]++;
        }
    }

    /** The range's points, oldest first, the last one holding {@code nowSecond}; missing points are {@code NaN}. */
    public double[] read(StatisticsRange range, long nowSecond) {
        var out = new double[range.buckets()];
        java.util.Arrays.fill(out, Double.NaN);
        int tier = range.ordinal();
        visit(range, nowSecond, (index, slot) -> out[index] = sums[tier][slot] / counts[tier][slot]);
        return out;
    }

    /** The range's per-point sums, oldest first; a point nobody recorded into is {@code 0}. */
    public double[] readSums(StatisticsRange range, long nowSecond) {
        var out = new double[range.buckets()];
        visit(range, nowSecond, (index, slot) -> out[index] = sums[range.ordinal()][slot]);
        return out;
    }

    /** How many seconds each point of the range holds, oldest first. */
    public int[] readCounts(StatisticsRange range, long nowSecond) {
        var out = new int[range.buckets()];
        visit(range, nowSecond, (index, slot) -> out[index] = counts[range.ordinal()][slot]);
        return out;
    }

    private interface SlotVisitor {
        void visit(int index, int slot);
    }

    /** Calls {@code visitor} for each point of the range that holds a recorded second. */
    private void visit(StatisticsRange range, long nowSecond, SlotVisitor visitor) {
        int tier = range.ordinal();
        int size = range.buckets();
        long last = Math.floorDiv(nowSecond, range.seconds());
        for (int index = 0; index < size; index++) {
            long bucket = last - (size - 1 - index);
            int slot = (int) Math.floorMod(bucket, (long) size);
            if (buckets[tier][slot] == bucket && counts[tier][slot] > 0) visitor.visit(index, slot);
        }
    }

    /** Whether the longest range holds nothing at {@code nowSecond}, so the timeline can be forgotten. */
    public boolean idle(long nowSecond) {
        for (double value : read(StatisticsRange.LONGEST, nowSecond)) {
            if (!Double.isNaN(value)) return false;
        }
        return true;
    }

    /**
     * One range as saved: its newest point's bucket number and each slot's sum and count, a count of 0 for an empty
     * slot. A filled slot's bucket number follows from the newest one, so it is not saved.
     */
    public record SavedTier(long newestBucket, double[] sums, int[] counts) {
    }

    /** Each range's saved form by {@link StatisticsRange#ordinal()}, {@code null} for a range that holds nothing. */
    public SavedTier[] save() {
        var out = new SavedTier[RANGES.length];
        for (var range : RANGES) {
            int tier = range.ordinal();
            long newest = Long.MIN_VALUE;
            for (int slot = 0; slot < range.buckets(); slot++) {
                if (counts[tier][slot] > 0) newest = Math.max(newest, buckets[tier][slot]);
            }
            if (newest == Long.MIN_VALUE) continue;
            var savedCounts = new int[range.buckets()];
            var savedSums = new double[range.buckets()];
            for (int slot = 0; slot < range.buckets(); slot++) {
                // A slot can still hold a point older than one ring behind the newest: it is not kept.
                if (counts[tier][slot] > 0 && newest - buckets[tier][slot] < range.buckets()) {
                    savedCounts[slot] = counts[tier][slot];
                    savedSums[slot] = sums[tier][slot];
                }
            }
            out[tier] = new SavedTier(newest, savedSums, savedCounts);
        }
        return out;
    }

    /** A timeline holding what {@link #save()} gave; a range saved with another number of points is left empty. */
    public static Timeline restore(SavedTier[] saved) {
        var timeline = new Timeline();
        for (var range : RANGES) {
            int tier = range.ordinal();
            var savedTier = tier < saved.length ? saved[tier] : null;
            int size = range.buckets();
            if (savedTier == null || savedTier.sums().length != size || savedTier.counts().length != size) continue;
            int newestSlot = (int) Math.floorMod(savedTier.newestBucket(), (long) size);
            for (int slot = 0; slot < size; slot++) {
                if (savedTier.counts()[slot] <= 0) continue;
                timeline.buckets[tier][slot] = savedTier.newestBucket() - Math.floorMod(newestSlot - slot, size);
                timeline.sums[tier][slot] = savedTier.sums()[slot];
                timeline.counts[tier][slot] = savedTier.counts()[slot];
            }
        }
        return timeline;
    }
}
