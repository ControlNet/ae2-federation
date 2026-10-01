package space.controlnet.ae2federation.client.policy;

import org.jetbrains.annotations.Nullable;

/**
 * The energy a network card and its stats show. A network that shares an energy pool with others (Federation sharing
 * or Quartz Fibers) shows the pool, so every member reads the same figures however its own cells are filled; one on
 * its own shows its own cells. The pool sums every Grid AE2 joins into the Grid's energy overlay.
 */
public record EnergyFigures(long stored, long max, boolean shared) {
    /** The pool figures are absent when the server could not walk the Grid's energy overlay. */
    public static EnergyFigures of(long stored, long max, @Nullable Long pool, @Nullable Long poolMax, int poolGrids) {
        if (pool != null && poolMax != null && poolGrids > 1) {
            return new EnergyFigures(Math.min(pool, poolMax), poolMax, true);
        }
        return new EnergyFigures(stored, max, false);
    }

    /** Stored against capacity, 0 to 1, or -1 when there is no capacity to read against. */
    public float fraction() {
        return max <= 0 ? -1 : (float) Math.min(1, stored / (double) max);
    }

    public int percent() {
        return max <= 0 ? 0 : (int) Math.min(100, Math.round(stored * 100d / max));
    }
}
