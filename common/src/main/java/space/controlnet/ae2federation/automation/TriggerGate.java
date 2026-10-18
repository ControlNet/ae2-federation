package space.controlnet.ae2federation.automation;

/**
 * When an installation's runs start: at most one start per interval, triggers during the cooldown merging into one
 * start after it, and the overlap mode deciding what a trigger does while runs are going.
 */
public final class TriggerGate {
    public enum Action {
        NONE, START,
        /** Stop the runs that are going, then start. */
        RESTART
    }

    private final int interval;
    private final Installation.Overlap overlap;
    private long lastStart = Long.MIN_VALUE / 2;
    private boolean waiting;

    public TriggerGate(int interval, Installation.Overlap overlap) {
        this.interval = interval;
        this.overlap = overlap;
    }

    /** A trigger arrived while {@code running} runs are going. */
    public void fire(int running) {
        if (!dropped(running)) {
            waiting = true;
        }
    }

    /** What to do at tick {@code now}, with {@code running} runs going. */
    public Action poll(long now, int running) {
        if (!waiting || now - lastStart < interval) {
            return Action.NONE;
        }
        waiting = false;
        if (dropped(running)) {
            return Action.NONE;
        }
        lastStart = now;
        return overlap == Installation.Overlap.RESTART && running > 0 ? Action.RESTART : Action.START;
    }

    private boolean dropped(int running) {
        return switch (overlap) {
            case IGNORE -> running > 0;
            case RESTART -> false;
            case PARALLEL -> running >= Installation.PARALLEL_LIMIT;
        };
    }
}
