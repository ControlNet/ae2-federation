package space.controlnet.ae2federation.function;

/** {@code range(start, stop, step)}: produced lazily, so {@code range(10**9)} costs nothing until it is used. */
public record RangeValue(long start, long stop, long step) {
    public long length() {
        if (step > 0 ? start >= stop : start <= stop) {
            return 0;
        }
        return step > 0 ? (stop - start - 1) / step + 1 : (start - stop - 1) / -step + 1;
    }

    public long get(long index) {
        return start + index * step;
    }

    @Override
    public String toString() {
        return Ops.repr(this);
    }
}
