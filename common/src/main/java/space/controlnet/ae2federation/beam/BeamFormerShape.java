package space.controlnet.ae2federation.beam;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import space.controlnet.ae2federation.power.CableCrossSection;

/**
 * The size rule of a Beam Former: a square 1x1, 2x2 or 3x3, one block thick. It faces along an axis on which it is one
 * block thick: a 2x2 or 3x3 has one such axis, a 1x1 has all three.
 */
public final class BeamFormerShape {
    private BeamFormerShape() {
    }

    /** The front cross-section of the body from {@code min} to {@code max}, if it is a former. */
    public static Optional<CableCrossSection> of(BlockPos min, BlockPos max) {
        return axes(min, max).stream().findFirst()
                .flatMap(axis -> CableCrossSection.of(side(min, max, axis), side(min, max, axis)));
    }

    /** The axes the body from {@code min} to {@code max} may face along; empty when it is no former. */
    public static Set<Direction.Axis> axes(BlockPos min, BlockPos max) {
        var axes = EnumSet.noneOf(Direction.Axis.class);
        for (var axis : Direction.Axis.values()) {
            if (extent(min, max, axis) != 1) continue;
            int[] across = across(min, max, axis);
            if (across[0] == across[1] && across[0] <= 3) axes.add(axis);
        }
        return axes;
    }

    private static int side(BlockPos min, BlockPos max, Direction.Axis axis) {
        return across(min, max, axis)[0];
    }

    private static int[] across(BlockPos min, BlockPos max, Direction.Axis axis) {
        int[] across = new int[2];
        int index = 0;
        for (var other : Direction.Axis.values()) {
            if (other != axis) across[index++] = extent(min, max, other);
        }
        return across;
    }

    private static int extent(BlockPos min, BlockPos max, Direction.Axis axis) {
        return max.get(axis) - min.get(axis) + 1;
    }
}
