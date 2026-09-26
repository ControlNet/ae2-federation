package space.controlnet.ae2federation.storage.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class SourceSnapshotLedgerTest {
    @Test
    void retainsTwoSequentialAbsoluteChangesForTheSameKey() {
        var updates = new ArrayList<SourceQuantityUpdate<String>>();
        var ledger = new SourceSnapshotLedger<String>(7, 8, updates::add);
        ledger.beginSnapshot(7);
        ledger.completeSnapshot(7, Map.of("iron", 0L), false);

        assertTrue(ledger.acceptAbsolute(7, "iron", 4));
        assertTrue(ledger.acceptAbsolute(7, "iron", 9));

        assertEquals(2, updates.size());
        assertEquals(1L, updates.get(0).eventVersion());
        assertEquals(2L, updates.get(1).eventVersion());
        assertEquals(4L, updates.get(0).absoluteAmount());
        assertEquals(9L, updates.get(1).absoluteAmount());
        assertEquals(9L, ledger.amount("iron"));
    }

    @Test
    void interpretsAbsoluteAndDeltaEventsAtExplicitBoundaries() {
        var updates = new ArrayList<SourceQuantityUpdate<String>>();
        var ledger = initialized(updates, Map.of("iron", 10L));

        ledger.acceptAbsolute(7, "iron", 12);
        ledger.acceptDelta(7, "iron", 3);

        assertEquals(15L, ledger.amount("iron"));
        assertEquals(12L, updates.get(0).absoluteAmount());
        assertEquals(15L, updates.get(1).absoluteAmount());
        assertThrows(IllegalArgumentException.class, () -> ledger.acceptDelta(7, "iron", -16));
    }

    @Test
    void replaysEveryEventThatRacesTheSnapshotBaseline() {
        var updates = new ArrayList<SourceQuantityUpdate<String>>();
        var ledger = new SourceSnapshotLedger<String>(7, 8, updates::add);
        ledger.beginSnapshot(7);

        ledger.acceptAbsolute(7, "iron", 4);
        ledger.acceptAbsolute(7, "iron", 9);
        ledger.completeSnapshot(7, Map.of("iron", 0L), false);

        assertEquals(2, updates.size());
        assertEquals(9L, ledger.amount("iron"));
        assertEquals(1L, ledger.snapshotVersion());
    }

    @Test
    void firstBaselineAndResetPublishOnlyChangedAbsoluteState() {
        var updates = new ArrayList<SourceQuantityUpdate<String>>();
        var ledger = new SourceSnapshotLedger<String>(7, 8, updates::add);
        ledger.beginSnapshot(7);
        ledger.completeSnapshot(7, Map.of("iron", 5L), true);
        ledger.beginSnapshot(7);
        ledger.completeSnapshot(7, Map.of("iron", 8L, "gold", 2L), true);

        assertEquals(3, updates.size());
        assertEquals(5L, updates.get(0).absoluteAmount());
        assertEquals(8L, updates.get(1).absoluteAmount());
        assertEquals(2L, updates.get(2).absoluteAmount());
        assertEquals(2L, ledger.snapshotVersion());
    }

    @Test
    void staleGenerationAndClosedLedgerFailClosedBeforePublication() {
        var updates = new ArrayList<SourceQuantityUpdate<String>>();
        var ledger = initialized(updates, Map.of("iron", 3L));

        assertFalse(ledger.acceptAbsolute(6, "iron", 9));
        ledger.close();
        assertFalse(ledger.acceptAbsolute(7, "iron", 9));

        assertTrue(updates.isEmpty());
        assertEquals(3L, ledger.amount("iron"));
    }

    @Test
    void snapshotRaceQueueIsBoundedAndFailsClosedOnOverflow() {
        var ledger = new SourceSnapshotLedger<String>(7, 1, ignored -> {});
        ledger.beginSnapshot(7);
        ledger.acceptAbsolute(7, "iron", 1);

        assertThrows(SnapshotQueueOverflowException.class, () -> ledger.acceptAbsolute(7, "iron", 2));
        assertTrue(ledger.closed());
    }

    private static SourceSnapshotLedger<String> initialized(
            ArrayList<SourceQuantityUpdate<String>> updates, Map<String, Long> baseline) {
        var ledger = new SourceSnapshotLedger<String>(7, 8, updates::add);
        ledger.beginSnapshot(7);
        ledger.completeSnapshot(7, baseline, false);
        return ledger;
    }
}
