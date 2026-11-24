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
    void addingAMapAtOnceMatchesAddingEachKey() {
        var each = new TopKeys<String>(4);
        var all = new TopKeys<String>(4);
        var random = new java.util.Random(7);
        for (long second = 1000; second < 1400; second += 1 + random.nextInt(3)) {
            var amounts = new it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap<String>();
            int keys = random.nextInt(20);
            for (int i = 0; i < keys; i++) amounts.addTo("key" + random.nextInt(30), random.nextInt(5));
            for (var entry : amounts.object2LongEntrySet()) each.add(second, entry.getKey(), entry.getLongValue());
            all.addAll(second, amounts);
            for (var range : StatisticsRange.values()) {
                assertEquals(each.read(range, second, 100), all.read(range, second, 100), range + " at " + second);
            }
        }
    }

    @Test
    void pruningChangesNothingAnyRangeShowsFromThenOn() {
        var pruned = new TopKeys<String>(4);
        var kept = new TopKeys<String>(4);
        var random = new java.util.Random(11);
        var keys = java.util.List.of("iron", "gold", "copper", "tin", "lead", "zinc", "coal", "dirt", "sand", "clay");
        // A busy hour of many keys, nothing for longer than the shorter ranges show, then a few keys again.
        for (long second = 0; second < 30_000; second++) {
            int count = second < 3600 ? 10 : second > 12_000 ? 2 : 0;
            for (int at = 0; at < count; at++) {
                var key = keys.get(random.nextInt(count));
                double amount = 1 + random.nextInt(50);
                pruned.add(second, key, amount);
                kept.add(second, key, amount);
            }
            if (second % 60 == 0) pruned.prune(second);
            if (second % 997 == 0) {
                for (var range : StatisticsRange.values()) {
                    assertEquals(kept.read(range, second, 100), pruned.read(range, second, 100), range + " at " + second);
                    var expected = kept.series(range, second, keys);
                    var actual = pruned.series(range, second, keys);
                    for (var key : keys) {
                        assertEquals(java.util.Arrays.toString(expected.get(key)),
                                java.util.Arrays.toString(actual.get(key)), key + " " + range + " at " + second);
                    }
                }
            }
        }
        assertTrue(pruned.prune(30_000 + 86_400 + 600), "nothing left a day later");
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

    @Test
    void idleSaysWhatReadingTheLongestRangeSays() {
        var top = new TopKeys<String>(2);
        top.add(599, "iron", 1);
        top.add(600, "gold", 2);
        top.add(5000, "iron", 3);
        top.add(90_000, "copper", 4);
        for (long now = 0; now < 90_000 + 87_000; now += 300) {
            assertEquals(top.read(StatisticsRange.LONGEST, now, 1).isEmpty(), top.idle(now), "at second " + now);
        }
    }
}

