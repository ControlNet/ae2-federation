package space.controlnet.ae2federation.client.policy;

/**
 * A processing wire from a pattern port to an Endpoint card: a cubic curve that leaves the port and enters the card
 * horizontally, as node editors draw them. Its control points sit halfway across, so it never loops back.
 */
public record WireCurve(float fromX, float fromY, float toX, float toY) {
    /** The point at {@code t} (0..1) along the curve, as {@code [x, y]}. */
    public float[] at(float t) {
        float bend = (toX - fromX) / 2;
        float c1x = fromX + bend;
        float c2x = toX - bend;
        float u = 1 - t;
        float x = u * u * u * fromX + 3 * u * u * t * c1x + 3 * u * t * t * c2x + t * t * t * toX;
        float y = u * u * u * fromY + 3 * u * u * t * fromY + 3 * u * t * t * toY + t * t * t * toY;
        return new float[] {x, y};
    }

    /** The curve's direction at {@code t} (0..1), as {@code [dx, dy]}, not normalised. */
    public float[] tangent(float t) {
        float bend = (toX - fromX) / 2;
        float c1x = fromX + bend;
        float c2x = toX - bend;
        float u = 1 - t;
        float dx = 3 * u * u * (c1x - fromX) + 6 * u * t * (c2x - c1x) + 3 * t * t * (toX - c2x);
        float dy = 6 * u * t * (toY - fromY);
        return new float[] {dx, dy};
    }

    /** {@code segments + 1} points along the curve, as flat {@code [x, y, ...]}. */
    public float[] points(int segments) {
        var points = new float[(segments + 1) * 2];
        for (int index = 0; index <= segments; index++) {
            var point = at(index / (float) segments);
            points[index * 2] = point[0];
            points[index * 2 + 1] = point[1];
        }
        return points;
    }

    /** Distance from a point to the curve, measured to a polyline of {@code segments} pieces. */
    public float distance(float x, float y, int segments) {
        var points = points(segments);
        float best = Float.MAX_VALUE;
        for (int index = 2; index < points.length; index += 2) {
            float ax = points[index - 2];
            float ay = points[index - 1];
            float dx = points[index] - ax;
            float dy = points[index + 1] - ay;
            float length = dx * dx + dy * dy;
            float t = length == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / length));
            float px = ax + dx * t - x;
            float py = ay + dy * t - y;
            best = Math.min(best, (float) Math.sqrt(px * px + py * py));
        }
        return best;
    }
}
