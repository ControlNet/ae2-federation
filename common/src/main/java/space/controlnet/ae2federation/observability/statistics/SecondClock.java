package space.controlnet.ae2federation.observability.statistics;

import org.jetbrains.annotations.Nullable;

/**
 * Turns server ticks into wall-clock seconds for {@link FederationStatistics}: which second to finish once the clock
 * has passed it, and which seconds in between the server still ran through. A tick that takes longer than a second
 * (a lag spike) runs through the seconds it spans, so they are observed; a gap between two ticks longer than
 * {@link #IDLE_GAP_MILLIS} is time no tick ran (a paused singleplayer game), so it stays unobserved.
 */
final class SecondClock {
    /** A wait between the end of one tick and the start of the next longer than this means the server was idle. */
    static final long IDLE_GAP_MILLIS = 1000;

    /**
     * What a tick's end asks for: finish second {@code finish}, then observe the seconds {@code observeFrom} to
     * {@code observeTo} (inclusive, none when {@code observeFrom > observeTo}).
     */
    record Step(long finish, long observeFrom, long observeTo) {
    }

    private long lastSecond = Long.MIN_VALUE;
    private long lastEnd = Long.MIN_VALUE;
    private long start = Long.MIN_VALUE;

    /** A tick starts at {@code millis}. */
    void started(long millis) {
        start = millis;
    }

    /** A tick ends at {@code millis}; the step to take, or {@code null} while the second has not changed. */
    @Nullable Step ended(long millis) {
        long now = Math.floorDiv(millis, 1000L);
        long previous = lastSecond;
        boolean idle = start != Long.MIN_VALUE && lastEnd != Long.MIN_VALUE && start - lastEnd > IDLE_GAP_MILLIS;
        long startSecond = Math.floorDiv(start, 1000L);
        lastEnd = millis;
        if (previous == Long.MIN_VALUE) {
            lastSecond = now;
            return null;
        }
        if (now == previous) return null;
        lastSecond = now;
        return new Step(previous, idle ? Math.max(previous + 1, startSecond) : previous + 1, now - 1);
    }
}
