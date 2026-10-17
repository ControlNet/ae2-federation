package space.controlnet.ae2federation.beam;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.power.CableCrossSection;

/** A Beam Former is a square 1x1, 2x2 or 3x3, one block thick; it faces along an axis on which it is one block thick. */
final class BeamFormerShapeTest {
    @Test
    void squaresOneBlockThickAreFormers() {
        assertEquals(Optional.of(CableCrossSection.ONE_BY_ONE), BeamFormerShape.of(BlockPos.ZERO, BlockPos.ZERO));
        assertEquals(Optional.of(CableCrossSection.TWO_BY_TWO),
                BeamFormerShape.of(BlockPos.ZERO, new BlockPos(0, 1, 1)), "2x2 across x");
        assertEquals(Optional.of(CableCrossSection.TWO_BY_TWO),
                BeamFormerShape.of(BlockPos.ZERO, new BlockPos(1, 0, 1)), "2x2 lying flat");
        assertEquals(Optional.of(CableCrossSection.THREE_BY_THREE),
                BeamFormerShape.of(BlockPos.ZERO, new BlockPos(2, 2, 0)), "3x3 across z");
    }

    @Test
    void anyOtherBoxIsNoFormer() {
        assertEquals(Optional.empty(), BeamFormerShape.of(BlockPos.ZERO, new BlockPos(0, 1, 0)), "1x2 is not square");
        assertEquals(Optional.empty(), BeamFormerShape.of(BlockPos.ZERO, new BlockPos(0, 2, 0)), "1x3 is not square");
        assertEquals(Optional.empty(), BeamFormerShape.of(BlockPos.ZERO, new BlockPos(0, 1, 2)), "2x3 is not square");
        assertEquals(Optional.empty(), BeamFormerShape.of(BlockPos.ZERO, new BlockPos(1, 1, 1)), "2x2x2 is too thick");
        assertEquals(Optional.empty(), BeamFormerShape.of(BlockPos.ZERO, new BlockPos(3, 3, 0)), "4x4 is too large");
    }

    @Test
    void aFormerFacesAlongItsThinAxes() {
        assertEquals(EnumSet.allOf(Direction.Axis.class), BeamFormerShape.axes(BlockPos.ZERO, BlockPos.ZERO),
                "a 1x1 is thin every way");
        assertEquals(Set.of(Direction.Axis.X), BeamFormerShape.axes(BlockPos.ZERO, new BlockPos(0, 1, 1)));
        assertEquals(Set.of(Direction.Axis.Y), BeamFormerShape.axes(BlockPos.ZERO, new BlockPos(2, 0, 2)));
        assertEquals(Set.of(), BeamFormerShape.axes(BlockPos.ZERO, new BlockPos(0, 1, 0)), "no former, no axis");
    }
}
