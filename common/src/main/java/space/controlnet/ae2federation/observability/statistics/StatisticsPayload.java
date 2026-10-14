package space.controlnet.ae2federation.observability.statistics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.identity.NetworkId;

/**
 * What the statistics page shows of one domain over one range, as sent to the client while the page is open: every
 * member network's gauges and own stock, what moved between members, pattern pushes, finished and cancelled jobs, and
 * the keys that moved most. Points are floats, oldest first; a missing point is {@code NaN}. Counters are sent as sums
 * per point with the seconds the server observed in each, so the page can show rates and totals alike. Rows name a
 * network by its place in {@code networks} and a ranked key by its place in {@code keys}, which keeps a busy domain's
 * payload small.
 */
public final class StatisticsPayload {
    /** Keys sent per pair and resource kind, enough for the page's ranking of either kind. */
    public static final int RANKED_PER_KIND = 8;
    private static final String[] GAUGES = {StatisticsNames.POWER, StatisticsNames.POOL, StatisticsNames.CPUS,
            StatisticsNames.BUSY_CPUS, StatisticsNames.CHANNELS};
    private static final String[] COUNTERS = {StatisticsNames.DONE, StatisticsNames.CANCELLED};

    /** What moved, or was pushed, from one member network to another in each point; {@code type} is empty for pushes. */
    public record Flow(String from, String to, String type, float[] sums) {
    }

    /** One of the keys that moved most from {@code from} to {@code to} over the range. */
    public record Ranked<K>(String from, String to, K key, double amount) {
    }

    /**
     * A decoded payload. {@code networks} maps a member's id to its series: the gauges and counters of
     * {@link StatisticsNames} by name, and its own stock as {@code stored/<key type>}.
     */
    public record Snapshot<K>(StatisticsRange range, long now, int[] observed, Map<String, Map<String, float[]>> networks,
            List<Flow> moved, List<Flow> pushed, List<Ranked<K>> ranking, boolean storageRules, boolean craftingRules) {
    }

    private StatisticsPayload() {
    }

    /**
     * @param members      the domain's member networks; only their series and the flows between them are sent
     * @param now          the newest second the book has finished
     * @param storageRules whether a Storage rule between two members is switched on
     * @param keyWriter    a ranked key as a tag
     * @param kindOf       a ranked key's resource kind, so each kind keeps its own top keys
     */
    public static <K> CompoundTag encode(StatisticsBook<K> book, Collection<NetworkId> members, StatisticsRange range,
            long now, boolean storageRules, boolean craftingRules, Function<K, Tag> keyWriter, Function<K, String> kindOf) {
        var ids = new HashMap<String, Integer>();
        for (var member : members) ids.putIfAbsent(member.value().toString(), ids.size());
        var tag = new CompoundTag();
        tag.putString("range", range.name());
        tag.putLong("now", now);
        tag.putIntArray("observed", book.observedSeconds(range, now));
        tag.putBoolean("storageRules", storageRules);
        tag.putBoolean("craftingRules", craftingRules);
        var networks = new ListTag();
        for (var member : members) {
            var series = new CompoundTag();
            for (var name : GAUGES) points(series, name, book.gauge(StatisticsNames.network(name, member), range, now));
            for (var name : COUNTERS) sums(series, name, book.sums(StatisticsNames.network(name, member), range, now));
            var stored = StatisticsNames.STORED + "/" + member.value() + "/";
            for (var name : book.gaugeNames()) {
                if (name.startsWith(stored)) {
                    points(series, StatisticsNames.STORED + "/" + name.substring(stored.length()), book.gauge(name, range, now));
                }
            }
            var network = new CompoundTag();
            network.putString("id", member.value().toString());
            network.put("series", series);
            networks.add(network);
        }
        tag.put("networks", networks);
        var moved = new ListTag();
        var pushed = new ListTag();
        for (var name : book.counterNames()) {
            var parts = name.split("/", 4);
            if (parts.length < 3 || !ids.containsKey(parts[1]) || !ids.containsKey(parts[2])) continue;
            boolean flow = parts[0].equals(StatisticsNames.MOVED) && parts.length == 4;
            if (!flow && !(parts[0].equals(StatisticsNames.PUSHED) && parts.length == 3)) continue;
            var sums = book.sums(name, range, now);
            if (zero(sums)) continue;
            var row = pair(ids, parts[1], parts[2]);
            if (flow) row.putString("type", parts[3]);
            row.put("sums", encode(sums));
            (flow ? moved : pushed).add(row);
        }
        tag.put("moved", moved);
        tag.put("pushed", pushed);
        var ranking = new ListTag();
        var keys = new ListTag();
        var keyIndex = new HashMap<K, Integer>();
        for (var name : book.rankingNames()) {
            var parts = name.split("/", 3);
            if (parts.length != 3 || !parts[0].equals(StatisticsNames.MOVED) || !ids.containsKey(parts[1])
                    || !ids.containsKey(parts[2])) continue;
            var perKind = new HashMap<String, Integer>();
            for (var ranked : book.ranking(name, range, now, Integer.MAX_VALUE)) {
                if (perKind.merge(kindOf.apply(ranked.key()), 1, Integer::sum) > RANKED_PER_KIND) continue;
                var row = pair(ids, parts[1], parts[2]);
                row.putInt("key", keyIndex.computeIfAbsent(ranked.key(), key -> {
                    keys.add(keyWriter.apply(key));
                    return keys.size() - 1;
                }));
                row.putDouble("amount", ranked.amount());
                ranking.add(row);
            }
        }
        tag.put("keys", keys);
        tag.put("ranking", ranking);
        return tag;
    }

    /** The payload, or {@code null} for the empty tag the server sends while the page is closed. */
    public static <K> @Nullable Snapshot<K> decode(CompoundTag tag, Function<Tag, @Nullable K> keyReader) {
        if (!tag.contains("range")) return null;
        StatisticsRange range;
        try {
            range = StatisticsRange.valueOf(tag.getString("range"));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
        int size = range.buckets();
        var networks = new HashMap<String, Map<String, float[]>>();
        var ids = new ArrayList<String>();
        for (var value : tag.getList("networks", Tag.TAG_COMPOUND)) {
            var network = (CompoundTag) value;
            var series = new HashMap<String, float[]>();
            var seriesTag = network.getCompound("series");
            for (var name : seriesTag.getAllKeys()) series.put(name, decode(seriesTag.getIntArray(name), size));
            ids.add(network.getString("id"));
            networks.put(network.getString("id"), Map.copyOf(series));
        }
        var keys = new ArrayList<@Nullable K>();
        if (tag.get("keys") instanceof ListTag list) for (var value : list) keys.add(keyReader.apply(value));
        var ranking = new ArrayList<Ranked<K>>();
        for (var value : tag.getList("ranking", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) value;
            int index = row.getInt("key");
            var key = index < keys.size() ? keys.get(index) : null;
            var from = id(ids, row.getInt("from"));
            var to = id(ids, row.getInt("to"));
            if (key != null && from != null && to != null) ranking.add(new Ranked<>(from, to, key, row.getDouble("amount")));
        }
        ranking.sort((left, right) -> Double.compare(right.amount(), left.amount()));
        var observed = tag.getIntArray("observed");
        if (observed.length != size) observed = java.util.Arrays.copyOf(observed, size);
        return new Snapshot<>(range, tag.getLong("now"), observed, Map.copyOf(networks),
                flows(tag.getList("moved", Tag.TAG_COMPOUND), ids, size), flows(tag.getList("pushed", Tag.TAG_COMPOUND), ids, size),
                List.copyOf(ranking), tag.getBoolean("storageRules"), tag.getBoolean("craftingRules"));
    }

    private static List<Flow> flows(ListTag list, List<String> ids, int size) {
        var out = new ArrayList<Flow>();
        for (var value : list) {
            var row = (CompoundTag) value;
            var from = id(ids, row.getInt("from"));
            var to = id(ids, row.getInt("to"));
            if (from != null && to != null) {
                out.add(new Flow(from, to, row.getString("type"), decode(row.getIntArray("sums"), size)));
            }
        }
        return List.copyOf(out);
    }

    /** A row naming its two networks by their places in {@code networks}. */
    private static CompoundTag pair(Map<String, Integer> ids, String from, String to) {
        var row = new CompoundTag();
        row.putShort("from", ids.get(from).shortValue());
        row.putShort("to", ids.get(to).shortValue());
        return row;
    }

    private static @Nullable String id(List<String> ids, int index) {
        return index >= 0 && index < ids.size() ? ids.get(index) : null;
    }

    /** A gauge's points, left out when it was never sampled in the range. */
    private static void points(CompoundTag series, String name, double[] values) {
        for (double value : values) {
            if (!Double.isNaN(value)) {
                series.put(name, encode(values));
                return;
            }
        }
    }

    /** A counter's sums, left out when it gathered nothing in the range. */
    private static void sums(CompoundTag series, String name, double[] values) {
        if (!zero(values)) series.put(name, encode(values));
    }

    private static boolean zero(double[] values) {
        for (double value : values) if (value != 0) return false;
        return true;
    }

    /** Float bits; a series whose points are all the same is sent as that one point. */
    private static IntArrayTag encode(double[] values) {
        var bits = new int[values.length];
        boolean constant = true;
        for (int index = 0; index < values.length; index++) {
            bits[index] = Float.floatToIntBits((float) values[index]);
            constant &= bits[index] == bits[0];
        }
        return new IntArrayTag(constant && bits.length > 0 ? new int[] {bits[0]} : bits);
    }

    private static float[] decode(int[] bits, int size) {
        var out = new float[size];
        if (bits.length == 1) {
            java.util.Arrays.fill(out, Float.intBitsToFloat(bits[0]));
            return out;
        }
        java.util.Arrays.fill(out, Float.NaN);
        for (int index = 0; index < Math.min(size, bits.length); index++) out[index] = Float.intBitsToFloat(bits[index]);
        return out;
    }
}
