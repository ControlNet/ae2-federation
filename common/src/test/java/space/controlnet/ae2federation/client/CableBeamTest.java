package space.controlnet.ae2federation.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class CableBeamTest {
    @Test
    void loadMappingMatchesApprovedMAndDoesNotMarkFullAsOverloaded() {
        var idle = CableBeamStyle.fromLoad(0);
        var medium = CableBeamStyle.fromLoad(.6);
        var full = CableBeamStyle.fromLoad(1);
        var overload = CableBeamStyle.fromLoad(1.25);
        assertEquals(.03, idle.coreRadius(), 1e-7);
        assertEquals(.051, medium.coreRadius(), 1e-7);
        assertEquals(.131, medium.haloRadius(), 1e-7);
        assertEquals(2.2, medium.pulseDensity(), 1e-6);
        assertEquals(1.38, medium.emission(), 1e-6);
        assertFalse(full.overloaded());
        assertTrue(overload.overloaded());
        assertEquals(full.coreRadius(), overload.coreRadius());
        assertEquals(full.haloRadius(), CableBeamStyle.fromLoad(100).haloRadius());
        for (double invalid : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> CableBeamStyle.fromLoad(invalid));
        }
        for (int tick = 0; tick < 64; tick++) {
            assertTrue(overload.strength(tick, .5f) >= 1.39);
            assertTrue(overload.strength(tick, .5f) <= 2.21);
        }
    }

    @Test
    void allMasksAndLodsStayInsideTheShellAndOpenOnlyConnectedFaces() {
        for (int mask = 0; mask < 64; mask++) {
            final int expected = mask;
            for (int lod = 0; lod < 3; lod++) {
                int[] faces = {0}, count = {0};
                CableBeamMesh.emit(mask, -30000000, 3, -1, 63, 1, CableBeamStyle.fromLoad(1.25), lod,
                        (glow, x, y, z, r, g, b, alpha) -> {
                            count[0]++;
                            float[] p = {x, y, z};
                            for (int a = 0; a < 3; a++) {
                                assertTrue(Float.isFinite(p[a]));
                                assertTrue(p[a] >= -1e-6 && p[a] <= 1.000001);
                                if (p[a] == 0) faces[0] |= 2 << (2 * a);
                                if (p[a] == 1) faces[0] |= 1 << (2 * a);
                            }
                            // The carrier stays in the 6-voxel glass window; shoulders and fog stay inside the 10-voxel neck.
                            assertTrue(insideShell(expected, p, glow ? 3f / 16 : 5f / 16), "Outside mask " + expected);
                            assertTrue(alpha >= 0 && alpha <= 1);
                            assertTrue(r >= 0 && r <= 1 && g >= 0 && g <= 1 && b >= 0 && b <= 1);
                        });
                assertEquals(mask, faces[0]);
                assertEquals(0, count[0] % 4);
                assertTrue(count[0] > 0 && count[0] <= MAX_VERTICES, "Vertices " + count[0]);
            }
        }
    }

    private static final int MAX_VERTICES = 25000;

    private static boolean insideShell(int mask, float[] p, float inset) {
        for (int arm = -1; arm < 6; arm++) {
            if (arm >= 0 && (mask & (1 << arm)) == 0) continue;
            boolean inside = true;
            for (int a = 0; a < 3; a++) {
                float lo = inset, hi = 1 - inset;
                if (arm >= 0 && arm / 2 == a) {
                    if ((arm & 1) == 0) hi = 1; else lo = 0;
                }
                inside &= p[a] >= lo - 1e-6 && p[a] <= hi + 1e-6;
            }
            if (inside) return true;
        }
        return false;
    }

    @Test
    void spatialAndClockWrapKeepBothWaveAndBoundaryGeometryContinuous() {
        for (double load : new double[] {0, .25, .6, 1, 1.25}) {
            var style = CableBeamStyle.fromLoad(load);
            for (int x : new int[] {-30000000, -3, -1, 0, 1, 29999999}) {
                assertEquals(style.wave(CableBeamStyle.phase(x, 1, 90001, .2f)),
                        style.wave(CableBeamStyle.phase(x + 1, 0, 90001, .2f)), 1e-6);
                assertEquals(boundary(x, 1, style), boundary(x + 1, 0, style));
            }
            assertEquals(style.wave(CableBeamStyle.phase(-1, .3f, 63, 1)),
                    style.wave(CableBeamStyle.phase(-1, .3f, 64, 0)), 1e-6);
        }
    }

    private static Set<List<Integer>> boundary(int worldX, int side, CableBeamStyle style) {
        var result = new HashSet<List<Integer>>();
        CableBeamMesh.emit(3, worldX, 0, 0, 97, .25f, style, 0, (glow, x, y, z, r, g, b, alpha) -> {
            if (x == side) result.add(List.of(Math.round(y * 100000), Math.round(z * 100000),
                    Math.round(alpha * 100000)));
        });
        return result;
    }

    @Test
    void passEmissionIsEquivalentAndDistanceLodReducesWork() {
        var style = CableBeamStyle.fromLoad(.6);
        List<Boolean> all = new ArrayList<>(), passes = new ArrayList<>();
        CableBeamMesh.emit(19, 0, 0, 0, 1, 0, style, 0, (glow,x,y,z,r,g,b,a) -> all.add(glow));
        for (boolean pass : new boolean[] {false, true}) {
            CableBeamMesh.emitLayer(19, 0, 0, 0, 1, 0, style, 0, pass, (glow,x,y,z,r,g,b,a) -> {
                assertEquals(pass, glow); passes.add(glow);
            });
        }
        assertEquals(all.size(), passes.size());
        int[] counts = new int[3];
        for (int lod = 0; lod < 3; lod++) {
            int index = lod;
            CableBeamMesh.emit(3, 0, 0, 0, 1, 0, style, lod, (glow,x,y,z,r,g,b,a) -> counts[index]++);
        }
        assertTrue(counts[0] > counts[1] && counts[1] > counts[2]);
        // Budgets quoted in docs/art/cable-m-renderer.md.
        assertEquals(4960, counts[0]);
        assertEquals(256, counts[2]);
        int[] sixWay = {0};
        CableBeamMesh.emit(63, 0, 0, 0, 1, 0, style, 0, (glow,x,y,z,r,g,b,a) -> sixWay[0]++);
        assertEquals(14592, sixWay[0]);
        assertEquals(0, CableBeamMesh.detail(20 * 20));
        assertEquals(1, CableBeamMesh.detail(30 * 30));
        assertEquals(2, CableBeamMesh.detail(60 * 60));
    }

    @Test
    void smoothingIsIndependentOfFrameRate() {
        for (double target : new double[] {0, 1.25}) {
            double start = target == 0 ? 1.25 : 0;
            double expected = CableBeamStyle.smooth(start, target, 1);
            for (int fps : new int[] {20, 60, 144}) {
                double value = start;
                for (int i = 0; i < fps; i++) value = CableBeamStyle.smooth(value, target, 1.0 / fps);
                assertEquals(expected, value, 1e-10);
            }
        }
    }
}
