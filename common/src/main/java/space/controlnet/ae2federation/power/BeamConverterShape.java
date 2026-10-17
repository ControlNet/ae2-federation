package space.controlnet.ae2federation.power;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** The size rule of a Beam Converter: M x N across its front, 2·max(M, N) long toward it. */
public final class BeamConverterShape {
    private BeamConverterShape() {
    }

    /** The front cross-section of the body from {@code min} to {@code max} facing {@code facing}, if it is a converter. */
    public static Optional<CableCrossSection> of(BlockPos min, BlockPos max, Direction facing) {
        var axis = facing.getAxis();
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
