package space.controlnet.ae2federation.client.policy;

/**
 * A processing wire from a pattern port to an Endpoint card: a cubic curve that leaves the port and enters the card
 * horizontally, as node editors draw them. Its control points sit halfway across, so it never loops back.
 *
 * <p>A topology Endpoint link may instead leave and enter {@code vertical}ly, and may bend only as far as
 * {@code bendEnd} (an x, or a y when vertical) and run straight on from there to its end, so it can pass between other
 * nodes. Points along it are spread by length over the bend and the run alike.
 */
public record WireCurve(float fromX, float fromY, float toX, float toY, boolean vertical, float bendEnd) {
    /** Bends all the way, horizontally. */
    public WireCurve(float fromX, float fromY, float toX, float toY) {
        this(fromX, fromY, toX, toY, false, toX);
    }

    /** Leaves and enters vertically, bending all the way. */
    public static WireCurve vertical(float fromX, float fromY, float toX, float toY) {
        return new WireCurve(fromX, fromY, toX, toY, true, toY);
    }

    /** The same wire moved by {@code (dx, dy)}. */
    public WireCurve translated(float dx, float dy) {
        return new WireCurve(fromX + dx, fromY + dy, toX + dx, toY + dy, vertical, bendEnd + (vertical ? dy : dx));
    }

    /** The point at {@code t} (0..1) along the curve, as {@code [x, y]}. */
    public float[] at(float t) {
        float split = split();
        if (t >= split && split < 1) {
            float along = (t - split) / (1 - split);
            return vertical ? new float[] {toX, bendEnd + (toY - bendEnd) * along}
                    : new float[] {bendEnd + (toX - bendEnd) * along, toY};
        }
        return bend(split == 0 ? 0 : t / split);
    }

    /** The curve's direction at {@code t} (0..1), as {@code [dx, dy]}, not normalised. */
    public float[] tangent(float t) {
        float split = split();
        if (t >= split && split < 1) return vertical ? new float[] {0, toY - bendEnd} : new float[] {toX - bendEnd, 0};
        float s = split == 0 ? 0 : t / split;
        float u = 1 - s;
        if (vertical) {
            float bend = (bendEnd - fromY) / 2;
            float c1y = fromY + bend;
            float c2y = bendEnd - bend;
            float dy = 3 * u * u * (c1y - fromY) + 6 * u * s * (c2y - c1y) + 3 * s * s * (bendEnd - c2y);
            return new float[] {6 * u * s * (toX - fromX), dy};
        }
        float bend = (bendEnd - fromX) / 2;
        float c1x = fromX + bend;
        float c2x = bendEnd - bend;
        float dx = 3 * u * u * (c1x - fromX) + 6 * u * s * (c2x - c1x) + 3 * s * s * (bendEnd - c2x);
        return new float[] {dx, 6 * u * s * (toY - fromY)};
    }

    /** The point at {@code s} (0..1) along the bend alone. */
    private float[] bend(float s) {
        float u = 1 - s;
        float ease = 3 * u * s * s + s * s * s;
        if (vertical) {
            float bend = (bendEnd - fromY) / 2;
            float y = u * u * u * fromY + 3 * u * u * s * (fromY + bend) + 3 * u * s * s * (bendEnd - bend) + s * s * s * bendEnd;
            return new float[] {fromX + (toX - fromX) * ease, y};
        }
        float bend = (bendEnd - fromX) / 2;
        float x = u * u * u * fromX + 3 * u * u * s * (fromX + bend) + 3 * u * s * s * (bendEnd - bend) + s * s * s * bendEnd;
        return new float[] {x, fromY + (toY - fromY) * ease};
    }

    /** How far along (0..1) the bend ends and the straight run begins, by length; 1 without a run. */
    private float split() {
        float run = Math.abs(vertical ? toY - bendEnd : toX - bendEnd);
        if (run == 0) return 1;
        float bent = 0;
        var previous = bend(0);
        for (int index = 1; index <= 8; index++) {
            var point = bend(index / 8f);
            bent += (float) Math.hypot(point[0] - previous[0], point[1] - previous[1]);
            previous = point;
        }
        return bent / (bent + run);
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
