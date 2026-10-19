package space.controlnet.ae2federation.beam;

/**
 * Whether a pair is linked, given what the path check has seen each tick. A link breaks only after its beam has stayed
 * blocked for the delay, and returns only after it has stayed clear for the delay, because every change re-judges the
 * domain's topology. A new pair whose first finished sweep is clear links at once, so a world that starts or a pair
 * just built does not wait.
 */
public final class BeamDebounce {
    /** What the path check knows this tick. */
    public enum Seen {
        /** A blocking position is known and still there. */
        BLOCKED,
        /** No blocker is known and the last finished sweep found none. */
        CLEAR,
        /** No blocker is known, but no sweep has finished since the last one went. */
        UNKNOWN
    }

    private final int delay;
    private boolean linked;
    private boolean fresh = true;
    private long blockedSince = -1;
    private long clearSince = -1;

    public BeamDebounce(int delay) {
        this.delay = delay;
    }

    /** Takes what was seen at {@code tick} and returns whether the pair is linked. */
    public boolean update(Seen seen, long tick) {
        switch (seen) {
            case BLOCKED -> {
                fresh = false;
                clearSince = -1;
                if (blockedSince < 0) blockedSince = tick;
                if (linked && tick - blockedSince >= delay) linked = false;
            }
            case CLEAR -> {
                blockedSince = -1;
                if (clearSince < 0) clearSince = tick;
                if (!linked && (fresh || tick - clearSince >= delay)) linked = true;
                fresh = false;
            }
            case UNKNOWN -> {
                blockedSince = -1;
                clearSince = -1;
            }
        }
        return linked;
    }

    public boolean linked() {
        return linked;
    }
}
