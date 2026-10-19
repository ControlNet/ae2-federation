package space.controlnet.ae2federation.bundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/** Forming, extending, branching, breaking and merging bundles, as cells only. */
final class BundleSetTest {
    /** 2x2 along X, x 0..5, y 0..1, z 0..1. */
    private static final BundleSegment RUN = new BundleSegment(new BlockPos(0, 0, 0), new BlockPos(5, 1, 1),
            Direction.Axis.X);

    @Test
    void aFormedBundleHoldsEveryCellOfItsBox() {
        var set = new BundleSet();
        var bundle = set.form(RUN);
        assertEquals(24, set.cells(bundle.id()).size());
        assertEquals(bundle.id(), set.at(new BlockPos(3, 1, 0)).orElseThrow().bundle().id());
        assertTrue(set.at(new BlockPos(6, 0, 0)).isEmpty());
        assertEquals(BundleShape.of(2, 2).orElseThrow(), bundle.shape());
    }

    @Test
    void extendingAddsOneLayerAtAnEndFace() {
        var set = new BundleSet();
        set.form(RUN);
        var plan = assertInstanceOf(BundleSet.Placement.class, set.planExtend(new BlockPos(5, 0, 1), Direction.EAST));
        assertEquals(Set.of(new BlockPos(6, 0, 0), new BlockPos(6, 1, 0), new BlockPos(6, 0, 1),
                new BlockPos(6, 1, 1)), new HashSet<>(plan.cells()));
        assertEquals(4, plan.cost());
        set.apply(plan);
        assertEquals(7, set.at(new BlockPos(6, 1, 1)).orElseThrow().segment().length());
        assertInstanceOf(BundleSet.Refused.class, set.planExtend(new BlockPos(3, 1, 1), Direction.UP),
                "an extension starts only from an end face");
    }

    @Test
    void aBranchTakesAsManyLayersAsItsOtherSideNeeds() {
        var set = new BundleSet();
        set.form(RUN);
        // From the south side (+Z) the side is 2 high, so a 2x2 branch takes two layers centred on the cursor:
        // a click on the west quarter of x=3 covers x 2..3.
        var plan = assertInstanceOf(BundleSet.Placement.class,
                set.planBranch(new BlockPos(3, 0, 1), Direction.SOUTH, 0.25));
        assertEquals(new BundleSegment(new BlockPos(2, 0, 2), new BlockPos(3, 1, 2), Direction.Axis.Z), plan.segment());
        assertEquals(4, plan.cost());
        set.apply(plan);
        var branch = set.at(new BlockPos(2, 1, 2)).orElseThrow();
        assertEquals(set.at(new BlockPos(0, 0, 0)).orElseThrow().bundle().id(), branch.bundle().id());
        assertTrue(set.linked(new BlockPos(2, 1, 1), new BlockPos(2, 1, 2)), "the branch joins its parent");
        // A branch near an end stays on the run.
        var end = assertInstanceOf(BundleSet.Placement.class,
                set.planBranch(new BlockPos(5, 0, 0), Direction.NORTH, 0.5));
        assertEquals(new BlockPos(4, 0, -1), end.segment().min());
    }

    @Test
    void aFlatBundleBranchesFromItsNarrowSideOverTwoLayers() {
        var set = new BundleSet();
        // 1x2: one high (Y), two wide (Z), along X.
        set.form(new BundleSegment(new BlockPos(0, 0, 0), new BlockPos(5, 0, 1), Direction.Axis.X));
        var narrow = assertInstanceOf(BundleSet.Placement.class,
                set.planBranch(new BlockPos(2, 0, 1), Direction.SOUTH, 0.75));
        assertEquals(new BundleSegment(new BlockPos(2, 0, 2), new BlockPos(3, 0, 2), Direction.Axis.Z), narrow.segment());
        assertEquals(2, narrow.cost());
        var wide = assertInstanceOf(BundleSet.Placement.class,
                set.planBranch(new BlockPos(2, 0, 1), Direction.UP, 0.75));
        assertEquals(new BundleSegment(new BlockPos(2, 1, 0), new BlockPos(2, 1, 1), Direction.Axis.Y), wide.segment());
    }

    @Test
    void aBranchNeedsRoomAlongTheRun() {
        var set = new BundleSet();
        set.form(new BundleSegment(new BlockPos(0, 0, 0), new BlockPos(0, 1, 1), Direction.Axis.X));
        assertEquals(BundleSet.Problem.TOO_SHORT,
                assertInstanceOf(BundleSet.Refused.class,
                        set.planBranch(new BlockPos(0, 0, 1), Direction.SOUTH, 0.5)).problem());
    }

    @Test
    void breakingACellRevertsItsLayerAndSplitsTheBundle() {
        var set = new BundleSet();
        set.form(RUN);
        var removal = set.remove(new BlockPos(2, 1, 1));
        assertEquals(Set.of(new BlockPos(2, 0, 0), new BlockPos(2, 1, 0), new BlockPos(2, 0, 1)),
                new HashSet<>(removal.reverted()));
        var west = set.at(new BlockPos(0, 0, 0)).orElseThrow();
        var east = set.at(new BlockPos(5, 0, 0)).orElseThrow();
        assertNotEquals(west.bundle().id(), east.bundle().id());
        assertEquals(2, west.segment().length());
        assertEquals(3, east.segment().length());
        assertTrue(set.at(new BlockPos(2, 0, 0)).isEmpty(), "the rest of the layer is ordinary cable");
        assertTrue(removal.touched().contains(new BlockPos(5, 1, 1)), "every cell of the old bundle resyncs");
        assertFalse(removal.touched().contains(new BlockPos(2, 1, 1)), "the broken cell is gone");
    }

    @Test
    void aBranchWhoseBaseLosesALayerBecomesItsOwnBundle() {
        var set = new BundleSet();
        set.form(RUN);
        set.apply((BundleSet.Placement) set.planBranch(new BlockPos(3, 0, 1), Direction.SOUTH, 0.25));
        set.apply((BundleSet.Placement) set.planExtend(new BlockPos(2, 0, 2), Direction.SOUTH));
        set.remove(new BlockPos(3, 0, 0));
        var branch = set.at(new BlockPos(2, 0, 3)).orElseThrow();
        assertNotEquals(set.at(new BlockPos(0, 0, 0)).orElseThrow().bundle().id(), branch.bundle().id());
        assertFalse(set.linked(new BlockPos(2, 0, 1), new BlockPos(2, 0, 2)), "half a base is no junction");
    }

    @Test
    void repairingABrokenLayerMergesTheTwoHalvesAgain() {
        var set = new BundleSet();
        set.form(RUN);
        set.remove(new BlockPos(2, 1, 1));
        var repaired = set.form(new BundleSegment(new BlockPos(2, 0, 0), new BlockPos(2, 1, 1), Direction.Axis.X));
        var whole = set.at(new BlockPos(0, 0, 0)).orElseThrow();
        assertEquals(repaired.id(), whole.bundle().id());
        assertEquals(6, whole.segment().length(), "aligned ends merge into one run");
        assertEquals(1, set.bundles().size());
    }

    @Test
    void bundlesSideBySideStaySeparate() {
        var set = new BundleSet();
        var a = set.form(RUN);
        var b = set.form(new BundleSegment(new BlockPos(0, 0, 2), new BlockPos(5, 1, 3), Direction.Axis.X));
        assertNotEquals(a.id(), b.id());
        assertFalse(set.linked(new BlockPos(1, 0, 1), new BlockPos(1, 0, 2)));
        assertTrue(set.linked(new BlockPos(1, 0, 1), new BlockPos(1, 0, 0)));
        assertTrue(set.linked(new BlockPos(1, 0, 1), new BlockPos(2, 0, 1)));
    }

    @Test
    void aBundleFormedAgainstASideJoinsLikeABranch() {
        var set = new BundleSet();
        set.form(RUN);
        set.form(new BundleSegment(new BlockPos(4, 0, 2), new BlockPos(5, 1, 6), Direction.Axis.Z));
        assertEquals(1, set.bundles().size(), "a full-width, same-shape contact is a branch however it was built");
        // Offset by one layer upward it no longer covers the side's full height.
        var offset = new BundleSet();
        offset.form(RUN);
        offset.form(new BundleSegment(new BlockPos(4, 1, 2), new BlockPos(5, 2, 6), Direction.Axis.Z));
        assertEquals(2, offset.bundles().size());
    }

    @Test
    void occupiedCellsRefuseAPlacement() {
        var set = new BundleSet();
        set.form(RUN);
        set.form(new BundleSegment(new BlockPos(6, 0, 0), new BlockPos(6, 0, 1), Direction.Axis.Y));
        assertEquals(BundleSet.Problem.OCCUPIED, assertInstanceOf(BundleSet.Refused.class,
                set.planExtend(new BlockPos(5, 0, 0), Direction.EAST)).problem());
    }

    @Test
    void aDifferentShapeNeverMerges() {
        var set = new BundleSet();
        set.form(RUN);
        set.form(new BundleSegment(new BlockPos(6, 0, 0), new BlockPos(8, 0, 1), Direction.Axis.X));
        assertEquals(2, set.bundles().size());
    }
}
