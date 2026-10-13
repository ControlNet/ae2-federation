package space.controlnet.ae2federation.bundle;

import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import space.controlnet.ae2federation.router.CableVisualConnections;

/**
 * What one bundled cell shows, one bit per side in {@link Direction#get3DDataValue} order. The bundle's shell is one
 * box the size of its cross-section, so a cell shows a side of it only where the cell lies on the segment's outer
 * boundary ({@code edges}). Along the axis the shell runs on into the next cell of the segment, joins a linked
 * segment ({@code joins}), or ends; an end or a side whose whole contact lies against one device body sends out a
 * dense arm ({@code dense}). Where a branch's end face lies against a side, that side opens; {@code joinsBefore} and
 * {@code joinsAfter} tell whether the opening goes on into the previous and next cell along the axis, so the cells at
 * its rim keep a strip of the side.
 *
 * <p>Anything else stays closed: an ordinary cable, a bundle that does not link, or a device that covers a contact only
 * in part.
 */
public record BundleLook(Direction.Axis axis, int edges, int joins, int dense, int joinsBefore, int joinsAfter) {
    /** Whether a device body takes the whole contact that {@code face} of {@code pos} belongs to. */
    @FunctionalInterface
    public interface Contacts {
        boolean whole(BundleSegment segment, BlockPos pos, Direction face);
    }

    /** The look of the cell at {@code pos}, or empty when it is not bundled. */
    public static Optional<BundleLook> of(BlockPos pos, Function<BlockPos, Optional<BundleSegment>> segments,
            Contacts contacts) {
        var found = segments.apply(pos);
        if (found.isEmpty()) return Optional.empty();
        var segment = found.get();
        var axis = segment.axis();
        int edges = 0;
        int joins = 0;
        int dense = 0;
        int before = 0;
        int after = 0;
        for (var side : Direction.values()) {
            if (!onEdge(segment, pos, side)) continue;
            edges |= bit(side);
            var neighbour = pos.relative(side);
            var other = segments.apply(neighbour);
            if (other.isPresent()) {
                if (!BundleSet.linked(pos, segment, neighbour, other.get())) continue;
                joins |= bit(side);
                if (side.getAxis() == axis) continue;
                if (opensOnto(pos.relative(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE)),
                        side, segment, other.get(), segments)) {
                    before |= bit(side);
                }
                if (opensOnto(pos.relative(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE)),
                        side, segment, other.get(), segments)) {
                    after |= bit(side);
                }
            } else if (contacts.whole(segment, pos, side)) {
                dense |= bit(side);
            }
        }
        return Optional.of(new BundleLook(axis, edges, joins, dense, before, after));
    }

    /** {@link #of(BlockPos, Function, Contacts)} as either side sees the level. */
    public static Optional<BundleLook> of(BlockGetter level, BlockPos pos) {
        return of(pos, cell -> BundleView.segmentAt(level, cell),
                (segment, cell, face) -> BundleContacts.contact(level, segment, cell, face).whole());
    }

    /** Whether the cell lies on the segment's boundary toward {@code side}: the shell, or the segment's end. */
    public boolean edge(Direction side) {
        return (edges & bit(side)) != 0;
    }

    /** Whether the shell runs on into the next cell of the same segment toward {@code side}. */
    public boolean continues(Direction side) {
        return side.getAxis() == axis && !edge(side);
    }

    /** Whether a linked segment lies toward {@code side}: a branch's end face against this side, or this end's. */
    public boolean join(Direction side) {
        return (joins & bit(side)) != 0;
    }

    /** Whether a device takes the whole contact toward {@code side}, so a dense arm reaches it. */
    public boolean dense(Direction side) {
        return (dense & bit(side)) != 0;
    }

    /** Whether the opening toward {@code side} goes on into the previous cell along the axis. */
    public boolean joinsBefore(Direction side) {
        return (joinsBefore & bit(side)) != 0;
    }

    /** Whether the opening toward {@code side} goes on into the next cell along the axis. */
    public boolean joinsAfter(Direction side) {
        return (joinsAfter & bit(side)) != 0;
    }

    /** Whether the shell is open toward {@code side}: running on, or joining a linked segment. */
    public boolean open(Direction side) {
        return continues(side) || join(side);
    }

    /** The sides a beam leaves through, as {@link CableVisualConnections#mask} encodes them. */
    public int flowMask() {
        int mask = 0;
        for (int index = 0; index < CableVisualConnections.DIRECTIONS.length; index++) {
            var side = CableVisualConnections.DIRECTIONS[index];
            if (open(side) || dense(side)) mask |= 1 << index;
        }
        return mask;
    }

    private static int bit(Direction side) {
        return 1 << side.get3DDataValue();
    }

    private static boolean onEdge(BundleSegment segment, BlockPos pos, Direction side) {
        var axis = side.getAxis();
        return pos.get(axis) == (side.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? segment.max().get(axis) : segment.min().get(axis));
    }

    /** Whether {@code cell}, of the same segment, opens toward {@code side} onto the same linked segment. */
    private static boolean opensOnto(BlockPos cell, Direction side, BundleSegment segment, BundleSegment linked,
            Function<BlockPos, Optional<BundleSegment>> segments) {
        return segment.contains(cell) && segments.apply(cell.relative(side)).filter(linked::equals).isPresent();
    }
}
