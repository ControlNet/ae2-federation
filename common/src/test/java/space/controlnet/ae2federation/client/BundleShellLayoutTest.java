package space.controlnet.ae2federation.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void railsTakeThreeVoxelsAtEachClosedEnd() {
        var slices = BundleShellLayout.slices(new BundleShellLayout.Span(2, 14, true, true));
        assertEquals(List.of(new BundleShellLayout.Slice(2, 5, 2, 5), new BundleShellLayout.Slice(5, 11, 5, 7),
                new BundleShellLayout.Slice(11, 14, 11, 14)), slices);
        assertEquals(List.of(new BundleShellLayout.Slice(0, 16, 5, 7)),
                BundleShellLayout.slices(new BundleShellLayout.Span(0, 16, false, false)));
    }
}
