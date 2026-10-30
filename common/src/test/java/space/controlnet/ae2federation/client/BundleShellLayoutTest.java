package space.controlnet.ae2federation.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.bundle.BundleLook;
import space.controlnet.ae2federation.bundle.BundleSegment;
import space.controlnet.ae2federation.bundle.BundleSet;

/** One shell the size of the cross-section: only outer faces, rails only where the shell's face ends. */
final class BundleShellLayoutTest {
    private static BundleLook look(BundleSet set, BlockPos pos) {
        return BundleLook.of(pos, cell -> set.at(cell).map(BundleSet.Membership::segment), (s, p, f) -> false)
                .orElseThrow();
    }

    private static Set<Direction> normals(List<BundleShellLayout.Panel> panels) {
        return panels.stream().map(BundleShellLayout.Panel::normal).collect(Collectors.toSet());
    }

    @Test
    void aThickArmShowsACableArmsRimOnlyWhereItEnds() {
        var set = new BundleSet();
        set.form(new BundleSegment(new BlockPos(0, 0, 0), new BlockPos(5, 1, 1), Direction.Axis.X));
        var look = BundleLook.of(new BlockPos(5, 0, 0), cell -> set.at(cell).map(BundleSet.Membership::segment),
                (s, p, f) -> f == Direction.EAST).orElseThrow();
        var faces = BundleShellLayout.armFaces(look, Direction.EAST);
        // Only the outer sides: not the device's, the shell's, or the ones toward the arm's other cells.
        assertEquals(Set.of(Direction.DOWN, Direction.NORTH),
                faces.stream().map(face -> face.panel().normal()).collect(Collectors.toSet()));
        for (var face : faces) {
            // The arm ends at 3 and runs on past 16; the line texture is see-through outside columns 2..13.
            var columns = new java.util.TreeMap<Integer, Integer>();
            for (var tile : face.tiles()) {
                assertTrue(tile.texelV() == 14 || tile.texelV() == 15, tile.toString());
                if (tile.v0() != 14) continue;
                for (int at = tile.u0(); at < tile.u1(); at++) columns.put(at, tile.texelU());
            }
            var expected = new java.util.TreeMap<Integer, Integer>();
            for (int at = 3; at < 16; at++) expected.put(at, at < 5 ? at : 5);
            assertEquals(expected, columns, face.panel().normal().getName());
        }
    }

    @Test
    void aSideArmOverTwoLayersOfOneBodyHasNoRimBetweenThem() {
        var set = new BundleSet();
        set.form(new BundleSegment(new BlockPos(0, 0, 0), new BlockPos(5, 1, 1), Direction.Axis.X));
        BundleLook.Contacts body = new BundleLook.Contacts() {
            @Override
            public boolean whole(BundleSegment segment, BlockPos pos, Direction face) {
                return face == Direction.UP && (pos.getX() == 2 || pos.getX() == 3);
            }

            @Override
            public boolean oneBody(BlockPos pos, Direction face, Direction toward) {
                return true;
            }
        };
        var look = BundleLook.of(new BlockPos(2, 1, 1), cell -> set.at(cell).map(BundleSet.Membership::segment), body)
                .orElseThrow();
        var faces = BundleShellLayout.armFaces(look, Direction.UP);
        // The outer side and the layer's far end; not the end toward the next layer, where the arm runs on.
        assertEquals(Set.of(Direction.SOUTH, Direction.WEST),
                faces.stream().map(face -> face.panel().normal()).collect(Collectors.toSet()));
        var south = faces.stream().filter(face -> face.panel().normal() == Direction.SOUTH).findFirst().orElseThrow();
        for (var tile : south.tiles()) {
            for (int at = tile.u0(); at < tile.u1(); at++) {
                assertEquals(at < 5 ? at : 5, tile.texelU(), "rim only at the arm's west end, at " + at);
            }
        }
    }

    @Test
    void theMiddleOfAThreeByThreeDrawsNothing() {
        var set = new BundleSet();
        set.form(new BundleSegment(new BlockPos(0, 0, 0), new BlockPos(2, 2, 4), Direction.Axis.Z));
        assertTrue(BundleShellLayout.panels(look(set, new BlockPos(1, 1, 2))).isEmpty(),
                "no face between cells of one bundle");
    }

    @Test
    void anOuterCellDrawsItsPartOfOneFramedFace() {
        var set = new BundleSet();
        // 2 wide (X) by 3 high (Y), along Z.
        set.form(new BundleSegment(new BlockPos(0, 0, 0), new BlockPos(1, 2, 4), Direction.Axis.Z));
        var panels = BundleShellLayout.panels(look(set, new BlockPos(0, 1, 2)));
        assertEquals(Set.of(Direction.WEST), normals(panels), "a middle cell of the west face shows that face only");
        var west = panels.get(0);
        assertEquals(2, west.plane(), 0);
        var height = west.uAxis() == Direction.Axis.Y ? west.u() : west.v();
        var length = west.uAxis() == Direction.Axis.Z ? west.u() : west.v();
        assertEquals(new BundleShellLayout.Span(0, 16, false, false), height, "no rail between cells of one face");
        assertEquals(new BundleShellLayout.Span(0, 16, false, false), length);
        var corner = BundleShellLayout.panels(look(set, new BlockPos(1, 2, 2)));
        assertEquals(Set.of(Direction.EAST, Direction.UP), normals(corner));
        for (var panel : corner) {
            var across = panel.uAxis() == Direction.Axis.Z ? panel.v() : panel.u();
            assertEquals(new BundleShellLayout.Span(0, 14, false, true), across, "the rail runs along the outer edge");
        }
    }

    @Test
    void aClosedEndCapsTheShellAndFramesItsSides() {
        var set = new BundleSet();
        set.form(new BundleSegment(new BlockPos(0, 0, 0), new BlockPos(1, 1, 4), Direction.Axis.Z));
        var panels = BundleShellLayout.panels(look(set, new BlockPos(0, 0, 4)));
        assertEquals(Set.of(Direction.WEST, Direction.DOWN, Direction.SOUTH), normals(panels));
        for (var panel : panels) {
            if (panel.normal() == Direction.SOUTH) {
                assertEquals(14, panel.plane(), 0);
                continue;
            }
            var length = panel.uAxis() == Direction.Axis.Z ? panel.u() : panel.v();
            assertEquals(new BundleShellLayout.Span(0, 14, false, true), length);
        }
    }

    @Test
    void aBranchOpensTheSideItMeetsAndCarriesItsWallsIn() {
        var set = new BundleSet();
        // A 2x2 run along X and a branch south over x 2..3.
        set.form(new BundleSegment(new BlockPos(0, 0, 0), new BlockPos(5, 1, 1), Direction.Axis.X));
        set.form(new BundleSegment(new BlockPos(2, 0, 2), new BlockPos(3, 1, 2), Direction.Axis.Z));
        var panels = BundleShellLayout.panels(look(set, new BlockPos(2, 0, 1)));
        var south = panels.stream().filter(panel -> panel.normal() == Direction.SOUTH).toList();
        assertEquals(1, south.size(), "only the strip before the opening is left of the south face");
        var strip = south.get(0);
        var along = strip.uAxis() == Direction.Axis.X ? strip.u() : strip.v();
        assertEquals(new BundleShellLayout.Span(0, 2, false, false), along);
        // The branch's west wall at x=2 and its floor at y=0 reach from the shell (z=14) to the cell's face.
        assertTrue(panels.stream().anyMatch(panel -> panel.normal() == Direction.WEST && panel.plane() == 2
                && (panel.uAxis() == Direction.Axis.Z ? panel.u() : panel.v()).equals(
                        new BundleShellLayout.Span(14, 16, false, false))));
        assertTrue(panels.stream().anyMatch(panel -> panel.normal() == Direction.DOWN && panel.plane() == 2
                && (panel.uAxis() == Direction.Axis.Z ? panel.u() : panel.v()).low() == 14));
        var branch = BundleShellLayout.panels(look(set, new BlockPos(2, 0, 2)));
        assertTrue(branch.stream().noneMatch(panel -> panel.normal() == Direction.NORTH),
                "the branch's end against the run stays open");
    }

    /** The texel voxel {@code a, b} of {@code panel} samples, as "u,v". */
    private static String texel(BundleShellLayout.Panel panel, int a, int b) {
        for (var tile : BundleShellLayout.tiles(panel)) {
            if (a >= tile.u0() && a < tile.u1() && b >= tile.v0() && b < tile.v1()) {
                return tile.texelU() + "," + tile.texelV();
            }
        }
        throw new AssertionError("no tile at " + a + "," + b);
    }

    private static final String GLASS = "6,6";

    @Test
    void railsTakeThreeVoxelsAtEachClosedEdgeAndTheFrameCornerWhereTwoMeet() {
        var span = new BundleShellLayout.Span(2, 14, true, true);
        var panel = new BundleShellLayout.Panel(Direction.SOUTH, 14, Direction.Axis.X, span, Direction.Axis.Y, span);
        assertEquals("2,6", texel(panel, 2, 8));
        assertEquals("4,6", texel(panel, 4, 8));
        assertEquals(GLASS, texel(panel, 5, 8));
        assertEquals("4,6", texel(panel, 11, 8), "the high rail mirrors the low one");
        assertEquals("2,6", texel(panel, 13, 8));
        assertEquals("2,3", texel(panel, 2, 3), "the corner comes from the frame's corner");
        assertEquals("4,4", texel(panel, 4, 4));
        var open = new BundleShellLayout.Span(0, 16, false, false);
        var middle = new BundleShellLayout.Panel(Direction.SOUTH, 14, Direction.Axis.X, open, Direction.Axis.Y, open);
        assertEquals(List.of(new BundleShellLayout.Tile(0, 0, 16, 16, 6, 6)), BundleShellLayout.tiles(middle),
                "a face that runs on every way is one pane of glass");
    }

    @Test
    void noRailCrossesABranchJointAndTheRailsTurnIntoTheBranch() {
        var set = new BundleSet();
        set.form(new BundleSegment(new BlockPos(0, 0, 0), new BlockPos(5, 1, 1), Direction.Axis.X));
        set.form(new BundleSegment(new BlockPos(2, 0, 2), new BlockPos(3, 1, 2), Direction.Axis.Z));
        // The run's floor at x=2, its south edge (z voxel 13) where the branch starts: rail until x voxel 2, then the
        // rail turns south into the branch's west wall, and the joint itself is glass.
        var floor = BundleShellLayout.panels(look(set, new BlockPos(2, 0, 1))).stream()
                .filter(panel -> panel.normal() == Direction.DOWN).findFirst().orElseThrow();
        assertEquals(Direction.Axis.X, floor.uAxis());
        assertEquals("2,6", texel(floor, 1, 13), "the rail beside the joint");
        assertEquals("2,6", texel(floor, 2, 13), "turning: the branch wall's outer column");
        assertEquals("3,6", texel(floor, 3, 13));
        assertEquals("4,6", texel(floor, 4, 13));
        assertEquals(GLASS, texel(floor, 5, 13), "no rail across the joint");
        assertEquals("3,6", texel(floor, 3, 12), "the turn is an L");
        var next = BundleShellLayout.panels(look(set, new BlockPos(3, 0, 1))).stream()
                .filter(panel -> panel.normal() == Direction.DOWN).findFirst().orElseThrow();
        assertEquals(GLASS, texel(next, 2, 13));
        assertEquals("2,6", texel(next, 13, 13), "the rail turns back out at the branch's far wall");
    }

    @Test
    void aTurnCarriesTheRunsEndOnWithNoRailAtTheBend() {
        var set = new BundleSet();
        set.form(new BundleSegment(new BlockPos(0, 0, 0), new BlockPos(5, 1, 1), Direction.Axis.X));
        // A turn south from the run's last two layers.
        set.form(new BundleSegment(new BlockPos(4, 0, 2), new BlockPos(5, 1, 3), Direction.Axis.Z));
        var end = BundleShellLayout.panels(look(set, new BlockPos(5, 0, 1))).stream()
                .filter(panel -> panel.normal() == Direction.EAST && panel.uAxis() == Direction.Axis.Y)
                .findFirst().orElseThrow();
        assertTrue(end.openings().stream().anyMatch(opening -> opening.edge() == Direction.SOUTH));
        // The end cap spans y (u) by z (v); its south edge is at z voxel 14, where the turn's wall carries it on.
        assertEquals(GLASS, texel(end, 8, 13), "the end's edge toward the turn has no rail");
        assertEquals("2,6", texel(end, 2, 13), "the end's outer rail runs straight on into the turn");
    }

    /**
     * A 2x2 cube with a one-layer arm toward three sides (local layer, row, column: arms toward row -, column + and
     * layer +), as a run made of the cube and one arm, with the other two arms as branches. Each cube cell between two
     * arms has a concave corner on its closed face, where the arms' rails meet; whichever arm the run takes, one of
     * them lies on the run's end cap. In every orientation and with every run, the rails turn the corner as an L.
     */
    @Test
    void theRailsTurnEveryConcaveCornerOfAThreeArmBendWhicheverArmTheRunTakes() {
        // Local axes: 0 layer, 1 row, 2 column. Arms and the cube as {min, max} per local axis.
        int[][] rowArm = {{0, 1}, {0, 0}, {1, 2}};
        int[][] columnArm = {{0, 1}, {1, 2}, {3, 3}};
        int[][] layerArm = {{2, 2}, {1, 2}, {1, 2}};
        int[][][] arms = {layerArm, rowArm, columnArm};
        int[][] cube = {{0, 1}, {1, 2}, {1, 2}};
        // Concave cube cells: {layer, row, column}, closed face {axis, sign}, and the two arms' directions.
        int[][][] corners = {
                {{0, 1, 2}, {0, -1}, {1, -1}, {2, 1}},
                {{1, 2, 2}, {1, 1}, {0, 1}, {2, 1}},
                {{1, 1, 1}, {2, -1}, {0, 1}, {1, -1}}};
        int[][] permutations = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
        for (var permutation : permutations) {
            for (int signs = 0; signs < 8; signs++) {
                var frame = new Frame(permutation, signs);
                for (int run = 0; run < 3; run++) {
                    var set = new BundleSet();
                    for (int arm = 0; arm < 3; arm++) {
                        var box = arms[arm];
                        if (arm == run) {
                            box = box.clone();
                            box[run] = new int[] {Math.min(box[run][0], cube[run][0]),
                                    Math.max(box[run][1], cube[run][1])};
                        }
                        set.form(frame.segment(box, arm));
                    }
                    for (var corner : corners) {
                        var cell = frame.pos(corner[0][0], corner[0][1], corner[0][2]);
                        var face = frame.direction(corner[1][0], corner[1][1]);
                        var first = frame.direction(corner[2][0], corner[2][1]);
                        var second = frame.direction(corner[3][0], corner[3][1]);
                        var panels = BundleShellLayout.panels(look(set, cell)).stream()
                                .filter(panel -> panel.normal() == face).toList();
                        for (int p = -2; p < 3; p++) {
                            for (int q = -2; q < 3; q++) {
                                if (p < 0 && q < 0) continue;
                                // p voxels in from the first arm's wall, q from the second's.
                                int[] voxel = new int[3];
                                voxel[first.getAxis().ordinal()] = first.getAxisDirection().getStep() > 0
                                        ? 13 - p : 2 + p;
                                voxel[second.getAxis().ordinal()] = second.getAxisDirection().getStep() > 0
                                        ? 13 - q : 2 + q;
                                String expected = (2 + Math.max(p, q)) + ",6";
                                assertEquals(expected, texelAt(panels, voxel), "run along local axis " + run + ", "
                                        + frame + ", cell " + cell + " face " + face + ", p " + p + " q " + q);
                            }
                        }
                    }
                }
            }
        }
    }

    /** The texel the one panel covering {@code voxel} (its in-plane coordinates) samples there. */
    private static String texelAt(List<BundleShellLayout.Panel> panels, int[] voxel) {
        String found = null;
        for (var panel : panels) {
            int a = voxel[panel.uAxis().ordinal()];
            int b = voxel[panel.vAxis().ordinal()];
            if (a < panel.u().low() || a >= panel.u().high() || b < panel.v().low() || b >= panel.v().high()) continue;
            assertTrue(found == null, "two panels over one voxel");
            found = texel(panel, a, b);
        }
        if (found == null) throw new AssertionError("no panel at " + Arrays.toString(voxel));
        return found;
    }

    /** Local layer, row and column axes mapped onto world axes, each either way round. */
    private record Frame(int[] permutation, int signs) {
        BlockPos pos(int layer, int row, int column) {
            int[] local = {layer, row, column};
            int[] world = new int[3];
            for (int i = 0; i < 3; i++) world[permutation[i]] = sign(i) * local[i];
            return new BlockPos(world[0], world[1], world[2]);
        }

        BundleSegment segment(int[][] box, int axis) {
            var a = pos(box[0][0], box[1][0], box[2][0]);
            var b = pos(box[0][1], box[1][1], box[2][1]);
            return new BundleSegment(BlockPos.min(a, b), BlockPos.max(a, b), Direction.Axis.values()[permutation[axis]]);
        }

        Direction direction(int axis, int sign) {
            return Direction.fromAxisAndDirection(Direction.Axis.values()[permutation[axis]],
                    sign * sign(axis) > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
        }

        private int sign(int axis) {
            return (signs >> axis & 1) == 0 ? 1 : -1;
        }

        @Override
        public String toString() {
            return "axes " + Arrays.toString(permutation) + " signs " + signs;
        }
    }
}
