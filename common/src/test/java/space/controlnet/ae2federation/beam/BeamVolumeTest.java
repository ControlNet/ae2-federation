package space.controlnet.ae2federation.beam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/** The blocks between a former's front and its partner's, in scan order: layer by layer away from the front. */
final class BeamVolumeTest {
    private static final BlockPos MIN = new BlockPos(0, 10, 20);
    private static final BlockPos MAX = new BlockPos(0, 11, 21);

    @Test
    void aBeamCoversItsSectionForItsLength() {
        var beam = BeamVolume.of(MIN, MAX, Direction.EAST, 5);
        assertEquals(20, beam.size());
        var pos = new BlockPos.MutableBlockPos();
        assertEquals(new BlockPos(1, 10, 20), beam.at(0, pos), "first, the layer in front");
        assertEquals(new BlockPos(1, 11, 21), beam.at(3, pos), "the first layer ends");
        assertEquals(new BlockPos(2, 10, 20), beam.at(4, pos), "then the next layer");
        assertEquals(new BlockPos(5, 11, 21), beam.at(19, pos), "the last block before the partner");
    }

    @Test
    void aBeamRunsTheWayItsFormerFaces() {
        var beam = BeamVolume.of(new BlockPos(10, 10, 20), new BlockPos(10, 11, 21), Direction.WEST, 5);
        var pos = new BlockPos.MutableBlockPos();
        assertEquals(new BlockPos(9, 10, 20), beam.at(0, pos));
        assertEquals(new BlockPos(5, 11, 21), beam.at(19, pos));
        assertTrue(beam.contains(new BlockPos(5, 10, 21)));
        assertFalse(beam.contains(new BlockPos(4, 10, 21)), "the partner's front is not in the beam");
        assertFalse(beam.contains(new BlockPos(10, 10, 20)), "nor the former itself");
        assertFalse(beam.contains(new BlockPos(7, 12, 20)), "nor a block beside it");
        assertEquals(new BlockPos(7, 11, 20), beam.at(beam.indexOf(new BlockPos(7, 11, 20)), pos));
    }

    @Test
    void frontsTouchingLeaveAnEmptyBeam() {
        var beam = BeamVolume.of(MIN, MAX, Direction.EAST, 0);
        assertEquals(0, beam.size());
        assertFalse(beam.contains(new BlockPos(1, 10, 20)));
    }
}
