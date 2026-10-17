package space.controlnet.ae2federation.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Cached centerlines and cross-sections for all masks; emits primitives without per-vertex allocation. Along a straight
 * stretch only the pulses change the beam's width, so stretches the pulses leave flat are drawn as one segment: the
 * merged edges stay within {@link #SHAPE_TOLERANCE} of the full sampling.
 */
public final class CableBeamMesh {
    @FunctionalInterface
    public interface VertexSink {
        void vertex(boolean glow, float x, float y, float z, float red, float green, float blue, float alpha);
    }

    record Point(float x, float y, float z, float mix) {
        float component(int axis) { return axis == 0 ? x : axis == 1 ? y : z; }
    }
    record Sample(Point point, float nx, float ny, float nz, float bx, float by, float bz) {}
    /** {@code fixed} marks the samples that must be kept: both ends and wherever the centerline bends. */
    record Path(int firstAxis, int secondAxis, Sample[] samples, boolean[] fixed) {}
    /** How far a merged edge may stray from the full sampling, in blocks: under half a pixel up close. */
    private static final float SHAPE_TOLERANCE = .001f;
    /** The longest path has 64 segments. */
    static final int MAX_SAMPLES = 65;
    /** Per-sample radii of the carrier, the shoulder and the middle halo; reused, as only the render thread draws. */
    private static final ThreadLocal<float[][]> RADII = ThreadLocal.withInitial(() -> new float[3][MAX_SAMPLES]);
    /** The slopes a merged segment may still take, per width. */
    private static final ThreadLocal<float[][]> SLOPES = ThreadLocal.withInitial(() -> new float[2][3]);
    private static final List<List<List<Path>>> CACHE = buildCache();
    /** Fog vertex: x, y, z, centre weight, wave axis, layer. */
    private static final int FOG_STRIDE = 6;
    /** Half-widths stay inside the 10-voxel dense neck, the narrowest part of the shell. */
    private static final float[] FOG_HALF_WIDTH = {.29f, .20f, .11f};
    private static final float[] FOG_ALPHA = {.05f, .07f, .10f};
    /**
     * A mask's fog vertices, and the places along an axis their pulses are read at: many vertices share one, so each
     * pulse is computed once per place rather than once per vertex.
     */
    private record Fog(float[] vertices, int[] place, int[] placeAxis, float[] placeAlong, int[] faces) {}
    /** How far merged fog may stray from the full sampling, in alpha: under half a step of an 8-bit channel. */
    private static final float FOG_TOLERANCE = 1 / 512f;
    /** Fog vertices per segment of a face: two strips of four. */
    private static final int FOG_SEGMENT = 8;
    private static final List<List<Fog>> FOG = buildFog();
    /** Pulses at each fog place, reused per cable. */
    private static final ThreadLocal<float[]> PULSES = ThreadLocal.withInitial(() -> new float[256]);

    private CableBeamMesh() {}

    /** 0 near, 1 medium, 2 distant. Distant cables retain a carrier without animated detail. */
    public static int detail(double distanceSquared) {
        return distanceSquared > 48 * 48 ? 2 : distanceSquared > 24 * 24 ? 1 : 0;
    }

    public static void emit(int mask, int x, int y, int z, long tick, float partialTick,
            CableBeamStyle style, int detail, VertexSink sink) {
        emit(mask, x, y, z, tick, partialTick, style, detail, -1, false, sink);
    }

    public static void emitLayer(int mask, int x, int y, int z, long tick, float partialTick,
            CableBeamStyle style, int detail, boolean glow, VertexSink sink) {
        emitLayer(mask, x, y, z, tick, partialTick, style, detail, glow, false, sink);
    }

    /**
     * As {@link #emitLayer(int, int, int, int, long, float, CableBeamStyle, int, boolean, VertexSink)}; a
     * {@code volume} beam is a solid tube of light, its glow nested tubes around it, rather than two crossed ribbons.
     */
    public static void emitLayer(int mask, int x, int y, int z, long tick, float partialTick,
            CableBeamStyle style, int detail, boolean glow, boolean volume, VertexSink sink) {
        emit(mask, x, y, z, tick, partialTick, style, detail, glow ? 1 : 0, volume, sink);
    }

    private static void emit(int mask, int x, int y, int z, long tick, float partialTick,
            CableBeamStyle style, int detail, int layer, boolean volume, VertexSink sink) {
        if (mask < 0 || mask > 63 || detail < 0 || detail > 2) throw new IllegalArgumentException("Invalid beam geometry");
        float strength = style.strength(tick, partialTick);
        float breath = style.breath(tick, partialTick);
        boolean red = style.overloaded();
        float[][] radii = RADII.get();
        float[] core = radii[0], shoulder = radii[1], middle = radii[2];
        for (var path : CACHE.get(detail).get(mask)) {
            var samples = path.samples;
            // Each sample's wave once; the three widths it sets are all the pulses change along the beam.
            for (int i = 0; i < samples.length; i++) {
                float w = detail == 2 ? 0 : wave(path, samples[i].point, x, y, z, tick, partialTick, style);
                core[i] = Math.max(style.coreRadius() * breath, style.coreRadius() * .8f + style.pulseAmplitude() * w);
                shoulder[i] = style.coreRadius() * 1.6f + style.pulseAmplitude() * w;
                middle[i] = style.haloRadius() * .62f * breath + .35f * style.pulseAmplitude() * w;
            }
            for (int i = 0, j; i < samples.length - 1; i = j) {
                j = reach(path.fixed, i, samples.length - 1, radii);
                var a = samples[i];
                var b = samples[j];
                float ra = core[i];
                float rb = core[j];
                if (volume) {
                    tubes(sink, a, b, layer, detail, red, strength, (float) style.load(), ra, rb, shoulder[i],
                            shoulder[j], middle[i], middle[j], style.haloRadius() * breath);
                    continue;
                }
                for (int plane = 0; plane < 2; plane++) {
                    for (int sign : SIGNS) {
                        // Full-bright carrier, pale at its centre and saturated at its rim, so a thick core reads as light.
                        if (layer != 1) strip(sink, a, b, plane, 0, sign * ra, 0, sign * rb, false,
                                red ? 1 : .82f, red ? .86f : 1, red ? .78f : 1,
                                red ? 1 : .22f, red ? .30f : .86f, red ? .22f : 1,
                                Math.min(1, strength), Math.min(1, .85f * strength));
                        if (detail == 2 || layer == 0) continue;
                        // Soft additive shoulders; all respect shell depth.
                        float shoulderA = shoulder[i];
                        float shoulderB = shoulder[j];
                        float opacity = (.12f + .88f * (float) Math.min(style.load(), 1)) * strength;
                        glowStrip(sink, a, b, plane, sign * shoulderA, sign * shoulderB,
                                red ? 1 : .14f, red ? .045f : .85f, red ? .015f : 1, .70f * opacity);
                        glowStrip(sink, a, b, plane, sign * middle[i], sign * middle[j],
                                red ? 1 : .08f, red ? .025f : .7f, red ? .012f : 1, .70f * opacity);
                        glowStrip(sink, a, b, plane, sign * style.haloRadius() * breath,
                                sign * style.haloRadius() * breath,
                                red ? 1 : .15f, red ? .025f : .68f, red ? .012f : 1, .22f * opacity);
                    }
                }
            }
            if (volume && layer != 1) {
                // A tube is hollow: close it where the beam stops inside the block, as at a closed end.
                cap(sink, samples[0], core[0], red, strength);
                cap(sink, samples[samples.length - 1], core[samples.length - 1], red, strength);
            }
        }
        if (layer != 0) emitFog(mask, x, y, z, tick, partialTick, style, detail, strength, sink);
    }

    /** Sides of a volume beam's tubes. */
    private static final int TUBE_SIDES = 8;
    private static final float[] TUBE_COS = new float[TUBE_SIDES + 1];
    private static final float[] TUBE_SIN = new float[TUBE_SIDES + 1];

    static {
        for (int k = 0; k <= TUBE_SIDES; k++) {
            TUBE_COS[k] = (float) Math.cos(Math.TAU * k / TUBE_SIDES);
            TUBE_SIN[k] = (float) Math.sin(Math.TAU * k / TUBE_SIDES);
        }
    }

    /**
     * One segment of a volume beam: a pale full-bright tube that writes depth, and around it three additive tubes,
     * each fainter and wider, so the light thickens toward the middle of the beam however it is seen.
     */
    private static void tubes(VertexSink sink, Sample a, Sample b, int layer, int detail, boolean red,
            float strength, float load, float coreA, float coreB, float shoulderA, float shoulderB, float middleA,
            float middleB, float halo) {
        if (layer != 1) tube(sink, a, b, coreA, coreB, false, red ? 1 : .55f, red ? .62f : .95f, red ? .55f : 1,
                Math.min(1, strength));
        if (detail == 2 || layer == 0) return;
        // Every tube is seen through twice, front and back, so each carries half a ribbon's light.
        float opacity = (.12f + .88f * Math.min(load, 1)) * strength;
        tube(sink, a, b, shoulderA, shoulderB, true, red ? 1 : .14f, red ? .045f : .85f, red ? .015f : 1,
                .35f * opacity);
        tube(sink, a, b, middleA, middleB, true, red ? 1 : .08f, red ? .025f : .7f, red ? .012f : 1,
                .30f * opacity);
        tube(sink, a, b, halo, halo, true, red ? 1 : .15f, red ? .025f : .68f, red ? .012f : 1, .10f * opacity);
    }

    private static void tube(VertexSink sink, Sample a, Sample b, float radiusA, float radiusB, boolean glow,
            float r, float g, float blue, float alpha) {
        for (int k = 0; k < TUBE_SIDES; k++) {
            around(sink, a, radiusA, k, glow, r, g, blue, alpha);
            around(sink, b, radiusB, k, glow, r, g, blue, alpha);
            around(sink, b, radiusB, k + 1, glow, r, g, blue, alpha);
            around(sink, a, radiusA, k + 1, glow, r, g, blue, alpha);
        }
    }

    /** The core tube's end, as quads from its centre, unless the beam runs on through the block's face there. */
    private static void cap(VertexSink sink, Sample end, float radius, boolean red, float strength) {
        var p = end.point;
        if (p.x == 0 || p.x == 1 || p.y == 0 || p.y == 1 || p.z == 0 || p.z == 1) return;
        float r = red ? 1 : .55f, g = red ? .62f : .95f, b = red ? .55f : 1, alpha = Math.min(1, strength);
        for (int k = 0; k < TUBE_SIDES; k += 2) {
            around(sink, end, 0, k, false, r, g, b, alpha);
            around(sink, end, radius, k, false, r, g, b, alpha);
            around(sink, end, radius, k + 1, false, r, g, b, alpha);
            around(sink, end, radius, k + 2, false, r, g, b, alpha);
        }
    }

    private static void around(VertexSink out, Sample sample, float radius, int k, boolean glow,
            float r, float g, float b, float alpha) {
        var p = sample.point;
        float c = TUBE_COS[k] * radius, s = TUBE_SIN[k] * radius;
        out.vertex(glow, p.x + c * sample.nx + s * sample.bx, p.y + c * sample.ny + s * sample.by,
                p.z + c * sample.nz + s * sample.bz, r, g, b, Math.min(1, alpha));
    }

    /**
     * The furthest sample after {@code from} that one segment can reach: no fixed sample in between, and every width
     * in between within {@link #SHAPE_TOLERANCE} of the straight line between the ends. Samples are evenly spaced, so
     * each sample passed narrows the slopes the segment may take; one pass finds the end.
     */
    private static int reach(boolean[] fixed, int from, int last, float[][] radii) {
        float[] low = SLOPES.get()[0], high = SLOPES.get()[1];
        for (int r = 0; r < radii.length; r++) {
            low[r] = Float.NEGATIVE_INFINITY;
            high[r] = Float.POSITIVE_INFINITY;
        }
        int to = from + 1;
        while (to < last && !fixed[to]) {
            // Sample "to" is passed over: the segment's slope must keep it within tolerance.
            int steps = to - from;
            boolean fits = true;
            for (int r = 0; r < radii.length; r++) {
                float rise = radii[r][to] - radii[r][from];
                low[r] = Math.max(low[r], (rise - SHAPE_TOLERANCE) / steps);
                high[r] = Math.min(high[r], (rise + SHAPE_TOLERANCE) / steps);
                float slope = (radii[r][to + 1] - radii[r][from]) / (steps + 1);
                fits &= slope >= low[r] && slope <= high[r];
            }
            if (!fits) break;
            to++;
        }
        return to;
    }

    /** Light scattered inside the shell: nested soft-edged volumes that brighten where a pulse passes. */
    private static void emitFog(int mask, int x, int y, int z, long tick, float partialTick, CableBeamStyle style,
            int detail, float strength, VertexSink sink) {
        boolean red = style.overloaded();
        float fog = (.35f + .65f * (float) Math.min(style.load(), 1)) * strength / style.emission();
        int[] world = {x, y, z};
        var mesh = FOG.get(detail).get(mask);
        float[] pulses = PULSES.get();
        for (int p = 0; p < mesh.placeAxis.length; p++) {
            pulses[p] = detail == 2 ? .5f : style.wave(CableBeamStyle.phase(world[mesh.placeAxis[p]], mesh.placeAlong[p],
                    tick, partialTick));
        }
        float r = red ? 1 : .10f, g = red ? .10f : .72f, b = red ? .04f : 1;
        // A face runs along its arm in segments; where the pulses leave the brightness even, segments merge.
        var faces = mesh.faces;
        for (int f = 0; f < faces.length; f += 3) {
            int start = faces[f], segments = faces[f + 1];
            float tolerance = FOG_TOLERANCE / (FOG_ALPHA[faces[f + 2]] * fog * .9f);
            for (int from = 0, to; from < segments; from = to) {
                to = fogReach(mesh, pulses, start, from, segments, tolerance);
                int first = start + from * FOG_SEGMENT, last = start + (to - 1) * FOG_SEGMENT;
                for (int strip = 0; strip < FOG_SEGMENT; strip += 4) {
                    emitFogVertex(sink, mesh, pulses, first + strip, fog, r, g, b);
                    emitFogVertex(sink, mesh, pulses, last + strip + 1, fog, r, g, b);
                    emitFogVertex(sink, mesh, pulses, last + strip + 2, fog, r, g, b);
                    emitFogVertex(sink, mesh, pulses, first + strip + 3, fog, r, g, b);
                }
            }
        }
    }

    /**
     * The segment after the last one that one quad can cover from segment {@code from} of a face starting at vertex
     * {@code start}: every boundary passed over keeps its pulse within {@code tolerance} of the straight line between
     * the ends. Boundaries are evenly spaced.
     */
    private static int fogReach(Fog mesh, float[] pulses, int start, int from, int segments, float tolerance) {
        float base = pulses[mesh.place[start + from * FOG_SEGMENT]];
        float low = Float.NEGATIVE_INFINITY, high = Float.POSITIVE_INFINITY;
        int to = from + 1;
        while (to < segments) {
            int steps = to - from;
            float rise = pulses[mesh.place[start + to * FOG_SEGMENT]] - base;
            low = Math.max(low, (rise - tolerance) / steps);
            high = Math.min(high, (rise + tolerance) / steps);
            // The far end of segment "to", the next boundary.
            float slope = (pulses[mesh.place[start + to * FOG_SEGMENT + 1]] - base) / (steps + 1);
            if (slope < low || slope > high) break;
            to++;
        }
        return to;
    }

    private static void emitFogVertex(VertexSink sink, Fog mesh, float[] pulses, int vertex, float fog, float r, float g,
            float b) {
        int i = vertex * FOG_STRIDE;
        float[] vertices = mesh.vertices;
        float alpha = FOG_ALPHA[(int) vertices[i + 5]] * vertices[i + 3] * fog * (.55f + .9f * pulses[mesh.place[vertex]]);
        sink.vertex(true, vertices[i], vertices[i + 1], vertices[i + 2], r, g, b, Math.min(1, alpha));
    }

    private static void glowStrip(VertexSink out, Sample a, Sample b, int plane, float edgeA, float edgeB,
            float r, float g, float blue, float alpha) {
        strip(out, a, b, plane, 0, edgeA, 0, edgeB, true, r, g, blue, r, g, blue, alpha, 0);
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
            float b0, float b1, boolean glow, float r0, float g0, float blue0, float r1, float g1, float blue1,
            float alpha0, float alpha1) {
        vertex(out, a, plane, a0, glow, r0, g0, blue0, alpha0);
        vertex(out, b, plane, b0, glow, r0, g0, blue0, alpha0);
        vertex(out, b, plane, b1, glow, r1, g1, blue1, alpha1);
        vertex(out, a, plane, a1, glow, r1, g1, blue1, alpha1);
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

    private static List<List<Fog>> buildFog() {
        var levels = new ArrayList<List<Fog>>();
        for (int detail = 0; detail < 3; detail++) {
            var masks = new ArrayList<Fog>();
            for (int mask = 0; mask < 64; mask++) {
                var faces = new ArrayList<Integer>();
                masks.add(places(buildFog(mask, detail, faces), faces));
            }
            levels.add(List.copyOf(masks));
        }
        return List.copyOf(levels);
    }

    /** {@code faces} receives each face's first vertex, segment count and layer. */
    private static float[] buildFog(int mask, int detail, List<Integer> faces) {
        var out = new ArrayList<Float>();
        int main = mask == 0 ? 0 : Integer.numberOfTrailingZeros(mask) / 2;
        int segments = new int[] {4, 2, 1}[detail];
        for (int layer = 0; layer < (detail == 2 ? 2 : FOG_HALF_WIDTH.length); layer++) {
            float lo = .5f - FOG_HALF_WIDTH[layer], hi = .5f + FOG_HALF_WIDTH[layer];
            for (int axis = 0; axis < 3; axis++) {
                for (int side = 0; side < 2; side++) {
                    float at = side == 0 ? hi : lo;
                    if ((mask & 1 << (2 * axis + side)) == 0) {
                        // Centre volume face, aligned with the main arm so its strips continue into that arm.
                        int u = axis != main ? main : (main + 1) % 3;
                        faces.addAll(List.of(out.size() / FOG_STRIDE, 1, layer));
                        fogFace(out, axis, at, u, lo, hi, 3 - axis - u, lo, hi, 1, main, layer);
                        continue;
                    }
                    float from = side == 0 ? hi : 0, to = side == 0 ? 1 : lo;
                    for (int normal = 0; normal < 3; normal++) {
                        if (normal == axis) continue;
                        for (float plane : new float[] {lo, hi}) {
                            faces.addAll(List.of(out.size() / FOG_STRIDE, segments, layer));
                            fogFace(out, normal, plane, axis, from, to, 3 - axis - normal, lo, hi, segments, axis, layer);
                        }
                    }
                }
            }
        }
        var result = new float[out.size()];
        for (int i = 0; i < result.length; i++) result[i] = out.get(i);
        return result;
    }

    /** Groups {@code vertices} by the place along an axis their pulse is read at; {@code faces} as buildFog lists them. */
    private static Fog places(float[] vertices, List<Integer> faces) {
        int count = vertices.length / FOG_STRIDE;
        var place = new int[count];
        var keys = new java.util.LinkedHashMap<Long, Integer>();
        for (int v = 0; v < count; v++) {
            int i = v * FOG_STRIDE;
            int axis = (int) vertices[i + 4];
            long key = (long) axis << 32 | Float.floatToIntBits(vertices[i + axis]) & 0xffffffffL;
            place[v] = keys.computeIfAbsent(key, unused -> keys.size());
        }
        if (keys.size() > 256) throw new IllegalStateException("Too many fog places");
        var placeAxis = new int[keys.size()];
        var placeAlong = new float[keys.size()];
        keys.forEach((key, index) -> {
            placeAxis[index] = (int) (key >>> 32);
            placeAlong[index] = Float.intBitsToFloat((int) (long) key);
        });
        return new Fog(vertices, place, placeAxis, placeAlong, faces.stream().mapToInt(Integer::intValue).toArray());
    }

    /** One face split lengthwise into segments and crosswise into two strips, transparent at both long edges. */
    private static void fogFace(List<Float> out, int normal, float at, int u, float u0, float u1, int v, float v0,
            float v1, int segments, int waveAxis, int layer) {
        float vm = (v0 + v1) / 2;
        for (int i = 0; i < segments; i++) {
            float ua = u0 + (u1 - u0) * i / segments, ub = u0 + (u1 - u0) * (i + 1) / segments;
            float[][] strips = {{v0, vm, 0, 1}, {vm, v1, 1, 0}};
            for (var strip : strips) {
                fogVertex(out, normal, at, u, ua, v, strip[0], strip[2], waveAxis, layer);
                fogVertex(out, normal, at, u, ub, v, strip[0], strip[2], waveAxis, layer);
                fogVertex(out, normal, at, u, ub, v, strip[1], strip[3], waveAxis, layer);
                fogVertex(out, normal, at, u, ua, v, strip[1], strip[3], waveAxis, layer);
            }
        }
    }

    private static void fogVertex(List<Float> out, int normal, float at, int u, float uValue, int v, float vValue,
            float weight, int waveAxis, int layer) {
        float[] p = new float[3];
        p[normal] = at; p[u] = uValue; p[v] = vValue;
        out.add(p[0]); out.add(p[1]); out.add(p[2]);
        out.add(weight); out.add((float) waveAxis); out.add((float) layer);
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
            // A fixed reference normal across each planar elbow avoids twisting.
            int normal = first == second ? (first + 1) % 3 : 3 - first - second;
            float nx = normal == 0 ? 1 : 0, ny = normal == 1 ? 1 : 0, nz = normal == 2 ? 1 : 0;
            samples[i] = new Sample(points.get(i), nx, ny, nz, ty * nz - tz * ny,
                    tz * nx - tx * nz, tx * ny - ty * nx);
        }
        if (samples.length > MAX_SAMPLES) throw new IllegalStateException("Beam path too long");
        // Keep both ends and every sample where the centerline or its frame turns.
        var fixed = new boolean[samples.length];
        fixed[0] = fixed[samples.length - 1] = true;
        for (int i = 1; i < samples.length - 1; i++) {
            var before = samples[i - 1];
            var here = samples[i];
            var after = samples[i + 1];
            fixed[i] = !sameFrame(before, here) || !sameFrame(here, after)
                    || !collinear(before.point, here.point, after.point);
        }
        return new Path(first, second, samples, fixed);
    }

    private static boolean sameFrame(Sample a, Sample b) {
        return a.nx == b.nx && a.ny == b.ny && a.nz == b.nz && a.bx == b.bx && a.by == b.by && a.bz == b.bz;
    }

    private static boolean collinear(Point a, Point b, Point c) {
        float ux = b.x - a.x, uy = b.y - a.y, uz = b.z - a.z;
        float vx = c.x - b.x, vy = c.y - b.y, vz = c.z - b.z;
        float cx = uy * vz - uz * vy, cy = uz * vx - ux * vz, cz = ux * vy - uy * vx;
        return cx * cx + cy * cy + cz * cz < 1e-12f;
    }
}
