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
    void anOrdinaryCableHasNoBundleLook() {
        var set = new BundleSet();
        set.form(RUN);
        assertTrue(BundleLook.of(new BlockPos(6, 0, 0), cell -> set.at(cell).map(BundleSet.Membership::segment),
                NO_DEVICES).isEmpty());
    }
}
