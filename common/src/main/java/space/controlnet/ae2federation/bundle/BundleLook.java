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
 *
 * <p>A 2x2 or 3x3 bundle ({@code side} 2 or 3, else 0) is drawn as one thick cable. In a 2x2 one the artist's ring
 * stands at every {@link #RING_SPACING}th layer boundary along a straight stretch ({@code rings}: bit 0 the boundary
 * before this cell, bit 1 the one after); in a 3x3 one it stands inside every {@link #HUGE_RING_SPACING}th layer but
 * an end layer (bit 2). Where a branch leaves, the layers it covers carry the artist's corner rings instead. {@code jointsBefore} and
 * {@code jointsAfter} name the sides with such a branch, for every cell of those layers, and whether the branch goes on
 * into the previous or the next layer; the middle layer of a 3x3 branch has both. {@code contacts} names, for every cell of
 * the layer, the sides where a device takes the whole row of that side at this layer, so the one beam can turn toward it;
 * {@code contactsBefore} and {@code contactsAfter} name those whose device body also takes the previous or the next
 * layer's row, so the beam turns once for the whole stretch. {@code armsBefore} and {@code armsAfter} name the cell's
 * dense sides across the bundle whose device body also takes the previous or the next layer's row, so one arm runs on
 * along the whole stretch the body covers.
 */
public record BundleLook(Direction.Axis axis, int edges, int joins, int dense, int joinsBefore, int joinsAfter,
        int side, int rings, int jointsBefore, int jointsAfter, int contacts, int contactsBefore, int contactsAfter,
        int armsBefore, int armsAfter) {
    /** Layer boundaries between two rings of a straight 2x2 stretch, counted in world coordinates. */
    public static final int RING_SPACING = 2;
    /**
     * Layers between two rings of a straight 3x3 stretch, counted in world coordinates. A guess, as the 2x2 spacing
     * was: the artist only asked that rings not stand too close.
     */
    public static final int HUGE_RING_SPACING = 3;

    /** Whether a device body takes the whole contact that {@code face} of {@code pos} belongs to. */
    @FunctionalInterface
    public interface Contacts {
        boolean whole(BundleSegment segment, BlockPos pos, Direction face);

        /**
         * Whether the device beside {@code face} of {@code pos} belongs to one body with the device beside the same face
         * of the next cell {@code toward}.
         */
        default boolean oneBody(BlockPos pos, Direction face, Direction toward) {
            return false;
        }
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
        int width = segment.shape().filter(shape -> shape.narrow() == shape.wide()).map(BundleShape::narrow).orElse(0);
        if (width == 0) {
            return Optional.of(new BundleLook(axis, edges, joins, dense, before, after, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        }
        int jointsBefore = 0;
        int jointsAfter = 0;
        int layerContacts = 0;
        int contactsBefore = 0;
        int contactsAfter = 0;
        var lower = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
        for (var side : Direction.values()) {
            if (side.getAxis() == axis) continue;
            var face = faceCell(segment, pos, side);
            if (face.equals(pos) && (joins & bit(side)) == 0) {
                // This cell's own outer side, already judged above: no branch there, so dense says it all.
                layerContacts |= dense & bit(side);
                continue;
            }
            var branch = segments.apply(face.relative(side));
            if (branch.isEmpty()) {
                if (contacts.whole(segment, face, side)) {
                    layerContacts |= bit(side);
                    if (armRunsOn(segment, face, side, lower, contacts)) contactsBefore |= bit(side);
                    if (armRunsOn(segment, face, side, lower.getOpposite(), contacts)) contactsAfter |= bit(side);
                }
                continue;
            }
            if (!BundleSet.linked(face, segment, face.relative(side), branch.get())) continue;
            if (opensOnto(face.relative(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE)), side,
                    segment, branch.get(), segments)) {
                jointsBefore |= bit(side);
            }
            if (opensOnto(face.relative(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE)), side,
                    segment, branch.get(), segments)) {
                jointsAfter |= bit(side);
            }
        }
        int rings = 0;
        int layer = pos.get(axis);
        boolean joint = (jointsBefore | jointsAfter) != 0;
        if (width == 2 && !joint && ringAt(segment, pos, layer, segments)) rings |= 1;
        if (width == 2 && !joint && ringAt(segment, pos, layer + 1, segments)) rings |= 2;
        // Never in an end layer, where it would stand against the end face; a 2x2 ring keeps a layer from it too.
        if (width == 3 && !joint && Math.floorMod(layer, HUGE_RING_SPACING) == 0
                && layer != segment.min().get(axis) && layer != segment.max().get(axis)) {
            rings |= 4;
        }
        int armsBefore = 0;
        int armsAfter = 0;
        for (var side : Direction.values()) {
            if (side.getAxis() == axis || (dense & bit(side)) == 0) continue;
            if (armRunsOn(segment, pos, side, Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE),
                    contacts)) {
                armsBefore |= bit(side);
            }
            if (armRunsOn(segment, pos, side, Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE),
                    contacts)) {
                armsAfter |= bit(side);
            }
        }
        // This cell's own outer sides: its arms already say whether the body runs on.
        contactsBefore |= armsBefore & layerContacts;
        contactsAfter |= armsAfter & layerContacts;
        return Optional.of(new BundleLook(axis, edges, joins, dense, before, after, width, rings, jointsBefore,
                jointsAfter, layerContacts, contactsBefore, contactsAfter, armsBefore, armsAfter));
    }

    /** {@link #of(BlockPos, Function, Contacts)} as either side sees the level. */
    public static Optional<BundleLook> of(BlockGetter level, BlockPos pos) {
        return of(pos, cell -> BundleView.segmentAt(level, cell), new Contacts() {
            @Override
            public boolean whole(BundleSegment segment, BlockPos cell, Direction face) {
                return BundleContacts.whole(level, segment, cell, face);
            }

            @Override
            public boolean oneBody(BlockPos cell, Direction face, Direction toward) {
                return BundleContacts.oneBody(level, cell.relative(face), toward);
            }
        });
    }

    /**
     * Whether the dense arm toward {@code side} of {@code pos}, whose whole row one device body takes, runs on into the
     * next cell {@code toward}: the device beside it belongs to the same body. A formed body is a solid cuboid, so it then
     * takes that cell's whole row too, which need not be read again.
     */
    private static boolean armRunsOn(BundleSegment segment, BlockPos pos, Direction side, Direction toward,
            Contacts contacts) {
        return segment.contains(pos.relative(toward)) && contacts.oneBody(pos, side, toward);
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

    /** Whether the bundle is drawn as one thick cable, with the artist's rings and one beam down its middle. */
    public boolean thick() {
        return side != 0;
    }

    /** Whether a 3x3 ring stands inside this cell's layer. */
    public boolean ringInside() {
        return (rings & 4) != 0;
    }

    /** Whether a ring stands at the boundary before this cell's layer. */
    public boolean ringBefore() {
        return (rings & 1) != 0;
    }

    /** Whether a ring stands at the boundary after this cell's layer. */
    public boolean ringAfter() {
        return (rings & 2) != 0;
    }

    /** Whether a branch leaving toward {@code side} from this layer covers the previous one too. */
    public boolean jointBefore(Direction side) {
        return (jointsBefore & bit(side)) != 0;
    }

    /** Whether a device takes the whole row toward {@code side} at this layer of a 2x2 or 3x3 bundle. */
    public boolean contact(Direction side) {
        return (contacts & bit(side)) != 0;
    }

    /** Whether the dense arm toward {@code side} runs on into the previous cell along the bundle. */
    public boolean armBefore(Direction side) {
        return (armsBefore & bit(side)) != 0;
    }

    /** Whether the dense arm toward {@code side} runs on into the next cell along the bundle. */
    public boolean armAfter(Direction side) {
        return (armsAfter & bit(side)) != 0;
    }

    /** Whether a branch leaving toward {@code side} from this layer covers the next one too. */
    public boolean jointAfter(Direction side) {
        return (jointsAfter & bit(side)) != 0;
    }

    private static int bit(Direction side) {
        return 1 << side.get3DDataValue();
    }

    /** The cell of {@code pos}'s layer and row on the segment's outer side toward {@code side}. */
    private static BlockPos faceCell(BundleSegment segment, BlockPos pos, Direction side) {
        var axis = side.getAxis();
        return BundleSegment.with(pos, axis, side.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? segment.max().get(axis) : segment.min().get(axis));
    }

    /**
     * Whether a ring stands at the boundary between layers {@code layer - 1} and {@code layer}: both lie in the segment,
     * the boundary falls on the spacing in world coordinates, so a ring stays put when the bundle grows, and no branch
     * leaves either layer.
     */
    private static boolean ringAt(BundleSegment segment, BlockPos pos, int layer,
            Function<BlockPos, Optional<BundleSegment>> segments) {
        var axis = segment.axis();
        if (layer - 1 < segment.min().get(axis) || layer > segment.max().get(axis)) return false;
        if (Math.floorMod(layer, RING_SPACING) != 0) return false;
        return !branches(segment, BundleSegment.with(pos, axis, layer - 1), segments)
                && !branches(segment, BundleSegment.with(pos, axis, layer), segments);
    }

    /** Whether a branch leaves any side of the layer holding {@code cell}. */
    private static boolean branches(BundleSegment segment, BlockPos cell,
            Function<BlockPos, Optional<BundleSegment>> segments) {
        for (var side : Direction.values()) {
            if (side.getAxis() == segment.axis()) continue;
            var face = faceCell(segment, cell, side);
            var other = segments.apply(face.relative(side));
            if (other.isPresent() && BundleSet.linked(face, segment, face.relative(side), other.get())) return true;
        }
        return false;
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
