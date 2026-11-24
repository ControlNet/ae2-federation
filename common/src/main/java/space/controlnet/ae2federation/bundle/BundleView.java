package space.controlnet.ae2federation.bundle;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import space.controlnet.ae2federation.router.FederationCableBlockEntity;

/**
 * Bundles as either side sees them. The server reads its {@link BundledCables}; a client reads the segment each cable
 * reports and rebuilds the bundles around a position from those, which is enough to preview and outline them.
 */
public final class BundleView {
    /** Segments read at most when rebuilding the bundles around one position on a client. */
    private static final int MAX_SEGMENTS = 64;

    private BundleView() {
    }

    /**
     * The segment holding the cable at {@code pos}. The server reads its registry; anything else, such as a client
     * level or a chunk section being meshed, reads the segment the cable last reported.
     */
    public static Optional<BundleSegment> segmentAt(BlockGetter level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            return BundledCables.get(serverLevel).at(pos).map(BundleSet.Membership::segment);
        }
        if (level instanceof Level loaded && !loaded.isLoaded(pos)) return Optional.empty();
        return level.getBlockEntity(pos) instanceof FederationCableBlockEntity cable
                ? Optional.ofNullable(cable.bundleSegment()) : Optional.empty();
    }

    public static boolean bundled(BlockGetter level, BlockPos pos) {
        return segmentAt(level, pos).isPresent();
    }

    /**
     * The bundles at and beside {@code pos}, rebuilt from the segments the cables report: each segment found brings in
     * the segments touching it, so a whole bundle comes in with its branches.
     */
    public static BundleSet around(Level level, BlockPos pos) {
        var starts = new java.util.ArrayList<BlockPos>();
        starts.add(pos);
        for (var face : Direction.values()) starts.add(pos.relative(face));
        return around(level, starts);
    }

    /**
     * Whether bundling {@code segment}, whose cells are free, would leave two branches of one run sharing only some
     * of their layers: the server asks its registry, a client the bundles around the segment.
     */
    public static boolean overlapsBranch(Level level, BundleSegment segment) {
        if (level instanceof ServerLevel serverLevel) return BundledCables.get(serverLevel).overlapsBranch(segment);
        var starts = new java.util.ArrayList<BlockPos>();
        for (var face : Direction.values()) starts.addAll(outside(segment, face).cells());
        return around(level, starts).overlapsBranch(segment);
    }

    private static BundleSet around(Level level, java.util.List<BlockPos> starts) {
        var found = new LinkedHashSet<BundleSegment>();
        var queue = new ArrayDeque<BundleSegment>();
        for (var start : starts) {
            segmentAt(level, start).filter(found::add).ifPresent(queue::add);
        }
        while (!queue.isEmpty() && found.size() < MAX_SEGMENTS) {
            var segment = queue.poll();
            for (var face : Direction.values()) {
                for (var cell : outside(segment, face).cells()) {
                    segmentAt(level, cell).filter(found::add).ifPresent(queue::add);
                }
            }
        }
        var set = new BundleSet();
        for (var segment : found) {
            try {
                set.form(segment);
            } catch (IllegalArgumentException ignored) {
                // A stale segment overlapping a newer one: the cable will send its own again.
            }
        }
        return set;
    }

    /** The cells just outside {@code segment}'s face toward {@code face}. */
    static BundleSegment outside(BundleSegment segment, Direction face) {
        var axis = face.getAxis();
        int at = (face.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? segment.max().get(axis) : segment.min().get(axis)) + face.getAxisDirection().getStep();
        return new BundleSegment(BundleSegment.with(segment.min(), axis, at), BundleSegment.with(segment.max(), axis, at),
                segment.axis());
    }
}
