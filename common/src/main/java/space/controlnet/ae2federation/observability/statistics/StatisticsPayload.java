package space.controlnet.ae2federation.observability.statistics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.identity.NetworkId;

/**
 * What the statistics page shows of one domain over one range, as sent to the client while the page is open: every
 * member network's gauges and own stock, what moved between members, pattern pushes, started, finished and cancelled
 * jobs, and what the chosen metric needs besides ({@link Request}): the keys that moved most, the amounts of the
 * items each network holds, what moved by item, the crafting CPUs, or the keys to search. Points are floats, oldest
 * first; a missing point is {@code NaN}. Counters are sent as sums per point with the seconds the server observed in
 * each, so the page can show rates and totals alike. Rows name a network by its place in {@code networks} and a key
 * by its place in {@code keys}, which keeps a busy domain's payload small.
 */
public final class StatisticsPayload {
    /** Keys sent per pair and resource kind, enough for the page's ranking of either kind. */
    public static final int RANKED_PER_KIND = 8;
    /** Items of the chosen kind whose stock is sent besides the pinned ones: the most held across the domain. */
    public static final int STOCK_KEYS = 16;
    /** Items of the chosen kind whose moved amounts are sent, for the domain and per network in each direction. */
    public static final int MOVED_KEYS = 6;
    /** Keys of the chosen kind sent to search, the most held first. */
    public static final int CATALOG_KEYS = 1000;
    private static final String[] GAUGES = {StatisticsNames.POWER, StatisticsNames.POOL, StatisticsNames.USAGE,
            StatisticsNames.CPUS, StatisticsNames.BUSY_CPUS, StatisticsNames.CHANNELS};
    private static final String[] COUNTERS = {StatisticsNames.DONE, StatisticsNames.CANCELLED, StatisticsNames.STARTED};

    /** The metrics of the page; what each needs besides the common figures is sent only while it is chosen. */
    public static final String POWER = "power";
    public static final String STOCK = "stock";
    public static final String MOVED = "moved";
    public static final String CRAFTING = "crafting";
    public static final String CPUS = "cpus";
    public static final String CHANNELS = "channels";

    /**
     * What the open page asks for: its range, its metric, the resource kind the item views show, whether moved
     * amounts are wanted by item, and whether the keys to search are wanted. Sent as one string, {@link #encode()}.
     */
    public record Request(StatisticsRange range, String metric, String kind, boolean byItem, boolean catalog) {
        public Request {
            kind = kind == null ? "" : kind;
            metric = metric == null || metric.isEmpty() ? POWER : metric;
        }

        public String encode() {
            return range.name() + ";" + metric + ";" + kind + ";" + (byItem ? "items" : "") + (catalog ? ",catalog" : "");
        }

        /** The request a string names, or {@code null} for anything else (the page closed). */
        public static @Nullable Request parse(@Nullable String value) {
            if (value == null || value.isEmpty()) return null;
            var parts = value.split(";", -1);
            StatisticsRange range;
            try {
                range = StatisticsRange.valueOf(parts[0]);
            } catch (IllegalArgumentException unknown) {
                return null;
            }
            var flags = parts.length > 3 ? java.util.Set.of(parts[3].split(",")) : java.util.Set.<String>of();
            return new Request(range, parts.length > 1 ? parts[1] : POWER, parts.length > 2 ? parts[2] : "",
                    flags.contains("items"), flags.contains("catalog"));
        }
    }

    /** A crafting CPU of a member: {@code output} is {@code null} while it is idle. */
    public record Cpu<K>(String network, String name, int coProcessors, long storage, @Nullable K output, long amount,
            long total, long progress, long elapsedNanos) {
    }

    /**
     * What the server knows besides the book: the members' crafting CPUs now (for the crafting metrics), what the
     * domain's networks hold of each key (to search), the keys pinned in the domain and whether this player may pin.
     */
    public record Extras<K>(List<Cpu<K>> cpus, Map<K, Long> catalog, java.util.Set<K> pinned, boolean canPin) {
        public static <K> Extras<K> none() {
            return new Extras<>(List.of(), Map.of(), java.util.Set.of(), false);
        }
    }

    /**
     * A key's points: how much {@code network} holds of it (stock), or what moved of it (moved, as sums per point);
     * {@code network} is empty for the whole domain, and {@code direction} is {@code in} or {@code out} for moved
     * amounts of one network.
     */
    public record KeySeries<K>(K key, String network, String direction, float[] points) {
    }

    /** A key with an amount, for search. */
    public record Amount<K>(K key, double amount) {
    }

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
            List<Flow> moved, List<Flow> pushed, List<Ranked<K>> ranking, boolean storageRules, boolean craftingRules,
            String metric, String kind, List<KeySeries<K>> stock, java.util.Set<K> pinned, List<KeySeries<K>> movedKeys,
            List<Cpu<K>> cpus, List<Amount<K>> catalog, boolean canPin) {
    }

    /** Keys written once each, rows naming them by index. */
    private static final class KeyTable<K> {
        final ListTag tags = new ListTag();
        final Map<K, Integer> index = new HashMap<>();
        final Function<K, Tag> writer;

        KeyTable(Function<K, Tag> writer) {
            this.writer = writer;
        }

        int of(K key) {
            return index.computeIfAbsent(key, added -> {
                tags.add(writer.apply(added));
                return tags.size() - 1;
            });
        }
    }

    private StatisticsPayload() {
    }

    /**
     * @param members      the domain's member networks; only their series and the flows between them are sent
     * @param now          the newest second the book has finished
     * @param storageRules whether a Storage rule between two members is switched on
     * @param keyWriter    a key as a tag
     * @param kindOf       a key's resource kind, so each kind keeps its own top keys
     */
    public static <K> CompoundTag encode(StatisticsBook<K> book, Collection<NetworkId> members, Request request,
            long now, boolean storageRules, boolean craftingRules, Extras<K> extras, Function<K, Tag> keyWriter,
            Function<K, String> kindOf) {
        var range = request.range();
        var ids = new HashMap<String, Integer>();
        for (var member : members) ids.putIfAbsent(member.value().toString(), ids.size());
        var keys = new KeyTable<>(keyWriter);
        var tag = new CompoundTag();
        tag.putString("range", range.name());
        tag.putString("metric", request.metric());
        tag.putString("kind", request.kind());
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
        if (request.metric().equals(MOVED)) {
            // Each pair's ranking is read once: the list and the by-item series both use it.
            var rankings = new java.util.LinkedHashMap<String, List<TopKeys.Ranked<K>>>();
            for (var name : pairRankings(book, ids)) rankings.put(name, book.ranking(name, range, now, Integer.MAX_VALUE));
            for (var entry : rankings.entrySet()) {
                var parts = entry.getKey().split("/", 3);
                var perKind = new HashMap<String, Integer>();
                for (var ranked : entry.getValue()) {
                    if (perKind.merge(kindOf.apply(ranked.key()), 1, Integer::sum) > RANKED_PER_KIND) continue;
                    var row = pair(ids, parts[1], parts[2]);
                    row.putInt("key", keys.of(ranked.key()));
                    row.putDouble("amount", ranked.amount());
                    ranking.add(row);
                }
            }
            if (request.byItem()) tag.put("movedKeys", movedKeys(book, ids, rankings, request, now, keys, kindOf));
        }
        tag.put("ranking", ranking);
        var pinned = new ListTag();
        for (var key : extras.pinned()) pinned.add(IntTag.valueOf(keys.of(key)));
        tag.put("pinned", pinned);
        tag.putBoolean("canPin", extras.canPin());
        if (request.metric().equals(STOCK)) tag.put("stock", stock(book, members, ids, request, now, extras, keys, kindOf));
        if (request.metric().equals(CRAFTING) || request.metric().equals(CPUS)) {
            var cpus = new ListTag();
            for (var cpu : extras.cpus()) {
                var index = ids.get(cpu.network());
                if (index == null) continue;
                var row = new CompoundTag();
                row.putShort("network", index.shortValue());
                row.putString("name", cpu.name());
                row.putInt("coProcessors", cpu.coProcessors());
                row.putLong("storage", cpu.storage());
                if (cpu.output() != null) {
                    row.putInt("output", keys.of(cpu.output()));
                    row.putLong("amount", cpu.amount());
                    row.putLong("total", cpu.total());
                    row.putLong("progress", cpu.progress());
                    row.putLong("elapsed", cpu.elapsedNanos());
                }
                cpus.add(row);
            }
            tag.put("cpus", cpus);
        }
        if (request.catalog()) {
            var catalog = new ListTag();
            extras.catalog().entrySet().stream().filter(entry -> kindOf.apply(entry.getKey()).equals(request.kind()))
                    .sorted((left, right) -> Long.compare(right.getValue(), left.getValue())).limit(CATALOG_KEYS)
                    .forEach(entry -> {
                        var row = new CompoundTag();
                        row.putInt("key", keys.of(entry.getKey()));
                        row.putDouble("amount", entry.getValue());
                        catalog.add(row);
                    });
            tag.put("catalog", catalog);
        }
        tag.put("keys", keys.tags);
        return tag;
    }

    /** The rankings of what moved between two members. */
    private static <K> List<String> pairRankings(StatisticsBook<K> book, Map<String, Integer> ids) {
        var out = new ArrayList<String>();
        for (var name : book.rankingNames()) {
            var parts = name.split("/", 3);
            if (parts.length == 3 && parts[0].equals(StatisticsNames.MOVED) && ids.containsKey(parts[1])
                    && ids.containsKey(parts[2])) out.add(name);
        }
        return out;
    }

    /**
     * How much each member holds of the domain's most-held keys of the chosen kind and of the pinned ones: one row per
     * key and network that holds points.
     */
    private static <K> ListTag stock(StatisticsBook<K> book, Collection<NetworkId> members, Map<String, Integer> ids,
            Request request, long now, Extras<K> extras, KeyTable<K> keys, Function<K, String> kindOf) {
        var held = new HashMap<K, double[]>();
        for (var member : members) {
            var name = StatisticsNames.network(StatisticsNames.STOCK, member);
            for (var key : book.keys(name)) {
                if (!kindOf.apply(key).equals(request.kind())) continue;
                double latest = latest(book.keyGauge(name, key, request.range(), now));
                if (!Double.isNaN(latest)) held.computeIfAbsent(key, ignored -> new double[1])[0] += latest;
            }
        }
        var chosen = new java.util.LinkedHashSet<K>();
        for (var key : extras.pinned()) if (kindOf.apply(key).equals(request.kind())) chosen.add(key);
        held.entrySet().stream().sorted((left, right) -> Double.compare(right.getValue()[0], left.getValue()[0]))
                .limit(STOCK_KEYS).forEach(entry -> chosen.add(entry.getKey()));
        var rows = new ListTag();
        for (var key : chosen) {
            for (var member : members) {
                var points = book.keyGauge(StatisticsNames.network(StatisticsNames.STOCK, member), key, request.range(), now);
                if (Double.isNaN(latest(points))) continue;
                var row = new CompoundTag();
                row.putInt("key", keys.of(key));
                row.putShort("network", ids.get(member.value().toString()).shortValue());
                row.put("points", encode(points));
                rows.add(row);
            }
        }
        return rows;
    }

    /**
     * What moved of the chosen kind by key: the domain's {@link #MOVED_KEYS} keys that moved most, and each member's
     * that came in and went out most, as sums per point.
     */
    private static <K> ListTag movedKeys(StatisticsBook<K> book, Map<String, Integer> ids,
            Map<String, List<TopKeys.Ranked<K>>> rankings, Request request, long now, KeyTable<K> keys,
            Function<K, String> kindOf) {
        var range = request.range();
        var names = rankings.keySet();
        var domain = new HashMap<K, double[]>();
        var in = new HashMap<String, Map<K, double[]>>();
        var out = new HashMap<String, Map<K, double[]>>();
        for (var name : names) {
            var parts = name.split("/", 3);
            for (var ranked : rankings.get(name)) {
                if (!kindOf.apply(ranked.key()).equals(request.kind())) continue;
                domain.computeIfAbsent(ranked.key(), ignored -> new double[1])[0] += ranked.amount();
                out.computeIfAbsent(parts[1], ignored -> new HashMap<>())
                        .computeIfAbsent(ranked.key(), ignored -> new double[1])[0] += ranked.amount();
                in.computeIfAbsent(parts[2], ignored -> new HashMap<>())
                        .computeIfAbsent(ranked.key(), ignored -> new double[1])[0] += ranked.amount();
            }
        }
        var domainKeys = top(domain);
        var inKeys = new HashMap<String, List<K>>();
        in.forEach((network, totals) -> inKeys.put(network, top(totals)));
        var outKeys = new HashMap<String, List<K>>();
        out.forEach((network, totals) -> outKeys.put(network, top(totals)));
        var domainSeries = new HashMap<K, double[]>();
        var inSeries = new HashMap<String, Map<K, double[]>>();
        var outSeries = new HashMap<String, Map<K, double[]>>();
        for (var name : names) {
            var parts = name.split("/", 3);
            var wanted = new java.util.HashSet<K>(domainKeys);
            wanted.addAll(inKeys.getOrDefault(parts[2], List.of()));
            wanted.addAll(outKeys.getOrDefault(parts[1], List.of()));
            book.rankingSeries(name, range, now, wanted).forEach((key, sums) -> {
                if (domainKeys.contains(key)) add(domainSeries, key, sums);
                if (inKeys.getOrDefault(parts[2], List.of()).contains(key)) {
                    add(inSeries.computeIfAbsent(parts[2], ignored -> new HashMap<>()), key, sums);
                }
                if (outKeys.getOrDefault(parts[1], List.of()).contains(key)) {
                    add(outSeries.computeIfAbsent(parts[1], ignored -> new HashMap<>()), key, sums);
                }
            });
        }
        var rows = new ListTag();
        domainSeries.forEach((key, sums) -> rows.add(keyRow(keys.of(key), -1, "", sums)));
        inSeries.forEach((network, series) -> series.forEach((key, sums) ->
                rows.add(keyRow(keys.of(key), ids.get(network), "in", sums))));
        outSeries.forEach((network, series) -> series.forEach((key, sums) ->
                rows.add(keyRow(keys.of(key), ids.get(network), "out", sums))));
        return rows;
    }

    private static <K> List<K> top(Map<K, double[]> totals) {
        return totals.entrySet().stream().sorted((left, right) -> Double.compare(right.getValue()[0], left.getValue()[0]))
                .limit(MOVED_KEYS).map(Map.Entry::getKey).toList();
    }

    private static <K> void add(Map<K, double[]> into, K key, double[] sums) {
        var total = into.computeIfAbsent(key, ignored -> new double[sums.length]);
        for (int index = 0; index < sums.length; index++) total[index] += sums[index];
    }

    private static CompoundTag keyRow(int key, int network, String direction, double[] points) {
        var row = new CompoundTag();
        row.putInt("key", key);
        row.putShort("network", (short) network);
        if (!direction.isEmpty()) row.putString("direction", direction);
        row.put("points", encode(points));
        return row;
    }

    private static double latest(double[] points) {
        for (int index = points.length - 1; index >= 0; index--) if (!Double.isNaN(points[index])) return points[index];
        return Double.NaN;
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
            var key = key(keys, row.getInt("key"));
            var from = id(ids, row.getInt("from"));
            var to = id(ids, row.getInt("to"));
            if (key != null && from != null && to != null) ranking.add(new Ranked<>(from, to, key, row.getDouble("amount")));
        }
        ranking.sort((left, right) -> Double.compare(right.amount(), left.amount()));
        var observed = tag.getIntArray("observed");
        if (observed.length != size) observed = java.util.Arrays.copyOf(observed, size);
        var pinned = new java.util.LinkedHashSet<K>();
        for (var value : tag.getList("pinned", Tag.TAG_INT)) {
            var key = key(keys, ((IntTag) value).getAsInt());
            if (key != null) pinned.add(key);
        }
        var cpus = new ArrayList<Cpu<K>>();
        for (var value : tag.getList("cpus", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) value;
            var network = id(ids, row.getShort("network"));
            if (network == null) continue;
            var output = row.contains("output") ? key(keys, row.getInt("output")) : null;
            cpus.add(new Cpu<>(network, row.getString("name"), row.getInt("coProcessors"), row.getLong("storage"), output,
                    row.getLong("amount"), row.getLong("total"), row.getLong("progress"), row.getLong("elapsed")));
        }
        var catalog = new ArrayList<Amount<K>>();
        for (var value : tag.getList("catalog", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) value;
            var key = key(keys, row.getInt("key"));
            if (key != null) catalog.add(new Amount<>(key, row.getDouble("amount")));
        }
        return new Snapshot<>(range, tag.getLong("now"), observed, Map.copyOf(networks),
                flows(tag.getList("moved", Tag.TAG_COMPOUND), ids, size), flows(tag.getList("pushed", Tag.TAG_COMPOUND), ids, size),
                List.copyOf(ranking), tag.getBoolean("storageRules"), tag.getBoolean("craftingRules"),
                tag.getString("metric"), tag.getString("kind"), keySeries(tag.getList("stock", Tag.TAG_COMPOUND), keys, ids, size),
                java.util.Collections.unmodifiableSet(pinned),
                keySeries(tag.getList("movedKeys", Tag.TAG_COMPOUND), keys, ids, size), List.copyOf(cpus),
                List.copyOf(catalog), tag.getBoolean("canPin"));
    }

    private static <K> List<KeySeries<K>> keySeries(ListTag list, List<@Nullable K> keys, List<String> ids, int size) {
        var out = new ArrayList<KeySeries<K>>();
        for (var value : list) {
            var row = (CompoundTag) value;
            var key = key(keys, row.getInt("key"));
            int network = row.getShort("network");
            var id = network < 0 ? "" : id(ids, network);
            if (key != null && id != null) {
                out.add(new KeySeries<>(key, id, row.getString("direction"), decode(row.getIntArray("points"), size)));
            }
        }
        return List.copyOf(out);
    }

    private static <K> @Nullable K key(List<@Nullable K> keys, int index) {
        return index >= 0 && index < keys.size() ? keys.get(index) : null;
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
