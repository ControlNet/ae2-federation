package space.controlnet.ae2federation.client.policy;

/**
 * A topology link between two network cards of the same size: a curve from the edge of one card that faces the other
 * to the facing edge of the other, side edges when the cards stand side by side and top/bottom edges when they are
 * stacked. Its label sits where the curve passes the middle, or further along when another label needs the middle;
 * flow dots travel along it and hide under the label.
 */
public record TopologyLink(WireCurve curve, float labelAt) {
    /** The link between cards whose top-left corners are {@code (ax, ay)} and {@code (bx, by)}. */
    public static TopologyLink between(float ax, float ay, float bx, float by, float width, float height) {
        float fromCenterX = ax + width / 2;
        float toCenterX = bx + width / 2;
        if (Math.abs(toCenterX - fromCenterX) >= width) {
            boolean right = toCenterX > fromCenterX;
            return new TopologyLink(new WireCurve(right ? ax + width : ax, ay + height / 2, right ? bx : bx + width,
                    by + height / 2), 0.5f);
        }
        boolean down = by > ay;
        return new TopologyLink(new WireCurve(fromCenterX, down ? ay + height : ay, toCenterX, down ? by : by + height), 0.5f);
    }

    /** The same link with its label at {@code t} (0..1) along the curve. */
    public TopologyLink withLabelAt(float t) {
        return new TopologyLink(curve, t);
    }

    public float[] start() {
        return new float[] {curve.fromX(), curve.fromY()};
    }

    public float[] end() {
        return new float[] {curve.toX(), curve.toY()};
    }

    public float[] middle() {
        return curve.at(0.5f);
    }

    /** Where the label's centre sits on the curve. */
    public float[] label() {
        return curve.at(labelAt);
    }

    /**
     * {@code count} evenly spaced dots at {@code phase} (0..1), from the start or, when {@code returning}, from the
     * end, as flat {@code [x, y, ...]}. Dots inside the label box of the given half size around the label are left out.
     */
    public float[] dots(float phase, int count, boolean returning, float labelHalfWidth, float labelHalfHeight) {
        var middle = label();
        var points = new float[count * 2];
        int shown = 0;
        for (int dot = 0; dot < count; dot++) {
            float t = (phase + dot / (float) count) % 1f;
            var point = curve.at(returning ? 1 - t : t);
            if (labelHalfWidth > 0 && Math.abs(point[0] - middle[0]) <= labelHalfWidth && Math.abs(point[1] - middle[1]) <= labelHalfHeight) continue;
            points[shown * 2] = point[0];
            points[shown * 2 + 1] = point[1];
            shown++;
        }
        return java.util.Arrays.copyOf(points, shown * 2);
    }
}
