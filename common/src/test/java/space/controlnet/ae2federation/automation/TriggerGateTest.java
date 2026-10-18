package space.controlnet.ae2federation.automation;

import static org.junit.jupiter.api.Assertions.*;
import static space.controlnet.ae2federation.automation.TriggerGate.Action.*;

import org.junit.jupiter.api.Test;

/** The rate limit and the overlap modes, tick by tick. */
final class TriggerGateTest {
    private static TriggerGate gate(Installation.Overlap overlap) {
        return new TriggerGate(20, overlap);
    }

    @Test
    void aTriggerStartsAtOnceAndTriggersInTheCooldownMergeIntoOneStartAfterIt() {
        var gate = gate(Installation.Overlap.PARALLEL);
        gate.fire(0);
        assertEquals(START, gate.poll(100, 0));
        gate.fire(0);
        gate.fire(0);
        assertEquals(NONE, gate.poll(110, 0));
        assertEquals(NONE, gate.poll(119, 0));
        assertEquals(START, gate.poll(120, 0), "one start for both triggers, when the 20 ticks are over");
        assertEquals(NONE, gate.poll(200, 0), "nothing is left waiting");
    }

    @Test
    void ignoreDropsATriggerWhileARunIsGoing() {
        var gate = gate(Installation.Overlap.IGNORE);
        gate.fire(1);
        assertEquals(NONE, gate.poll(100, 1));
        assertEquals(NONE, gate.poll(101, 0), "dropped, not kept for later");
        gate.fire(0);
        assertEquals(START, gate.poll(102, 0));
        gate.fire(0);
        assertEquals(NONE, gate.poll(130, 1), "waited out the cooldown, but a run is going by then");
    }

    @Test
    void restartStopsTheRunningOneAndParallelStopsAtTheLimit() {
        var restart = gate(Installation.Overlap.RESTART);
        restart.fire(1);
        assertEquals(RESTART, restart.poll(100, 1));
        restart.fire(0);
        assertEquals(START, restart.poll(120, 0));

        var parallel = gate(Installation.Overlap.PARALLEL);
        parallel.fire(3);
        assertEquals(START, parallel.poll(100, 3));
        parallel.fire(Installation.PARALLEL_LIMIT);
        assertEquals(NONE, parallel.poll(200, Installation.PARALLEL_LIMIT));
    }
}
