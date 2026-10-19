package space.controlnet.ae2federation.quantum;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import space.controlnet.ae2federation.power.CableCrossSection;

/**
 * The size rule of a Quantum Cube: a solid cube of edge K = 3, 4 or 5. Each outer face keeps a one-block border that
 * links nothing, so a face links inside its (K-2) x (K-2) centre only and the cube takes cable up to that square.
 */
public final class QuantumCubeShape {
    public static final int MIN_EDGE = 3;
    public static final int MAX_EDGE = 5;

    private QuantumCubeShape() {
    }

    /** The largest cross-section the body from {@code min} to {@code max} takes, if it is a Quantum Cube. */
    public static Optional<CableCrossSection> of(BlockPos min, BlockPos max) {
        int edge = max.getX() - min.getX() + 1;
        if (edge < MIN_EDGE || edge > MAX_EDGE || max.getY() - min.getY() + 1 != edge
                || max.getZ() - min.getZ() + 1 != edge) {
            return Optional.empty();
        }
        return CableCrossSection.of(edge - 2, edge - 2);
    }

    /**
     * Whether {@code face} of the member at {@code position} links: always toward the rest of the body; on an outer
     * face only inside the face's centre, where both coordinates across the face lie strictly between the body's.
     */
    public static boolean links(BlockPos min, BlockPos max, BlockPos position, Direction face) {
        var axis = face.getAxis();
        int toward = position.get(axis) + face.getAxisDirection().getStep();
        if (toward >= min.get(axis) && toward <= max.get(axis)) return true;
        for (var other : Direction.Axis.values()) {
            if (other == axis) continue;
            if (position.get(other) <= min.get(other) || position.get(other) >= max.get(other)) return false;
        }
        return true;
    }
}
