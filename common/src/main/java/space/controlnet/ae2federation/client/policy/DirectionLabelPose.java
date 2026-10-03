package space.controlnet.ae2federation.client.policy;

/**
 * Where one direction's label sits beside a topology link and how it turns. The label sits on the left of its travel
 * direction (provider to consumer), so a pair's two directions sit on opposite sides of the link, and a cap on its
 * leading end points at the consumer. Within {@link #MAX_TILT} of level it runs along the link, turned back upright when
 * the link heads left; steeper, it stays level, stacks its chips one under another and its cap points up or down.
 *
 * @param rotation degrees clockwise on screen, within ±{@link #MAX_TILT}
 * @param stacked whether the chips stand in a column, as a level label's do, rather than a row
 * @param width the whole label with its cap, before rotation
 * @param offsetX the label centre's offset from the link point
 */
public record DirectionLabelPose(float rotation, Cap cap, boolean stacked, float width, float height, float offsetX,
        float offsetY) {
    public static final float MAX_TILT = 35;
    private static final float MAX_TILT_SINE = (float) Math.sin(Math.toRadians(MAX_TILT));

    /** Which end of the label carries the cap, as seen upright. */
    public enum Cap {
        LEFT, RIGHT, UP, DOWN
    }

    /**
     * The pose for a link heading {@code (dx, dy)} from provider to consumer at the label's spot, for a label body of
     * the given size with its chips in a row and in a column, a cap {@code cap} deep and {@code clearance} kept between
     * the label and the line.
     */
    public static DirectionLabelPose of(float dx, float dy, float rowWidth, float rowHeight, float columnWidth,
            float columnHeight, float cap, float clearance) {
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        float ux = length == 0 ? 1 : dx / length;
        float uy = length == 0 ? 0 : dy / length;
        // Left of travel, with y pointing down the screen.
        float nx = uy;
        float ny = -ux;
        if (Math.abs(uy) <= MAX_TILT_SINE) {
            boolean leftward = ux < 0;
            float angle = (float) Math.toDegrees(Math.atan2(uy, ux));
            float rotation = leftward ? angle > 0 ? angle - 180 : angle + 180 : angle;
            float distance = rowHeight / 2 + clearance;
            return new DirectionLabelPose(rotation, leftward ? Cap.LEFT : Cap.RIGHT, false, rowWidth + cap, rowHeight,
                    nx * distance, ny * distance);
        }
        float width = columnWidth;
        float height = columnHeight + cap;
        float distance = Math.abs(nx) * width / 2 + Math.abs(ny) * height / 2 + clearance;
        return new DirectionLabelPose(0, uy > 0 ? Cap.DOWN : Cap.UP, true, width, height, nx * distance, ny * distance);
    }
}
