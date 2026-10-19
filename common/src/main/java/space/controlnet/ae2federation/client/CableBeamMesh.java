package space.controlnet.ae2federation.client;

import java.util.ArrayList;
import java.util.List;

/**
 * The centerlines of a cable cell's beam for every connection mask, with a frame round each sample, and the distance
 * tiers beams take their detail from. {@link BundleBeamMesh} sweeps its plasma beam along them, in an ordinary cell and
 * stretched over a bundle's box.
 */
public final class CableBeamMesh {
    record Point(float x, float y, float z, float mix) {}
    record Sample(Point point, float nx, float ny, float nz, float bx, float by, float bz) {}
    record Path(int firstAxis, int secondAxis, Sample[] samples) {}
    /** The longest path has 64 segments. */
    static final int MAX_SAMPLES = 65;

    private CableBeamMesh() {}

    /** 0 near, 1 medium, 2 distant. */
    public static int detail(double distanceSquared) {
        return distanceSquared > 48 * 48 ? 2 : distanceSquared > 24 * 24 ? 1 : 0;
    }

    /** The centerlines of {@code mask}'s beam in a unit cell, {@code segments} to a full arm or an elbow. */
    static List<Path> build(int mask, int segments) {
        var paths = new ArrayList<Path>();
        int first = Integer.numberOfTrailingZeros(mask);
        int second = Integer.numberOfTrailingZeros(mask & (mask - 1));
        if (Integer.bitCount(mask) == 2 && first / 2 != second / 2) {
            int a = first / 2, b = second / 2;
            float sa = (first & 1) == 0 ? 1 : -1, sb = (second & 1) == 0 ? 1 : -1;
            float radius = .1875f;
            var points = new ArrayList<Point>();
            for (int i = 0; i <= segments; i++) {
                double t = (double) i / segments;
                float[] p = {.5f, .5f, .5f};
                float mix;
                if (t < .3125) {
                    p[a] += sa * (.5f - (float) t); mix = 0;
                } else if (t > .6875) {
                    p[b] += sb * ((float) t - .5f); mix = 1;
                } else {
                    mix = (float) ((t - .3125) / .375);
                    double angle = mix * Math.PI / 2;
                    p[a] += sa * radius * (1 - (float) Math.sin(angle));
                    p[b] += sb * radius * (1 - (float) Math.cos(angle));
                }
                points.add(new Point(p[0], p[1], p[2], mix));
            }
            paths.add(path(a, b, points));
        } else {
            for (int axis = 0; axis < 3; axis++) {
                int pair = mask >> (2 * axis) & 3;
                if (pair == 0 && (mask != 0 || axis != 0)) continue;
                float from = (pair & 2) != 0 ? 0 : mask == 0 ? .375f : .5f;
                float to = (pair & 1) != 0 ? 1 : mask == 0 ? .625f : .5f;
                var points = new ArrayList<Point>();
                int count = Math.max(1, Math.round((to - from) * segments));
                for (int i = 0; i <= count; i++) {
                    float[] p = {.5f, .5f, .5f}; p[axis] = from + (to - from) * i / count;
                    points.add(new Point(p[0], p[1], p[2], 0));
                }
                paths.add(path(axis, axis, points));
            }
        }
        return List.copyOf(paths);
    }

    private static Path path(int first, int second, List<Point> points) {
        var samples = new Sample[points.size()];
        for (int i = 0; i < samples.length; i++) {
            var before = points.get(Math.max(0, i - 1));
            var after = points.get(Math.min(samples.length - 1, i + 1));
            float tx = after.x - before.x, ty = after.y - before.y, tz = after.z - before.z;
            float length = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
            tx /= length; ty /= length; tz /= length;
            // Each end leaves along its own arm, square to the face, however coarsely an elbow is sampled.
            if (i == 0 || i == samples.length - 1) {
                int axis = i == 0 ? first : second;
                float along = axis == 0 ? tx : axis == 1 ? ty : tz;
                tx = axis == 0 ? Math.signum(along) : 0;
                ty = axis == 1 ? Math.signum(along) : 0;
                tz = axis == 2 ? Math.signum(along) : 0;
            }
            // A fixed reference normal across each planar elbow avoids twisting.
            int normal = first == second ? (first + 1) % 3 : 3 - first - second;
            float nx = normal == 0 ? 1 : 0, ny = normal == 1 ? 1 : 0, nz = normal == 2 ? 1 : 0;
            samples[i] = new Sample(points.get(i), nx, ny, nz, ty * nz - tz * ny,
                    tz * nx - tx * nz, tx * ny - ty * nx);
        }
        if (samples.length > MAX_SAMPLES) throw new IllegalStateException("Beam path too long");
        return new Path(first, second, samples);
    }
}
