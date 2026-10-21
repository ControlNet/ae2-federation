package space.controlnet.ae2federation.script;

/** {@code range(start, stop, step)}: produced lazily, so {@code range(10**9)} costs nothing until it is used. */
public record RangeValue(long start, long stop, long step) {
    /** How many numbers it holds; a range wider than 64 bits can count stops the run. */
    public long length() {
        if (step > 0 ? start >= stop : start <= stop) {
            return 0;
        }
        // The distance is below 2^64, so it fits as an unsigned value even where it overflows a signed one.
        long n = step > 0 ? Long.divideUnsigned(stop - start - 1, step) + 1
                : Long.divideUnsigned(start - stop - 1, -step) + 1;
        if (n <= 0) {
            throw new RunError("OverflowError", Ops.TOO_LARGE);
        }
        return n;
    }

    /** Item {@code index}; exact even where {@code index * step} alone overflows, as the item itself fits. */
    public long get(long index) {
        return start + index * step;
    }

    @Override
    public String toString() {
        return Ops.repr(this);
    }
}
