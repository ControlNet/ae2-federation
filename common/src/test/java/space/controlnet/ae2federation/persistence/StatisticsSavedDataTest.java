package space.controlnet.ae2federation.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.observability.statistics.StatisticsBook;
import space.controlnet.ae2federation.observability.statistics.StatisticsRange;
import space.controlnet.ae2federation.observability.statistics.TopKeys;

class StatisticsSavedDataTest {
    private static final long NOW = 864_000;
    private static final List<String> KEYS = List.of("iron", "gold", "copper", "tin", "lead");

    /** A route that moved five keys in every point of every range: 444 points. */
    private static TopKeys<String> busyRoute() {
        var top = new TopKeys<String>(StatisticsBook.RANKING_CAP);
        var seconds = new java.util.TreeSet<Long>();
        for (var range : StatisticsRange.values()) {
            long last = Math.floorDiv(NOW, range.seconds());
            for (int index = 0; index < range.buckets(); index++) seconds.add((last - index) * range.seconds());
        }
        for (long second : seconds) {
            for (int key = 0; key < KEYS.size(); key++) top.add(second, KEYS.get(key), (second % 7) + key + 1);
        }
        return top;
    }

    private static int compounds(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            int count = 1;
            for (var name : compound.getAllKeys()) count += compounds(compound.get(name));
            return count;
        }
        if (tag instanceof ListTag list) {
            int count = 0;
            for (var element : list) count += compounds(element);
            return count;
        }
        return 0;
    }

    @Test
    void aRankingSavesAsOneTagPerRangeNotOnePerPoint() {
        var top = busyRoute();
        var points = top.save(NOW);
        assertEquals(444, points.size());
        var tag = StatisticsSavedData.ranking(points, KEYS::indexOf);
        // 2000 routes saved 888 000 compound tags in each autosave at one tag per point.
        assertTrue(compounds(tag) <= StatisticsRange.values().length, compounds(tag) + " compound tags");
    }

    @Test
    void aSavedRankingLoadsAsItWasAndDropsKeysThatNoLongerLoad() {
        var top = busyRoute();
        var tag = StatisticsSavedData.ranking(top.save(NOW), KEYS::indexOf);
        var restored = TopKeys.restore(StatisticsBook.RANKING_CAP, StatisticsSavedData.ranking(tag, KEYS::get));
        for (var range : StatisticsRange.values()) {
            assertEquals(top.read(range, NOW, 16), restored.read(range, NOW, 16), range.name());
            assertEquals(java.util.Arrays.toString(top.series(range, NOW, KEYS).get("tin")),
                    java.util.Arrays.toString(restored.series(range, NOW, KEYS).get("tin")), range.name());
        }
        var withoutGold = TopKeys.restore(StatisticsBook.RANKING_CAP,
                StatisticsSavedData.ranking(tag, index -> index == 1 ? null : KEYS.get(index)));
        var expected = new ArrayList<>(top.read(StatisticsRange.DAY, NOW, 16));
        expected.removeIf(ranked -> ranked.key().equals("gold"));
        assertEquals(expected, withoutGold.read(StatisticsRange.DAY, NOW, 16));
    }
}
