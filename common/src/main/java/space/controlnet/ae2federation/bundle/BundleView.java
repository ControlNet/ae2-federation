package space.controlnet.ae2federation.bundle;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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

    public static Optional<BundleSegment> segmentAt(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            return BundledCables.get(serverLevel).at(pos).map(BundleSet.Membership::segment);
        }
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof FederationCableBlockEntity cable
                ? Optional.ofNullable(cable.bundleSegment()) : Optional.empty();
    }

    public static boolean bundled(Level level, BlockPos pos) {
        return segmentAt(level, pos).isPresent();
    }

    /**
     * The bundles at and beside {@code pos}, rebuilt from the segments the cables report: each segment found brings in
     * the segments touching it, so a whole bundle comes in with its branches.
     */
    public static BundleSet around(Level level, BlockPos pos) {
        var found = new LinkedHashSet<BundleSegment>();
        var queue = new ArrayDeque<BundleSegment>();
        var starts = new java.util.ArrayList<BlockPos>();
        starts.add(pos);
        for (var face : Direction.values()) starts.add(pos.relative(face));
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
