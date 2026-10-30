package space.controlnet.ae2federation.bundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.router.CableVisualConnections;

/** A 2x2 or 3x3 bundle's one beam: drawn by each layer's corner cell, as many layers long as it is wide at a branch. */
final class BundleBeamTest {
    /** 2x2 along X, x 0..5, y 0..1, z 0..1, with a 2x2 branch south over x 2..3. */
    private static final BundleSegment RUN = new BundleSegment(new BlockPos(0, 0, 0), new BlockPos(5, 1, 1),
            Direction.Axis.X);
    private static final BundleSegment BRANCH = new BundleSegment(new BlockPos(2, 0, 2), new BlockPos(3, 1, 3),
            Direction.Axis.Z);
    private static final BundleLook.Contacts NO_DEVICES = (segment, pos, face) -> false;

    private static Optional<BundleBeam> beam(BundleSet set, BlockPos pos) {
        var segment = set.at(pos).orElseThrow().segment();
        return BundleBeam.of(pos, segment, look(set, pos).orElseThrow(),
                layers -> look(set, pos.relative(segment.axis(), layers)));
    }

    private static Optional<BundleLook> look(BundleSet set, BlockPos pos) {
        return BundleLook.of(pos, cell -> set.at(cell).map(BundleSet.Membership::segment), NO_DEVICES);
    }

    private static int mask(Direction... sides) {
        int mask = 0;
        for (var side : sides) mask |= 1 << List.of(CableVisualConnections.DIRECTIONS).indexOf(side);
        return mask;
    }

    private static BundleSet withBranch() {
        var set = new BundleSet();
        set.form(RUN);
        set.form(BRANCH);
        return set;
    }

    @Test
    void theBeamTurnsIntoADeviceAlongASide() {
        var set = new BundleSet();
        set.form(RUN);
        // A device takes the whole top row at x 3: the top cells' faces there, not the corner cell's own.
        BundleLook.Contacts top = (segment, pos, face) -> face == Direction.UP && pos.getX() == 3;
        var segment = set.at(new BlockPos(3, 0, 0)).orElseThrow().segment();
        java.util.function.Function<BlockPos, Optional<BundleLook>> looks =
                pos -> BundleLook.of(pos, cell -> set.at(cell).map(BundleSet.Membership::segment), top);
        var corner = new BlockPos(3, 0, 0);
        var beam = BundleBeam.of(corner, segment, looks.apply(corner).orElseThrow(),
                layers -> looks.apply(corner.relative(Direction.Axis.X, layers))).orElseThrow();
        assertEquals(mask(Direction.EAST, Direction.WEST, Direction.UP), beam.mask());
        var next = new BlockPos(4, 0, 0);
        assertEquals(mask(Direction.EAST, Direction.WEST), BundleBeam.of(next, segment, looks.apply(next).orElseThrow(),
                layers -> looks.apply(next.relative(Direction.Axis.X, layers))).orElseThrow().mask());
    }

    /** Devices taking the whole top row at x {@code from..to}: one body, or one device per layer. */
    private static BundleLook.Contacts topDevices(int from, int to, boolean oneBody) {
        return new BundleLook.Contacts() {
            @Override
            public boolean whole(BundleSegment segment, BlockPos pos, Direction face) {
                return face == Direction.UP && pos.getX() >= from && pos.getX() <= to;
            }

            @Override
            public boolean oneBody(BlockPos pos, Direction face, Direction toward) {
                return oneBody;
            }
        };
    }

    /** The beam of the corner cell of layer {@code x} in {@link #RUN}, with {@code contacts}. */
    private static BundleBeam cornerBeam(BundleLook.Contacts contacts, int x) {
        var set = new BundleSet();
        set.form(RUN);
        java.util.function.Function<BlockPos, Optional<BundleLook>> looks =
                pos -> BundleLook.of(pos, cell -> set.at(cell).map(BundleSet.Membership::segment), contacts);
        var corner = new BlockPos(x, 0, 0);
        return BundleBeam.of(corner, RUN, looks.apply(corner).orElseThrow(),
                layers -> looks.apply(corner.relative(Direction.Axis.X, layers))).orElseThrow();
    }

    @Test
    void theBeamTurnsOnceIntoADeviceOverTwoLayersBetweenThem() {
        var contacts = topDevices(2, 3, true);
        var through = mask(Direction.EAST, Direction.WEST);
        assertEquals(new BundleBeam(Direction.Axis.X, through, 1), cornerBeam(contacts, 1));
        // One box over both layers turns at its middle, the boundary between them; the second layer draws none.
        assertEquals(new BundleBeam(Direction.Axis.X, through | mask(Direction.UP), 2), cornerBeam(contacts, 2));
        assertEquals(new BundleBeam(Direction.Axis.X, 0, 0), cornerBeam(contacts, 3));
        assertEquals(new BundleBeam(Direction.Axis.X, through, 1), cornerBeam(contacts, 4));
    }

    @Test
    void theBeamTurnsOnceIntoADeviceOverThreeLayersAtTheMiddleOne() {
        var contacts = topDevices(1, 3, true);
        var through = mask(Direction.EAST, Direction.WEST);
        assertEquals(new BundleBeam(Direction.Axis.X, through, 1), cornerBeam(contacts, 1));
        assertEquals(new BundleBeam(Direction.Axis.X, through | mask(Direction.UP), 1), cornerBeam(contacts, 2));
        assertEquals(new BundleBeam(Direction.Axis.X, through, 1), cornerBeam(contacts, 3));
    }

    @Test
    void theBeamTurnsIntoEachOfTwoDevicesSideBySide() {
        var contacts = topDevices(2, 3, false);
        var turning = mask(Direction.EAST, Direction.WEST, Direction.UP);
        assertEquals(new BundleBeam(Direction.Axis.X, turning, 1), cornerBeam(contacts, 2));
        assertEquals(new BundleBeam(Direction.Axis.X, turning, 1), cornerBeam(contacts, 3));
    }

    @Test
    void aCellOffItsLayersCornerKnowsFromItsOwnSegmentThatItDrawsNoBeam() {
        // A cell's renderer skips reading its neighbours when its own segment says it draws nothing: that must be
        // exactly what the full look says, at runs, branches, turns and ends, for 2x2, 3x3 and non-square bundles.
        var sets = List.of(withBranch(), new BundleSet(), new BundleSet(), new BundleSet());
        sets.get(1).form(new BundleSegment(new BlockPos(0, 0, 0), new BlockPos(8, 2, 2), Direction.Axis.X));
        sets.get(1).form(new BundleSegment(new BlockPos(3, 3, 0), new BlockPos(5, 6, 2), Direction.Axis.Y));
        sets.get(2).form(new BundleSegment(new BlockPos(0, 0, 0), new BlockPos(1, 1, 4), Direction.Axis.Z));
        sets.get(2).form(new BundleSegment(new BlockPos(2, 0, 3), new BlockPos(5, 1, 4), Direction.Axis.X));
        sets.get(3).form(new BundleSegment(new BlockPos(0, 0, 0), new BlockPos(4, 0, 1), Direction.Axis.X));
        int skipped = 0;
        for (var set : sets) {
            for (var segment : set.segments()) {
                for (var cell : segment.cells()) {
                    var own = BundleBeam.offCorner(cell, segment);
                    if (own == null) continue;
                    skipped++;
                    assertEquals(beam(set, cell), Optional.of(own), cell.toString());
                }
            }
        }
        assertTrue(skipped > 50, "cells checked: " + skipped);
        assertEquals(null, BundleBeam.offCorner(new BlockPos(1, 0, 1),
                new BundleSegment(new BlockPos(0, 0, 0), new BlockPos(4, 0, 1), Direction.Axis.X)),
                "a 1x2 bundle draws a beam in every cell");
    }

    @Test
    void eachLayersCornerCellDrawsTheBeamAcrossTheWholeCrossSection() {
        var set = withBranch();
        var beam = beam(set, new BlockPos(4, 0, 0)).orElseThrow();
        assertEquals(new BundleBeam(Direction.Axis.X, mask(Direction.EAST, Direction.WEST), 1), beam);
        assertEquals(2, beam.extent(Direction.Axis.Y));
        assertEquals(1, beam.extent(Direction.Axis.X));
        for (var other : List.of(new BlockPos(4, 1, 0), new BlockPos(4, 0, 1), new BlockPos(4, 1, 1))) {
            assertEquals(0, beam(set, other).orElseThrow().length(), other.toString());
        }
        assertEquals(mask(Direction.EAST), beam(set, new BlockPos(0, 0, 0)).orElseThrow().mask(),
                "a closed end stops the beam");
    }

    @Test
    void aBranchsTwoLayersShareOneJunction() {
        var set = withBranch();
        assertEquals(new BundleBeam(Direction.Axis.X, mask(Direction.EAST, Direction.WEST, Direction.SOUTH), 2),
                beam(set, new BlockPos(2, 0, 0)).orElseThrow());
        assertEquals(0, beam(set, new BlockPos(3, 0, 0)).orElseThrow().length());
        assertEquals(new BundleBeam(Direction.Axis.Z, mask(Direction.NORTH, Direction.SOUTH), 1),
                beam(set, new BlockPos(2, 0, 2)).orElseThrow());
    }

    @Test
    void aTurnHasNoArmPastTheEnd() {
        var set = new BundleSet();
        set.form(new BundleSegment(new BlockPos(0, 0, 0), new BlockPos(3, 1, 1), Direction.Axis.X));
        set.form(BRANCH);
        assertEquals(new BundleBeam(Direction.Axis.X, mask(Direction.WEST, Direction.SOUTH), 2),
                beam(set, new BlockPos(2, 0, 0)).orElseThrow());
    }

    @Test
    void a3x3BundleDrawsOneBeamThreeWideAndThreeLayersLongAtABranch() {
        var set = new BundleSet();
        set.form(new BundleSegment(new BlockPos(0, 0, 0), new BlockPos(8, 2, 2), Direction.Axis.X));
        set.form(new BundleSegment(new BlockPos(3, 0, 3), new BlockPos(5, 2, 5), Direction.Axis.Z));
        var run = beam(set, new BlockPos(1, 0, 0)).orElseThrow();
        assertEquals(new BundleBeam(Direction.Axis.X, mask(Direction.EAST, Direction.WEST), 1, 3), run);
        assertEquals(3, run.extent(Direction.Axis.Z));
        assertEquals(0, beam(set, new BlockPos(1, 1, 1)).orElseThrow().length(), "only the corner cell draws");
        assertEquals(new BundleBeam(Direction.Axis.X, mask(Direction.EAST, Direction.WEST, Direction.SOUTH), 3, 3),
                beam(set, new BlockPos(3, 0, 0)).orElseThrow());
        assertEquals(0, beam(set, new BlockPos(4, 0, 0)).orElseThrow().length());
        assertEquals(0, beam(set, new BlockPos(5, 0, 0)).orElseThrow().length());
    }

    @Test
    void a3x3TurnHasNoArmPastTheEnd() {
        var set = new BundleSet();
        set.form(new BundleSegment(new BlockPos(0, 0, 0), new BlockPos(5, 2, 2), Direction.Axis.X));
        set.form(new BundleSegment(new BlockPos(3, 0, 3), new BlockPos(5, 2, 5), Direction.Axis.Z));
        assertEquals(new BundleBeam(Direction.Axis.X, mask(Direction.WEST, Direction.SOUTH), 3, 3),
                beam(set, new BlockPos(3, 0, 0)).orElseThrow());
    }

    @Test
    void otherCrossSectionsDrawABeamPerCell() {
        var set = new BundleSet();
        set.form(new BundleSegment(new BlockPos(0, 0, 0), new BlockPos(5, 0, 2), Direction.Axis.X));
        assertTrue(beam(set, new BlockPos(2, 0, 1)).isEmpty());
    }
}
