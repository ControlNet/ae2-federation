package space.controlnet.ae2federation.beam;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The formed Beam Formers of one level, by line: formers of one square on one line are kept in order along it, so a
 * former's partner is found without looking at any other. The partner is the nearest one in front with the same square
 * on the same line, if it faces back and the gap between the fronts is within range; anything else in between is left
 * to the path check.
 */
public final class BeamPartnerIndex {
    /** A formed former: its corners and the way its front faces. */
    public record Former(BlockPos min, BlockPos max, Direction facing) {
        public Former {
            min = min.immutable();
            max = max.immutable();
        }
    }

    /** The partner of a former and the number of blocks between their fronts. */
    public record Partner(Former former, int gap) {
    }

    /** One line: an axis, the square's lowest corner across it, and the square's side. */
    private record Line(Direction.Axis axis, int first, int second, int side) {
        static Line of(Former former) {
            var axis = former.facing().getAxis();
            int[] across = new int[2];
            int index = 0;
            for (var other : Direction.Axis.values()) {
                if (other != axis) across[index++] = former.min().get(other);
            }
            var side = (axis == Direction.Axis.X ? former.max().getY() - former.min().getY()
                    : former.max().getX() - former.min().getX()) + 1;
            return new Line(axis, across[0], across[1], side);
        }
    }

    private final Map<Line, TreeMap<Integer, Former>> lines = new HashMap<>();

    public void put(Former former) {
        lines.computeIfAbsent(Line.of(former), line -> new TreeMap<>())
                .put(former.min().get(former.facing().getAxis()), former);
    }

    public void remove(Former former) {
        var line = Line.of(former);
        var formers = lines.get(line);
        if (formers == null) return;
        formers.remove(former.min().get(former.facing().getAxis()), former);
        if (formers.isEmpty()) lines.remove(line);
    }

    /** The partner of {@code former} at most {@code range} blocks from its front. */
    public Optional<Partner> partner(Former former, int range) {
        var formers = lines.get(Line.of(former));
        if (formers == null) return Optional.empty();
        int front = former.min().get(former.facing().getAxis());
        var next = former.facing().getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? formers.higherEntry(front) : formers.lowerEntry(front);
        if (next == null || next.getValue().facing() != former.facing().getOpposite()) return Optional.empty();
        int gap = Math.abs(next.getKey() - front) - 1;
        return gap <= range ? Optional.of(new Partner(next.getValue(), gap)) : Optional.empty();
    }
}
