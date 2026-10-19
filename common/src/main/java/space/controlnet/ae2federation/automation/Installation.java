package space.controlnet.ae2federation.automation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A public function installed on a computing device: the arguments it is called with, what starts a run, at most one
 * start per {@code interval} ticks, and what a trigger does while a run is still going. Runs use the version of the
 * file last pushed.
 */
public record Installation(String id, String device, String file, String function, List<Object> args,
        Trigger trigger, int interval, Overlap overlap) {
    public static final String PREFIX = "install";
    /** The rate limit (user, 2026-10-18): one start per 20 ticks unless chosen otherwise, never under 5. */
    public static final int DEFAULT_INTERVAL = 20;
    public static final int MIN_INTERVAL = 5;
    /** Runs at once in parallel mode (assistant proposal). */
    public static final int PARALLEL_LIMIT = 4;

    public Installation {
        args = values(args);
        interval = Math.max(MIN_INTERVAL, interval);
    }

    /** What a trigger does while a run is going (user, 2026-10-18); ignore by default (assistant proposal). */
    public enum Overlap {
        /** The trigger is dropped. */
        IGNORE,
        /** The run stops, what it did stays done, and a new one starts. */
        RESTART,
        /** Another run starts, up to {@link #PARALLEL_LIMIT}. */
        PARALLEL
    }

    /** What starts a run. Triggers pass no data; the function reads what it needs. */
    public sealed interface Trigger {
        /** Only a player starts it. */
        record Manual() implements Trigger {
        }

        /** The device's redstone signal turns on. */
        record Redstone() implements Trigger {
        }

        /** Every {@code ticks} ticks. */
        record Schedule(int ticks) implements Trigger {
            public Schedule {
                ticks = Math.max(1, ticks);
            }
        }

        /** A {@code bool} function of the same file, called every interval, turns true. */
        record Condition(String function, List<Object> args) implements Trigger {
            public Condition {
                args = values(args);
            }
        }

        /** An event from the curated list, with its arguments. */
        record Event(String name, List<Object> args) implements Trigger {
            public Event {
                args = values(args);
            }
        }
    }

    /**
     * An argument as the text an {@code install} or {@code args} request takes back: numbers and strings as they are,
     * {@code True} and {@code False}, and nothing for {@code None}.
     */
    public static String text(Object value) {
        if (value == null) return "";
        if (value instanceof Boolean flag) return flag ? "True" : "False";
        return String.valueOf(value);
    }

    /** A copy that keeps {@code None} arguments, which {@link List#copyOf} refuses. */
    private static List<Object> values(List<Object> args) {
        return Collections.unmodifiableList(new ArrayList<>(args));
    }

    public Installation withArgs(List<Object> args) {
        return new Installation(id, device, file, function, args, trigger, interval, overlap);
    }

    public Installation withTrigger(Trigger trigger) {
        return new Installation(id, device, file, function, args, trigger, interval, overlap);
    }

    public Installation withInterval(int interval) {
        return new Installation(id, device, file, function, args, trigger, interval, overlap);
    }

    public Installation withOverlap(Overlap overlap) {
        return new Installation(id, device, file, function, args, trigger, interval, overlap);
    }
}
