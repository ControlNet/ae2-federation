package space.controlnet.ae2federation.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.bundle.BundleBeam;
import space.controlnet.ae2federation.router.CableVisualConnections;

/** The 2x2 bundle's plasma beam (Blender study R): a core inside lobed sheaths that twist and roll along the run. */
final class BundleBeamMeshTest {
    private static final double[] LOADS = {0, .25, .6, 1, 1.25};
    /** One layer of a run along X, open at both ends. */
    private static final BundleBeam RUN = new BundleBeam(Direction.Axis.X, mask(Direction.EAST, Direction.WEST), 1);
    /** A joint two layers long where a branch leaves upward and the run goes on. */
    private static final BundleBeam BRANCH = new BundleBeam(Direction.Axis.X,
            mask(Direction.EAST, Direction.WEST, Direction.UP), 2);
    /** A joint where the run turns upward. */
    private static final BundleBeam TURN = new BundleBeam(Direction.Axis.X, mask(Direction.WEST, Direction.UP), 2);

    private record Vertex(float x, float y, float z, float r, float g, float b, float a, float nx, float ny, float nz) {
    }

    private static int mask(Direction... sides) {
        int mask = 0;
        for (var side : sides) mask |= 1 << List.of(CableVisualConnections.DIRECTIONS).indexOf(side);
        return mask;
    }

    private static List<Vertex> emit(BundleBeam beam, int x, long tick, float partial, CableBeamStyle style, int detail,
            boolean glow) {
        var out = new ArrayList<Vertex>();
        BundleBeamMesh.emit(beam, x, 64, 0, tick, partial, style, detail, glow,
                (px, py, pz, r, g, b, a, nx, ny, nz) -> out.add(new Vertex(px, py, pz, r, g, b, a, nx, ny, nz)));
        return out;
    }

    private static List<Vertex> both(BundleBeam beam, CableBeamStyle style, int detail) {
        var all = emit(beam, 0, 3, .5f, style, detail, true);
        all.addAll(emit(beam, 0, 3, .5f, style, detail, false));
        return all;
    }

    @Test
    void aRunLayerCostsNoMoreThanTheFourOrdinaryBeamsItReplaces() {
        for (double load : LOADS) {
            int count = both(RUN, CableBeamStyle.fromLoad(load), 0).size();
            // CableBeamTest holds an ordinary straight cell to 1700 vertices up close; a 2x2 layer is four cells.
            assertTrue(count <= 4 * 1700, "load " + load + ": " + count);
            assertEquals(0, count % 4, "quads");
        }
    }

    @Test
    void theCoreAlwaysPassesThroughTheRings() {
        // The artist's ring flanges leave a bore of radius 10/32 block round the bundle's axis.
        for (double load : LOADS) {
            var style = CableBeamStyle.fromLoad(load);
            for (long tick = 0; tick < 64; tick += 7) {
                for (var v : emit(RUN, 0, tick, .3f, style, 0, false)) {
                    assertTrue(Math.hypot(v.y() - 1, v.z() - 1) <= .30, "load " + load + " " + v);
                }
            }
        }
    }

    @Test
    void theSheathsAreWiderThanTheCoreAndRoundedAllRound() {
        var style = CableBeamStyle.fromLoad(.6);
        double core = 0, sheath = 0;
        Set<Long> directions = new TreeSet<>();
        for (var v : emit(RUN, 0, 5, 0, style, 0, false)) core = Math.max(core, Math.hypot(v.y() - 1, v.z() - 1));
        for (var v : emit(RUN, 0, 5, 0, style, 0, true)) {
            double dy = v.y() - 1, dz = v.z() - 1;
            sheath = Math.max(sheath, Math.hypot(dy, dz));
            directions.add(Math.round(Math.toDegrees(Math.atan2(dz, dy)) / 22.5));
        }
        assertTrue(sheath > 2 * core, core + " " + sheath);
        assertTrue(sheath < 1 - 2 / 16.0, "inside the shell: " + sheath);
        // Sixteen sides round the sheaths, so their lobes read as lobes.
        assertTrue(directions.size() >= 16, directions.toString());
    }

    @Test
    void anIdleBundleStillGlowsBroad() {
        float alpha = 0;
        for (var v : emit(RUN, 0, 0, 0, CableBeamStyle.fromLoad(0), 0, true)) alpha = Math.max(alpha, v.a());
        assertTrue(alpha >= .1f, "idle sheath alpha " + alpha);
    }

    @Test
    void layersMeetWithoutASeam() {
        // The last ring of one layer and the first of the next are the same ring: lobes and pulses run on.
        var style = CableBeamStyle.fromLoad(.6);
        for (long tick = 0; tick < 64; tick += 9) {
            var end = new TreeSet<String>();
            var start = new TreeSet<String>();
            for (var v : emit(RUN, 4, tick, .5f, style, 0, true)) {
                if (Math.abs(v.x() - 1) < 1e-6) end.add(key(v.y(), v.z()));
            }
            for (var v : emit(RUN, 5, tick, .5f, style, 0, true)) {
                if (Math.abs(v.x()) < 1e-6) start.add(key(v.y(), v.z()));
            }
            assertTrue(!end.isEmpty() && end.equals(start), "tick " + tick);
        }
    }

    private static String key(float y, float z) {
        return Math.round(y * 2000) + "," + Math.round(z * 2000);
    }

    @Test
    void theShapeMovesButKeepsItsTopology() {
        var style = CableBeamStyle.fromLoad(.6);
        int count = emit(BRANCH, 0, 0, 0, style, 0, true).size();
        var first = emit(RUN, 0, 0, 0, style, 0, true);
        var later = emit(RUN, 0, 16, 0, style, 0, true);
        assertEquals(first.size(), later.size());
        assertTrue(!first.equals(later), "the lobes move");
        for (long tick = 1; tick < 64; tick += 5) assertEquals(count, emit(BRANCH, 0, tick, .25f, style, 0, true).size());
    }

    @Test
    void jointsReachTheFacesTheirArmsLeaveBy() {
        var style = CableBeamStyle.fromLoad(.6);
        for (var beam : List.of(BRANCH, TURN)) {
            double low = 2, high = 0, top = 0;
            for (var v : emit(beam, 0, 0, 0, style, 0, false)) {
                low = Math.min(low, v.x());
                high = Math.max(high, v.x());
                top = Math.max(top, v.y());
            }
            assertEquals(0, low, .2, beam.toString());
            assertEquals(2, top, .2, "the branch arm reaches the box's top: " + beam);
            if (beam == BRANCH) assertEquals(2, high, .2, "the run goes on");
            else assertTrue(high < 1.5, "a turn does not go on: " + high);
        }
    }

    @Test
    void normalsPointOutOfTheTubes() {
        for (var v : emit(RUN, 0, 7, 0, CableBeamStyle.fromLoad(.6), 0, true)) {
            assertEquals(1, Math.sqrt(v.nx() * v.nx() + v.ny() * v.ny() + v.nz() * v.nz()), 1e-4);
            assertEquals(0, v.nx(), 1e-6, "across a straight run");
            assertTrue(v.ny() * (v.y() - 1) + v.nz() * (v.z() - 1) >= -1e-6, "outward " + v);
        }
    }

    @Test
    void anOverloadedBeamIsRed() {
        for (var v : both(RUN, CableBeamStyle.fromLoad(1.25), 0)) assertEquals(1, v.r(), 1e-6);
        for (var v : both(RUN, CableBeamStyle.fromLoad(.6), 0)) assertTrue(v.b() > .9f);
    }

    @Test
    void distanceDropsTheSheathsDetail() {
        var style = CableBeamStyle.fromLoad(.6);
        int near = both(RUN, style, 0).size(), medium = both(RUN, style, 1).size(), far = both(RUN, style, 2).size();
        assertTrue(near > medium && medium > far && far > 0, near + " " + medium + " " + far);
    }

    private static List<Vertex> cell(int mask, int x, long tick, float partial, CableBeamStyle style, int detail,
            boolean glow) {
        var out = new ArrayList<Vertex>();
        BundleBeamMesh.emitCell(mask, x, 64, 0, tick, partial, style, detail, glow,
                (px, py, pz, r, g, b, a, nx, ny, nz) -> out.add(new Vertex(px, py, pz, r, g, b, a, nx, ny, nz)));
        return out;
    }

    /** An ordinary cell's beam (R / LITE) for every mask and distance stays in its shell and opens only its arms. */
    @Test
    void anOrdinaryCellStaysInsideItsShellForEveryMaskAndDistance() {
        for (int mask = 0; mask < 64; mask++) {
            for (int detail = 0; detail < 3; detail++) {
                for (double load : new double[] {0, .6, 1.25}) {
                    var style = CableBeamStyle.fromLoad(load);
                    for (long tick : new long[] {0, 21, 43}) {
                        int faces = 0, count = 0;
                        for (boolean glow : new boolean[] {true, false}) {
                            for (var v : cell(mask, -3, tick, .5f, style, detail, glow)) {
                                count++;
                                float[] p = {v.x(), v.y(), v.z()};
                                for (int a = 0; a < 3; a++) {
                                    assertTrue(Float.isFinite(p[a]) && p[a] >= -1e-6 && p[a] <= 1.000001, "mask " + mask + " " + v);
                                    if (p[a] == 0) faces |= 2 << (2 * a);
                                    if (p[a] == 1) faces |= 1 << (2 * a);
                                }
                                // The core stays in the 6-voxel glass window; the glow inside the 10-voxel neck.
                                assertTrue(insideShell(mask, p, glow ? 3f / 16 : 5f / 16), "mask " + mask + " " + v);
                                assertTrue(v.a() >= 0 && v.a() <= 1);
                            }
                        }
                        assertEquals(mask, faces, "mask " + mask);
                        assertEquals(0, count % 4);
                    }
                }
            }
        }
    }

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
    void aStraightCellKeepsAllItsLightWithinTheGlassWindow() {
        // The 1x1 shell's window reaches 3/16 block from the axis; R is scaled so that even a breathing alarm fits.
        for (double load : LOADS) {
            var style = CableBeamStyle.fromLoad(load);
            for (long tick = 0; tick < 64; tick += 3) {
                for (var v : both(cell(3, 0, tick, .3f, style, 0, true), cell(3, 0, tick, .3f, style, 0, false))) {
                    assertTrue(Math.hypot(v.y() - .5, v.z() - .5) <= 3 / 16.0 + 1e-6, "load " + load + " " + v);
                }
            }
        }
    }

    private static List<Vertex> both(List<Vertex> glow, List<Vertex> core) {
        var all = new ArrayList<>(glow);
        all.addAll(core);
        return all;
    }

    @Test
    void cellsOfARunMeetWithoutASeamAndKeepTheTwoBlockClock() {
        var style = CableBeamStyle.fromLoad(.6);
        for (long tick = 0; tick < 64; tick += 9) {
            var end = new TreeSet<String>();
            var start = new TreeSet<String>();
            for (var v : cell(3, 4, tick, .5f, style, 0, true)) {
                if (Math.abs(v.x() - 1) < 1e-6) end.add(key(v.y(), v.z()));
            }
            for (var v : cell(3, 5, tick, .5f, style, 0, true)) {
                if (Math.abs(v.x()) < 1e-6) start.add(key(v.y(), v.z()));
            }
            assertTrue(!end.isEmpty() && end.equals(start), "tick " + tick);
            assertEquals(cell(3, 4, tick, .5f, style, 0, true), cell(3, 6, tick, .5f, style, 0, true));
            assertTrue(!cell(3, 4, tick, .5f, style, 0, true).equals(cell(3, 5, tick, .5f, style, 0, true)));
        }
    }

    @Test
    void aStraightCellCostsLessThanTheShippedBeamAndLessStillFarAway() {
        int[] counts = new int[3];
        for (double load : LOADS) {
            var style = CableBeamStyle.fromLoad(load);
            for (int detail = 0; detail < 3; detail++) {
                counts[detail] = both(cell(3, 0, 5, 0, style, detail, true), cell(3, 0, 5, 0, style, detail, false))
                        .size();
            }
            // CableBeamTest holds the shipped straight beam to 1700 vertices up close.
            assertTrue(counts[0] <= 1200, "near " + counts[0]);
            assertTrue(counts[0] > counts[1] && counts[1] > counts[2] && counts[2] > 0, java.util.Arrays.toString(counts));
            assertEquals(0, counts[0] % 4);
        }
    }

    @Test
    void anOrdinaryCellAlarmsRedAndHasNoMiddleSheath() {
        for (var v : cell(19, 0, 3, 0, CableBeamStyle.fromLoad(1.25), 0, true)) assertEquals(1, v.r(), 1e-6);
        // Two sheaths of eight sides, against the bundle's three of sixteen.
        int bundleQuads = emit(RUN, 0, 5, 0, CableBeamStyle.fromLoad(.6), 0, true).size();
        int cellQuads = cell(3, 0, 5, 0, CableBeamStyle.fromLoad(.6), 0, true).size();
        assertTrue(2 * cellQuads < bundleQuads, cellQuads + " " + bundleQuads);
    }

    @Test
    void softEdgesFadeWhereATubeTurnsAwayFromTheEye() {
        assertEquals(2, BundleBeamMesh.fade(0, 0, 1, 0, 0, 1), 1e-6);
        assertEquals(2, BundleBeamMesh.fade(0, 0, -1, 0, 0, 1), 1e-6, "the far side of a tube, seen through it");
        assertEquals(0, BundleBeamMesh.fade(1, 0, 0, 0, 0, 1), 1e-6);
        assertTrue(BundleBeamMesh.fade(.6f, 0, .8f, 0, 0, 1) < BundleBeamMesh.fade(.3f, 0, .954f, 0, 0, 1));
    }
}
