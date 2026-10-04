package space.controlnet.ae2federation.client.menu;

/**
 * The pixels of AE2's on switch (`guis/checkbox.png`, 22x12) that are its blue track, as {x, y, width, height, kind}
 * in the sprite's grid. A re-exporting rule's switch is AE2's on switch with these pixels green; the knob and outline
 * stay AE2's. Plain data, so a unit test can check it against AE2's texture.
 */
final class SwitchTrackOverlay {
    static final int TRACK = 0;
    static final int GLINT = 1;
    /** AE2's track #9CD3FF and its highlight #DAFFFF, recoloured green. */
    static final int TRACK_GREEN = 0xff6cd680;
    static final int GLINT_GREEN = 0xffceffd6;

    /** The on switch at (0,40). */
    static final int[][] ON = {
            {1, 2, 9, 2, TRACK}, {1, 4, 4, 5, TRACK}, {5, 4, 1, 5, GLINT}, {6, 4, 4, 5, TRACK}, {1, 9, 9, 2, TRACK}};
    /** Its hover at (22,40), whose knob rim sits two pixels further left. */
    static final int[][] ON_HOVER = {
            {1, 2, 7, 2, TRACK}, {1, 4, 4, 5, TRACK}, {5, 4, 1, 5, GLINT}, {6, 4, 2, 5, TRACK}, {1, 9, 7, 2, TRACK}};

    private SwitchTrackOverlay() {
    }

    static int color(int kind) {
        return kind == TRACK ? TRACK_GREEN : GLINT_GREEN;
    }
}
