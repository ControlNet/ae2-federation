package space.controlnet.ae2federation.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * J2 / Portal Ring: local coordinates about the face centre, with z measured from the body's back. A 2x2 front draws
 * one ring, a 3x3 front two, each over the artist's ring-shaped emitter parts.
 */
public final class BeamPortalMesh {
    @FunctionalInterface
    public interface Sink {
        void vertex(float x, float y, float z, float r, float g, float b, float alpha);
    }

    private static final int[] SEGMENTS = {96, 48, 24};
    /** Each ring's bright core, inner and outer edge in pixels, for a 2x2 and a 3x3 front; all one pixel wide. */
    private static final float[][][] CORES = {{{7, 8}}, {{11.25f, 12.25f}, {15.25f, 16.25f}}};
    // Bands as {core edge, offset, core edge, offset, alpha}, edge 0 being the core's inner edge and 1 its outer.
    private static final double[][] MAIN = {{0, -.6, 0, -.3, .10}, {0, -.3, 0, 0, .32}, {0, 0, 1, 0, .98},
            {1, 0, 1, .3, .32}, {1, .3, 1, .6, .10}};
    private static final double[][][] SPILL = {
            {{0, -1.5, 0, -.6, .025}, {0, -.6, 0, 0, .045}, {0, 0, 1, 0, .06}, {1, 0, 1, .6, .045},
                    {1, .6, 1, 1.2, .025}, {1, 1.2, 1, 1.8, .012}},
            {{0, -1.5, 0, 0, .035}, {0, 0, 1, .6, .0525}, {1, .6, 1, 1.8, .0185}},
            {{0, -1.5, 1, 1.8, .035}}};
    private static final float[][] CIRCLE = new float[3][];
    private static final float[] STREAK = new float[24];

    static {
        for (int detail = 0; detail < 3; detail++) CIRCLE[detail] = angles(1, SEGMENTS[detail], 1, 0);
        float[] angles = angles(6, 1, .025 * 6 / Math.TAU, .2);
        System.arraycopy(angles, 0, STREAK, 0, angles.length);
    }

    private static final Matrix4f[][] ORIENTATION = new Matrix4f[2][6];

    static {
        for (int side = 2; side <= 3; side++) for (var facing : Direction.values()) {
            var m = new Matrix4f().zero().m33(1);
            oriented(facing, side, (x, y, z, r, g, b, a) -> m.m30(x).m31(y).m32(z), 0, 0, 0, 0, 0, 0, 0);
            oriented(facing, side, (x, y, z, r, g, b, a) -> m.m00(x - m.m30()).m01(y - m.m31()).m02(z - m.m32()),
                    1, 0, 0, 0, 0, 0, 0);
            oriented(facing, side, (x, y, z, r, g, b, a) -> m.m10(x - m.m30()).m11(y - m.m31()).m12(z - m.m32()),
                    0, 1, 0, 0, 0, 0, 0);
            oriented(facing, side, (x, y, z, r, g, b, a) -> m.m20(x - m.m30()).m21(y - m.m31()).m22(z - m.m32()),
                    0, 0, 1, 0, 0, 0, 0);
            ORIENTATION[side - 2][facing.ordinal()] = m;
        }
    }

    private BeamPortalMesh() {}

    public static boolean drawn(int side) {
        return side == 2 || side == 3;
    }

    public static Matrix4f transform(Matrix4f pose, Direction facing, int side, Matrix4f destination) {
        return pose.mul(ORIENTATION[side - 2][facing.ordinal()], destination);
    }

    public static AABB bounds(BlockPos origin, Direction facing, int side) {
        var cores = CORES[side - 2];
        double centre = side / 2.0;
        double radius = (float) (cores[cores.length - 1][1] + SPILL[0][SPILL[0].length - 1][3]) / 16;
        double near = 14.02f / 16, far = 19f / 16;
        double low = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? near : 1 - far;
        double high = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? far : 1 - near;
        var axis = facing.getAxis();
        return new AABB(origin.getX() + (axis == Direction.Axis.X ? low : centre - radius),
                origin.getY() + (axis == Direction.Axis.Y ? low : centre - radius),
                origin.getZ() + (axis == Direction.Axis.Z ? low : centre - radius),
                origin.getX() + (axis == Direction.Axis.X ? high : centre + radius),
                origin.getY() + (axis == Direction.Axis.Y ? high : centre + radius),
                origin.getZ() + (axis == Direction.Axis.Z ? high : centre + radius));
    }

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
            CableBeamStyle style, Sink sink) {
        if (!linked || !drawn(side)) return;
        var cores = CORES[side - 2];
        // The streaks cross the emitter's centre plate, which grows with the body.
        double scale = side / 2.0;
        double phase = phase(tick, partialTick);
        float strength = (float) ((.82 + .12 * Math.min(style.load(), 1)) * (1 + .04 * Math.sin(Math.TAU * phase)));
        boolean red = style.overloaded();
        float r = red ? 1 : .14f, g = red ? .045f : .85f, b = red ? .015f : 1;
        var circle = CIRCLE[detail];
        // The faint spill shows no facets at 48 segments, even up close.
        var soft = CIRCLE[Math.max(detail, 1)];
        for (var core : cores) for (var band : SPILL[detail]) {
            band(soft, 0, soft.length, radius(core, band, 0), radius(core, band, 2), 18.999f / 16, 0,
                    r, g, b, (float) band[4] * strength, sink);
        }
        for (var core : cores) for (var band : MAIN) {
            boolean bright = band[0] != band[2];
            band(circle, 0, circle.length, radius(core, band, 0), radius(core, band, 2), 19f / 16, 0,
                    bright && !red ? .92f : r, bright && !red ? 1 : g, b, (float) band[4] * strength, sink);
        }
        for (int i = 0; i < 6; i++) {
            double travel = (phase + i / 6.0) % 1;
            float radius = (float) (scale * (4.6 - 2.8 * travel));
            float fade = (float) Math.sin(Math.PI * travel);
            band(STREAK, i * 4, i * 4 + 4, radius - (float) (.55 * scale), radius, 14.02f / 16, 0,
                    r, g, b, .07f * fade * fade * strength, sink);
        }
    }

    private static float radius(float[] core, double[] band, int at) {
        return (float) (core[(int) band[at]] + band[at + 1]);
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
    public static void oriented(Direction facing, int side, Sink sink, float x, float y, float z,
            float r, float g, float b, float alpha) {
        float c = side / 2f;
        switch (facing) {
            case SOUTH -> sink.vertex(c + x, c + y, z, r, g, b, alpha);
            case NORTH -> sink.vertex(c - x, c + y, 1 - z, r, g, b, alpha);
            case EAST -> sink.vertex(z, c + y, c - x, r, g, b, alpha);
            case WEST -> sink.vertex(1 - z, c + y, c + x, r, g, b, alpha);
            case UP -> sink.vertex(c + x, z, c - y, r, g, b, alpha);
            case DOWN -> sink.vertex(c + x, 1 - z, c + y, r, g, b, alpha);
        }
    }
}
