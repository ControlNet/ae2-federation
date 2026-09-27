package space.controlnet.ae2federation.observability.meter;

import java.util.ArrayDeque;

/**
 * Accepted deliveries of one directional rule over a sliding window of game ticks. Only real, accepted transfers
 * are recorded, so an empty window means nothing moved, never "unknown".
 */
public final class PairFlowWindow {
    private final long windowTicks;
    private final int maxEvents;
    private final ArrayDeque<long[]> events = new ArrayDeque<>();

    public PairFlowWindow(long windowTicks, int maxEvents) {
        if (windowTicks < 1 || maxEvents < 1) throw new IllegalArgumentException("Window must be positive");
        this.windowTicks = windowTicks;
        this.maxEvents = maxEvents;
    }

    public record Summary(int events, long amount, long ticksSinceLast) {
        public static final Summary NONE = new Summary(0, 0, -1);

        public boolean active() {
            return events > 0;
        }
    }

    public void record(long tick, long amount) {
        if (amount <= 0) return;
        events.addLast(new long[] {tick, amount});
        while (events.size() > maxEvents) events.removeFirst();
    }

    /** Deliveries within the window ending at {@code now}; older ones are dropped. */
    public Summary summarize(long now) {
        while (!events.isEmpty() && events.peekFirst()[0] <= now - windowTicks) events.removeFirst();
        if (events.isEmpty()) return Summary.NONE;
        long amount = 0;
        for (var event : events) amount = Math.addExact(amount, event[1]);
        return new Summary(events.size(), amount, Math.max(0, now - events.peekLast()[0]));
    }

    public boolean isEmpty(long now) {
        return !summarize(now).active();
    }
}
