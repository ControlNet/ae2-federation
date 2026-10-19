package space.controlnet.ae2federation.observability.statistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TopKeysTest {
    @Test
    void sumsEachKeyOverTheRangeAndSortsLargestFirst() {
        var top = new TopKeys<String>(8);
        top.add(1000, "iron", 10);
        top.add(1001, "iron", 5);
        top.add(1001, "gold", 12);
        top.add(1001, "dirt", 1);
        var ranked = top.read(StatisticsRange.SECONDS, 1001, 2);
        assertEquals(List.of("iron", "gold"), ranked.stream().map(TopKeys.Ranked::key).toList());
        assertEquals(15, ranked.get(0).amount());
    }

    @Test
    void forgetsBucketsOutsideTheRange() {
        var top = new TopKeys<String>(8);
        top.add(10, "iron", 10);
        top.add(200, "gold", 1);
        var ranked = top.read(StatisticsRange.SECONDS, 200, 4);
        assertEquals(List.of("gold"), ranked.stream().map(TopKeys.Ranked::key).toList());
        // The minute range still holds second 10.
        assertEquals(2, top.read(StatisticsRange.MINUTES, 200, 4).size());
    }

    @Test
    void aRestoredRankingReadsAsTheSavedOne() {
        var top = new TopKeys<String>(8);
        top.add(10_000, "iron", 10);
        top.add(10_400, "gold", 4);
        top.add(20_000, "iron", 1);
        var restored = TopKeys.restore(8, top.save(20_000));
        for (var range : StatisticsRange.values()) {
            assertEquals(top.read(range, 20_000, 8), restored.read(range, 20_000, 8), range.name());
        }
        assertEquals(List.of("iron", "gold"),
                restored.read(StatisticsRange.DAY, 20_000, 8).stream().map(TopKeys.Ranked::key).toList());
    }

    @Test
    void aBucketKeepsOnlyItsLargestKeys() {
        var top = new TopKeys<String>(4);
        for (int i = 0; i < 100; i++) top.add(50, "key" + i, i);
        var ranked = top.read(StatisticsRange.SECONDS, 50, 100);
        assertTrue(ranked.size() <= 8, "a bucket is trimmed back to its cap once it holds twice as many");
        assertEquals("key99", ranked.get(0).key());
        assertEquals(99, ranked.get(0).amount());
    }

    @Test
    void aKeysSeriesHoldsWhatItMovedInEachPoint() {
        var top = new TopKeys<String>(4);
        top.add(100, "iron", 3);
        top.add(102, "iron", 5);
        top.add(102, "gold", 1);
        var series = top.series(StatisticsRange.SECONDS, 102, java.util.List.of("iron", "copper"));
        assertEquals(5, series.get("iron")[119]);
        assertEquals(0, series.get("iron")[118]);
        assertEquals(3, series.get("iron")[117]);
        assertEquals(0, series.get("copper")[119]);
        assertEquals(8, top.series(StatisticsRange.MINUTES, 102, java.util.List.of("iron")).get("iron")[59]);
    }
}
