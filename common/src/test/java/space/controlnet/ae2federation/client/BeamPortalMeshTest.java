package space.controlnet.ae2federation.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

final class BeamPortalMeshTest {
    private record Vertex(float x, float y, float z, float r, float g, float b, float alpha) {}

    private static List<Vertex> mesh(boolean linked, int side, int detail, long tick, float partial,
            CableBeamStyle style) {
        var vertices = new ArrayList<Vertex>();
        BeamPortalMesh.Sink sink = (x, y, z, r, g, b, a) -> vertices.add(new Vertex(x, y, z, r, g, b, a));
        BeamPortalMesh.emit(linked, side, detail, tick, partial, style, false, sink);
        BeamPortalMesh.emit(linked, side, detail, tick, partial, style, true, sink);
        return vertices;
    }

    @Test
    void countsAndEligibility() {
        int[] counts = {3928, 2264, 1144};
        for (int detail = 0; detail < 3; detail++) {
            for (double load : new double[] {0, .6, 1, 1.25}) {
                var style = CableBeamStyle.fromLoad(load);
                assertEquals(counts[detail], mesh(true, 2, detail, 13, .5f, style).size());
                assertTrue(mesh(false, 2, detail, 13, .5f, style).isEmpty());
                assertTrue(mesh(true, 1, detail, 13, .5f, style).isEmpty());
                assertTrue(mesh(true, 3, detail, 13, .5f, style).isEmpty());
            }
        }
        assertEquals(0, CableBeamMesh.detail(24 * 24));
        assertEquals(1, CableBeamMesh.detail(48 * 48));
        assertEquals(2, CableBeamMesh.detail(49 * 49));
    }

    @Test
    void studyRadiiPlanesAndAlphas() {
        var vertices = mesh(true, 2, 0, 0, 0, CableBeamStyle.fromLoad(0));
        float[][] bands = {{0, 4.8f, 14.01f, .68f}, {5.5f, 6.4f, 18.999f, .025f},
                {6.4f, 7, 18.999f, .045f}, {7, 8, 18.999f, .06f}, {8, 8.6f, 18.999f, .045f},
                {8.6f, 9.2f, 18.999f, .025f}, {9.2f, 9.8f, 18.999f, .012f},
                {6.4f, 6.7f, 19, .10f}, {6.7f, 7, 19, .32f}, {7, 8, 19, .98f},
                {8, 8.3f, 19, .32f}, {8.3f, 8.6f, 19, .10f}, {8.94f, 9.06f, 19, .65f}};
        // The disc and spill use 48 segments; the main bands and line use 96.
        for (int band = 0, start = 0; band < bands.length; start += band < 7 ? 192 : 384, band++) {
            for (int i = 0; i < (band < 7 ? 192 : 384); i++) {
                var v = vertices.get(start + i);
                assertEquals(bands[band][i % 4 == 0 || i % 4 == 3 ? 0 : 1] / 16,
                        Math.hypot(v.x, v.y), 1e-7);
                assertEquals(bands[band][2] / 16, v.z, 1e-7);
                assertEquals(bands[band][3] * (band == 0 ? 1 : .82), v.alpha, 1e-7);
            }
        }
        for (int i = 3648; i < 3904; i++) {
            var v = vertices.get(i);
            assertEquals((i % 4 == 0 || i % 4 == 3 ? 9.35 : 9.5) / 16, Math.hypot(v.x, v.y), 1e-7);
            assertEquals(19f / 16, v.z);
        }
        for (int i = 3904; i < 3928; i++) assertEquals(14.02f / 16, vertices.get(i).z);
        assertEquals(0, vertices.get(3904).alpha);
        assertEquals(4.6 / 16, Math.hypot(vertices.get(3905).x, vertices.get(3905).y), 1e-7);
    }

    @Test
    void animationLoopsAndUsesStudySpeeds() {
        for (int detail = 0; detail < 3; detail++) {
            var style = CableBeamStyle.fromLoad(.6);
            assertEquals(mesh(true, 2, detail, 7, .3f, style), mesh(true, 2, detail, 71, .3f, style));
        }
        var vertices = mesh(true, 2, 0, 32, 0, CableBeamStyle.fromLoad(.6));
        assertEquals(Math.toRadians(3.75 / 2), Math.atan2(vertices.get(3264).y, vertices.get(3264).x), 1e-7);
        assertEquals(Math.toRadians(-22.5 / 2), Math.atan2(vertices.get(3648).y, vertices.get(3648).x), 1e-7);
    }

    @Test
    void normalAlarmAndUnpoweredPalettes() {
        var normal = mesh(true, 2, 0, 0, 0, CableBeamStyle.fromLoad(.6));
        for (var v : normal.subList(0, 192)) {
            assertEquals(.008f, v.r);
            assertEquals(.018f, v.g);
            assertEquals(.028f, v.b);
            assertEquals(.68f, v.alpha);
        }
        assertEquals(.14f, normal.get(192).r);
        assertEquals(.85f, normal.get(192).g);
        assertEquals(.92f, normal.get(2112).r);
        assertEquals(1, normal.get(2112).g);
        for (var style : List.of(CableBeamStyle.fromLoad(1.25), CableBeamStyle.fromLoad(0, true))) {
            var vertices = mesh(true, 2, 0, 0, 0, style);
            for (var v : vertices.subList(0, 192)) {
                assertEquals(.028f, v.r);
                assertEquals(.006f, v.g);
                assertEquals(.004f, v.b);
                assertEquals(.68f, v.alpha);
            }
            for (var v : vertices.subList(192, vertices.size())) {
                assertEquals(1, v.r);
                assertEquals(.045f, v.g);
                assertEquals(.015f, v.b);
            }
            assertEquals(.98 * (.82 + .12 * Math.min(style.load(), 1)), vertices.get(2112).alpha, 1e-7);
        }
    }

    @Test
    void allFacingsProjectIntoTheGapAndStayInsidePairBounds() {
        for (var facing : Direction.values()) {
            BeamPortalMesh.Sink sink = (x, y, z, r, g, b, a) -> {
                float along = switch (facing.getAxis()) { case X -> x; case Y -> y; case Z -> z; };
                assertEquals(facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 19f / 16 : -3f / 16,
                        along);
                assertTrue(x >= -.1875 && x <= 2 && y >= -.1875 && y <= 2 && z >= -.1875 && z <= 2);
            };
            BeamPortalMesh.oriented(facing, sink, .6f, -.6f, 19f / 16, 1, 1, 1, 1);
        }
    }
}
