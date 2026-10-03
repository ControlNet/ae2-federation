package space.controlnet.ae2federation.observability.meter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class LaneFlowTotalsTest {
    @Test
    void keepsEachResourceTypeApartAndCountsEveryDelivery() {
        var totals = new LaneFlowTotals();
        totals.add("ae2:i", new PairFlowWindow.Summary(2, 4, 0));
        totals.add("ae2:f", new PairFlowWindow.Summary(1, 1_500, 3));
        totals.add("ae2:i", new PairFlowWindow.Summary(1, 3, 1));
        assertEquals(4, totals.events());
        assertEquals(Map.of("ae2:f", 1_500L, "ae2:i", 7L), totals.amounts());
        assertEquals("ae2:f", totals.amounts().keySet().iterator().next());
        assertTrue(totals.active());
    }

    @Test
    void ignoresQuietWindows() {
        var totals = new LaneFlowTotals();
        totals.add("ae2:i", PairFlowWindow.Summary.NONE);
        assertFalse(totals.active());
        assertTrue(totals.amounts().isEmpty());
    }

    @Test
    void saturatesInsteadOfOverflowing() {
        var totals = new LaneFlowTotals();
        totals.add("ae2:i", new PairFlowWindow.Summary(1, Long.MAX_VALUE, 0));
        totals.add("ae2:i", new PairFlowWindow.Summary(1, 1, 0));
        assertEquals(Long.MAX_VALUE, totals.amounts().get("ae2:i"));
    }
}
