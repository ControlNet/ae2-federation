package space.controlnet.ae2federation.power;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/**
 * A Beam Converter is exactly M x N across its front and 2·max(M, N) long; its size alone gives the axis it runs along,
 * whichever way its blocks face.
 */
final class BeamConverterShapeTest {
    @Test
    void everyBuildableCrossSectionHasItsConverter() {
        for (var section : CableCrossSection.values()) {
            // Along x: y and z are the cross-section, in either order.
            var max = new BlockPos(section.converterLength() - 1, section.wide() - 1, section.narrow() - 1);
            if (!section.buildable()) {
                assertEquals(Optional.empty(), BeamConverterShape.of(BlockPos.ZERO, max), section + " is not built today");
                continue;
            }
            assertEquals(Optional.of(section), BeamConverterShape.of(BlockPos.ZERO, max), section.toString());
            assertEquals(Optional.of(Direction.Axis.X), BeamConverterShape.axis(BlockPos.ZERO, max), section + " axis");
            var alongZ = new BlockPos(section.narrow() - 1, section.wide() - 1, section.converterLength() - 1);
            assertEquals(Optional.of(section), BeamConverterShape.of(BlockPos.ZERO, alongZ), section + " along z");
            assertEquals(Optional.of(Direction.Axis.Z), BeamConverterShape.axis(BlockPos.ZERO, alongZ), section + " z axis");
        }
    }

    @Test
    void anyOtherBoxIsNoConverter() {
        assertEquals(Optional.empty(), BeamConverterShape.of(BlockPos.ZERO, new BlockPos(0, 0, 0)), "one block is too short");
        assertEquals(Optional.empty(), BeamConverterShape.axis(BlockPos.ZERO, new BlockPos(0, 0, 0)), "nor has an axis");
        assertEquals(Optional.empty(), BeamConverterShape.of(BlockPos.ZERO, new BlockPos(1, 1, 2)),
                "2x2 across needs a length of 4, not 3");
        assertEquals(Optional.empty(), BeamConverterShape.of(BlockPos.ZERO, new BlockPos(1, 1, 4)),
                "2x2x5 is a block too long");
        assertEquals(Optional.empty(), BeamConverterShape.of(BlockPos.ZERO, new BlockPos(3, 1, 0)),
                "no 1x2x4 converter while 1x2 bundles are not built");
        assertEquals(Optional.of(Direction.Axis.Y), BeamConverterShape.axis(BlockPos.ZERO, new BlockPos(0, 1, 0)),
                "a 1x1x2 converter standing up runs along y");
    }
}
