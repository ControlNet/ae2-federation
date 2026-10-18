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
        assertEquals(.018, idle.coreRadius(), 1e-7);
        assertEquals(.0342, medium.coreRadius(), 1e-7);
        assertEquals(.114, medium.haloRadius(), 1e-7);
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
    void aCableShinesByItsCapacityCountAndAlarmsOnlyWhileTheLimitStopsItsDomain() {
        assertEquals(0, CableBeamStyle.loadOf(0, 4, true), "an idle cable keeps only its carrier");
        assertEquals(.75, CableBeamStyle.loadOf(3, 4, true), 1e-9);
        assertFalse(CableBeamStyle.fromLoad(CableBeamStyle.loadOf(4, 4, true)).overloaded(), "a full cable still runs");
        assertTrue(CableBeamStyle.fromLoad(CableBeamStyle.loadOf(5, 4, true)).overloaded());
        assertEquals(1, CableBeamStyle.loadOf(5, 4, false), "a limit that is not enforced stops nothing, so no alarm");
        assertEquals(.5, CableBeamStyle.loadOf(2, 4, false), 1e-9);
        assertEquals(0, CableBeamStyle.loadOf(3, 0, true), "a count not yet received shows an idle carrier");
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
        // Budgets quoted in docs/art/cable-m-renderer.md: flat stretches merge, so these are typical, not fixed.
        assertTrue(counts[0] <= NEAR_STRAIGHT_BUDGET, "Near straight " + counts[0]);
        assertTrue(counts[2] <= 256, "Distant straight " + counts[2]);
        int[] sixWay = {0};
        CableBeamMesh.emit(63, 0, 0, 0, 1, 0, style, 0, (glow,x,y,z,r,g,b,a) -> sixWay[0]++);
        assertTrue(sixWay[0] <= NEAR_SIX_WAY_BUDGET, "Near six-way " + sixWay[0]);
        assertEquals(0, CableBeamMesh.detail(20 * 20));
        assertEquals(1, CableBeamMesh.detail(30 * 30));
        assertEquals(2, CableBeamMesh.detail(60 * 60));
    }

    private static final int NEAR_STRAIGHT_BUDGET = 1700;
    private static final int NEAR_SIX_WAY_BUDGET = 4800;
    /** How far a merged beam edge may stray from the full 64-sample shape, in blocks: under half a pixel up close. */
    private static final float SHAPE_TOLERANCE = .001f;

    @Test
    void aStraightBeamMergesOnlyStretchesItsShapeDoesNotNeed() {
        for (double load : new double[] {0, .25, .6, 1, 1.25}) {
            var style = CableBeamStyle.fromLoad(load);
            for (long tick = 0; tick < 64; tick += 5) {
                for (int worldX : new int[] {-3, 0, 5}) {
                    float partial = .25f;
                    // The carrier's upper edge in its first plane: x along the beam, y - .5 its radius there.
                    var edge = new java.util.TreeMap<Float, Float>();
                    CableBeamMesh.emit(3, worldX, 0, 0, tick, partial, style, 0, (glow, x, y, z, r, g, b, a) -> {
                        if (!glow && z == .5f && y > .5f) edge.put(x, y - .5f);
                    });
                    float breath = style.breath(tick, partial);
                    for (int i = 0; i <= 64; i++) {
                        float along = i / 64f;
                        float wave = style.wave(CableBeamStyle.phase(worldX, along, tick, partial));
                        float expected = Math.max(style.coreRadius() * breath,
                                style.coreRadius() * .8f + style.pulseAmplitude() * wave);
                        var low = edge.floorEntry(along);
                        var high = edge.ceilingEntry(along);
                        float drawn = low.getKey().equals(high.getKey()) ? low.getValue()
                                : low.getValue() + (high.getValue() - low.getValue())
                                        * (along - low.getKey()) / (high.getKey() - low.getKey());
                        assertEquals(expected, drawn, SHAPE_TOLERANCE + 1e-6,
                                "load " + load + " tick " + tick + " x " + worldX + " at " + along);
                    }
                }
            }
        }
    }

    /** How far merged fog may stray from the full sampling, in alpha: under half a step of an 8-bit channel. */
    private static final float FOG_TOLERANCE = 1 / 512f;

    @Test
    void fogMergesOnlyStretchesThePulsesLeaveEven() {
        for (double load : new double[] {0, .6, 1, 1.25}) {
            var style = CableBeamStyle.fromLoad(load);
            for (long tick = 0; tick < 64; tick += 3) {
                for (int worldX : new int[] {-3, 0, 5}) {
                    float partial = .75f;
                    // The innermost fog volume's upper face, along its middle line: alpha as it runs along x.
                    var line = new java.util.TreeMap<Float, Float>();
                    CableBeamMesh.emit(3, worldX, 0, 0, tick, partial, style, 0, (glow, x, y, z, r, g, b, a) -> {
                        if (glow && Math.abs(y - .61f) < 1e-6 && z == .5f) line.put(x, a);
                    });
                    float fog = (.35f + .65f * (float) Math.min(style.load(), 1)) * style.strength(tick, partial)
                            / style.emission();
                    float[] full = {0, .0975f, .195f, .2925f, .39f, .61f, .7075f, .805f, .9025f, 1};
                    for (float along : full) {
                        float pulse = style.wave(CableBeamStyle.phase(worldX, along, tick, partial));
                        float expected = Math.min(1, .10f * fog * (.55f + .9f * pulse));
                        var low = line.floorEntry(along + 1e-6f);
                        var high = line.ceilingEntry(along - 1e-6f);
                        float drawn = low.getKey() >= high.getKey() ? low.getValue()
                                : low.getValue() + (high.getValue() - low.getValue())
                                        * (along - low.getKey()) / (high.getKey() - low.getKey());
                        assertEquals(expected, drawn, FOG_TOLERANCE + 1e-6,
                                "load " + load + " tick " + tick + " x " + worldX + " at " + along);
                    }
                }
            }
        }
    }

    @Test
    void nearBeamsStayWithinTheirVertexBudgetOverAWholeCycle() {
        for (double load : new double[] {0, .6, 1, 1.25}) {
            var style = CableBeamStyle.fromLoad(load);
            long straight = 0, sixWay = 0;
            for (long tick = 0; tick < 64; tick++) {
                int[] counts = {0, 0};
                CableBeamMesh.emit(3, 7, 0, 0, tick, .5f, style, 0, (glow, x, y, z, r, g, b, a) -> counts[0]++);
                CableBeamMesh.emit(63, 7, 0, 0, tick, .5f, style, 0, (glow, x, y, z, r, g, b, a) -> counts[1]++);
                straight += counts[0];
                sixWay += counts[1];
            }
            assertTrue(straight / 64 <= NEAR_STRAIGHT_BUDGET, "load " + load + " straight " + straight / 64);
            assertTrue(sixWay / 64 <= NEAR_SIX_WAY_BUDGET, "load " + load + " six-way " + sixWay / 64);
        }
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
