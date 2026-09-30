package space.controlnet.ae2federation.energy;

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
    private static final ThreadLocal<Set<Object>> VISITED = new ThreadLocal<>();

    private EnergyRouteGuard() {
    }

    /** Runs a demand of {@code consumer}; a demand already running on this thread joins it. */
    static double demand(Object consumer, DoubleSupplier operation) {
        var visited = VISITED.get();
        if (visited != null) {
            visited.add(consumer);
            return operation.getAsDouble();
        }
        visited = Collections.newSetFromMap(new IdentityHashMap<>());
        visited.add(consumer);
        VISITED.set(visited);
        try {
            return operation.getAsDouble();
        } finally {
            VISITED.remove();
        }
    }

    /** Whether the running demand may draw on {@code provider}: false when it already visited that Grid. */
    static boolean visit(Object provider) {
        var visited = VISITED.get();
        return visited == null || visited.add(provider);
    }
}
