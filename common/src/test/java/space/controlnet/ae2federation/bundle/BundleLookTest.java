package space.controlnet.ae2federation.bundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.router.CableVisualConnections;

/** What one bundled cell shows: its outer sides, its links, its device arms and the runs of a branch's opening. */
final class BundleLookTest {
    /** 2x2 along X, x 0..5, y 0..1, z 0..1. */
    private static final BundleSegment RUN = new BundleSegment(new BlockPos(0, 0, 0), new BlockPos(5, 1, 1),
            Direction.Axis.X);
    private static final BundleLook.Contacts NO_DEVICES = (segment, pos, face) -> false;

    private static BundleLook look(BundleSet set, BlockPos pos, BundleLook.Contacts contacts) {
        return BundleLook.of(pos, cell -> set.at(cell).map(BundleSet.Membership::segment), contacts).orElseThrow();
    }

    private static int mask(Direction... sides) {
        int mask = 0;
        for (var side : sides) mask |= 1 << List.of(CableVisualConnections.DIRECTIONS).indexOf(side);
        return mask;
    }

    @Test
    void aCellInsideARunShowsOnlyItsOuterSides() {
        var set = new BundleSet();
        set.form(RUN);
        var look = look(set, new BlockPos(3, 0, 0), NO_DEVICES);
        assertEquals(Direction.Axis.X, look.axis());
        for (var side : Direction.values()) {
            assertEquals(side == Direction.DOWN || side == Direction.NORTH, look.edge(side), side.getName());
        }
        assertTrue(look.continues(Direction.EAST) && look.continues(Direction.WEST));
        assertFalse(look.continues(Direction.UP), "a side within the cross-section does not continue a beam");
        assertEquals(mask(Direction.EAST, Direction.WEST), look.flowMask());
    }

    @Test
    void anEndIsClosedUnlessADeviceTakesTheWholeFace() {
        var set = new BundleSet();
        set.form(RUN);
        var closed = look(set, new BlockPos(5, 1, 1), NO_DEVICES);
        assertTrue(closed.edge(Direction.EAST) && !closed.continues(Direction.EAST) && !closed.dense(Direction.EAST));
        assertEquals(mask(Direction.WEST), closed.flowMask());
        var device = look(set, new BlockPos(5, 1, 1), (segment, pos, face) -> face == Direction.EAST);
        assertTrue(device.dense(Direction.EAST));
        assertEquals(mask(Direction.EAST, Direction.WEST), device.flowMask());
    }

    @Test
    void aBranchJoinsItsRunWhereItsWholeEndFaceLies() {
        var set = new BundleSet();
        set.form(RUN);
        // A 2x2 branch south over x 2..3, one layer long.
        set.form(new BundleSegment(new BlockPos(2, 0, 2), new BlockPos(3, 1, 2), Direction.Axis.Z));
        var first = look(set, new BlockPos(2, 0, 1), NO_DEVICES);
        assertTrue(first.join(Direction.SOUTH));
        assertFalse(first.joinsBefore(Direction.SOUTH), "x=1 is outside the branch's opening");
        assertTrue(first.joinsAfter(Direction.SOUTH), "x=3 opens onto the same branch");
        assertEquals(mask(Direction.EAST, Direction.WEST, Direction.SOUTH), first.flowMask());
        var last = look(set, new BlockPos(3, 1, 1), NO_DEVICES);
        assertTrue(last.joinsBefore(Direction.SOUTH) && !last.joinsAfter(Direction.SOUTH));
        var branch = look(set, new BlockPos(2, 0, 2), NO_DEVICES);
        assertEquals(Direction.Axis.Z, branch.axis());
        assertTrue(branch.join(Direction.NORTH) && branch.edge(Direction.SOUTH) && !branch.join(Direction.SOUTH));
        assertTrue(branch.edge(Direction.WEST) && branch.edge(Direction.DOWN));
        assertEquals(mask(Direction.NORTH), branch.flowMask());
    }

    @Test
    void bundlesThatDoNotLinkStayClosedTowardEachOther() {
        var set = new BundleSet();
        set.form(RUN);
        set.form(new BundleSegment(new BlockPos(0, 0, 2), new BlockPos(5, 1, 3), Direction.Axis.X));
        var asked = new java.util.HashSet<Direction>();
        var look = look(set, new BlockPos(3, 0, 1), (segment, pos, face) -> {
            asked.add(face);
            return true;
        });
        assertFalse(look.join(Direction.SOUTH));
        assertFalse(look.dense(Direction.SOUTH), "another bundle is no device");
        assertFalse(asked.contains(Direction.SOUTH));
        assertEquals(Set.of(Direction.DOWN), asked, "only outer sides facing no bundle ask about devices");
    }

    @Test
    void aStraight2x2RunHasARingAtEveryOtherLayerBoundary() {
        var set = new BundleSet();
        set.form(RUN);
        // Boundaries at x = 2 and x = 4 fall on the spacing; x = 0 and x = 6 lie outside the run.
        for (int x = 0; x <= 5; x++) {
            var look = look(set, new BlockPos(x, 1, 0), NO_DEVICES);
            assertTrue(look.thick());
            assertEquals(x == 2 || x == 4, look.ringBefore(), "ring before x=" + x);
            assertEquals(x == 1 || x == 3, look.ringAfter(), "ring after x=" + x);
        }
    }

    @Test
    void otherCrossSectionsHaveNoRings() {
        var set = new BundleSet();
        set.form(new BundleSegment(new BlockPos(0, 0, 0), new BlockPos(5, 0, 1), Direction.Axis.X));
        var look = look(set, new BlockPos(1, 0, 0), NO_DEVICES);
        assertFalse(look.thick());
        assertFalse(look.ringAfter());
    }

    @Test
    void aBranchMarksBothItsLayersAcrossTheWholeCrossSection() {
        var set = new BundleSet();
        set.form(RUN);
        set.form(new BundleSegment(new BlockPos(2, 0, 2), new BlockPos(3, 1, 3), Direction.Axis.Z));
        for (var cell : new BundleSegment(new BlockPos(2, 0, 0), new BlockPos(2, 1, 1), Direction.Axis.X).cells()) {
            var look = look(set, cell, NO_DEVICES);
            assertTrue(look.jointAfter(Direction.SOUTH) && !look.jointBefore(Direction.SOUTH), cell.toString());
        }
        var high = look(set, new BlockPos(3, 1, 0), NO_DEVICES);
        assertTrue(high.jointBefore(Direction.SOUTH) && !high.jointAfter(Direction.SOUTH),
                "the row away from the branch shares its corner rings");
        assertFalse(look(set, new BlockPos(1, 0, 0), NO_DEVICES).ringAfter(), "no ring beside a branch");
        assertFalse(look(set, new BlockPos(4, 0, 0), NO_DEVICES).ringBefore(), "no ring beside a branch");
        var branch = look(set, new BlockPos(2, 0, 2), NO_DEVICES);
        assertEquals(0, branch.jointsBefore() | branch.jointsAfter(), "the branch's own end is no joint");
    }

    @Test
    void aStraight3x3RunHasARingInsideEveryThirdLayer() {
        var set = new BundleSet();
        set.form(new BundleSegment(new BlockPos(-2, 0, 0), new BlockPos(7, 2, 2), Direction.Axis.X));
        for (int x = -2; x <= 7; x++) {
            var look = look(set, new BlockPos(x, 1, 2), NO_DEVICES);
            assertEquals(3, look.side());
            assertEquals(Math.floorMod(x, 3) == 0, look.ringInside(), "ring in x=" + x);
            assertFalse(look.ringBefore() || look.ringAfter(), "3x3 rings stand inside a layer");
        }
    }

    @Test
    void no3x3RingStandsInARunsEndLayer() {
        var set = new BundleSet();
        set.form(new BundleSegment(new BlockPos(0, 0, 0), new BlockPos(9, 2, 2), Direction.Axis.X));
        for (int x = 0; x <= 9; x++) {
            assertEquals(x == 3 || x == 6, look(set, new BlockPos(x, 1, 1), NO_DEVICES).ringInside(), "ring in x=" + x);
        }
    }

    @Test
    void a3x3BranchMarksItsThreeLayersFirstMiddleAndLast() {
        var set = new BundleSet();
        set.form(new BundleSegment(new BlockPos(0, 0, 0), new BlockPos(8, 2, 2), Direction.Axis.X));
        set.form(new BundleSegment(new BlockPos(3, 0, 3), new BlockPos(5, 2, 5), Direction.Axis.Z));
        for (int y = 0; y <= 2; y++) {
            for (int z = 0; z <= 2; z++) {
                var first = look(set, new BlockPos(3, y, z), NO_DEVICES);
                assertTrue(!first.jointBefore(Direction.SOUTH) && first.jointAfter(Direction.SOUTH));
                var middle = look(set, new BlockPos(4, y, z), NO_DEVICES);
                assertTrue(middle.jointBefore(Direction.SOUTH) && middle.jointAfter(Direction.SOUTH));
                var last = look(set, new BlockPos(5, y, z), NO_DEVICES);
                assertTrue(last.jointBefore(Direction.SOUTH) && !last.jointAfter(Direction.SOUTH));
                assertFalse(first.ringInside() || middle.ringInside() || last.ringInside(), "no ring in a joint");
            }
        }
        assertTrue(look(set, new BlockPos(6, 0, 0), NO_DEVICES).ringInside(), "the next layer keeps its ring");
    }

    @Test
    void anOrdinaryCableHasNoBundleLook() {
        var set = new BundleSet();
        set.form(RUN);
        assertTrue(BundleLook.of(new BlockPos(6, 0, 0), cell -> set.at(cell).map(BundleSet.Membership::segment),
                NO_DEVICES).isEmpty());
    }
}
