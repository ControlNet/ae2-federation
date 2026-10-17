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

    @Test
    void softEdgesFadeWhereATubeTurnsAwayFromTheEye() {
        assertEquals(2, BundleBeamMesh.fade(0, 0, 1, 0, 0, 1), 1e-6);
        assertEquals(2, BundleBeamMesh.fade(0, 0, -1, 0, 0, 1), 1e-6, "the far side of a tube, seen through it");
        assertEquals(0, BundleBeamMesh.fade(1, 0, 0, 0, 0, 1), 1e-6);
        assertTrue(BundleBeamMesh.fade(.6f, 0, .8f, 0, 0, 1) < BundleBeamMesh.fade(.3f, 0, .954f, 0, 0, 1));
    }
}
