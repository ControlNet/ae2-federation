package space.controlnet.ae2federation.observability.meter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PairFlowWindowTest {
    @Test
    void summarizesAcceptedDeliveriesInsideTheWindow() {
        var window = new PairFlowWindow(100);
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
        var window = new PairFlowWindow(100);
        window.record(10, 4);
        window.record(150, 1);
        assertEquals(1, window.summarize(160).events());
        assertFalse(window.summarize(400).active());
        assertEquals(PairFlowWindow.Summary.NONE, window.summarize(400));
    }

    @Test
    void countsEveryDeliveryOfABurstWithinOneTick() {
        var window = new PairFlowWindow(100);
        for (int i = 0; i < 10_000; i++) window.record(5, 1);
        window.record(6, 2);
        var summary = window.summarize(6);
        assertEquals(10_001, summary.events());
        assertEquals(10_002, summary.amount());
        assertEquals(0, summary.ticksSinceLast());
        assertEquals(2, window.buckets());
    }

    @Test
    void keepsNoMoreBucketsThanTicksInTheWindowWithoutBeingSummarized() {
        var window = new PairFlowWindow(100);
        for (long tick = 0; tick < 1_000; tick++) window.record(tick, 1);
        assertEquals(100, window.buckets());
        assertEquals(100, window.summarize(999).events());
    }

    @Test
    void saturatesInsteadOfOverflowingOnHugeEnergyAmounts() {
        var window = new PairFlowWindow(100);
        window.record(1, Long.MAX_VALUE - 1);
        window.record(1, 10);
        window.record(2, 10);
        assertEquals(Long.MAX_VALUE, window.summarize(2).amount());
    }
}
