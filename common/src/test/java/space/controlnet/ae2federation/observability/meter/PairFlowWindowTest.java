package space.controlnet.ae2federation.observability.meter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PairFlowWindowTest {
    @Test
    void summarizesAcceptedDeliveriesInsideTheWindow() {
        var window = new PairFlowWindow(100, 64);
        window.record(10, 4);
        window.record(50, 6);
        window.record(60, 0);
        var summary = window.summarize(70);
        assertEquals(2, summary.events());
        assertEquals(10, summary.amount());
        assertEquals(20, summary.ticksSinceLast());
        assertTrue(summary.active());
    }

    @Test
    void forgetsDeliveriesOlderThanTheWindow() {
        var window = new PairFlowWindow(100, 64);
        window.record(10, 4);
        window.record(150, 1);
        assertEquals(1, window.summarize(160).events());
        assertFalse(window.summarize(400).active());
        assertEquals(PairFlowWindow.Summary.NONE, window.summarize(400));
    }

    @Test
    void keepsOnlyTheNewestEvents() {
        var window = new PairFlowWindow(1000, 2);
        window.record(1, 1);
        window.record(2, 2);
        window.record(3, 3);
        assertEquals(5, window.summarize(4).amount());
    }
}
