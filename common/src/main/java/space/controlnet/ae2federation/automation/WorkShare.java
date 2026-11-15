package space.controlnet.ae2federation.automation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The server's program work for one tick, in the runner's units ({@code Runner.tick(int, long)}). Each run may take up to {@link #perRun} a tick; all runs together up to {@link #perTick}. Once
 * the tick's work is gone the remaining runs get none this tick, and the next tick starts with the first installation
 * that got none, so a busy server shares its program time in turn instead of always serving the same installations.
 *
 * <p>Assistant choices (2026-11-15, after the owner chose to count work by values): 200,000 a run, the old 10,000
 * plain steps; 2,000,000 a tick, 10 runs at full speed. Measured in the test JVM: a run's budget of copying,
 * comparing or searching long lists and strings takes 1.2-1.7 ms (it took 1.4-2.2 s before), of plain steps 0.45 ms;
 * so all programs together take at most about 15 ms of a tick, plus one step past the budget.
 */
final class WorkShare {
    static final long PER_RUN = 200_000;
    static final long PER_TICK = 2_000_000;

    private final long perRun;
    private final long perTick;
    private long left;
    /** The installation the next tick starts with: the first that got nothing this tick. */
    private String resumeAt;
    private String firstDenied;

    WorkShare(long perRun, long perTick) {
        this.perRun = perRun;
        this.perTick = perTick;
    }

    /** A new tick: all of its work is left. */
    void begin() {
        left = perTick;
        firstDenied = null;
    }

    /** {@code items} in this tick's order: from the installation the last tick could not serve, then around. */
    <T> List<T> order(List<T> items, Function<T, String> id) {
        int start = 0;
        if (resumeAt != null) {
            for (int i = 0; i < items.size(); i++) {
                if (id.apply(items.get(i)).equals(resumeAt)) {
                    start = i;
                    break;
                }
            }
        }
        var out = new ArrayList<T>(items.size());
        out.addAll(items.subList(start, items.size()));
        out.addAll(items.subList(0, start));
        return out;
    }

    /** The work a run of {@code installation} may take now; zero once the tick's work is gone. */
    long grant(String installation) {
        if (left <= 0) {
            if (firstDenied == null) {
                firstDenied = installation;
            }
            return 0;
        }
        return Math.min(perRun, left);
    }

    /** A run took {@code work}, past its grant included. */
    void spend(long work) {
        left -= work;
    }

    /** The tick is over: the next starts with the first installation this one could not serve. */
    void end() {
        resumeAt = firstDenied;
    }
}
