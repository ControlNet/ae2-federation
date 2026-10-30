package space.controlnet.ae2federation.script;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * What a run's current step may still do, for the thread that runs it. The size caps bound each value, but not how
 * many values one step walks (comparing two lists that share their parts over and over) nor how much a run keeps
 * (a chain of records, each holding a long string). So a step stops past {@link #MAX_WORK} values walked, and a run
 * stops with {@code MemoryError} once what it holds passes {@link #MAX_HELD} items, measured by walking its values
 * each time it has made {@link #CHECK_EVERY} new ones: the walk costs at most what was made since the last one.
 * Outside a run (the checker's constants, the editor) nothing is metered.
 */
final class Meter {
    /** Values one step may compare or write out; about 50 ms of work in the test JVM. */
    static final long MAX_WORK = 5_000_000;
    /** Items (characters, list items, fields, entries) a run may hold at once: 40 strings at their cap. */
    static final long MAX_HELD = 4_000_000;
    static final long CHECK_EVERY = MAX_HELD / 2;

    private static final ThreadLocal<Meter> CURRENT = new ThreadLocal<>();

    private long work;
    private long made;

    /** Meters this thread until {@link #leave}; gives the meter it replaced, to restore. */
    static Meter enter(Meter meter) {
        var previous = CURRENT.get();
        CURRENT.set(meter);
        return previous;
    }

    static void leave(Meter previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }

    /** A new step: its work starts from zero. */
    void step() {
        work = 0;
    }

    /** Whether enough was made since the last walk that the run's values should be walked again. */
    boolean due() {
        return made >= CHECK_EVERY;
    }

    /** Walks what {@code roots} reach, each value once; stops the run past {@link #MAX_HELD} items. */
    void walk(Iterable<?> roots) {
        made = 0;
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        var todo = new ArrayDeque<Object>();
        roots.forEach(root -> {
            if (root != null) todo.push(root);
        });
        long held = 0;
        while (!todo.isEmpty()) {
            var value = todo.pop();
            if (!seen.add(value)) {
                continue;
            }
            held += switch (value) {
                case String s -> s.length();
                case ListValue l -> {
                    l.items.forEach(item -> {
                        if (item != null) todo.push(item);
                    });
                    yield l.items.size() + 1L;
                }
                case DictValue d -> {
                    for (var entry : d.entries.values()) {
                        if (entry.key != null) todo.push(entry.key);
                        if (entry.value != null) todo.push(entry.value);
                    }
                    yield 2L * d.size() + 1;
                }
                case SetValue s -> {
                    s.items.values().forEach(item -> {
                        if (item != null) todo.push(item);
                    });
                    yield s.size() + 1L;
                }
                case RecordValue r -> {
                    r.fields.values().forEach(field -> {
                        if (field != null) todo.push(field);
                    });
                    yield r.fields.size() + 1L;
                }
                case Held h -> {
                    h.parts().forEach(part -> {
                        if (part != null) todo.push(part);
                    });
                    yield 1;
                }
                default -> 1;
            };
            if (held > MAX_HELD) {
                throw new RunError("MemoryError", "a run holds at most " + MAX_HELD
                        + " items (characters, list items, fields) at once");
            }
        }
    }

    /** A runner's own value that holds program values, such as a loop's cursor. */
    interface Held {
        Iterable<?> parts();
    }

    /** {@code n} values were walked in this step. */
    static void work(long n) {
        var meter = CURRENT.get();
        if (meter != null && (meter.work += n) > MAX_WORK) {
            throw new RunError("RuntimeError", "one step may compare or write out at most " + MAX_WORK + " values");
        }
    }

    /** {@code n} new items (characters, list items, fields, entries) were made. */
    static void made(long n) {
        var meter = CURRENT.get();
        if (meter != null) {
            meter.made += n;
        }
    }
}
