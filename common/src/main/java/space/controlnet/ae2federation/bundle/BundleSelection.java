package space.controlnet.ae2federation.bundle;

import java.util.ArrayList;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The wrench's three clicks. The first two are opposite corners of the cross-section; the third is a cable along it
 * and sets the length. A third click inside the cross-section's plane makes a single layer. A 1xN cross-section is a
 * line, so the third click also picks its plane: the axis it leaves the line along, or, on the line itself, the axis of
 * the clicked face.
 */
public final class BundleSelection {
    public static final int MAX_LENGTH = 64;

    private BundleSelection() {
    }

    public enum Problem {
        SINGLE_CABLE,
        TOO_WIDE,
        NOT_FLAT,
        NOT_ALONG,
        FACE_ALONG_LINE,
        TOO_LONG
    }

    public sealed interface Result permits Section, Box, Invalid {
    }

    /** A valid cross-section; {@link #axis} is empty while it is a line whose plane the third click still picks. */
    public record Section(BlockPos min, BlockPos max, BundleShape shape) implements Result {
        public Optional<Direction.Axis> axis() {
            var flat = flatAxes(min, max);
            return flat.size() == 1 ? Optional.of(flat.get(0)) : Optional.empty();
        }
    }

    public record Box(BundleSegment segment) implements Result {
    }

    public record Invalid(Problem problem) implements Result {
    }

    /** The cross-section between two corner clicks. */
    public static Result crossSection(BlockPos first, BlockPos second) {
        var min = BlockPos.min(first, second);
        var max = BlockPos.max(first, second);
        var flat = flatAxes(min, max);
        if (flat.size() == 3) return new Invalid(Problem.SINGLE_CABLE);
        for (var axis : Direction.Axis.values()) {
            if (max.get(axis) - min.get(axis) + 1 > BundleShape.MAX_SIDE) return new Invalid(Problem.TOO_WIDE);
        }
        if (flat.isEmpty()) return new Invalid(Problem.NOT_FLAT);
        // One flat axis: an MxN plane whose sides are the other two. Two flat axes: a 1xN line.
        var shape = flat.size() == 1
                ? BundleShape.of(extent(min, max, BundleSegment.across(flat.get(0))[0]),
                        extent(min, max, BundleSegment.across(flat.get(0))[1]))
                : BundleShape.of(1, longest(min, max));
        return shape.<Result>map(s -> new Section(min, max, s)).orElseGet(() -> new Invalid(Problem.TOO_WIDE));
    }

    /** The bundle box from both corners, the third click and the face that click hit. */
    public static Result box(BlockPos first, BlockPos second, BlockPos third, Direction face) {
        var result = crossSection(first, second);
        if (!(result instanceof Section section)) return result;
        var min = section.min();
        var max = section.max();
        var flat = flatAxes(min, max);
        var leaving = new ArrayList<Direction.Axis>();
        for (var axis : Direction.Axis.values()) {
            int value = third.get(axis);
            boolean inside = value >= min.get(axis) && value <= max.get(axis);
            if (inside) continue;
            if (!flat.contains(axis)) return new Invalid(Problem.NOT_ALONG);
            leaving.add(axis);
        }
        Direction.Axis axis;
        if (leaving.size() > 1) return new Invalid(Problem.NOT_ALONG);
        if (leaving.size() == 1) {
            axis = leaving.get(0);
        } else if (flat.size() == 1) {
            axis = flat.get(0);
        } else if (flat.contains(face.getAxis())) {
            axis = face.getAxis();
        } else {
            return new Invalid(Problem.FACE_ALONG_LINE);
        }
        var boxMin = BundleSegment.with(min, axis, Math.min(min.get(axis), third.get(axis)));
        var boxMax = BundleSegment.with(max, axis, Math.max(max.get(axis), third.get(axis)));
        var segment = new BundleSegment(boxMin, boxMax, axis);
        if (segment.length() > MAX_LENGTH) return new Invalid(Problem.TOO_LONG);
        return new Box(segment);
    }

    /** The axes along which the box is one cell thick. */
    private static java.util.List<Direction.Axis> flatAxes(BlockPos min, BlockPos max) {
        var flat = new ArrayList<Direction.Axis>(3);
        for (var axis : Direction.Axis.values()) {
            if (min.get(axis) == max.get(axis)) flat.add(axis);
        }
        return flat;
    }

    private static int extent(BlockPos min, BlockPos max, Direction.Axis axis) {
        return max.get(axis) - min.get(axis) + 1;
    }

    private static int longest(BlockPos min, BlockPos max) {
        int longest = 1;
        for (var axis : Direction.Axis.values()) longest = Math.max(longest, extent(min, max, axis));
        return longest;
    }
}
