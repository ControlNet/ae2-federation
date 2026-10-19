package space.controlnet.ae2federation.bundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/** The wrench's three clicks: two corners of the cross-section, then a cable along it for the length. */
final class BundleSelectionTest {
    /** Every cross-section the rule knows, built or not. */
    private static final java.util.function.Predicate<BundleShape> ANY = shape -> true;

    @Test
    void shapesFollowTheUsersTableAndThreeByThreeIsUnlimited() {
        assertEquals(OptionalInt.of(3), BundleShape.of(2, 1).orElseThrow().multiplier());
        assertEquals(OptionalInt.of(4), BundleShape.of(1, 3).orElseThrow().multiplier());
        assertEquals(OptionalInt.of(6), BundleShape.of(2, 2).orElseThrow().multiplier());
        assertEquals(OptionalInt.of(9), BundleShape.of(3, 2).orElseThrow().multiplier());
        assertEquals(OptionalInt.empty(), BundleShape.of(3, 3).orElseThrow().multiplier());
        assertTrue(BundleShape.of(1, 1).isEmpty(), "one cable is not a bundle");
        assertTrue(BundleShape.of(1, 4).isEmpty(), "at most three a side");
        assertEquals(6, BundleShape.of(3, 2).orElseThrow().area());
    }

    @Test
    void twoCornersInAPlaneFixTheAxisAndTheThirdClickTheLength() {
        var section = BundleSelection.crossSection(new BlockPos(0, 1, 5), new BlockPos(0, 0, 6));
        var fixed = assertInstanceOf(BundleSelection.Section.class, section);
        assertEquals(BundleShape.of(2, 2).orElseThrow(), fixed.shape());

        var box = assertInstanceOf(BundleSelection.Box.class, BundleSelection.box(
                new BlockPos(0, 1, 5), new BlockPos(0, 0, 6), new BlockPos(5, 1, 6), Direction.UP));
        assertEquals(new BundleSegment(new BlockPos(0, 0, 5), new BlockPos(5, 1, 6), Direction.Axis.X), box.segment());
        assertEquals(6, box.segment().length());
    }

    @Test
    void aThirdClickInThePlaneMakesOneLayer() {
        var box = assertInstanceOf(BundleSelection.Box.class, BundleSelection.box(
                new BlockPos(0, 1, 5), new BlockPos(0, 0, 6), new BlockPos(0, 0, 6), Direction.EAST));
        assertEquals(new BundleSegment(new BlockPos(0, 0, 5), new BlockPos(0, 1, 6), Direction.Axis.X), box.segment());
    }

    @Test
    void aLineNeedsTheThirdClickToChooseItsPlane() {
        // Lines are not built today; their rule stays for when they are. 1x2 along Z: the third click above sets the
        // axis Y; one to the east sets X.
        var up = assertInstanceOf(BundleSelection.Box.class, BundleSelection.box(
                new BlockPos(0, 0, 5), new BlockPos(0, 0, 6), new BlockPos(0, 3, 5), Direction.UP, ANY));
        assertEquals(Direction.Axis.Y, up.segment().axis());
        assertEquals(4, up.segment().length());
        var east = assertInstanceOf(BundleSelection.Box.class, BundleSelection.box(
                new BlockPos(0, 0, 5), new BlockPos(0, 0, 6), new BlockPos(2, 0, 6), Direction.UP, ANY));
        assertEquals(Direction.Axis.X, east.segment().axis());
        // On the line itself, the clicked face sets the axis; a face along the line cannot.
        var flat = assertInstanceOf(BundleSelection.Box.class, BundleSelection.box(
                new BlockPos(0, 0, 5), new BlockPos(0, 0, 6), new BlockPos(0, 0, 6), Direction.UP, ANY));
        assertEquals(new BundleSegment(new BlockPos(0, 0, 5), new BlockPos(0, 0, 6), Direction.Axis.Y), flat.segment());
        assertEquals(BundleSelection.Problem.FACE_ALONG_LINE, assertInstanceOf(BundleSelection.Invalid.class,
                BundleSelection.box(new BlockPos(0, 0, 5), new BlockPos(0, 0, 6), new BlockPos(0, 0, 6),
                        Direction.SOUTH, ANY)).problem());
    }

    @Test
    void onlySquareCrossSectionsCanBeBuiltToday() {
        assertEquals(BundleSelection.Problem.NOT_BUILDABLE, problem(BundleSelection.crossSection(
                new BlockPos(0, 0, 5), new BlockPos(0, 0, 6))), "a 1x2 line");
        assertEquals(BundleSelection.Problem.NOT_BUILDABLE, problem(BundleSelection.crossSection(
                new BlockPos(0, 0, 4), new BlockPos(0, 0, 6))), "a 1x3 line");
        assertEquals(BundleSelection.Problem.NOT_BUILDABLE, problem(BundleSelection.crossSection(
                new BlockPos(0, 0, 4), new BlockPos(0, 1, 6))), "a 2x3 plane");
        assertEquals(BundleSelection.Problem.NOT_BUILDABLE, problem(BundleSelection.box(
                new BlockPos(0, 0, 5), new BlockPos(0, 0, 6), new BlockPos(2, 0, 6), Direction.UP)), "the box too");
        assertEquals(BundleShape.of(3, 3).orElseThrow(), assertInstanceOf(BundleSelection.Section.class,
                BundleSelection.crossSection(new BlockPos(0, 0, 4), new BlockPos(0, 2, 6))).shape());
        // The rule itself still knows them.
        assertEquals(BundleShape.of(2, 3).orElseThrow(), assertInstanceOf(BundleSelection.Section.class,
                BundleSelection.crossSection(new BlockPos(0, 0, 4), new BlockPos(0, 1, 6), ANY)).shape());
    }

    @Test
    void badSelectionsSayWhy() {
        assertEquals(BundleSelection.Problem.SINGLE_CABLE, problem(BundleSelection.crossSection(
                new BlockPos(1, 1, 1), new BlockPos(1, 1, 1))));
        assertEquals(BundleSelection.Problem.TOO_WIDE, problem(BundleSelection.crossSection(
                new BlockPos(0, 0, 0), new BlockPos(0, 3, 0))));
        assertEquals(BundleSelection.Problem.NOT_FLAT, problem(BundleSelection.crossSection(
                new BlockPos(0, 0, 0), new BlockPos(1, 1, 1))));
        assertEquals(BundleSelection.Problem.NOT_ALONG, problem(BundleSelection.box(
                new BlockPos(0, 0, 5), new BlockPos(0, 1, 6), new BlockPos(4, 3, 6), Direction.UP)));
        assertEquals(BundleSelection.Problem.TOO_LONG, problem(BundleSelection.box(new BlockPos(0, 0, 5),
                new BlockPos(0, 1, 6), new BlockPos(BundleSelection.MAX_LENGTH, 1, 6), Direction.UP)));
    }

    private static BundleSelection.Problem problem(BundleSelection.Result result) {
        return assertInstanceOf(BundleSelection.Invalid.class, result).problem();
    }
}
