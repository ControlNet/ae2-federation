package space.controlnet.ae2federation.observability.meter;

import java.util.ArrayDeque;

/**
 * Accepted deliveries of one directional rule over a sliding window of game ticks. Only real, accepted transfers
 * are recorded, so an empty window means nothing moved, never "unknown".
 *
 * <p>Deliveries are kept as one bucket per tick, so a burst of storage operations in one tick costs no allocation
 * after the first and nothing is dropped: the window holds at most {@code windowTicks} buckets.
 */
public final class PairFlowWindow {
    private final long windowTicks;
    private final ArrayDeque<Bucket> buckets = new ArrayDeque<>();

    public PairFlowWindow(long windowTicks) {
        if (windowTicks < 1) throw new IllegalArgumentException("Window must be positive");
        this.windowTicks = windowTicks;
    }

    public record Summary(long events, long amount, long ticksSinceLast) {
        public static final Summary NONE = new Summary(0, 0, -1);

        public boolean active() {
            return events > 0;
        }
    }

    private static final class Bucket {
        private final long tick;
        private long events;
        private long amount;

        private Bucket(long tick) {
            this.tick = tick;
        }
    }

    public void record(long tick, long amount) {
        if (amount <= 0) return;
        var last = buckets.peekLast();
        if (last == null || last.tick != tick) {
            prune(tick);
            last = new Bucket(tick);
            buckets.addLast(last);
        }
        last.events = saturatedAdd(last.events, 1);
        last.amount = saturatedAdd(last.amount, amount);
    }

    /** Deliveries within the window ending at {@code now}; older ones are dropped. */
    public Summary summarize(long now) {
        prune(now);
        if (buckets.isEmpty()) return Summary.NONE;
        long events = 0;
        long amount = 0;
        for (var bucket : buckets) {
            events = saturatedAdd(events, bucket.events);
            amount = saturatedAdd(amount, bucket.amount);
        }
        return new Summary(events, amount, Math.max(0, now - buckets.peekLast().tick));
    }

    public boolean isEmpty(long now) {
        return !summarize(now).active();
    }

    /** Ticks with deliveries currently held; bounded by the window length. */
    int buckets() {
        return buckets.size();
    }

    private void prune(long now) {
        while (!buckets.isEmpty() && buckets.peekFirst().tick <= now - windowTicks) buckets.removeFirst();
    }

    private static long saturatedAdd(long left, long right) {
        long sum = left + right;
        return ((left ^ sum) & (right ^ sum)) < 0 ? Long.MAX_VALUE : sum;
    }
}
