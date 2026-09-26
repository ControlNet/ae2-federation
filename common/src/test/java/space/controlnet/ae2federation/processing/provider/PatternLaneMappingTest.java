package space.controlnet.ae2federation.processing.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

final class PatternLaneMappingTest {
    @Test
    void replacementReturnsOnlyAffectedLanesAndKeepsDeterministicViews() {
        var mapping = new PatternLaneMapping(4, 3);

        var first = mapping.replace(mapping.handle(1), Set.of(2, 0));
        assertTrue(first.isPresent());
        assertEquals(Set.of(0, 2), first.orElseThrow());
        assertEquals(Set.of(0, 2), mapping.lanesForSlot(1));
        assertEquals(Set.of(1), mapping.slotsForLane(0));

        var second = mapping.replace(mapping.handle(1), Set.of(1, 2));
        assertEquals(Set.of(0, 1, 2), second.orElseThrow());
        assertEquals(Set.of(1, 2), mapping.lanesForSlot(1));
        assertEquals(Set.of(1), mapping.slotsForLane(1));
    }

    @Test
    void staleHandleCannotReplaceNewerAssignment() {
        var mapping = new PatternLaneMapping(2, 2);
        var stale = mapping.handle(0);

        assertTrue(mapping.replace(stale, Set.of(0)).isPresent());
        assertFalse(mapping.replace(stale, Set.of(1)).isPresent());
        assertEquals(Set.of(0), mapping.lanesForSlot(0));
    }

    @Test
    void invalidSlotOrLaneIsRejectedBeforeMutation() {
        var mapping = new PatternLaneMapping(2, 2);
        var handle = mapping.handle(0);

        assertThrows(IllegalArgumentException.class, () -> mapping.replace(handle, Set.of(2)));
        assertThrows(IndexOutOfBoundsException.class, () -> mapping.handle(2));
        assertEquals(Set.of(), mapping.lanesForSlot(0));
        assertEquals(handle, mapping.handle(0));
    }
}
