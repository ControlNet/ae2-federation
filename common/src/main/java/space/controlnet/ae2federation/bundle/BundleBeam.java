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
 * branch covers, so the junction sits on the branch's own beam. Every other cell draws none ({@code length} 0).
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
        int mask = leaves(look, before) ? bit(before) : 0;
        if (look.jointsAfter() == 0) {
            if (leaves(look, after)) mask |= bit(after);
            return Optional.of(new BundleBeam(axis, mask, 1, side));
        }
        // A branch covers as many layers as the bundle is wide; the far one decides whether the run goes on.
        if (layer.apply(side - 1).filter(far -> leaves(far, after)).isPresent()) mask |= bit(after);
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

    /** How far the box reaches along {@code toward}'s axis, in blocks: across the bundle, or its length along it. */
    public int extent(Direction.Axis toward) {
        return toward == axis ? length : side;
    }

    private static boolean leaves(BundleLook look, Direction side) {
        return look.open(side) || look.dense(side);
    }

    private static int bit(Direction side) {
        return 1 << java.util.List.of(CableVisualConnections.DIRECTIONS).indexOf(side);
    }
}
