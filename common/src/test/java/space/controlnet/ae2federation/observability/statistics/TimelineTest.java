package space.controlnet.ae2federation.observability.statistics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TimelineTest {
    @Test
    void secondsRangeEndsAtTheCurrentSecond() {
        var timeline = new Timeline();
        timeline.add(1000, 4);
        timeline.add(1001, 6);
        var values = timeline.read(StatisticsRange.SECONDS, 1001);
        assertEquals(120, values.length);
        assertEquals(6, values[119]);
        assertEquals(4, values[118]);
        assertTrue(Double.isNaN(values[117]), "a second nobody recorded is missing, not zero");
    }

    @Test
    void longerRangesAverageTheSecondsTheyHold() {
        var timeline = new Timeline();
        // Seconds 600..604 fill one five-second bucket; 605 starts the next, which is still open.
        for (long second = 600; second < 605; second++) timeline.add(second, second - 600);
        timeline.add(605, 10);
        var five = timeline.read(StatisticsRange.FIVE_SECONDS, 605);
        assertEquals(10, five[119], "the open bucket shows what it has so far");
        assertEquals(2, five[118], "mean of 0,1,2,3,4");
        var minutes = timeline.read(StatisticsRange.MINUTES, 605);
        assertEquals(60, minutes.length);
        assertEquals((0 + 1 + 2 + 3 + 4 + 10) / 6.0, minutes[59], 1e-9);
    }

    @Test
    void secondsWithoutDataDoNotDiluteTheMean() {
        var timeline = new Timeline();
        timeline.add(60, 8);
        timeline.add(119, 2);
        assertEquals(5, timeline.read(StatisticsRange.MINUTES, 119)[59]);
    }

    @Test
    void oldBucketsAreMissingOnceTheRingWrapsPastThem() {
        var timeline = new Timeline();
        timeline.add(10, 1);
        assertEquals(1, timeline.read(StatisticsRange.SECONDS, 10)[119]);
        // 120 seconds later the slot of second 10 is reused for second 130; reading 130 must not show second 10.
        var later = timeline.read(StatisticsRange.SECONDS, 130);
        for (double value : later) assertTrue(Double.isNaN(value));
        timeline.add(130, 7);
        later = timeline.read(StatisticsRange.SECONDS, 130);
        assertEquals(7, later[119]);
        assertTrue(Double.isNaN(later[0]));
    }

    @Test
    void idleOnlyAfterTheLongestRangeHoldsNothing() {
        var timeline = new Timeline();
        timeline.add(100, 1);
        assertFalse(timeline.idle(100 + 3600 + 60), "the day range still holds it");
        assertTrue(timeline.idle(100 + 86_400 + 600));
    }

    @Test
    void theDayRangeHoldsTenMinutePoints() {
        var timeline = new Timeline();
        timeline.add(6000, 3);
        timeline.add(6599, 5);
        var day = timeline.read(StatisticsRange.DAY, 6599);
        assertEquals(144, day.length);
        assertEquals(4, day[143]);
        assertTrue(Double.isNaN(day[142]));
    }

    @Test
    void aRestoredTimelineReadsAsTheSavedOneAndKeepsAveraging() {
        var timeline = new Timeline();
        for (long second = 10_000; second < 10_700; second += 7) timeline.add(second, second % 13);
        var restored = Timeline.restore(timeline.save());
        for (var range : StatisticsRange.values()) {
            assertArrayEquals(timeline.read(range, 10_700), restored.read(range, 10_700), range.name());
        }
        // The open ten-minute point keeps its count, so a later second averages in rather than replacing it.
        timeline.add(10_750, 100);
        restored.add(10_750, 100);
        assertArrayEquals(timeline.read(StatisticsRange.DAY, 10_750), restored.read(StatisticsRange.DAY, 10_750));
    }

    @Test
    void anEmptyTimelineSavesNothingAndASavedTierOfAnotherSizeIsDropped() {
        assertTrue(java.util.Arrays.stream(new Timeline().save()).allMatch(java.util.Objects::isNull));
        var tiers = new Timeline.SavedTier[StatisticsRange.values().length];
        tiers[StatisticsRange.DAY.ordinal()] = new Timeline.SavedTier(5, new double[3], new int[] {1, 1, 1});
        var restored = Timeline.restore(tiers);
        assertTrue(restored.idle(3000));
    }
}
