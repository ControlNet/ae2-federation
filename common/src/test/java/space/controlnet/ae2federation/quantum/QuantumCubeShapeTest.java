package space.controlnet.ae2federation.quantum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.power.CableCrossSection;

/**
 * A Quantum Cube is a solid cube of edge 3, 4 or 5; a face links only inside its (K-2) x (K-2) centre, so the cube
 * takes cable up to that square.
 */
final class QuantumCubeShapeTest {
    private static BlockPos edge(int edge) {
        return new BlockPos(edge - 1, edge - 1, edge - 1);
    }

    @Test
    void cubesOfThreeToFiveTakeTheirCentresSquare() {
        assertEquals(Optional.of(CableCrossSection.ONE_BY_ONE), QuantumCubeShape.of(BlockPos.ZERO, edge(3)));
        assertEquals(Optional.of(CableCrossSection.TWO_BY_TWO), QuantumCubeShape.of(BlockPos.ZERO, edge(4)));
        assertEquals(Optional.of(CableCrossSection.THREE_BY_THREE), QuantumCubeShape.of(BlockPos.ZERO, edge(5)));
    }

    @Test
    void anyOtherBoxIsNoCube() {
        assertEquals(Optional.empty(), QuantumCubeShape.of(BlockPos.ZERO, edge(1)), "1x1x1 is too small");
        assertEquals(Optional.empty(), QuantumCubeShape.of(BlockPos.ZERO, edge(2)), "2x2x2 is too small");
        assertEquals(Optional.empty(), QuantumCubeShape.of(BlockPos.ZERO, edge(6)), "6x6x6 is too large");
        assertEquals(Optional.empty(), QuantumCubeShape.of(BlockPos.ZERO, new BlockPos(2, 2, 3)), "3x3x4 is no cube");
        assertEquals(Optional.empty(), QuantumCubeShape.of(BlockPos.ZERO, new BlockPos(4, 3, 4)), "5x4x5 is no cube");
    }

    @Test
    void anOuterFaceLinksOnlyInsideItsCentre() {
        var max = edge(5);
        assertTrue(QuantumCubeShape.links(BlockPos.ZERO, max, new BlockPos(2, 2, 4), Direction.SOUTH), "the middle");
        assertTrue(QuantumCubeShape.links(BlockPos.ZERO, max, new BlockPos(1, 3, 4), Direction.SOUTH),
                "a corner of the 3x3 centre");
        assertFalse(QuantumCubeShape.links(BlockPos.ZERO, max, new BlockPos(0, 2, 4), Direction.SOUTH), "the border");
        assertFalse(QuantumCubeShape.links(BlockPos.ZERO, max, new BlockPos(4, 4, 4), Direction.SOUTH), "a corner");
        assertTrue(QuantumCubeShape.links(BlockPos.ZERO, max, new BlockPos(2, 0, 2), Direction.DOWN), "the bottom");
        assertFalse(QuantumCubeShape.links(BlockPos.ZERO, max, new BlockPos(2, 0, 4), Direction.DOWN),
                "the bottom's border");
        var small = edge(3);
        assertTrue(QuantumCubeShape.links(BlockPos.ZERO, small, new BlockPos(1, 1, 0), Direction.NORTH),
                "a 3x3x3 face's one centre block");
        assertFalse(QuantumCubeShape.links(BlockPos.ZERO, small, new BlockPos(1, 0, 0), Direction.NORTH));
    }

    @Test
    void facesTowardTheRestOfTheCubeAlwaysLink() {
        var max = edge(4);
        assertTrue(QuantumCubeShape.links(BlockPos.ZERO, max, BlockPos.ZERO, Direction.EAST), "a corner, inward");
        assertTrue(QuantumCubeShape.links(BlockPos.ZERO, max, new BlockPos(1, 1, 1), Direction.UP), "inside");
        assertFalse(QuantumCubeShape.links(BlockPos.ZERO, max, BlockPos.ZERO, Direction.WEST), "a corner, outward");
    }
}
