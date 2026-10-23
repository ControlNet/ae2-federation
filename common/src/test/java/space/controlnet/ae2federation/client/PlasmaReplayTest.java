package space.controlnet.ae2federation.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import net.minecraft.core.Direction;
import net.minecraft.util.FastColor;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.bundle.BundleBeam;
import space.controlnet.ae2federation.router.CableVisualConnections;

/**
 * The beam replay writes the buffer bytes it wrote before the frame cache kept colours and squared normals: the
 * reference below is the renderer as it was (commit 1f721f5a), reading the mesh's own vertices.
 */
final class PlasmaReplayTest {
    private static final int VERTEX = 32;
    /** The block format's last byte is padding, which no writer touches. */
    private static final int WRITTEN = 31;

    private static int mask(Direction... sides) {
        int mask = 0;
        for (var side : sides) mask |= 1 << List.of(CableVisualConnections.DIRECTIONS).indexOf(side);
        return mask;
    }

    private static final List<BundleBeam> BEAMS = List.of(
            new BundleBeam(Direction.Axis.X, mask(Direction.EAST, Direction.WEST), 1, 2),
            new BundleBeam(Direction.Axis.X, mask(Direction.EAST, Direction.WEST), 1, 3),
            new BundleBeam(Direction.Axis.X, mask(Direction.EAST, Direction.WEST, Direction.UP), 2, 2),
            new BundleBeam(Direction.Axis.X, mask(Direction.EAST, Direction.WEST, Direction.UP), 3, 3),
            new BundleBeam(Direction.Axis.Z, mask(Direction.NORTH, Direction.EAST), 3, 3),
            new BundleBeam(Direction.Axis.Y, 0, 1, 2));

    /** The mesh's own vertices: x, y, z, red, green, blue, alpha, normal. */
    private static List<float[]> raw(int trial, BundleBeam beam, int x, CableBeamStyle style, int detail,
            boolean glow) {
        var out = new ArrayList<float[]>();
        BundleBeamMesh.Sink sink = (px, py, pz, r, g, b, a, nx, ny, nz) -> out.add(
                new float[] {px, py, pz, r, g, b, a, nx, ny, nz});
        if (trial % 4 == 3) BundleBeamMesh.emitCell(trial % 64, x, 64, 3, 1000 + trial, .3f, style, detail, glow, sink);
        else BundleBeamMesh.emit(beam, x, 64, 3, 1000 + trial, .3f, style, detail, glow, sink);
        return out;
    }

    private static void referenceVertex(VertexConsumer out, float x, float y, float z, float alpha, float[] v,
            Vector3f normal) {
        int color = FastColor.ARGB32.color((int) (alpha * 255), (int) (v[3] * 255), (int) (v[4] * 255),
                (int) (v[5] * 255));
        out.addVertex(x, y, z, color, .5f, .5f, 0xA0000, 0xF000F0, normal.x(), normal.y(), normal.z());
    }

    /** The renderer's moved path as it was. */
    private static void referenceMoved(List<float[]> vertices, boolean fade, Matrix4f pose, Matrix4f modelView,
            boolean orthographic, Vector3f normal, VertexConsumer out) {
        float ox = pose.m30(), oy = pose.m31(), oz = pose.m32();
        float ex = modelView.m02(), ey = modelView.m12(), ez = modelView.m22();
        for (float[] v : vertices) {
            float x = v[0] + ox, y = v[1] + oy, z = v[2] + oz;
            float alpha = v[6];
            if (fade) {
                float nx = v[7], ny = v[8], nz = v[9];
                if (orthographic) {
                    float facing = (nx * ex + ny * ey + nz * ez) / (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                    alpha = Math.min(1, alpha * 2 * facing * facing);
                } else {
                    float facing = nx * x + ny * y + nz * z;
                    alpha = Math.min(1, alpha * 2 * facing * facing
                            / ((nx * nx + ny * ny + nz * nz) * (x * x + y * y + z * z)));
                }
            }
            referenceVertex(out, x, y, z, alpha, v, normal);
        }
    }

    /** The renderer's eye-space path as it was. */
    private static void referenceInEye(List<float[]> vertices, boolean fade, Matrix4f pose, Matrix4f modelView,
            boolean orthographic, Vector3f normal, VertexConsumer out) {
        var view = new Matrix4f(modelView).mul(pose);
        var turn = view.normal(new Matrix3f());
        var position = new Vector3f();
        var eye = new Vector3f();
        var facing = new Vector3f();
        for (float[] v : vertices) {
            float alpha = !fade ? v[6] : PlasmaFade.inEye(view, turn, orthographic, v[0], v[1], v[2], v[7], v[8], v[9],
                    v[6], eye, facing);
            pose.transformPosition(v[0], v[1], v[2], position);
            referenceVertex(out, position.x(), position.y(), position.z(), alpha, v, normal);
        }
    }

    private static byte[] write(ByteBufferBuilder bytes, Consumer<VertexConsumer> writer) {
        var builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        writer.accept(builder);
        var built = builder.build();
        if (built == null) return new byte[0];
        try (var mesh = built) {
            var buffer = mesh.vertexBuffer();
            byte[] out = new byte[buffer.remaining()];
            buffer.get(out);
            return out;
        }
    }

    @Test
    void theReplayWritesTheBytesTheRendererWroteBefore() {
        var random = new Random(20261020L);
        var frame = new CableBeamFrame();
        long vertices = 0;
        int differ = 0;
        try (var bytes = new ByteBufferBuilder(1 << 22)) {
            for (int trial = 0; trial < 240; trial++) {
                var beam = BEAMS.get(trial % BEAMS.size());
                var style = CableBeamStyle.fromLoad(random.nextDouble() * 1.3);
                int detail = trial % 3;
                boolean glow = trial % 5 != 4;
                int x = trial;
                float[] layer = trial % 4 == 3
                        ? frame.cell(trial % 64, x, 64, 3, 1000 + trial, .3f, style, detail, glow)
                        : frame.bundle(beam, x, 64, 3, 1000 + trial, .3f, style, detail, glow);
                var vertexList = raw(trial, beam, x, style, detail, glow);
                assertEquals(vertexList.size() * CableBeamFrame.BUNDLE_STRIDE, layer.length);
                boolean orthographic = trial % 7 == 0;
                var modelView = new Matrix4f().rotationXYZ((float) (random.nextDouble() * 3 - 1.5),
                        (float) (random.nextDouble() * Math.TAU), 0);
                // The world: the pose only moves the beam to the camera.
                var moved = new Matrix4f().translation((float) random.nextGaussian() * 20,
                        (float) random.nextGaussian() * 6, (float) random.nextGaussian() * 20);
                var normal = new Vector3f(0, 1, 0);
                byte[] expected = write(bytes, out -> referenceMoved(vertexList, glow, moved, modelView, orthographic,
                        normal, out));
                byte[] actual = write(bytes, out -> PlasmaReplay.moved(layer, glow, moved.m30(), moved.m31(),
                        moved.m32(), orthographic, modelView.m02(), modelView.m12(), modelView.m22(), normal, out));
                differ += compare(expected, actual);
                // A guidebook scene: the pose turns and scales.
                var scene = new Matrix4f().translation(3, -1, -9).rotateXYZ(.6f, -.4f, .2f).scale(.8f);
                var sceneNormal = scene.normal(new Matrix3f()).transform(new Vector3f(0, 1, 0)).normalize();
                var view = new Matrix4f(modelView).mul(scene);
                var turn = view.normal(new Matrix3f());
                expected = write(bytes, out -> referenceInEye(vertexList, glow, scene, modelView, orthographic,
                        sceneNormal, out));
                actual = write(bytes, out -> PlasmaReplay.inEye(layer, glow, scene, view, turn, orthographic,
                        sceneNormal, out));
                differ += compare(expected, actual);
                vertices += 2L * vertexList.size();
            }
        }
        assertTrue(vertices > 300_000, String.valueOf(vertices));
        assertEquals(0, differ, differ + " bytes differ in " + vertices + " vertices");
    }

    private static int compare(byte[] expected, byte[] actual) {
        assertEquals(expected.length, actual.length);
        int differ = 0;
        for (int i = 0; i < expected.length; i++) {
            if (i % VERTEX < WRITTEN && expected[i] != actual[i]) differ++;
        }
        return differ;
    }
}
