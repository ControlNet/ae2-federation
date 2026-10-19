package space.controlnet.ae2federation.bundle;

import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import space.controlnet.ae2federation.router.CableVisualConnections;

/**
 * Where a 2x2 bundle's one beam runs: down the middle of its cross-section, not one beam per cell. The cell at the low
 * corner of each layer draws it, as an ordinary cable's beam stretched over a box from that cell: two cells wide across
 * the bundle and {@code length} layers along it. Where a branch leaves, the box takes both layers the branch covers, so
 * the junction sits on the branch's own beam. Every other cell draws none ({@code length} 0).
 *
 * @param mask the sides the stretched beam leaves through, as {@link CableVisualConnections#mask} encodes them
 */
public record BundleBeam(Direction.Axis axis, int mask, int length) {
    /** The beam of the cell at {@code pos}, or empty when the cell is not in a 2x2 bundle and draws its own. */
    public static Optional<BundleBeam> of(BlockPos pos, BundleSegment segment, BundleLook look,
            Supplier<Optional<BundleLook>> nextLayer) {
        if (!look.large()) return Optional.empty();
        var axis = look.axis();
        var none = new BundleBeam(axis, 0, 0);
        for (var across : BundleSegment.across(axis)) {
            if (pos.get(across) != segment.min().get(across)) return Optional.of(none);
        }
        var before = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
        var after = before.getOpposite();
        if (look.jointsBefore() != 0) return Optional.of(none);
        int mask = leaves(look, before) ? bit(before) : 0;
        if (look.jointsAfter() == 0) {
            if (leaves(look, after)) mask |= bit(after);
            return Optional.of(new BundleBeam(axis, mask, 1));
        }
        if (nextLayer.get().filter(next -> leaves(next, after)).isPresent()) mask |= bit(after);
        for (var side : Direction.values()) {
            if (look.jointAfter(side)) mask |= bit(side);
        }
        return Optional.of(new BundleBeam(axis, mask, 2));
    }

    /** How far the box reaches along {@code toward}'s axis, in blocks: two across the bundle, its length along it. */
    public int extent(Direction.Axis toward) {
        return toward == axis ? length : 2;
    }

    private static boolean leaves(BundleLook look, Direction side) {
        return look.open(side) || look.dense(side);
    }

    private static int bit(Direction side) {
        return 1 << java.util.List.of(CableVisualConnections.DIRECTIONS).indexOf(side);
    }
}
