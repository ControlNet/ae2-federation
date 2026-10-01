package space.controlnet.ae2federation.energy;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.DoubleSupplier;

/**
 * Bounds one energy demand to a single visit per Grid. A demand that reaches a provider Grid's native energy service can
 * come back through that Grid's own Federation source; every Grid already visited by the running demand, the consumer
 * included, contributes nothing a second time. A revisit could only offer energy the first visit already took (or, in a
 * simulation, count it twice), so the walk stays linear in the number of Grids instead of following every trail.
 * Grids are compared by identity, like AE2 does.
 */
final class EnergyRouteGuard {
    /** One reusable visit set per thread; every demand is a single call, so allocating one per demand is waste. */
    private static final ThreadLocal<Demand> DEMAND = ThreadLocal.withInitial(Demand::new);
    /**
     * The demand state {@link #current} returned last. Its owner thread is checked on every read, so a thread
     * never uses another's; a miss falls back to the thread-local and replaces it.
     */
    private static volatile Demand last;

    private EnergyRouteGuard() {
    }

    /** Runs a demand of {@code consumer}; a demand already running on this thread joins it. */
    static double demand(Object consumer, DoubleSupplier operation) {
        var demand = current();
        var started = demand.enter(consumer);
        try {
            return operation.getAsDouble();
        } finally {
            demand.exit(started);
        }
    }

    /** Whether the running demand may draw on {@code provider}: false when it already visited that Grid. */
    static boolean visit(Object provider) {
        return current().visit(provider);
    }

    /** As {@link Demand#enter} on this thread's demand state. */
    static boolean enter(Object consumer) {
        return current().enter(consumer);
    }

    /** As {@link Demand#exit} on this thread's demand state. */
    static void exit(boolean started) {
        current().exit(started);
    }

    /** This thread's demand state; a demand looks it up once and passes it along. */
    static Demand current() {
        var demand = last;
        if (demand != null && demand.owner == Thread.currentThread()) {
            return demand;
        }
        demand = DEMAND.get();
        last = demand;
        return demand;
    }

    /**
     * One thread's running demand and the Grids it has visited. A demand usually reaches a handful, which an identity
     * scan of a small array answers faster than hashing; past {@link #SCAN_LIMIT} Grids an identity set takes over so
     * a large mesh stays linear. Ending a demand only resets the count: the next one, usually the same Grids again,
     * then writes a slot only where a different Grid goes, since every reference store into this long-lived array
     * pays a GC write barrier. {@link #forget} drops what a finished demand left behind.
     */
    static final class Demand {
        private static final int SCAN_LIMIT = 16;
        private final Thread owner = Thread.currentThread();
        private final Object[] recent = new Object[SCAN_LIMIT];
        private int size;
        private Set<Object> overflow;
        private boolean running;

        /**
         * Starts a demand of {@code consumer}, or joins the running one. Returns whether this call started it; the
         * caller passes that to {@link #exit} once its part of the demand is done.
         */
        boolean enter(Object consumer) {
            add(consumer);
            if (running) {
                return false;
            }
            running = true;
            return true;
        }

        /** Ends the running demand if {@code started}, the answer of the matching {@link #enter}. */
        void exit(boolean started) {
            if (started) {
                running = false;
                size = 0;
                overflow = null;
            }
        }

        /** Whether the running demand may draw on {@code provider}: false when it already visited that Grid. */
        boolean visit(Object provider) {
            return !running || add(provider);
        }

        /** Releases the Grids past the running demand's count, which only an ended demand left there. */
        void forget() {
            for (var index = size; index < SCAN_LIMIT && recent[index] != null; index++) {
                recent[index] = null;
            }
        }

        /** Adds {@code grid}; false when this demand already visited it. */
        private boolean add(Object grid) {
            if (overflow != null) {
                return overflow.add(grid);
            }
            for (var index = 0; index < size; index++) {
                if (recent[index] == grid) {
                    return false;
                }
            }
            if (size < SCAN_LIMIT) {
                if (recent[size] != grid) {
                    recent[size] = grid;
                }
                size++;
                return true;
            }
            overflow = Collections.newSetFromMap(new IdentityHashMap<>());
            overflow.addAll(Arrays.asList(recent));
            return overflow.add(grid);
        }
    }
}
