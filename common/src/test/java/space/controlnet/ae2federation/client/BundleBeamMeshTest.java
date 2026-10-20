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

/** The 2x2 and 3x3 bundles' plasma beam (Blender study R): a core inside lobed sheaths that twist and roll along the run. */
final class BundleBeamMeshTest {
    private static final double[] LOADS = {0, .25, .6, 1, 1.25};
    /** One layer of a run along X, open at both ends. */
    private static final BundleBeam RUN = new BundleBeam(Direction.Axis.X, mask(Direction.EAST, Direction.WEST), 1);
    /** A joint two layers long where a branch leaves upward and the run goes on. */
    private static final BundleBeam BRANCH = new BundleBeam(Direction.Axis.X,
            mask(Direction.EAST, Direction.WEST, Direction.UP), 2);
    /** A joint where the run turns upward. */
    private static final BundleBeam TURN = new BundleBeam(Direction.Axis.X, mask(Direction.WEST, Direction.UP), 2);

    /** Taken from the 2x2 beam before the 3x3 one got scales of its own (commit 3d2ff190). */
    private static final long TWO_BY_TWO_FINGERPRINT = 3090890168832736037L;

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
            // The M beam held an ordinary straight cell to 1700 vertices up close; a 2x2 layer is four cells.
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
    void a3x3CorePassesThroughItsNarrowerRingsAndItsJointReachesEveryArm() {
        // The artist's 3x3 ring leaves a bore of 8 voxels, radius 1/4 block, round the middle cell's axis.
        var run = new BundleBeam(Direction.Axis.X, mask(Direction.EAST, Direction.WEST), 1, 3);
        for (double load : LOADS) {
            var style = CableBeamStyle.fromLoad(load);
            for (long tick = 0; tick < 64; tick += 7) {
                for (var v : emit(run, 0, tick, .3f, style, 0, false)) {
                    assertTrue(Math.hypot(v.y() - 1.5, v.z() - 1.5) <= .24, "load " + load + " " + v);
                }
            }
        }
        var branch = new BundleBeam(Direction.Axis.X, mask(Direction.EAST, Direction.WEST, Direction.UP), 3, 3);
        double low = 3, high = 0, top = 0;
        for (var v : emit(branch, 0, 0, 0, CableBeamStyle.fromLoad(.6), 0, false)) {
            low = Math.min(low, v.x());
            high = Math.max(high, v.x());
            top = Math.max(top, v.y());
        }
        assertEquals(0, low, .2);
        assertEquals(3, high, .2);
        assertEquals(3, top, .2);
    }

    @Test
    void a3x3CoreFitsTheBoreAtFullLoadDuringAnAlarm() {
        var run = new BundleBeam(Direction.Axis.X, mask(Direction.EAST, Direction.WEST), 1, 3);
        double widest = 0;
        // An alarm breathes widest where its clock is at 0.
        for (var v : emit(run, 0, 0, 0, CableBeamStyle.fromLoad(1.25, true), 0, false)) {
            widest = Math.max(widest, Math.hypot(v.y() - 1.5, v.z() - 1.5));
        }
        assertTrue(widest > .2 && widest <= .225, "core " + widest);
    }

    @Test
    void a3x3GlowIsTheTwoByTwoGlowOneAndAHalfTimesAsWide() {
        var huge = new BundleBeam(Direction.Axis.X, mask(Direction.EAST, Direction.WEST), 1, 3);
        for (double load : LOADS) {
            var style = CableBeamStyle.fromLoad(load);
            for (long tick = 0; tick < 64; tick += 13) {
                double large = 0, wide = 0;
                for (var v : emit(RUN, 0, tick, .5f, style, 0, true)) {
                    large = Math.max(large, Math.hypot(v.y() - 1, v.z() - 1));
                }
                for (var v : emit(huge, 0, tick, .5f, style, 0, true)) {
                    wide = Math.max(wide, Math.hypot(v.y() - 1.5, v.z() - 1.5));
                }
                assertEquals(1.5, wide / large, 1e-4, "load " + load + " tick " + tick);
            }
        }
        assertEquals(both(RUN, CableBeamStyle.fromLoad(.6), 0).size(),
                both(huge, CableBeamStyle.fromLoad(.6), 0).size(), "the same vertices, only wider");
    }

    /** Every vertex of one pass, as raw bits, drawn by the cell at {@code x, y, z}. */
    private static List<Integer> bits(BundleBeam beam, int mask, int x, int y, int z, CableBeamStyle style, int detail,
            boolean glow) {
        var out = new ArrayList<Integer>();
        BundleBeamMesh.Sink sink = (px, py, pz, r, g, b, a, nx, ny, nz) -> {
            for (float f : new float[] {px, py, pz, r, g, b, a, nx, ny, nz}) out.add(Float.floatToRawIntBits(f));
        };
        if (beam != null) BundleBeamMesh.emit(beam, x, y, z, 13, .4f, style, detail, glow, sink);
        else BundleBeamMesh.emitCell(mask, x, y, z, 13, .4f, style, detail, glow, sink);
        return out;
    }

    @Test
    void aBeamReadsTheParityOfOnlyTheAxesItsArmsRunAlong() {
        // Cells that differ only in the parity of other axes draw the same vertices, so they may share one mesh.
        var beams = new ArrayList<BundleBeam>(List.of(RUN, BRANCH, TURN,
                new BundleBeam(Direction.Axis.X, mask(Direction.EAST, Direction.WEST), 1, 3),
                new BundleBeam(Direction.Axis.X, mask(Direction.EAST, Direction.WEST, Direction.UP), 3, 3),
                new BundleBeam(Direction.Axis.Z, mask(Direction.NORTH, Direction.EAST), 3, 3),
                new BundleBeam(Direction.Axis.Y, mask(Direction.UP, Direction.DOWN), 1, 2),
                new BundleBeam(Direction.Axis.Y, 0, 1, 2),
                new BundleBeam(Direction.Axis.Z, 0, 1, 3)));
        var style = CableBeamStyle.fromLoad(.6);
        int checked = 0;
        for (int item = 0; item < beams.size() + 64; item++) {
            var beam = item < beams.size() ? beams.get(item) : null;
            int mask = item - beams.size();
            for (int detail = 0; detail < 3; detail++) {
                for (boolean glow : new boolean[] {true, false}) {
                    int axes = beam != null ? BundleBeamMesh.parityAxes(beam, detail, glow)
                            : BundleBeamMesh.cellParityAxes(mask, detail, glow);
                    var byParity = new java.util.HashMap<Integer, List<Integer>>();
                    var seen = new java.util.HashSet<List<Integer>>();
                    for (int parity = 0; parity < 8; parity++) {
                        var mesh = bits(beam, mask, 6 + (parity & 1), 64 + (parity >> 1 & 1), -4 + (parity >> 2 & 1),
                                style, detail, glow);
                        var first = byParity.putIfAbsent(parity & axes, mesh);
                        if (first != null) assertEquals(first, mesh, beam + " " + mask + " " + detail + " " + glow);
                        seen.add(mesh);
                        checked++;
                    }
                    // And no axis is left out that the beam does read.
                    assertEquals(byParity.size(), seen.size(), beam + " " + mask + " " + detail + " " + glow);
                }
            }
        }
        assertTrue(checked > 1000);
        assertEquals(1, BundleBeamMesh.parityAxes(RUN, 0, true), "a run along X reads x only");
        assertEquals(0, BundleBeamMesh.parityAxes(RUN, 0, false), "the core is an even tube");
        assertEquals(0, BundleBeamMesh.parityAxes(RUN, 2, true), "far off a beam has no sheaths");
        assertEquals(1 | 2, BundleBeamMesh.parityAxes(BRANCH, 1, true));
    }

    @Test
    void the2x2BeamIsUnchangedByThe3x3Scales() {
        // Every 2x2 vertex, bit for bit, as before the 3x3 beam had scales of its own.
        long hash = 17;
        for (var beam : List.of(RUN, BRANCH, TURN)) {
            for (double load : LOADS) {
                for (int detail = 0; detail < 3; detail++) {
                    for (var v : both(beam, CableBeamStyle.fromLoad(load), detail)) {
                        for (float f : new float[] {v.x(), v.y(), v.z(), v.r(), v.g(), v.b(), v.a(), v.nx(), v.ny(),
                                v.nz()}) {
                            hash = hash * 31 + Float.floatToIntBits(f);
                        }
                    }
                }
            }
        }
        assertEquals(TWO_BY_TWO_FINGERPRINT, hash);
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
            // The M beam it replaced was held to 1700 vertices for a straight cell and 4800 for a six-way joint.
            assertTrue(counts[0] <= 1200, "near " + counts[0]);
            int sixWay = both(cell(63, 0, 5, 0, style, 0, true), cell(63, 0, 5, 0, style, 0, false)).size();
            assertTrue(sixWay <= 3600, "six-way " + sixWay);
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
