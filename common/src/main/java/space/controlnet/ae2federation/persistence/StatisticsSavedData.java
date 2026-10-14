package space.controlnet.ae2federation.persistence;

import appeng.api.stacks.AEKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import space.controlnet.ae2federation.observability.statistics.StatisticsBook;
import space.controlnet.ae2federation.observability.statistics.StatisticsRange;
import space.controlnet.ae2federation.observability.statistics.Timeline;
import space.controlnet.ae2federation.observability.statistics.TopKeys;

/**
 * The overworld's {@link StatisticsBook}: the last day of Federation statistics outlives a restart. Every key a
 * ranking, a key gauge or a pin names is written once, in a table they refer to by index; a key that no longer loads
 * (its mod was removed) drops what named it.
 */
public final class StatisticsSavedData extends SavedData {
    private static final String DATA_NAME = "ae2federation_statistics";
    private static final int FORMAT = 2;
    private static final StatisticsRange[] RANGES = StatisticsRange.values();

    private final StatisticsBook<AEKey> book;
    /** The second the book is saved as of; set by the owner before a save. */
    private long nowSecond;

    public StatisticsSavedData(StatisticsBook<AEKey> book) {
        this.book = book;
    }

    public static StatisticsSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new Factory<>(() -> new StatisticsSavedData(new StatisticsBook<>()), StatisticsSavedData::load),
                DATA_NAME);
    }

    public StatisticsBook<AEKey> book() {
        return book;
    }

    /** Marks the book changed as of {@code second}, so the next world save writes it. */
    public void changed(long second) {
        nowSecond = second;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var saved = book.save(nowSecond);
        tag.putInt("format", FORMAT);
        tag.put("observed", tiers(saved.observed()));
        tag.put("gauges", timelines(saved.gauges()));
        tag.put("counters", timelines(saved.counters()));
        var keys = new ListTag();
        var index = new HashMap<AEKey, Integer>();
        java.util.function.ToIntFunction<AEKey> indexOf = key -> index.computeIfAbsent(key, added -> {
            keys.add(added.toTagGeneric(registries));
            return keys.size() - 1;
        });
        var keyGauges = new CompoundTag();
        saved.keyGauges().forEach((name, byKey) -> {
            var list = new ListTag();
            byKey.forEach((key, tiers) -> {
                var entry = new CompoundTag();
                entry.putInt("key", indexOf.applyAsInt(key));
                entry.put("tiers", tiers(tiers));
                list.add(entry);
            });
            keyGauges.put(name, list);
        });
        var pins = new CompoundTag();
        saved.pins().forEach((name, pinned) -> pins.putIntArray(name,
                pinned.stream().mapToInt(indexOf).toArray()));
        var rankings = new CompoundTag();
        saved.rankings().forEach((name, points) -> {
            var list = new ListTag();
            for (var point : points) {
                var keyIndices = new int[point.amounts().size()];
                var amounts = new long[point.amounts().size()];
                int at = 0;
                for (var entry : point.amounts().entrySet()) {
                    keyIndices[at] = indexOf.applyAsInt(entry.getKey());
                    amounts[at++] = Double.doubleToRawLongBits(entry.getValue());
                }
                var pointTag = new CompoundTag();
                pointTag.putByte("range", (byte) point.range().ordinal());
                pointTag.putLong("bucket", point.bucket());
                pointTag.putIntArray("keys", keyIndices);
                pointTag.putLongArray("amounts", amounts);
                list.add(pointTag);
            }
            rankings.put(name, list);
        });
        tag.put("keys", keys);
        tag.put("rankings", rankings);
        tag.put("keyGauges", keyGauges);
        tag.put("pins", pins);
        return tag;
    }

    public static StatisticsSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("format") != FORMAT) return new StatisticsSavedData(new StatisticsBook<>());
        var keyTags = tag.getList("keys", Tag.TAG_COMPOUND);
        var keys = new ArrayList<AEKey>(keyTags.size());
        for (int at = 0; at < keyTags.size(); at++) {
            AEKey key;
            try {
                key = AEKey.fromTagGeneric(registries, keyTags.getCompound(at));
            } catch (RuntimeException unreadable) {
                key = null;
            }
            keys.add(key);
        }
        var rankings = new HashMap<String, List<TopKeys.SavedPoint<AEKey>>>();
        var rankingTags = tag.getCompound("rankings");
        for (var name : rankingTags.getAllKeys()) {
            var points = new ArrayList<TopKeys.SavedPoint<AEKey>>();
            var list = rankingTags.getList(name, Tag.TAG_COMPOUND);
            for (int at = 0; at < list.size(); at++) {
                var pointTag = list.getCompound(at);
                int range = pointTag.getByte("range");
                var keyIndices = pointTag.getIntArray("keys");
                var amounts = pointTag.getLongArray("amounts");
                if (range < 0 || range >= RANGES.length || keyIndices.length != amounts.length) continue;
                var byKey = new HashMap<AEKey, Double>();
                for (int entry = 0; entry < keyIndices.length; entry++) {
                    int keyIndex = keyIndices[entry];
                    var key = key(keys, keyIndex);
                    if (key != null) byKey.merge(key, Double.longBitsToDouble(amounts[entry]), Double::sum);
                }
                if (!byKey.isEmpty()) points.add(new TopKeys.SavedPoint<>(RANGES[range], pointTag.getLong("bucket"), byKey));
            }
            if (!points.isEmpty()) rankings.put(name, points);
        }
        var keyGauges = new HashMap<String, Map<AEKey, Timeline.SavedTier[]>>();
        var keyGaugeTags = tag.getCompound("keyGauges");
        for (var name : keyGaugeTags.getAllKeys()) {
            var byKey = new HashMap<AEKey, Timeline.SavedTier[]>();
            for (var value : keyGaugeTags.getList(name, Tag.TAG_COMPOUND)) {
                var entry = (CompoundTag) value;
                var key = key(keys, entry.getInt("key"));
                if (key != null) byKey.put(key, tiers(entry.getList("tiers", Tag.TAG_COMPOUND)));
            }
            if (!byKey.isEmpty()) keyGauges.put(name, byKey);
        }
        var pins = new HashMap<String, java.util.Set<AEKey>>();
        var pinTags = tag.getCompound("pins");
        for (var name : pinTags.getAllKeys()) {
            var pinned = new java.util.LinkedHashSet<AEKey>();
            for (int keyIndex : pinTags.getIntArray(name)) {
                var key = key(keys, keyIndex);
                if (key != null) pinned.add(key);
            }
            if (!pinned.isEmpty()) pins.put(name, pinned);
        }
        var saved = new StatisticsBook.Saved<>(tiers(tag.getList("observed", Tag.TAG_COMPOUND)),
                timelines(tag.getCompound("gauges")), keyGauges, timelines(tag.getCompound("counters")), rankings,
                pins);
        return new StatisticsSavedData(StatisticsBook.restore(saved));
    }

    private static AEKey key(List<AEKey> keys, int index) {
        return index >= 0 && index < keys.size() ? keys.get(index) : null;
    }

    private static CompoundTag timelines(Map<String, Timeline.SavedTier[]> timelines) {
        var tag = new CompoundTag();
        timelines.forEach((name, tiers) -> tag.put(name, tiers(tiers)));
        return tag;
    }

    private static Map<String, Timeline.SavedTier[]> timelines(CompoundTag tag) {
        var out = new HashMap<String, Timeline.SavedTier[]>();
        for (var name : tag.getAllKeys()) out.put(name, tiers(tag.getList(name, Tag.TAG_COMPOUND)));
        return out;
    }

    private static ListTag tiers(Timeline.SavedTier[] tiers) {
        var list = new ListTag();
        for (int range = 0; range < tiers.length; range++) {
            var tier = tiers[range];
            if (tier == null) continue;
            var sums = new long[tier.sums().length];
            for (int slot = 0; slot < sums.length; slot++) sums[slot] = Double.doubleToRawLongBits(tier.sums()[slot]);
            var tierTag = new CompoundTag();
            tierTag.putByte("range", (byte) range);
            tierTag.putLong("newest", tier.newestBucket());
            tierTag.put("sums", new LongArrayTag(sums));
            tierTag.putIntArray("counts", tier.counts());
            list.add(tierTag);
        }
        return list;
    }

    private static Timeline.SavedTier[] tiers(ListTag list) {
        var tiers = new Timeline.SavedTier[RANGES.length];
        for (int at = 0; at < list.size(); at++) {
            var tierTag = list.getCompound(at);
            int range = tierTag.getByte("range");
            if (range < 0 || range >= RANGES.length) continue;
            var bits = tierTag.getLongArray("sums");
            var sums = new double[bits.length];
            for (int slot = 0; slot < bits.length; slot++) sums[slot] = Double.longBitsToDouble(bits[slot]);
            tiers[range] = new Timeline.SavedTier(tierTag.getLong("newest"), sums, tierTag.getIntArray("counts"));
        }
        return tiers;
    }
}
