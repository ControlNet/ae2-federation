package space.controlnet.ae2federation.client.automation;

/** The script editor's caret blink: half a second shown, half hidden, starting when the caret last moved. */
final class CaretBlink {
    private CaretBlink() {
    }

    /** Whether the caret shows at {@code now}, having last moved at {@code moved} (both in milliseconds). */
    static boolean shown(long now, long moved) {
        return (now - moved) % 1000 < 500;
    }
}
