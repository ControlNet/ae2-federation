package space.controlnet.ae2federation.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class BeamPortalTransformTest {
    @Test
    void writtenVerticesMatchForEveryFacingDetailPhaseAndStyle() {
        var poses = new Matrix4f[] {new Matrix4f(), new Matrix4f().translation(127.25f, -63.5f, 201.75f),
                new Matrix4f().translation(3, -2, 7).rotateXYZ(.3f, -.7f, .2f).scale(.8f, 1.2f, .9f)};
        try (var oldBytes = new ByteBufferBuilder(1 << 20); var newBytes = new ByteBufferBuilder(1 << 20)) {
            for (int side = 2; side <= 3; side++) for (var facing : Direction.values()) for (int detail = 0; detail < 3; detail++) {
                for (int phase = 0; phase < 64; phase++) for (double load : new double[] {0, .5, 1, 1.5}) {
                    for (boolean alarm : new boolean[] {false, true}) for (var pose : poses) {
                        var style = CableBeamStyle.fromLoad(load, alarm);
                        var oldBuilder = new BufferBuilder(oldBytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
                        var newBuilder = new BufferBuilder(newBytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
                        int size = side;
                        var matrix = BeamPortalMesh.transform(pose, facing, side, new Matrix4f());
                        var oldSink = sink(oldBuilder, pose);
                        var newSink = sink(newBuilder, matrix);
                        for (boolean glow : new boolean[] {false, true}) {
                            BeamPortalMesh.emit(true, side, detail, phase, .3f, style, glow, (x, y, z, r, g, b, a) ->
                                    BeamPortalMesh.oriented(facing, size, oldSink, x, y, z, r, g, b, a));
                            BeamPortalMesh.emit(true, side, detail, phase, .3f, style, glow, newSink);
                        }
                        try (var oldMesh = oldBuilder.buildOrThrow(); var newMesh = newBuilder.buildOrThrow()) {
                            ByteBuffer a = oldMesh.vertexBuffer(), b = newMesh.vertexBuffer();
                            assertEquals(a.remaining(), b.remaining());
                            for (int offset = 0; offset < a.remaining(); offset += 32) {
                                // One matrix rounds differently from two: 1e-5 block, or two float steps far out.
                                for (int c = 0; c < 12; c += 4) {
                                    float expected = a.getFloat(offset + c);
                                    assertEquals(expected, b.getFloat(offset + c),
                                            Math.max(1e-5f, 2 * Math.ulp(expected)));
                                }
                                // BLOCK's final byte is unwritten padding.
                                for (int c = 12; c < 31; c++) assertEquals(a.get(offset + c), b.get(offset + c));
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void boundsContainEveryOrientedVertex() {
        var origin = new BlockPos(42, -60, 51);
        for (int side = 2; side <= 3; side++) for (var facing : Direction.values()) {
            int size = side;
            var bounds = BeamPortalMesh.bounds(origin, facing, side).inflate(1e-7);
            BeamPortalMesh.Sink check = (x, y, z, r, g, b, a) -> assertTrue(
                    bounds.contains(origin.getX() + x, origin.getY() + y, origin.getZ() + z));
            for (int detail = 0; detail < 3; detail++) for (int phase = 0; phase < 64; phase++) {
                for (boolean glow : new boolean[] {false, true}) BeamPortalMesh.emit(true, side, detail, phase, .3f,
                        CableBeamStyle.fromLoad(.5, false), glow,
                        (x, y, z, r, g, b, a) -> BeamPortalMesh.oriented(facing, size, check, x, y, z, r, g, b, a));
            }
        }
    }

    @Test
    void frustumRejectsOnlyTheOffscreenEnd() {
        var frustum = new Frustum(new Matrix4f(), new Matrix4f().perspective((float) Math.PI / 2, 1, .1f, 100));
        frustum.prepare(0, 0, 0);
        for (int side = 2; side <= 3; side++) for (var facing : Direction.values()) {
            assertTrue(frustum.isVisible(BeamPortalMesh.bounds(new BlockPos(-1, -1, -3), facing, side)));
            assertFalse(frustum.isVisible(BeamPortalMesh.bounds(new BlockPos(-1, -1, 3), facing, side)));
            assertFalse(frustum.isVisible(BeamPortalMesh.bounds(new BlockPos(20, -1, -3), facing, side)));
        }
    }

    private static BeamPortalMesh.Sink sink(BufferBuilder out, Matrix4f matrix) {
        return (x, y, z, r, g, b, a) -> out.addVertex(matrix, x, y, z)
                .setColor(r, g, b, a).setUv(0, 0).setUv2(240, 240).setNormal(0, 1, 0);
    }
}
