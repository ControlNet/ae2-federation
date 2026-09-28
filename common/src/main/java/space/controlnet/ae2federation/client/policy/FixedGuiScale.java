package space.controlnet.ae2federation.client.policy;

/**
 * The GUI scale Federation screens use regardless of the player's GUI scale option, so the workspace always has the
 * room it was laid out for and text keeps the same size relative to it. Integer scales keep the pixel font crisp.
 */
public final class FixedGuiScale {
    /** Logical space the 640x400 workspace needs, allowing a slightly shorter window before it compresses. */
    public static final int MIN_WIDTH = 640;
    public static final int MIN_HEIGHT = 380;

    private FixedGuiScale() {
    }

    /** The largest integer scale whose logical viewport still holds the workspace; 1 when none does. */
    public static int forFramebuffer(int width, int height) {
        int scale = 1;
        while (width / (scale + 1) >= MIN_WIDTH && height / (scale + 1) >= MIN_HEIGHT) scale++;
        return scale;
    }
}
