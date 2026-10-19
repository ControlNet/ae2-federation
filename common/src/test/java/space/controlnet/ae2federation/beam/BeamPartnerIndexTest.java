package space.controlnet.ae2federation.beam;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.beam.BeamPartnerIndex.Former;
import space.controlnet.ae2federation.beam.BeamPartnerIndex.Partner;

/**
 * A former's partner is the nearest former in front of it with the same square on the same line, if that one faces
 * back and the gap between the fronts is within range.
 */
final class BeamPartnerIndexTest {
    private static final int RANGE = 256;

    private static Former square(int x, int size, Direction facing) {
        return new Former(new BlockPos(x, 10, 20), new BlockPos(x, 10 + size - 1, 20 + size - 1), facing);
    }

    @Test
    void twoFormersFacingEachOtherArePartners() {
        var index = new BeamPartnerIndex();
        var west = square(0, 2, Direction.EAST);
        var east = square(10, 2, Direction.WEST);
        index.put(west);
        index.put(east);
        assertEquals(Optional.of(new Partner(east, 9)), index.partner(west, RANGE));
        assertEquals(Optional.of(new Partner(west, 9)), index.partner(east, RANGE));
        assertEquals(Optional.empty(), index.partner(west, 8), "a gap of 9 is out of a range of 8");
    }

    @Test
    void onlyTheNearestCounts() {
        var index = new BeamPartnerIndex();
        var west = square(0, 2, Direction.EAST);
        var middle = square(5, 2, Direction.WEST);
        var east = square(10, 2, Direction.WEST);
        index.put(west);
        index.put(middle);
        index.put(east);
        assertEquals(Optional.of(new Partner(middle, 4)), index.partner(west, RANGE));
        assertEquals(Optional.empty(), index.partner(east, RANGE), "the nearest toward it faces away");
        index.remove(middle);
        assertEquals(Optional.of(new Partner(east, 9)), index.partner(west, RANGE), "removed, it no longer hides");
    }

    @Test
    void anotherSquareOrLineIsNoPartner() {
        var index = new BeamPartnerIndex();
        var west = square(0, 2, Direction.EAST);
        index.put(west);
        index.put(square(10, 3, Direction.WEST));
        assertEquals(Optional.empty(), index.partner(west, RANGE), "a 3x3 does not pair with a 2x2");
        index.put(new Former(new BlockPos(12, 11, 20), new BlockPos(12, 12, 21), Direction.WEST));
        assertEquals(Optional.empty(), index.partner(west, RANGE), "a 2x2 one block higher is on another line");
        index.put(square(14, 2, Direction.EAST));
        assertEquals(Optional.empty(), index.partner(west, RANGE), "one facing the same way is no partner");
    }

    @Test
    void formersPairUpAndDownToo() {
        var index = new BeamPartnerIndex();
        var low = new Former(new BlockPos(3, 0, 3), new BlockPos(3, 0, 3), Direction.UP);
        var high = new Former(new BlockPos(3, 1, 3), new BlockPos(3, 1, 3), Direction.DOWN);
        index.put(low);
        index.put(high);
        assertEquals(Optional.of(new Partner(high, 0)), index.partner(low, RANGE), "fronts touching");
    }
}
