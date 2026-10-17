package space.controlnet.ae2federation.power;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/** A Beam Converter is exactly M x N across its front and 2·max(M, N) long, its long axis toward the front. */
final class BeamConverterShapeTest {
    @Test
    void everyCrossSectionHasItsConverter() {
        for (var section : CableCrossSection.values()) {
            // Facing east: x is the length; y and z are the cross-section, in either order.
            var max = new BlockPos(section.converterLength() - 1, section.wide() - 1, section.narrow() - 1);
            assertEquals(Optional.of(section), BeamConverterShape.of(BlockPos.ZERO, max, Direction.EAST), section.toString());
            assertEquals(Optional.of(section), BeamConverterShape.of(BlockPos.ZERO,
                    new BlockPos(section.narrow() - 1, section.wide() - 1, section.converterLength() - 1),
                    Direction.NORTH), section + " facing north");
        }
    }

    @Test
    void anyOtherBoxIsNoConverter() {
        assertEquals(Optional.empty(), BeamConverterShape.of(BlockPos.ZERO, new BlockPos(0, 0, 0), Direction.UP),
                "one block is too short");
        assertEquals(Optional.empty(), BeamConverterShape.of(BlockPos.ZERO, new BlockPos(1, 1, 2), Direction.UP),
                "2x2 across needs a length of 4, not 2");
        assertEquals(Optional.empty(), BeamConverterShape.of(BlockPos.ZERO, new BlockPos(1, 1, 4), Direction.SOUTH),
                "2x2x5 is a block too long");
        assertEquals(Optional.of(CableCrossSection.ONE_BY_TWO),
                BeamConverterShape.of(BlockPos.ZERO, new BlockPos(3, 1, 0), Direction.WEST), "a 1x2x4 converter");
        assertEquals(Optional.empty(), BeamConverterShape.of(BlockPos.ZERO, new BlockPos(3, 1, 0), Direction.UP),
                "the same body facing up is 2 long with a 4x1 front: the wrong axis");
    }
}
