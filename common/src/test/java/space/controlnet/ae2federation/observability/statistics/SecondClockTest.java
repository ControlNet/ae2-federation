package space.controlnet.ae2federation.observability.statistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SecondClockTest {
    @Test
    void theFirstTickOnlyStartsTheClock() {
        var clock = new SecondClock();
        clock.started(10_000);
        assertNull(clock.ended(10_020));
    }

    @Test
    void eachNewSecondFinishesThePreviousOne() {
        var clock = new SecondClock();
        clock.started(10_000);
        clock.ended(10_020);
        clock.started(10_500);
        assertNull(clock.ended(10_520), "still the same second");
        clock.started(11_000);
        assertEquals(new SecondClock.Step(10, 11, 10), clock.ended(11_020));
    }

    @Test
    void secondsALongTickRanThroughAreObserved() {
        var clock = new SecondClock();
        clock.started(10_000);
        clock.ended(10_020);
        clock.started(10_040);
        var step = clock.ended(14_500);
        assertEquals(new SecondClock.Step(10, 11, 13), step, "the tick ran from second 10 to 14");
    }

    @Test
    void secondsTheServerWasIdleAreNotObserved() {
        var clock = new SecondClock();
        clock.started(10_000);
        clock.ended(10_020);
        // Paused from 10.02 s to 20.5 s: no tick ran, so 11 to 19 stay unobserved; 20 had a tick in it.
        clock.started(20_500);
        assertEquals(new SecondClock.Step(10, 20, 20), clock.ended(21_200));
    }

    @Test
    void withoutTickStartsEveryGapCountsAsRunning() {
        var clock = new SecondClock();
        clock.ended(10_020);
        assertEquals(new SecondClock.Step(10, 11, 12), clock.ended(13_100));
    }
}
