package space.controlnet.ae2federation.client.policy;

import java.util.ArrayList;
import java.util.List;

/**
 * The visible parts of a topology link: from the edge of one card to the edge of the other, minus the link's label
 * at the midpoint. Flow dots travel only along them, so cards and labels never hide them.
 */
public final class FlowPath {
    private FlowPath() {
    }

    public record Segment(float fromX, float fromY, float toX, float toY) {
        /** {@code count} evenly spaced dots at {@code phase} (0..1) along the segment, as flat {@code [x, y, ...]}. */
        public float[] dots(float phase, int count) {
            var points = new float[count * 2];
            for (int dot = 0; dot < count; dot++) {
                float t = (phase + dot / (float) count) % 1f;
                points[dot * 2] = fromX + (toX - fromX) * t;
                points[dot * 2 + 1] = fromY + (toY - fromY) * t;
            }
            return points;
        }
    }

    /**
     * The visible stubs between two cards when a label of half size {@code labelHalfWidth} x {@code labelHalfHeight}
     * sits at the midpoint of the line, in order from {@code a} to {@code b}. Zero, one or two segments.
     */
    public static List<Segment> visible(float ax, float ay, float bx, float by, float cardHalfWidth, float cardHalfHeight,
            float labelHalfWidth, float labelHalfHeight) {
        float dx = bx - ax;
        float dy = by - ay;
        if (dx == 0 && dy == 0) return List.of();
        float card = inside(dx, dy, cardHalfWidth, cardHalfHeight);
        float label = labelHalfWidth <= 0 || labelHalfHeight <= 0 ? 0 : inside(dx, dy, labelHalfWidth, labelHalfHeight);
        var stubs = new ArrayList<Segment>(2);
        if (label == 0) {
            addStub(stubs, ax, ay, dx, dy, card, 1 - card);
        } else {
            addStub(stubs, ax, ay, dx, dy, card, 0.5f - label);
            addStub(stubs, ax, ay, dx, dy, 0.5f + label, 1 - card);
        }
        return stubs;
    }

    /** Fraction of the centre-to-centre vector that lies inside a box of the given half size around one end. */
    private static float inside(float dx, float dy, float halfWidth, float halfHeight) {
        return Math.min(dx == 0 ? Float.MAX_VALUE : halfWidth / Math.abs(dx), dy == 0 ? Float.MAX_VALUE : halfHeight / Math.abs(dy));
    }

    private static void addStub(List<Segment> stubs, float ax, float ay, float dx, float dy, float from, float to) {
        if (to - from <= 0) return;
        stubs.add(new Segment(ax + dx * from, ay + dy * from, ax + dx * to, ay + dy * to));
    }
}
