package space.controlnet.ae2federation.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.Direction;
import space.controlnet.ae2federation.bundle.BundleBeam;

/**
 * The one beam down a 2x2 bundle (Blender study R, {@code tools/blender/plasma_beam.py}): a white-hot core inside three
 * lobed sheaths that twist round it and roll along the run, and a halo. It follows an ordinary beam's centerlines,
 * stretched over the bundle's box in blocks, so runs, branches and turns meet as theirs do; its pulses and lobes take
 * their phase from world coordinates, so they run on from layer to layer and through two-layer joints.
 *
 * <p>The core writes depth and must pass through the artist's rings, whose bore has a radius of 10/32 block. The
 * sheaths and halo are additive, and fade toward their outlines as they turn from the eye ({@link #fade}), so the
 * nested tubes read as one soft volume rather than bands; the renderer applies that per vertex, by view, from the
 * normals this mesh carries.
 */
public final class BundleBeamMesh {
    @FunctionalInterface
    public interface Sink {
        void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float nx, float ny,
                float nz);
    }

    private static final int CORE_SIDES = 8;
    private static final int HALO_SIDES = 8;
    /** Sides round each sheath and centerline samples per block, near, medium and distant; distant has no sheaths. */
    private static final int[] SHEATH_SIDES = {16, 12, 0};
    private static final int[] SAMPLES_PER_BLOCK = {16, 8, 2};
    private static final int MAX_SIDES = 16;
    /** Inside out: lobes round the tube, how fast they roll (whole turns per beam cycle), and how deep. */
    private static final int[] LOBES = {3, 4, 5};
    private static final int[] ROLL = {1, -1, 2};
    private static final float[] DEPTH = {.22f, .16f, .10f};
    private static final float[] SHEATH_ALPHA = {.40f, .32f, .22f};
    private static final float HALO_ALPHA = .10f;
    /** Paths by detail, then box length (1 or 2 layers), then mask. */
    private static final List<List<List<List<CableBeamMesh.Path>>>> PATHS = buildPaths();
    private static final ThreadLocal<float[]> RADII =
            ThreadLocal.withInitial(() -> new float[CableBeamMesh.MAX_SAMPLES * (MAX_SIDES + 1)]);
    private static final ThreadLocal<float[]> POINTS = ThreadLocal.withInitial(() -> new float[CableBeamMesh.MAX_SAMPLES * 3]);

    private BundleBeamMesh() {}

    /**
     * How much of a soft layer's light a vertex keeps, seen along the unit vector {@code v} from it to the eye:
     * twice as much face-on, none edge-on, so a tube gives about the light of an even one in all.
     */
    public static float fade(float nx, float ny, float nz, float vx, float vy, float vz) {
        float facing = nx * vx + ny * vy + nz * vz;
        return 2 * facing * facing;
    }

    /**
     * The glow (sheaths and halo) or the core of {@code beam}, drawn by the cell at {@code x, y, z}, in blocks from
     * that cell's low corner.
     */
    public static void emit(BundleBeam beam, int x, int y, int z, long tick, float partialTick, CableBeamStyle style,
            int detail, boolean glow, Sink sink) {
        if (detail < 0 || detail > 2 || beam.length() < 1 || beam.length() > 2) {
            throw new IllegalArgumentException("Invalid bundle beam");
        }
        float u = (float) Math.min(style.load(), 1);
        float strength = style.strength(tick, partialTick);
        float breath = style.breath(tick, partialTick);
        boolean red = style.overloaded();
        float core = (.10f + .05f * u) * breath;
        int[] world = {x, y, z};
        float[] extent = {beam.extent(Direction.Axis.X), beam.extent(Direction.Axis.Y), beam.extent(Direction.Axis.Z)};
        double time = (Math.floorMod(tick, 64) + partialTick) / 64.0;
        for (var path : paths(beam, detail)) {
            var samples = path.samples();
            float[] points = POINTS.get();
            for (int i = 0; i < samples.length; i++) {
                var p = samples[i].point();
                points[i * 3] = p.x() * extent[0];
                points[i * 3 + 1] = p.y() * extent[1];
                points[i * 3 + 2] = p.z() * extent[2];
            }
            // A straight arm's core and halo are even tubes: its two ends are enough.
            boolean straight = path.firstAxis() == path.secondAxis();
            if (!glow) {
                float r = red ? 1 : .96f, g = red ? .9f : 1, b = red ? .82f : 1, alpha = Math.min(1, strength);
                even(sink, samples, points, straight, core, CORE_SIDES, r, g, b, alpha);
                cap(sink, samples, points, 0, core, r, g, b, alpha);
                cap(sink, samples, points, samples.length - 1, core, r, g, b, alpha);
                continue;
            }
            // Even idle the sheaths keep a third of their light, so an idle bundle still reads as one broad beam;
            // past 1.15 they stop brightening, so full load keeps its colour rather than clipping to white.
            float opacity = (.35f + .65f * u) * Math.min(Math.max(strength, .8f), 1.15f);
            int sides = SHEATH_SIDES[detail];
            for (int sheath = 0; sheath < (sides == 0 ? 0 : LOBES.length); sheath++) {
                float base = switch (sheath) {
                    case 0 -> core * 1.6f;
                    case 1 -> .22f + .04f * u;
                    default -> .31f + .02f * u;
                };
                float[] radii = RADII.get();
                for (int i = 0; i < samples.length; i++) {
                    var sample = samples[i];
                    for (int k = 0; k <= sides; k++) {
                        double angle = Math.TAU * k / sides;
                        float shape = shape(sheath, angle, path, sample, points, i, world, time, style, u);
                        radii[i * (MAX_SIDES + 1) + k] = base * shape;
                    }
                }
                float r, g, b;
                switch (sheath) {
                    // An alarm must read red: no pale sheath that would add up to white.
                    case 0 -> { r = red ? 1 : .55f; g = red ? .25f : .95f; b = red ? .18f : 1; }
                    case 1 -> { r = red ? 1 : .14f; g = red ? .045f : .85f; b = red ? .015f : 1; }
                    default -> { r = red ? 1 : .08f; g = red ? .025f : .7f; b = red ? .012f : 1; }
                }
                lobed(sink, samples, points, radii, sides, r, g, b, Math.min(1, SHEATH_ALPHA[sheath] * opacity));
            }
            even(sink, samples, points, straight, .44f * breath, HALO_SIDES, red ? 1 : .15f, red ? .025f : .68f,
                    red ? .012f : 1, Math.min(1, HALO_ALPHA * opacity));
        }
    }

    /**
     * How far a sheath reaches at {@code angle} round sample {@code i}, as a share of its base radius: lobes that
     * repeat every 2 blocks, as the beam clock does, swelled where a pulse passes. Across an elbow the value read
     * along the first arm's axis fades into the second's, as an ordinary beam's pulses do.
     */
    private static float shape(int sheath, double angle, CableBeamMesh.Path path, CableBeamMesh.Sample sample,
            float[] points, int i, int[] world, double time, CableBeamStyle style, float u) {
        float first = along(sheath, angle, path.firstAxis(), points, i, world, time, style, u);
        if (path.firstAxis() == path.secondAxis()) return first;
        float second = along(sheath, angle, path.secondAxis(), points, i, world, time, style, u);
        return first + (second - first) * sample.point().mix();
    }

    private static float along(int sheath, double angle, int axis, float[] points, int i, int[] world, double time,
            CableBeamStyle style, float u) {
        double position = (Math.floorMod(world[axis], 2) + points[i * 3 + axis]) / 2.0;
        float wave = style.wave(position - time);
        return 1 + DEPTH[sheath] * (float) Math.sin(LOBES[sheath] * angle + Math.TAU * (position - ROLL[sheath] * time))
                + .18f * wave * u;
    }

    private static void even(Sink sink, CableBeamMesh.Sample[] samples, float[] points, boolean straight, float radius,
            int sides, float r, float g, float b, float alpha) {
        int step = straight ? samples.length - 1 : 1;
        for (int i = 0; i + step < samples.length; i += step) {
            for (int k = 0; k < sides; k++) {
                around(sink, samples[i], points, i, radius, k, sides, r, g, b, alpha);
                around(sink, samples[i + step], points, i + step, radius, k, sides, r, g, b, alpha);
                around(sink, samples[i + step], points, i + step, radius, k + 1, sides, r, g, b, alpha);
                around(sink, samples[i], points, i, radius, k + 1, sides, r, g, b, alpha);
            }
        }
    }

    private static void lobed(Sink sink, CableBeamMesh.Sample[] samples, float[] points, float[] radii, int sides,
            float r, float g, float b, float alpha) {
        for (int i = 0; i + 1 < samples.length; i++) {
            for (int k = 0; k < sides; k++) {
                int a = i * (MAX_SIDES + 1), c = (i + 1) * (MAX_SIDES + 1);
                around(sink, samples[i], points, i, radii[a + k], k, sides, r, g, b, alpha);
                around(sink, samples[i + 1], points, i + 1, radii[c + k], k, sides, r, g, b, alpha);
                around(sink, samples[i + 1], points, i + 1, radii[c + k + 1], k + 1, sides, r, g, b, alpha);
                around(sink, samples[i], points, i, radii[a + k + 1], k + 1, sides, r, g, b, alpha);
            }
        }
    }

    /** The core's end, as quads from its centre, unless the beam runs on through the box's face there. */
    private static void cap(Sink sink, CableBeamMesh.Sample[] samples, float[] points, int i, float radius, float r,
            float g, float b, float alpha) {
        var p = samples[i].point();
        if (p.x() == 0 || p.x() == 1 || p.y() == 0 || p.y() == 1 || p.z() == 0 || p.z() == 1) return;
        for (int k = 0; k < CORE_SIDES; k += 2) {
            around(sink, samples[i], points, i, 0, k, CORE_SIDES, r, g, b, alpha);
            around(sink, samples[i], points, i, radius, k, CORE_SIDES, r, g, b, alpha);
            around(sink, samples[i], points, i, radius, k + 1, CORE_SIDES, r, g, b, alpha);
            around(sink, samples[i], points, i, radius, k + 2, CORE_SIDES, r, g, b, alpha);
        }
    }

    private static void around(Sink sink, CableBeamMesh.Sample sample, float[] points, int i, float radius, int k,
            int sides, float r, float g, float b, float alpha) {
        double angle = Math.TAU * k / sides;
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
        float nx = c * sample.nx() + s * sample.bx(), ny = c * sample.ny() + s * sample.by(),
                nz = c * sample.nz() + s * sample.bz();
        sink.vertex(points[i * 3] + radius * nx, points[i * 3 + 1] + radius * ny, points[i * 3 + 2] + radius * nz,
                r, g, b, alpha, nx, ny, nz);
    }

    private static List<CableBeamMesh.Path> paths(BundleBeam beam, int detail) {
        int mask = beam.mask();
        if (mask != 0) return PATHS.get(detail).get(beam.length() - 1).get(mask);
        // A bundle joined to nothing shows a short stub along its own axis, as a lone cable does.
        int axis = beam.axis().ordinal();
        var through = PATHS.get(detail).get(beam.length() - 1).get(3 << (2 * axis)).get(0);
        var stub = Arrays.stream(through.samples())
                .filter(sample -> component(sample.point(), axis) >= .375f && component(sample.point(), axis) <= .625f)
                .toArray(CableBeamMesh.Sample[]::new);
        if (stub.length < 2) return List.of();
        var fixed = new boolean[stub.length];
        fixed[0] = fixed[stub.length - 1] = true;
        return List.of(new CableBeamMesh.Path(axis, axis, stub, fixed));
    }

    private static float component(CableBeamMesh.Point point, int axis) {
        return axis == 0 ? point.x() : axis == 1 ? point.y() : point.z();
    }

    private static List<List<List<List<CableBeamMesh.Path>>>> buildPaths() {
        var details = new ArrayList<List<List<List<CableBeamMesh.Path>>>>();
        for (int perBlock : SAMPLES_PER_BLOCK) {
            var lengths = new ArrayList<List<List<CableBeamMesh.Path>>>();
            for (int length = 1; length <= 2; length++) {
                var masks = new ArrayList<List<CableBeamMesh.Path>>();
                for (int mask = 0; mask < 64; mask++) masks.add(CableBeamMesh.build(mask, perBlock * length));
                lengths.add(List.copyOf(masks));
            }
            details.add(List.copyOf(lengths));
        }
        return List.copyOf(details);
    }
}
