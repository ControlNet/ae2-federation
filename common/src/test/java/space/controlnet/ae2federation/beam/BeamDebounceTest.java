package space.controlnet.ae2federation.beam;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.beam.BeamDebounce.Seen;

/**
 * A link holds until its beam has stayed blocked for the delay and returns once it has stayed clear for the delay; a
 * new pair whose first sweep is clear links at once.
 */
final class BeamDebounceTest {
    private static final int DELAY = 20;

    @Test
    void aNewClearPairLinksAtOnce() {
        var beam = new BeamDebounce(DELAY);
        assertFalse(beam.update(Seen.UNKNOWN, 0), "nothing is known before the first sweep");
        assertTrue(beam.update(Seen.CLEAR, 3));
    }

    @Test
    void aLinkBreaksOnlyAfterStayingBlocked() {
        var beam = linked();
        for (var tick = 100; tick < 100 + DELAY; tick++) {
            assertTrue(beam.update(Seen.BLOCKED, tick), "still linked at " + tick);
        }
        assertFalse(beam.update(Seen.BLOCKED, 100 + DELAY));
    }

    @Test
    void aShortObstructionChangesNothing() {
        var beam = linked();
        for (var tick = 100; tick < 110; tick++) beam.update(Seen.BLOCKED, tick);
        assertTrue(beam.update(Seen.UNKNOWN, 110), "the blocker went before the delay");
        assertTrue(beam.update(Seen.BLOCKED, 125), "a new obstruction starts its own delay");
        assertTrue(beam.update(Seen.BLOCKED, 125 + DELAY - 1));
        assertFalse(beam.update(Seen.BLOCKED, 125 + DELAY));
    }

    @Test
    void aBrokenLinkReturnsOnlyAfterStayingClear() {
        var beam = new BeamDebounce(DELAY);
        assertFalse(beam.update(Seen.BLOCKED, 0), "a new pair seen blocked first");
        assertFalse(beam.update(Seen.CLEAR, 10), "must stay clear, as any broken link");
        assertFalse(beam.update(Seen.BLOCKED, 15), "a blocker resets the wait");
        assertFalse(beam.update(Seen.CLEAR, 20));
        assertFalse(beam.update(Seen.CLEAR, 20 + DELAY - 1));
        assertTrue(beam.update(Seen.CLEAR, 20 + DELAY));
    }

    private static BeamDebounce linked() {
        var beam = new BeamDebounce(DELAY);
        assertTrue(beam.update(Seen.CLEAR, 0));
        return beam;
    }
}
