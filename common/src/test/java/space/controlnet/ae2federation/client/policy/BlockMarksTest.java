package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class BlockMarksTest {
    @Test
    void parsesShortBlockPositions() {
        assertEquals(new BlockMarks.Mark(10, -57, 13), BlockMarks.parseShort("10, -57, 13").orElseThrow());
        assertTrue(BlockMarks.parseShort("10, -57").isEmpty());
        assertTrue(BlockMarks.parseShort("a, b, c").isEmpty());
    }

    @Test
    void readsFlatTriplesAndIgnoresPartialOnes() {
        assertEquals(List.of(new BlockMarks.Mark(1, 2, 3), new BlockMarks.Mark(4, 5, 6)),
                BlockMarks.fromFlat(List.of(1, 2, 3, 4, 5, 6, 7)));
    }

    @Test
    void centersAndSizesTheMapAroundAllMarks() {
        var marks = List.of(new BlockMarks.Mark(-4, 60, 10), new BlockMarks.Mark(6, 64, -2));
        var center = BlockMarks.center(marks).orElseThrow();
        assertEquals(new BlockMarks.Mark(1, 62, 4), center);
        assertEquals(8, BlockMarks.radius(center, marks, 8, 48));
        assertEquals(12, BlockMarks.radius(center, List.of(new BlockMarks.Mark(11, 0, 4)), 8, 48));
        assertEquals(48, BlockMarks.radius(center, List.of(new BlockMarks.Mark(500, 0, 4)), 8, 48));
        assertTrue(BlockMarks.center(List.of()).isEmpty());
    }
}
