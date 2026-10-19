package space.controlnet.ae2federation.bundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.router.CableVisualConnections;

/** A 2x2 bundle's one beam: drawn by each layer's corner cell, two layers long where a branch leaves. */
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
                () -> look(set, pos.relative(segment.axis(), 1)));
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
    void otherCrossSectionsDrawABeamPerCell() {
        var set = new BundleSet();
        set.form(new BundleSegment(new BlockPos(0, 0, 0), new BlockPos(5, 0, 2), Direction.Axis.X));
        assertTrue(beam(set, new BlockPos(2, 0, 1)).isEmpty());
    }
}
