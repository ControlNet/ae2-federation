package space.controlnet.ae2federation.bundle;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * A straight run of a bundle: a box of cells whose cross-section, across {@code axis}, is a {@link BundleShape}, and
 * whose length runs along it.
 */
public record BundleSegment(BlockPos min, BlockPos max, Direction.Axis axis) {
    public BundleSegment {
        min = min.immutable();
        max = max.immutable();
        if (min.getX() > max.getX() || min.getY() > max.getY() || min.getZ() > max.getZ()) {
            throw new IllegalArgumentException("Segment corners out of order: " + min + " " + max);
        }
    }

    /** The cells this many along {@code axis}, inclusive. */
    public int extent(Direction.Axis along) {
        return max.get(along) - min.get(along) + 1;
    }

    public int length() {
        return extent(axis);
    }

    /** The cross-section; empty when the box is too wide for a bundle. */
    public java.util.Optional<BundleShape> shape() {
        var across = across(axis);
        return BundleShape.of(extent(across[0]), extent(across[1]));
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    public List<BlockPos> cells() {
        var cells = new ArrayList<BlockPos>(extent(Direction.Axis.X) * extent(Direction.Axis.Y)
                * extent(Direction.Axis.Z));
        for (var pos : BlockPos.betweenClosed(min, max)) cells.add(pos.immutable());
        return cells;
    }

    /** The one-layer segment at {@code coordinate} along the axis. */
    public BundleSegment layer(int coordinate) {
        return new BundleSegment(with(min, axis, coordinate), with(max, axis, coordinate), axis);
    }

    /** The layer at the end that {@code toward} points out of; {@code toward} must lie along the axis. */
    public BundleSegment endLayer(Direction toward) {
        return layer(toward.getAxisDirection() == Direction.AxisDirection.POSITIVE ? max.get(axis) : min.get(axis));
    }

    /** The part from {@code from} to {@code to} along the axis, or null when that range is empty. */
    BundleSegment slice(int from, int to) {
        if (from > to) return null;
        return new BundleSegment(with(min, axis, from), with(max, axis, to), axis);
    }

    /** This box moved by {@code steps} toward {@code direction}. */
    public BundleSegment moved(Direction direction, int steps) {
        return new BundleSegment(min.relative(direction, steps), max.relative(direction, steps), axis);
    }

    /** The two axes across {@code axis}. */
    static Direction.Axis[] across(Direction.Axis axis) {
        return switch (axis) {
            case X -> new Direction.Axis[] {Direction.Axis.Y, Direction.Axis.Z};
            case Y -> new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z};
            case Z -> new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Y};
        };
    }

    /** The axis across both {@code first} and {@code second}, which differ. */
    static Direction.Axis third(Direction.Axis first, Direction.Axis second) {
        for (var axis : Direction.Axis.values()) {
            if (axis != first && axis != second) return axis;
        }
        throw new IllegalArgumentException("Same axis twice: " + first);
    }

    static BlockPos with(BlockPos pos, Direction.Axis axis, int value) {
        return switch (axis) {
            case X -> new BlockPos(value, pos.getY(), pos.getZ());
            case Y -> new BlockPos(pos.getX(), value, pos.getZ());
            case Z -> new BlockPos(pos.getX(), pos.getY(), value);
        };
    }
}
