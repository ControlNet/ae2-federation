package space.controlnet.ae2federation.storage.resources;

public final class NativeResourceAmounts {
    private NativeResourceAmounts() {
    }

    public static long checkedAdd(long current, long added) {
        if (current < 0 || added < 0) {
            throw new IllegalArgumentException("Native resource quantities must be non-negative");
        }
        return Math.addExact(current, added);
    }
}
