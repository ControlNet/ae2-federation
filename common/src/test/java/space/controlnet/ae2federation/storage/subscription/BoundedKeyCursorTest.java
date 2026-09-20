package space.controlnet.ae2federation.storage.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class BoundedKeyCursorTest {
    @Test
    void visitsAtMostBudgetAndResumesWithoutStarvingStableKeys() {
        var cursor = new BoundedKeyCursor<String>(3);
        cursor.addAll(List.of("iron", "gold", "copper"));

        assertEquals(List.of("iron", "gold"), cursor.next(2));
        assertEquals(List.of("copper", "iron"), cursor.next(2));
        assertEquals(List.of("gold", "copper"), cursor.next(2));
    }

    @Test
    void finiteKeyChurnPreservesProgressAndUniqueness() {
        var cursor = new BoundedKeyCursor<String>(3);
        cursor.addAll(List.of("iron", "gold"));

        assertEquals(List.of("iron"), cursor.next(1));
        cursor.add("copper");
        cursor.add("iron");

        assertEquals(List.of("gold", "copper", "iron"), cursor.next(3));
        assertEquals(3, cursor.size());
    }

    @Test
    void enforcesHardRetentionCeilingWithoutEviction() {
        var cursor = new BoundedKeyCursor<String>(2);
        cursor.addAll(List.of("iron", "gold"));

        var overflow = assertThrows(KeyRetentionOverflowException.class, () -> cursor.add("copper"));

        assertEquals(2, overflow.maximumRetainedKeys());
        assertEquals(List.of("iron", "gold"), cursor.retained());
    }

    @Test
    void stableKeyProgressesAtSupportedArrivalBound() {
        var cursor = new BoundedKeyCursor<String>(64);
        cursor.add("stable");
        assertEquals(List.of("stable"), cursor.next(8));

        var stableVisits = 1;
        for (var round = 0; round < 9; round++) {
            for (var arrival = 0; arrival < 7; arrival++) {
                cursor.add("churn-" + round + "-" + arrival);
            }
            if (cursor.next(8).contains("stable")) {
                stableVisits++;
            }
        }

        assertTrue(stableVisits >= 2);
        assertEquals(64, cursor.size());
    }
}
