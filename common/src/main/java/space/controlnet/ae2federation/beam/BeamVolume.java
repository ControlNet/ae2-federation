package space.controlnet.ae2federation.beam;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The blocks a beam crosses: the former's square, {@code length} layers deep in front of it, up to the partner's
 * front. Positions are indexed in scan order, layer by layer away from the former, so a scan reads neighbouring blocks
 * in turn.
 *
 * @param min the former's lowest corner
 * @param max the former's highest corner; the former is one block thick along {@code facing}
 */
public record BeamVolume(BlockPos min, BlockPos max, Direction facing, int length) {
    public static BeamVolume of(BlockPos min, BlockPos max, Direction facing, int length) {
        return new BeamVolume(min.immutable(), max.immutable(), facing, length);
    }

    public int size() {
        return side() * side() * length;
    }

    /** The position at {@code index}, written into {@code pos}. */
    public BlockPos.MutableBlockPos at(int index, BlockPos.MutableBlockPos pos) {
        int layer = side() * side();
        int step = index / layer + 1;
        int across = index % layer;
        var axis = facing.getAxis();
        int[] coords = new int[3];
        coords[axis.ordinal()] = front() + step * facing.getAxisDirection().getStep();
        coords[first().ordinal()] = min.get(first()) + across / side();
        coords[second().ordinal()] = min.get(second()) + across % side();
        return pos.set(coords[0], coords[1], coords[2]);
    }

    public boolean contains(BlockPos pos) {
        return indexOf(pos) >= 0;
    }

    /** The index of {@code pos}, or -1 when the beam does not cross it. */
    public int indexOf(BlockPos pos) {
        int step = (pos.get(facing.getAxis()) - front()) * facing.getAxisDirection().getStep();
        int u = pos.get(first()) - min.get(first());
        int v = pos.get(second()) - min.get(second());
        if (step < 1 || step > length || u < 0 || u >= side() || v < 0 || v >= side()) return -1;
        return (step - 1) * side() * side() + u * side() + v;
    }

    private int front() {
        return min.get(facing.getAxis());
    }

    private int side() {
        return max.get(first()) - min.get(first()) + 1;
    }

    /** The first of the two axes across the beam, in {@link Direction.Axis} order. */
    private Direction.Axis first() {
        return facing.getAxis() == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
    }

    private Direction.Axis second() {
        return facing.getAxis() == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
    }
}
