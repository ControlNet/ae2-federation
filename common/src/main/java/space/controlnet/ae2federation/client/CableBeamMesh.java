package space.controlnet.ae2federation.client;

import java.util.ArrayList;
import java.util.List;

/** Cached centerlines and cross-sections for all masks; emits primitives without per-vertex allocation. */
public final class CableBeamMesh {
    @FunctionalInterface
    public interface VertexSink {
        void vertex(boolean glow, float x, float y, float z, float red, float green, float blue, float alpha);
    }

    private record Point(float x, float y, float z, float mix) {
        float component(int axis) { return axis == 0 ? x : axis == 1 ? y : z; }
    }
    private record Sample(Point point, float nx, float ny, float nz, float bx, float by, float bz) {}
    private record Path(int firstAxis, int secondAxis, Sample[] samples) {}
    private static final List<List<List<Path>>> CACHE = buildCache();

    private CableBeamMesh() {}

    /** 0 near, 1 medium, 2 distant. Distant cables retain a carrier without animated detail. */
    public static int detail(double distanceSquared) {
        return distanceSquared > 48 * 48 ? 2 : distanceSquared > 24 * 24 ? 1 : 0;
    }

    public static void emit(int mask, int x, int y, int z, long tick, float partialTick,
            CableBeamStyle style, int detail, VertexSink sink) {
        emit(mask, x, y, z, tick, partialTick, style, detail, -1, sink);
    }

    public static void emitLayer(int mask, int x, int y, int z, long tick, float partialTick,
            CableBeamStyle style, int detail, boolean glow, VertexSink sink) {
        emit(mask, x, y, z, tick, partialTick, style, detail, glow ? 1 : 0, sink);
    }

    private static void emit(int mask, int x, int y, int z, long tick, float partialTick,
            CableBeamStyle style, int detail, int layer, VertexSink sink) {
        if (mask < 0 || mask > 63 || detail < 0 || detail > 2) throw new IllegalArgumentException("Invalid beam geometry");
        float strength = style.strength(tick, partialTick);
        float breath = style.breath(tick, partialTick);
        for (var path : CACHE.get(detail).get(mask)) {
            var samples = path.samples;
            for (int i = 0; i < samples.length - 1; i++) {
                var a = samples[i];
                var b = samples[i + 1];
                float wa = detail == 2 ? 0 : wave(path, a.point, x, y, z, tick, partialTick, style);
                float wb = detail == 2 ? 0 : wave(path, b.point, x, y, z, tick, partialTick, style);
                float ra = Math.max(style.coreRadius() * breath, style.coreRadius() * .8f + style.pulseAmplitude() * wa);
                float rb = Math.max(style.coreRadius() * breath, style.coreRadius() * .8f + style.pulseAmplitude() * wb);
                for (int plane = 0; plane < 2; plane++) {
                    // Full-bright carrier, then soft additive shoulders; all respect shell depth.
                    if (layer != 1) strip(sink, a, b, plane, -ra, ra, -rb, rb, false,
                            style.overloaded() ? 1 : .48f, style.overloaded() ? .55f : .94f,
                            style.overloaded() ? .50f : 1, Math.min(1, strength), Math.min(1, strength));
                    if (detail == 2 || layer == 0) continue;
                    float shoulderA = style.coreRadius() * 1.3f + style.pulseAmplitude() * wa;
                    float shoulderB = style.coreRadius() * 1.3f + style.pulseAmplitude() * wb;
                    float opacity = (.12f + .88f * (float) Math.min(style.load(), 1)) * strength;
                    for (int sign : SIGNS) {
                        strip(sink, a, b, plane, 0, sign * shoulderA, 0, sign * shoulderB, true,
                                style.overloaded() ? 1 : .14f, style.overloaded() ? .045f : .85f,
                                style.overloaded() ? .015f : 1, .70f * opacity, 0);
                        strip(sink, a, b, plane, 0,
                                sign * (style.haloRadius() * .62f * breath + .35f * style.pulseAmplitude() * wa),
                                0, sign * (style.haloRadius() * .62f * breath + .35f * style.pulseAmplitude() * wb), true,
                                style.overloaded() ? 1 : .08f, style.overloaded() ? .025f : .7f,
                                style.overloaded() ? .012f : 1, .70f * opacity, 0);
                        strip(sink, a, b, plane, 0, sign * style.haloRadius() * breath,
                                0, sign * style.haloRadius() * breath, true,
                                style.overloaded() ? 1 : .15f, style.overloaded() ? .025f : .68f,
                                style.overloaded() ? .012f : 1, .14f * opacity, 0);
                    }
                }
            }
        }
    }

    private static final int[] SIGNS = {-1, 1};

    private static float wave(Path path, Point point, int x, int y, int z, long tick, float partial,
            CableBeamStyle style) {
        int first = path.firstAxis == 0 ? x : path.firstAxis == 1 ? y : z;
        int second = path.secondAxis == 0 ? x : path.secondAxis == 1 ? y : z;
        float a = style.wave(CableBeamStyle.phase(first, point.component(path.firstAxis), tick, partial));
        if (path.firstAxis == path.secondAxis) return a;
        float b = style.wave(CableBeamStyle.phase(second, point.component(path.secondAxis), tick, partial));
        // Decorative elbow crossfade meets both neighboring phases, without claiming packet routing.
        return a + (b - a) * point.mix;
    }

    private static void strip(VertexSink out, Sample a, Sample b, int plane, float a0, float a1,
            float b0, float b1, boolean glow, float r, float g, float blue, float alpha0, float alpha1) {
        vertex(out, a, plane, a0, glow, r, g, blue, alpha0);
        vertex(out, b, plane, b0, glow, r, g, blue, alpha0);
        vertex(out, b, plane, b1, glow, r, g, blue, alpha1);
        vertex(out, a, plane, a1, glow, r, g, blue, alpha1);
    }

    private static void vertex(VertexSink out, Sample sample, int plane, float radius, boolean glow,
            float r, float g, float b, float alpha) {
        var p = sample.point;
        out.vertex(glow, p.x + radius * (plane == 0 ? sample.nx : sample.bx),
                p.y + radius * (plane == 0 ? sample.ny : sample.by),
                p.z + radius * (plane == 0 ? sample.nz : sample.bz), r, g, b, Math.min(1, alpha));
    }

    private static List<List<List<Path>>> buildCache() {
        var levels = new ArrayList<List<List<Path>>>();
        for (int segments : new int[] {64, 24, 4}) {
            var masks = new ArrayList<List<Path>>();
            for (int mask = 0; mask < 64; mask++) masks.add(build(mask, segments));
            levels.add(List.copyOf(masks));
        }
        return List.copyOf(levels);
    }

    private static List<Path> build(int mask, int segments) {
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
            // A fixed reference normal across each planar elbow avoids twisting.
            int normal = first == second ? (first + 1) % 3 : 3 - first - second;
            float nx = normal == 0 ? 1 : 0, ny = normal == 1 ? 1 : 0, nz = normal == 2 ? 1 : 0;
            samples[i] = new Sample(points.get(i), nx, ny, nz, ty * nz - tz * ny,
                    tz * nx - tx * nz, tx * ny - ty * nx);
        }
        return new Path(first, second, samples);
    }
}
