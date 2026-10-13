package space.controlnet.ae2federation.bundle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Every bundled cable cell of one level, as straight segments. Links are geometric: cells of one segment link to each
 * other, and a segment whose whole end face lies against a same-shape segment's side, covering that side's full width,
 * links to it as a branch. A bundle is a connected group of segments. Collinear segments with the same cross-section
 * that meet end to end become one segment, so aligned ends merge.
 */
public final class BundleSet {
    private final Map<Long, BundleSegment> segments = new HashMap<>();
    private final Map<BlockPos, Long> cells = new HashMap<>();
    private final Map<Long, Bundle> bundleCache = new HashMap<>();
    private long nextSerial = 1;

    /** One bundle; its id is the lowest serial among its segments and changes when the bundle splits or merges. */
    public record Bundle(long id, BundleShape shape, List<BundleSegment> segments) {
    }

    /** The bundle a cell belongs to, and the segment holding it. */
    public record Membership(Bundle bundle, BundleSegment segment) {
    }

    public enum Problem {
        NOT_BUNDLED,
        NOT_AN_END,
        NOT_A_SIDE,
        TOO_SHORT,
        OCCUPIED
    }

    public sealed interface Plan permits Placement, Refused {
    }

    /** New bundled cells, one ordinary cable each. */
    public record Placement(BundleSegment segment) implements Plan {
        public List<BlockPos> cells() {
            return segment.cells();
        }

        public int cost() {
            return segment.extent(Direction.Axis.X) * segment.extent(Direction.Axis.Y)
                    * segment.extent(Direction.Axis.Z);
        }
    }

    public record Refused(Problem problem) implements Plan {
    }

    /**
     * What breaking one cell did: the rest of its layer, now ordinary cables, and every remaining cell of the bundle it
     * was in, whose links may have changed.
     */
    public record Removal(List<BlockPos> reverted, Set<BlockPos> touched) {
    }

    /** Converts a box of cells into bundled cable, merging it with what it meets; returns its bundle. */
    public Bundle form(BundleSegment segment) {
        if (segment.shape().isEmpty()) throw new IllegalArgumentException("Not a bundle cross-section: " + segment);
        for (var pos : segment.cells()) {
            if (cells.containsKey(pos)) throw new IllegalArgumentException("Already bundled: " + pos);
        }
        long serial = coalesce(add(nextSerial++, segment));
        return bundleOf(serial);
    }

    public Bundle apply(Placement placement) {
        return form(placement.segment());
    }

    /** One more layer past the end face that {@code face} of {@code pos} lies on. */
    public Plan planExtend(BlockPos pos, Direction face) {
        var serial = cells.get(pos);
        if (serial == null) return new Refused(Problem.NOT_BUNDLED);
        var segment = segments.get(serial);
        if (face.getAxis() != segment.axis() || segment.contains(pos.relative(face))) {
            return new Refused(Problem.NOT_AN_END);
        }
        return placed(segment.endLayer(face).moved(face, 1));
    }

    /**
     * A branch out of the side that {@code face} of {@code pos} lies on. It covers that side's full width and takes as
     * many layers as the bundle is deep in {@code face}'s direction, so it has the same cross-section; the layers are
     * centred on the cursor, {@code along} being its position within the clicked cell along the run (0 to 1), and stay
     * within the segment.
     */
    public Plan planBranch(BlockPos pos, Direction face, double along) {
        var serial = cells.get(pos);
        if (serial == null) return new Refused(Problem.NOT_BUNDLED);
        var segment = segments.get(serial);
        var axis = segment.axis();
        if (face.getAxis() == axis || segment.contains(pos.relative(face))) return new Refused(Problem.NOT_A_SIDE);
        int layers = segment.extent(face.getAxis());
        if (layers > segment.length()) return new Refused(Problem.TOO_SHORT);
        int start = (int) Math.floor(pos.get(axis) + along + 0.5 - layers / 2.0);
        start = Math.max(segment.min().get(axis), Math.min(start, segment.max().get(axis) - layers + 1));
        var outward = face.getAxis();
        int outside = (face.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? segment.max().get(outward) : segment.min().get(outward)) + face.getAxisDirection().getStep();
        var min = BundleSegment.with(BundleSegment.with(segment.min(), axis, start), outward, outside);
        var max = BundleSegment.with(BundleSegment.with(segment.max(), axis, start + layers - 1), outward, outside);
        return placed(new BundleSegment(min, max, outward));
    }

    /** Breaks one cell: the rest of its layer reverts to ordinary cables and the segment splits there. */
    public Removal remove(BlockPos pos) {
        var serial = cells.get(pos);
        if (serial == null) return new Removal(List.of(), Set.of());
        var touched = new LinkedHashSet<BlockPos>();
        for (var segment : bundleOf(serial).segments()) touched.addAll(segment.cells());
        var segment = segments.get(serial);
        int at = pos.get(segment.axis());
        var reverted = new ArrayList<BlockPos>();
        for (var cell : segment.layer(at).cells()) {
            if (!cell.equals(pos)) reverted.add(cell);
        }
        removeSegment(serial);
        var before = segment.slice(segment.min().get(segment.axis()), at - 1);
        var after = segment.slice(at + 1, segment.max().get(segment.axis()));
        if (before != null) add(serial, before);
        if (after != null) add(nextSerial++, after);
        touched.remove(pos);
        reverted.forEach(touched::remove);
        return new Removal(reverted, touched);
    }

    public Optional<Membership> at(BlockPos pos) {
        var serial = cells.get(pos);
        if (serial == null) return Optional.empty();
        return Optional.of(new Membership(bundleOf(serial), segments.get(serial)));
    }

    public Set<BlockPos> cells(long bundleId) {
        var result = new HashSet<BlockPos>();
        for (var bundle : bundles()) {
            if (bundle.id() == bundleId) bundle.segments().forEach(segment -> result.addAll(segment.cells()));
        }
        return result;
    }

    public Collection<Bundle> bundles() {
        var result = new LinkedHashSet<Bundle>();
        for (var serial : segments.keySet()) result.add(bundleOf(serial));
        return List.copyOf(result);
    }

    /** Every segment, for saving; {@link #form} each again to restore them. */
    public List<BundleSegment> segments() {
        return List.copyOf(segments.values());
    }

    /** Whether two adjacent bundled cells carry a connection between them. */
    public boolean linked(BlockPos a, BlockPos b) {
        var sa = cells.get(a);
        var sb = cells.get(b);
        if (sa == null || sb == null || a.distManhattan(b) != 1) return false;
        if (sa.equals(sb)) return true;
        var toward = Direction.fromDelta(b.getX() - a.getX(), b.getY() - a.getY(), b.getZ() - a.getZ());
        var first = segments.get(sa);
        var second = segments.get(sb);
        return toward.getAxis() == first.axis() && endAgainst(first, toward, second)
                || toward.getAxis() == second.axis() && endAgainst(second, toward.getOpposite(), first);
    }

    private Plan placed(BundleSegment segment) {
        for (var pos : segment.cells()) {
            if (cells.containsKey(pos)) return new Refused(Problem.OCCUPIED);
        }
        return new Placement(segment);
    }

    private long add(long serial, BundleSegment segment) {
        segments.put(serial, segment);
        for (var pos : segment.cells()) cells.put(pos, serial);
        bundleCache.clear();
        return serial;
    }

    private void removeSegment(long serial) {
        var segment = segments.remove(serial);
        for (var pos : segment.cells()) cells.remove(pos);
        bundleCache.clear();
    }

    /** Joins the segment with collinear, same cross-section segments meeting its ends; returns the joined serial. */
    private long coalesce(long serial) {
        boolean joined = true;
        while (joined) {
            joined = false;
            var segment = segments.get(serial);
            for (var end : ends(segment.axis())) {
                var other = cells.get(segment.endLayer(end).moved(end, 1).min());
                if (other == null || !continues(segment, segments.get(other))) continue;
                var next = segments.get(other);
                removeSegment(serial);
                removeSegment(other);
                serial = add(Math.min(serial, other), new BundleSegment(BlockPos.min(segment.min(), next.min()),
                        BlockPos.max(segment.max(), next.max()), segment.axis()));
                joined = true;
                break;
            }
        }
        return serial;
    }

    /** Same axis and cross-section, meeting end to end. */
    private static boolean continues(BundleSegment a, BundleSegment b) {
        if (a.axis() != b.axis()) return false;
        for (var across : BundleSegment.across(a.axis())) {
            if (a.min().get(across) != b.min().get(across) || a.max().get(across) != b.max().get(across)) return false;
        }
        var axis = a.axis();
        return a.max().get(axis) + 1 == b.min().get(axis) || b.max().get(axis) + 1 == a.min().get(axis);
    }

    /** Whether {@code branch}'s end face toward {@code toward} lies wholly against a full-width side of {@code run}. */
    private static boolean endAgainst(BundleSegment branch, Direction toward, BundleSegment run) {
        if (branch.axis() == run.axis() || !branch.shape().equals(run.shape())) return false;
        var contact = branch.endLayer(toward).moved(toward, 1);
        if (!run.contains(contact.min()) || !run.contains(contact.max())) return false;
        var width = BundleSegment.third(branch.axis(), run.axis());
        return branch.min().get(width) == run.min().get(width) && branch.max().get(width) == run.max().get(width);
    }

    private Bundle bundleOf(long serial) {
        var cached = bundleCache.get(serial);
        if (cached != null) return cached;
        var group = new LinkedHashSet<Long>();
        var queue = new ArrayDeque<Long>();
        group.add(serial);
        queue.add(serial);
        while (!queue.isEmpty()) {
            for (var next : neighbours(queue.poll())) {
                if (group.add(next)) queue.add(next);
            }
        }
        long id = group.stream().min(Long::compare).orElseThrow();
        var members = group.stream().sorted().map(segments::get).toList();
        var bundle = new Bundle(id, members.get(0).shape().orElseThrow(), members);
        for (var member : group) bundleCache.put(member, bundle);
        return bundle;
    }

    /** The segments linked to this one, as branches or as the run it branches from. */
    private Set<Long> neighbours(long serial) {
        var segment = segments.get(serial);
        var found = new HashSet<Long>();
        for (var end : ends(segment.axis())) {
            var other = cells.get(segment.endLayer(end).moved(end, 1).min());
            if (other != null && endAgainst(segment, end, segments.get(other))) found.add(other);
        }
        for (var side : Direction.values()) {
            if (side.getAxis() == segment.axis()) continue;
            var face = sideLayer(segment, side);
            for (var pos : face.cells()) {
                var other = cells.get(pos);
                if (other != null && !found.contains(other)
                        && endAgainst(segments.get(other), side.getOpposite(), segment)) {
                    found.add(other);
                }
            }
        }
        return found;
    }

    /** The cells just outside the segment's side toward {@code side}. */
    private static BundleSegment sideLayer(BundleSegment segment, Direction side) {
        var axis = side.getAxis();
        int outside = (side.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? segment.max().get(axis) : segment.min().get(axis)) + side.getAxisDirection().getStep();
        return new BundleSegment(BundleSegment.with(segment.min(), axis, outside),
                BundleSegment.with(segment.max(), axis, outside), segment.axis());
    }

    private static List<Direction> ends(Direction.Axis axis) {
        return List.of(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE),
                Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE));
    }
}
