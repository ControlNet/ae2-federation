package space.controlnet.ae2federation.power;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The size rule of a Beam Converter: M x N across its front, 2·max(M, N) long toward it. At most one axis of a box can be
 * that long (were x and y both, x ≥ 2y and y ≥ 2x), so the size alone gives the axis; which end is the front is the
 * body's facing.
 */
public final class BeamConverterShape {
    private BeamConverterShape() {
    }

    /** The front cross-section of the body from {@code min} to {@code max}, if it is a converter. */
    public static Optional<CableCrossSection> of(BlockPos min, BlockPos max) {
        return axis(min, max).flatMap(axis -> section(min, max, axis));
    }

    /** The axis the body from {@code min} to {@code max} runs along, if it is a converter. */
    public static Optional<Direction.Axis> axis(BlockPos min, BlockPos max) {
        for (var axis : Direction.Axis.values()) {
            if (section(min, max, axis).isPresent()) return Optional.of(axis);
        }
        return Optional.empty();
    }

    private static Optional<CableCrossSection> section(BlockPos min, BlockPos max, Direction.Axis axis) {
        int length = extent(min, max, axis);
        int[] across = new int[2];
        int index = 0;
        for (var other : Direction.Axis.values()) {
            if (other != axis) across[index++] = extent(min, max, other);
        }
        return CableCrossSection.of(across[0], across[1]).filter(section -> section.converterLength() == length);
    }

    private static int extent(BlockPos min, BlockPos max, Direction.Axis axis) {
        return max.get(axis) - min.get(axis) + 1;
    }
}
