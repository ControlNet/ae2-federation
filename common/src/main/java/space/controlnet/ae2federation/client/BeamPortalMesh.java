package space.controlnet.ae2federation.client;

import net.minecraft.core.Direction;

/** J2 / Portal Ring: local coordinates about the face centre, with z measured from the body's back. */
public final class BeamPortalMesh {
    @FunctionalInterface
    public interface Sink {
        void vertex(float x, float y, float z, float r, float g, float b, float alpha);
    }

    private static final int[] SEGMENTS = {96, 48, 24};
    private static final float[][] MAIN = {{6.4f, 6.7f, .10f}, {6.7f, 7, .32f}, {7, 8, .98f},
            {8, 8.3f, .32f}, {8.3f, 8.6f, .10f}};
    private static final float[][][] SPILL = {
            {{5.5f, 6.4f, .025f}, {6.4f, 7, .045f}, {7, 8, .06f}, {8, 8.6f, .045f},
                    {8.6f, 9.2f, .025f}, {9.2f, 9.8f, .012f}},
            {{5.5f, 7, .035f}, {7, 8.6f, .0525f}, {8.6f, 9.8f, .0185f}},
            {{5.5f, 9.8f, .035f}}};
    private static final float[][] CIRCLE = new float[3][];
    private static final float[][] DASH = new float[3][];
    private static final float[] STREAK = new float[24];

    static {
        for (int detail = 0; detail < 3; detail++) {
            CIRCLE[detail] = angles(1, SEGMENTS[detail], 1, 0);
            DASH[detail] = angles(16, 4 >> detail, .60, 0);
        }
        float[] angles = angles(6, 1, .025 * 6 / Math.TAU, .2);
        System.arraycopy(angles, 0, STREAK, 0, angles.length);
    }

    private BeamPortalMesh() {}

    private static float[] angles(int count, int segments, double duty, double offset) {
        float[] out = new float[count * segments * 4];
        for (int i = 0; i < count; i++) {
            for (int j = 0; j < segments; j++) {
                int k = (i * segments + j) * 4;
                for (int end = 0; end < 2; end++) {
                    double angle = offset + Math.TAU / count * (i + duty * (j + end) / segments);
                    out[k + end * 2] = (float) Math.cos(angle);
                    out[k + end * 2 + 1] = (float) Math.sin(angle);
                }
            }
        }
        return out;
    }

    public static double phase(long tick, float partialTick) {
        return ((Math.floorMod(tick, 64) + partialTick) / 64.0) % 1;
    }

    public static void emit(boolean linked, int side, int detail, long tick, float partialTick,
            CableBeamStyle style, boolean glow, Sink sink) {
        if (!linked || side != 2) return;
        double phase = phase(tick, partialTick);
        float strength = (float) ((.82 + .12 * Math.min(style.load(), 1)) * (1 + .04 * Math.sin(Math.TAU * phase)));
        boolean red = style.overloaded();
        float r = red ? 1 : .14f, g = red ? .045f : .85f, b = red ? .015f : 1;
        var circle = CIRCLE[detail];
        if (!glow) {
            band(circle, 0, circle.length, 0, 4.8f, 14.01f / 16, 0,
                    red ? .028f : .008f, red ? .006f : .018f, red ? .004f : .028f, .68f, sink);
            return;
        }
        for (var band : SPILL[detail]) {
            band(circle, 0, circle.length, band[0], band[1], 18.999f / 16, 0, r, g, b, band[2] * strength, sink);
        }
        for (var band : MAIN) {
            boolean core = band[0] == 7;
            band(circle, 0, circle.length, band[0], band[1], 19f / 16, 0,
                    core && !red ? .92f : r, core && !red ? 1 : g, b, band[2] * strength, sink);
        }
        // Keep the line's 96-fold symmetry at every distance so its slow rotation always loops.
        band(CIRCLE[0], 0, CIRCLE[0].length, 8.94f, 9.06f, 19f / 16, Math.TAU * phase / 96,
                r, g, b, .65f * strength, sink);
        band(DASH[detail], 0, DASH[detail].length, 9.35f, 9.5f, 19f / 16, -Math.TAU * phase / 16,
                r, g, b, .48f * strength, sink);
        for (int i = 0; i < 6; i++) {
            double travel = (phase + i / 6.0) % 1;
            float radius = (float) (4.6 - 2.8 * travel);
            float fade = (float) Math.sin(Math.PI * travel);
            band(STREAK, i * 4, i * 4 + 4, radius - .55f, radius, 14.02f / 16, 0,
                    r, g, b, .07f * fade * fade * strength, sink);
        }
    }

    private static void band(float[] angles, int start, int end, float inner, float outer, float z, double rotation,
            float r, float g, float b, float alpha, Sink sink) {
        float c = (float) Math.cos(rotation), s = (float) Math.sin(rotation);
        for (int i = start; i < end; i += 4) {
            float ax = angles[i] * c - angles[i + 1] * s, ay = angles[i] * s + angles[i + 1] * c;
            float bx = angles[i + 2] * c - angles[i + 3] * s, by = angles[i + 2] * s + angles[i + 3] * c;
            sink.vertex(inner / 16 * ax, inner / 16 * ay, z, r, g, b, alpha);
            sink.vertex(outer / 16 * ax, outer / 16 * ay, z, r, g, b, alpha);
            sink.vertex(outer / 16 * bx, outer / 16 * by, z, r, g, b, alpha);
            sink.vertex(inner / 16 * bx, inner / 16 * by, z, r, g, b, alpha);
        }
    }

    /** The same rotations as the artist's south-facing model, about the assembled body's centre. */
    public static void oriented(Direction facing, Sink sink, float x, float y, float z,
            float r, float g, float b, float alpha) {
        switch (facing) {
            case SOUTH -> sink.vertex(1 + x, 1 + y, z, r, g, b, alpha);
            case NORTH -> sink.vertex(1 - x, 1 + y, 1 - z, r, g, b, alpha);
            case EAST -> sink.vertex(z, 1 + y, 1 - x, r, g, b, alpha);
            case WEST -> sink.vertex(1 - z, 1 + y, 1 + x, r, g, b, alpha);
            case UP -> sink.vertex(1 + x, z, 1 - y, r, g, b, alpha);
            case DOWN -> sink.vertex(1 + x, 1 - z, 1 + y, r, g, b, alpha);
        }
    }
}
