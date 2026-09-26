package space.controlnet.ae2federation.test.ui;

import java.util.concurrent.atomic.AtomicInteger;

/** Records even background failures swallowed by asynchronous search-tree futures. */
public final class FontThreadEvidence {
    private static final AtomicInteger VIOLATIONS = new AtomicInteger();

    private FontThreadEvidence() {}

    public static IllegalStateException violation() {
        VIOLATIONS.incrementAndGet();
        return new IllegalStateException("Font cache accessed outside the render thread: "
                + Thread.currentThread().getName());
    }

    public static int violations() {
        return VIOLATIONS.get();
    }
}
