package space.controlnet.ae2federation.bundle;

import java.util.Optional;
import java.util.function.IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.router.CableVisualConnections;

/**
 * Where a 2x2 or 3x3 bundle's one beam runs: down the middle of its cross-section, not one beam per cell. The cell at
 * the low corner of each layer draws it, as an ordinary cable's beam stretched over a box from that cell: {@code side}
 * cells wide across the bundle and {@code length} layers along it. Where a branch leaves, the box takes every layer the
 * branch covers, so the junction sits on the branch's own beam. The beam also turns toward a device that takes a whole
 * side row of its layer, as an ordinary cable's beam runs into a device, once for every stretch of layers one device
 * body takes: at the middle layer, or between the two middle ones, whose box then covers both. Every other cell draws
 * none ({@code length} 0), and neither does a layer an earlier layer's box covers.
 *
 * @param mask the sides the stretched beam leaves through, as {@link CableVisualConnections#mask} encodes them
 */
public record BundleBeam(Direction.Axis axis, int mask, int length, int side) {
    /** A 2x2 bundle's beam. */
    public BundleBeam(Direction.Axis axis, int mask, int length) {
        this(axis, mask, length, 2);
    }

    /**
     * The beam of the cell at {@code pos}, or empty when the cell is not in a 2x2 or 3x3 bundle and draws its own;
     * {@code layer} gives the look of the cell that many layers further along the axis.
     */
    public static Optional<BundleBeam> of(BlockPos pos, BundleSegment segment, BundleLook look,
            IntFunction<Optional<BundleLook>> layer) {
        if (!look.thick()) return Optional.empty();
        var axis = look.axis();
        int side = look.side();
        var none = new BundleBeam(axis, 0, 0, side);
        if (offCorner(pos, segment) != null) return Optional.of(none);
        var before = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
        var after = before.getOpposite();
        if (look.jointsBefore() != 0) return Optional.of(none);
        if (look.jointsAfter() == 0) return Optional.of(plain(look, layer, before, after, side));
        int mask = (leaves(look, before) ? bit(before) : 0) | contacts(look);
        // A branch covers as many layers as the bundle is wide; the far one decides whether the run goes on.
        if (layer.apply(side - 1).filter(far -> leaves(far, after)).isPresent()) mask |= bit(after);
        for (int next = 1; next < side; next++) mask |= layer.apply(next).map(BundleBeam::contacts).orElse(0);
        for (var toward : Direction.values()) {
            if (look.jointAfter(toward)) mask |= bit(toward);
        }
        return Optional.of(new BundleBeam(axis, mask, side, side));
    }

    /**
     * The beam of a cell that draws none because it lies off the low corner of its layer in a 2x2 or 3x3 bundle, or
     * null for any other cell. It needs only the cell's own segment, so such a cell need not read its neighbours.
     */
    public static @Nullable BundleBeam offCorner(BlockPos pos, BundleSegment segment) {
        int side = segment.shape().filter(shape -> shape.narrow() == shape.wide()).map(BundleShape::narrow).orElse(0);
        if (side == 0) return null;
        for (var across : BundleSegment.across(segment.axis())) {
            if (pos.get(across) != segment.min().get(across)) return new BundleBeam(segment.axis(), 0, 0, side);
        }
        return null;
    }

    /**
     * The beam of a layer with no branch. Each device body that takes its side rows turns the beam once, at the middle
     * layer of the stretch it takes or at the boundary of its two middle ones. Two layers share one box for such a
     * boundary only when every body touching either layer turns there; otherwise the body turns at its lower middle
     * layer, so no turn lands where its body is not.
     */
    private static BundleBeam plain(BundleLook look, IntFunction<Optional<BundleLook>> layer, Direction before,
            Direction after, int side) {
        var axis = look.axis();
        var here = Turns.of(look, 0, layer);
        if (here.upper() != 0 && layer.apply(-1).filter(BundleBeam::branchless)
                .filter(previous -> Turns.paired(previous, Turns.of(previous, -1, layer), look, here)).isPresent()) {
            return new BundleBeam(axis, 0, 0, side);
        }
        int mask = leaves(look, before) ? bit(before) : 0;
        var next = here.lower() == 0 ? Optional.<BundleLook>empty() : layer.apply(1).filter(BundleBeam::branchless);
        if (next.filter(upper -> Turns.paired(look, here, upper, Turns.of(upper, 1, layer))).isPresent()) {
            // The two middle layers share one box, which turns at their boundary.
            if (leaves(next.get(), after)) mask |= bit(after);
            return new BundleBeam(axis, mask | here.lower(), 2, side);
        }
        if (leaves(look, after)) mask |= bit(after);
        return new BundleBeam(axis, mask | here.middle() | here.lower(), 1, side);
    }

    /**
     * The sides of one layer whose device body turns the beam: at the middle layer of the stretch the body takes
     * ({@code middle}), or at the boundary of its two middle layers, this layer being the lower or the upper one.
     */
    private record Turns(int middle, int lower, int upper) {
        /** The turns of {@code look}, the layer {@code offset} layers along from the one {@code layer} counts from. */
        static Turns of(BundleLook look, int offset, IntFunction<Optional<BundleLook>> layer) {
            int middle = 0;
            int lower = 0;
            int upper = 0;
            for (var side : Direction.values()) {
                if (!look.contact(side)) continue;
                int bit = bit(side);
                int back = run(look, offset, -1, side, layer);
                int stretch = back + 1 + run(look, offset, 1, side, layer);
                int half = (stretch - 1) / 2;
                if (stretch % 2 == 1) {
                    if (back == half) middle |= bit;
                } else if (back == half) {
                    lower |= bit;
                } else if (back == half + 1) {
                    upper |= bit;
                }
            }
            return new Turns(middle, lower, upper);
        }

        /** Whether the boundary between two adjacent layers is where every body touching either of them turns. */
        static boolean paired(BundleLook lowerLook, Turns lower, BundleLook upperLook, Turns upper) {
            return lower.lower() != 0 && lower.lower() == contacts(lowerLook) && upper.upper() == contacts(upperLook)
                    && lower.lower() == upper.upper();
        }

        /** How many layers further {@code step}'s way the body on {@code side} still takes, without a branch. */
        private static int run(BundleLook look, int offset, int step, Direction side,
                IntFunction<Optional<BundleLook>> layer) {
            // BundleLook's masks count sides by their 3D data value.
            int bit = 1 << side.get3DDataValue();
            int count = 0;
            for (var at = look; ((step < 0 ? at.contactsBefore() : at.contactsAfter()) & bit) != 0; count++) {
                var neighbour = layer.apply(offset + step * (count + 1)).filter(BundleBeam::branchless);
                if (neighbour.isEmpty()) break;
                at = neighbour.get();
            }
            return count;
        }
    }

    private static boolean branchless(BundleLook look) {
        return (look.jointsBefore() | look.jointsAfter()) == 0;
    }

    /** How far the box reaches along {@code toward}'s axis, in blocks: across the bundle, or its length along it. */
    public int extent(Direction.Axis toward) {
        return toward == axis ? length : side;
    }

    /** The sides of {@code look}'s layer where a device takes a whole side row, so the beam turns into it. */
    private static int contacts(BundleLook look) {
        int mask = 0;
        for (var side : Direction.values()) {
            if (look.contact(side)) mask |= bit(side);
        }
        return mask;
    }

    private static boolean leaves(BundleLook look, Direction side) {
        return look.open(side) || look.dense(side);
    }

    private static int bit(Direction side) {
        return 1 << java.util.List.of(CableVisualConnections.DIRECTIONS).indexOf(side);
    }
}
