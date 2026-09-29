package space.controlnet.ae2federation.client;

import java.util.Optional;

/**
 * Where the cursor was when a screen closed, to give back to the screen the server opens right after it.
 *
 * <p>The server opens a new menu by first closing the current one. The client then shows no screen for a moment,
 * which grabs the mouse and moves it to the window centre, and releases it at the centre again for the new screen.
 * Going from one Federation screen to the next would otherwise always leave the cursor in the middle.
 */
final class ScreenSwitchCursor {
    /** The close and the open arrive together; a screen the player opens later starts centred as usual. */
    static final long SWITCH_MILLIS = 500;

    private double x;
    private double y;
    private long leftAt;
    private boolean pending;

    /** A screen closed with the cursor at {@code x, y} in window coordinates. */
    void left(double x, double y, long now) {
        this.x = x;
        this.y = y;
        leftAt = now;
        pending = true;
    }

    /** A screen opened: where to put the cursor, once, if the last one closed just before. */
    Optional<double[]> arrived(long now) {
        if (!pending) return Optional.empty();
        pending = false;
        return now - leftAt <= SWITCH_MILLIS ? Optional.of(new double[] {x, y}) : Optional.empty();
    }

    void forget() {
        pending = false;
    }
}
