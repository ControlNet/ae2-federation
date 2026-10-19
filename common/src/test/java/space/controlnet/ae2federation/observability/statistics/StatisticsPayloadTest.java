package space.controlnet.ae2federation.observability.statistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.identity.NetworkId;

class StatisticsPayloadTest {
    private static final NetworkId A = new NetworkId(UUID.fromString("e5370000-0000-0000-0000-000000000001"));
    private static final NetworkId B = new NetworkId(UUID.fromString("ef170000-0000-0000-0000-000000000002"));
    private static final NetworkId OUTSIDE = new NetworkId(UUID.fromString("f6310000-0000-0000-0000-000000000003"));

    private static String kind(String key) {
        return key.startsWith("fluid:") ? "ae2:f" : "ae2:i";
    }

    private static StatisticsPayload.Snapshot<String> roundTrip(StatisticsBook<String> book, List<NetworkId> members,
            StatisticsRange range, long now) {
        return roundTrip(book, members, new StatisticsPayload.Request(range, StatisticsPayload.MOVED, "ae2:i", false,
                false), now, StatisticsPayload.Extras.none());
    }

    private static StatisticsPayload.Snapshot<String> roundTrip(StatisticsBook<String> book, List<NetworkId> members,
            StatisticsPayload.Request request, long now, StatisticsPayload.Extras<String> extras) {
        var tag = StatisticsPayload.encode(book, members, request, now, true, false, extras, StringTag::valueOf,
                StatisticsPayloadTest::kind);
        return StatisticsPayload.decode(tag, value -> value.getAsString());
    }

    @Test
    void membersGaugesAndStockArriveAsPointsWithGapsKept() {
        var book = new StatisticsBook<String>();
        for (long second = 100; second < 110; second++) {
            book.observe(second);
            if (second != 105) book.gauge(StatisticsNames.network(StatisticsNames.POWER, A), second, second - 100);
            book.gauge(StatisticsNames.network(StatisticsNames.CPUS, A), second, 2);
            book.gauge(StatisticsNames.stored(A, "ae2:i"), second, 1000);
            book.gauge(StatisticsNames.network(StatisticsNames.POWER, OUTSIDE), second, 50);
        }
        var snapshot = roundTrip(book, List.of(A, B), StatisticsRange.SECONDS, 109);
        assertEquals(StatisticsRange.SECONDS, snapshot.range());
        assertEquals(109, snapshot.now());
        assertEquals(1, snapshot.observed()[119]);
        assertEquals(0, snapshot.observed()[100], "the server was not running then");
        var a = snapshot.networks().get(A.value().toString());
        assertEquals(9f, a.get(StatisticsNames.POWER)[119]);
        assertTrue(Float.isNaN(a.get(StatisticsNames.POWER)[115]), "a second without a sample stays missing");
        assertEquals(2f, a.get(StatisticsNames.CPUS)[119]);
        assertEquals(1000f, a.get(StatisticsNames.STORED + "/ae2:i")[112]);
        assertTrue(snapshot.networks().get(B.value().toString()).isEmpty(), "a member with nothing recorded has no series");
        assertFalse(snapshot.networks().containsKey(OUTSIDE.value().toString()), "only this domain's networks");
        assertTrue(snapshot.storageRules());
        assertFalse(snapshot.craftingRules());
    }

    @Test
    void theOpenedDomainsFederationPowerArrivesWithEveryMetricAndItsConvertersWithItsOwn() {
        var book = new StatisticsBook<String>();
        var prefix = StatisticsNames.fedpower("physical:3");
        for (long second = 100; second < 110; second++) {
            book.observe(second);
            book.gauge(prefix + StatisticsNames.FEDPOWER_USE + "/cables", second, 1.5);
            book.gauge(prefix + StatisticsNames.FEDPOWER_INPUT, second, 22);
            book.gauge(prefix + StatisticsNames.FEDPOWER_STORED, second, 82);
            book.gauge(prefix + StatisticsNames.FEDPOWER_STOPPED, second, second < 105 ? 1 : 0);
            book.gauge(StatisticsNames.fedpower("physical:4") + StatisticsNames.FEDPOWER_INPUT, second, 99);
        }
        var converter = new StatisticsPayload.Converter("minecraft:overworld", 12, 64, -30, "2x2x4", 1700, true);
        var power = new StatisticsPayload.Power("physical:3", true, List.of(converter));
        var extras = new StatisticsPayload.Extras<String>(List.of(), java.util.Map.of(), java.util.Set.of(), false, power);

        var moved = roundTrip(book, List.of(A), new StatisticsPayload.Request(StatisticsRange.SECONDS,
                StatisticsPayload.MOVED, "ae2:i", false, false), 109, extras);
        assertTrue(moved.powerRequired());
        assertEquals(22f, moved.domain().get(StatisticsNames.FEDPOWER_INPUT)[119], "the card's sparkline needs it");
        assertEquals(1.5f, moved.domain().get(StatisticsNames.FEDPOWER_USE + "/cables")[119]);
        assertEquals(1f, moved.domain().get(StatisticsNames.FEDPOWER_STOPPED)[112]);
        assertFalse(moved.domain().containsKey("physical:4/" + StatisticsNames.FEDPOWER_INPUT), "only this domain");
        assertTrue(moved.converters().isEmpty(), "the converter list comes with its own metric only");

        var shown = roundTrip(book, List.of(A), new StatisticsPayload.Request(StatisticsRange.SECONDS,
                StatisticsPayload.FEDPOWER, "ae2:i", false, false), 109, extras);
        assertEquals(List.of(converter), shown.converters());

        var off = roundTrip(book, List.of(A), new StatisticsPayload.Request(StatisticsRange.SECONDS,
                StatisticsPayload.FEDPOWER, "ae2:i", false, false), 109, StatisticsPayload.Extras.none());
        assertFalse(off.powerRequired(), "without power required the card is disabled");
        assertTrue(off.domain().isEmpty());
    }

    @Test
    void countersArriveAsSumsPerPointBetweenMembersOnly() {
        var book = new StatisticsBook<String>();
        for (long second = 600; second < 610; second++) book.observe(second);
        book.count(StatisticsNames.moved(A, B, "ae2:i"), 602, 30);
        book.count(StatisticsNames.moved(A, B, "ae2:i"), 603, 12);
        book.count(StatisticsNames.moved(B, A, "ae2:f"), 604, 4000);
        book.count(StatisticsNames.moved(A, OUTSIDE, "ae2:i"), 604, 9);
        book.count(StatisticsNames.pair(StatisticsNames.PUSHED, A, B), 604, 1);
        book.count(StatisticsNames.network(StatisticsNames.DONE, A), 606, 1);
        var snapshot = roundTrip(book, List.of(A, B), StatisticsRange.FIVE_SECONDS, 609);
        assertEquals(2, snapshot.moved().size(), "the flow to a network outside the domain is not shown");
        var items = snapshot.moved().stream().filter(flow -> flow.type().equals("ae2:i")).findFirst().orElseThrow();
        assertEquals(A.value().toString(), items.from());
        assertEquals(B.value().toString(), items.to());
        assertEquals(42f, items.sums()[118], "seconds 600-604 are one five-second point");
        assertEquals(5, snapshot.observed()[118]);
        assertEquals(1f, snapshot.pushed().get(0).sums()[118]);
        assertEquals(1f, snapshot.networks().get(A.value().toString()).get(StatisticsNames.DONE)[119]);
    }

    @Test
    void rankingsKeepTheTopKeysOfEachKindPerPair() {
        var book = new StatisticsBook<String>();
        book.observe(10);
        var pair = StatisticsNames.pair(StatisticsNames.MOVED, A, B);
        for (int index = 0; index < 12; index++) book.moved(pair, 10, "item:" + index, 100 - index);
        book.moved(pair, 10, "fluid:water", 1);
        book.moved(StatisticsNames.pair(StatisticsNames.MOVED, B, OUTSIDE), 10, "item:x", 5);
        var snapshot = roundTrip(book, List.of(A, B), StatisticsRange.SECONDS, 10);
        var keys = snapshot.ranking().stream().map(StatisticsPayload.Ranked::key).toList();
        assertEquals(StatisticsPayload.RANKED_PER_KIND + 1, keys.size(), "a small fluid is kept beside many items");
        assertTrue(keys.contains("fluid:water"));
        assertTrue(keys.contains("item:0"));
        assertFalse(keys.contains("item:11"));
        assertFalse(keys.contains("item:x"));
        assertEquals(100, snapshot.ranking().get(0).amount());
    }

    @Test
    void theRankingIsSentOnlyForTheMovedMetric() {
        var book = new StatisticsBook<String>();
        book.observe(10);
        book.moved(StatisticsNames.pair(StatisticsNames.MOVED, A, B), 10, "item:iron", 5);
        var power = roundTrip(book, List.of(A, B), new StatisticsPayload.Request(StatisticsRange.SECONDS,
                StatisticsPayload.POWER, "ae2:i", false, false), 10, StatisticsPayload.Extras.none());
        assertTrue(power.ranking().isEmpty());
        assertEquals(StatisticsPayload.POWER, power.metric());
    }

    @Test
    void aRequestRoundTripsAsAString() {
        var request = new StatisticsPayload.Request(StatisticsRange.MINUTES, StatisticsPayload.STOCK, "ae2:f", true, true);
        assertEquals(request, StatisticsPayload.Request.parse(request.encode()));
        var plain = StatisticsPayload.Request.parse("DAY");
        assertEquals(StatisticsRange.DAY, plain.range());
        assertEquals(StatisticsPayload.POWER, plain.metric());
        assertFalse(plain.byItem());
        assertTrue(StatisticsPayload.Request.parse("") == null);
        assertTrue(StatisticsPayload.Request.parse("NEVER;stock") == null);
    }

    @Test
    void stockSendsTheMostHeldAndPinnedKeysOfTheKindPerNetwork() {
        var book = new StatisticsBook<String>();
        for (long second = 0; second < 5; second++) {
            book.observe(second);
            for (int index = 0; index < StatisticsPayload.STOCK_KEYS + 2; index++) {
                book.keyGauge(StatisticsNames.network(StatisticsNames.STOCK, A), "item:" + index, second, 1000 - index);
            }
            book.keyGauge(StatisticsNames.network(StatisticsNames.STOCK, B), "item:0", second, 7);
            book.keyGauge(StatisticsNames.network(StatisticsNames.STOCK, A), "fluid:water", second, 99_999);
        }
        var extras = new StatisticsPayload.Extras<String>(List.of(), java.util.Map.of(),
                java.util.Set.of("item:17", "item:new"), true);
        var snapshot = roundTrip(book, List.of(A, B), new StatisticsPayload.Request(StatisticsRange.SECONDS,
                StatisticsPayload.STOCK, "ae2:i", false, false), 4, extras);
        var keys = snapshot.stock().stream().map(StatisticsPayload.KeySeries::key).collect(java.util.stream.Collectors.toSet());
        assertEquals(StatisticsPayload.STOCK_KEYS + 1, keys.size(), "the top keys and the pinned one held");
        assertTrue(keys.contains("item:17"), "pinned, though not among the most held");
        assertFalse(keys.contains("item:16"));
        assertFalse(keys.contains("fluid:water"), "another kind");
        var onB = snapshot.stock().stream().filter(row -> row.network().equals(B.value().toString())).toList();
        assertEquals(1, onB.size());
        assertEquals(7f, onB.get(0).points()[119]);
        assertEquals(java.util.Set.of("item:17", "item:new"), snapshot.pinned());
        assertTrue(snapshot.canPin());
    }

    @Test
    void movedByItemSendsTheDomainsAndEachNetworksTopKeys() {
        var book = new StatisticsBook<String>();
        book.observe(20);
        var ab = StatisticsNames.pair(StatisticsNames.MOVED, A, B);
        var ba = StatisticsNames.pair(StatisticsNames.MOVED, B, A);
        for (int index = 0; index < 8; index++) book.moved(ab, 20, "item:" + index, 100 - index);
        book.moved(ba, 20, "item:7", 500);
        book.moved(ba, 20, "fluid:water", 9000);
        var snapshot = roundTrip(book, List.of(A, B), new StatisticsPayload.Request(StatisticsRange.SECONDS,
                StatisticsPayload.MOVED, "ae2:i", true, false), 20, StatisticsPayload.Extras.none());
        var domain = snapshot.movedKeys().stream().filter(row -> row.network().isEmpty()).toList();
        assertEquals(StatisticsPayload.MOVED_KEYS, domain.size());
        var seven = domain.stream().filter(row -> row.key().equals("item:7")).findFirst().orElseThrow();
        assertEquals(593f, seven.points()[119], "both directions add up for the domain");
        var intoA = snapshot.movedKeys().stream().filter(row -> row.network().equals(A.value().toString())
                && row.direction().equals("in")).toList();
        assertEquals(List.of("item:7"), intoA.stream().map(StatisticsPayload.KeySeries::key).toList());
        assertEquals(500f, intoA.get(0).points()[119]);
        assertTrue(snapshot.movedKeys().stream().noneMatch(row -> row.key().equals("fluid:water")));
    }

    @Test
    void cpusAndTheCatalogArriveForTheirMetricAndFlag() {
        var book = new StatisticsBook<String>();
        book.observe(1);
        var extras = new StatisticsPayload.Extras<String>(List.of(
                new StatisticsPayload.Cpu<>(A.value().toString(), "Main", 4, 65_536, "item:gear", 64, 640, 320, 5_000_000_000L),
                new StatisticsPayload.Cpu<String>(B.value().toString(), "", 0, 1024, null, 0, 0, 0, 0),
                new StatisticsPayload.Cpu<String>(OUTSIDE.value().toString(), "", 0, 1024, null, 0, 0, 0, 0)),
                java.util.Map.of("item:a", 5L, "item:b", 50L, "fluid:water", 9L), java.util.Set.of(), false);
        var cpus = roundTrip(book, List.of(A, B), new StatisticsPayload.Request(StatisticsRange.SECONDS,
                StatisticsPayload.CPUS, "ae2:i", false, true), 1, extras);
        assertEquals(2, cpus.cpus().size(), "only the members' CPUs");
        assertEquals("item:gear", cpus.cpus().get(0).output());
        assertEquals(320, cpus.cpus().get(0).progress());
        assertTrue(cpus.cpus().get(1).output() == null);
        assertEquals(List.of("item:b", "item:a"), cpus.catalog().stream().map(StatisticsPayload.Amount::key).toList());
        var power = roundTrip(book, List.of(A, B), new StatisticsPayload.Request(StatisticsRange.SECONDS,
                StatisticsPayload.POWER, "ae2:i", false, false), 1, extras);
        assertTrue(power.cpus().isEmpty());
        assertTrue(power.catalog().isEmpty());
    }

    @Test
    void anEmptyTagDecodesToNoSnapshot() {
        assertTrue(StatisticsPayload.decode(new net.minecraft.nbt.CompoundTag(), value -> value.getAsString()) == null);
    }

    @Test
    void aConstantSeriesIsSentOnce() {
        var book = new StatisticsBook<String>();
        for (long second = 0; second < 120; second++) {
            book.observe(second);
            book.gauge(StatisticsNames.network(StatisticsNames.CHANNELS, A), second, 7);
        }
        var tag = StatisticsPayload.encode(book, List.of(A), new StatisticsPayload.Request(StatisticsRange.SECONDS,
                StatisticsPayload.CHANNELS, "", false, false), 119, false, false, StatisticsPayload.Extras.none(),
                StringTag::valueOf, key -> "ae2:i");
        var channels = tag.getList("networks", 10).getCompound(0).getCompound("series").getIntArray(StatisticsNames.CHANNELS);
        assertEquals(1, channels.length);
        var snapshot = StatisticsPayload.decode(tag, value -> value.getAsString());
        assertEquals(7f, snapshot.networks().get(A.value().toString()).get(StatisticsNames.CHANNELS)[0]);
        assertEquals(120, snapshot.networks().get(A.value().toString()).get(StatisticsNames.CHANNELS).length);
    }

    @Test
    void aBusyEightNetworkDomainStaysSmall() throws java.io.IOException {
        var book = new StatisticsBook<String>();
        var members = new java.util.ArrayList<NetworkId>();
        for (int index = 0; index < 8; index++) members.add(new NetworkId(new UUID(0x5eed, index)));
        var random = new java.util.Random(7);
        for (long second = 0; second < 120; second++) {
            book.observe(second);
            for (var from : members) {
                for (var name : new String[] {StatisticsNames.POWER, StatisticsNames.POOL, StatisticsNames.CPUS,
                        StatisticsNames.BUSY_CPUS, StatisticsNames.CHANNELS}) {
                    book.gauge(StatisticsNames.network(name, from), second, random.nextInt(1000));
                }
                book.gauge(StatisticsNames.stored(from, "ae2:i"), second, random.nextInt(100_000));
                book.gauge(StatisticsNames.stored(from, "ae2:f"), second, random.nextInt(100_000));
                book.count(StatisticsNames.network(StatisticsNames.DONE, from), second, random.nextInt(2));
                for (var to : members) {
                    if (from == to) continue;
                    book.count(StatisticsNames.moved(from, to, "ae2:i"), second, random.nextInt(64));
                    book.count(StatisticsNames.moved(from, to, "ae2:f"), second, random.nextInt(4000));
                    book.count(StatisticsNames.pair(StatisticsNames.PUSHED, from, to), second, random.nextInt(2));
                    var pair = StatisticsNames.pair(StatisticsNames.MOVED, from, to);
                    for (int key = 0; key < 12; key++) book.moved(pair, second, "item:" + key, random.nextInt(64));
                    book.moved(pair, second, "fluid:water", random.nextInt(4000));
                }
            }
        }
        var moved = new StatisticsPayload.Request(StatisticsRange.SECONDS, StatisticsPayload.MOVED, "ae2:i", true, false);
        var tag = StatisticsPayload.encode(book, members, moved, 119, true, true, StatisticsPayload.Extras.none(),
                StringTag::valueOf, StatisticsPayloadTest::kind);
        var snapshot = StatisticsPayload.decode(tag, value -> value.getAsString());
        assertEquals(8 * 7 * 2, snapshot.moved().size());
        assertEquals(8 * 7 * (StatisticsPayload.RANKED_PER_KIND + 1), snapshot.ranking().size());
        // Every pair moving items and fluids every second is the worst a domain of eight sends, once a second while the
        // page shows the last two minutes, for the moved metric by item.
        System.out.println("STATS_PAYLOAD moved bytes=" + size(tag) + " gz=" + gzipped(tag) + " buildMs=" + buildMillis(() ->
                StatisticsPayload.encode(book, members, moved, 119, true, true, StatisticsPayload.Extras.none(),
                        StringTag::valueOf, StatisticsPayloadTest::kind)));
        var power = new StatisticsPayload.Request(StatisticsRange.SECONDS, StatisticsPayload.POWER, "ae2:i", false, false);
        var powerTag = StatisticsPayload.encode(book, members, power, 119, true, true, StatisticsPayload.Extras.none(),
                StringTag::valueOf, StatisticsPayloadTest::kind);
        System.out.println("STATS_PAYLOAD power bytes=" + size(powerTag) + " gz=" + gzipped(powerTag) + " buildMs="
                + buildMillis(() -> StatisticsPayload.encode(book, members, power, 119, true, true,
                        StatisticsPayload.Extras.none(), StringTag::valueOf, StatisticsPayloadTest::kind)));
        assertTrue(size(tag) < 240_000, "payload of " + size(tag) + " bytes");
    }

    @Test
    void aFullEightNetworkStockStaysSmall() throws java.io.IOException {
        var book = new StatisticsBook<String>();
        var members = new java.util.ArrayList<NetworkId>();
        for (int index = 0; index < 8; index++) members.add(new NetworkId(new UUID(0x5eed, index)));
        var random = new java.util.Random(7);
        var pinned = new java.util.HashSet<String>();
        for (int key = 0; key < 16; key++) pinned.add("item:pin" + key);
        for (long second = 0; second < 120; second++) {
            book.observe(second);
            for (var network : members) {
                var name = StatisticsNames.network(StatisticsNames.STOCK, network);
                // Every network holds every one of the domain's top keys and every pinned one, changing each second.
                for (int key = 0; key < 16; key++) book.keyGauge(name, "item:" + key, second, 100_000 + random.nextInt(100_000));
                for (var key : pinned) book.keyGauge(name, key, second, random.nextInt(100_000));
            }
        }
        var stock = new StatisticsPayload.Request(StatisticsRange.SECONDS, StatisticsPayload.STOCK, "ae2:i", false, false);
        var tag = StatisticsPayload.encode(book, members, stock, 119, true, true,
                new StatisticsPayload.Extras<>(List.of(), java.util.Map.of(), pinned, false), StringTag::valueOf,
                StatisticsPayloadTest::kind);
        assertEquals(8 * 32, StatisticsPayload.decode(tag, value -> value.getAsString()).stock().size());
        System.out.println("STATS_PAYLOAD stock bytes=" + size(tag) + " gz=" + gzipped(tag) + " buildMs=" + buildMillis(() ->
                StatisticsPayload.encode(book, members, stock, 119, true, true,
                        new StatisticsPayload.Extras<>(List.of(), java.util.Map.of(), pinned, false), StringTag::valueOf,
                        StatisticsPayloadTest::kind)));
        assertTrue(size(tag) < 200_000, "payload of " + size(tag) + " bytes");
    }

    private static int gzipped(net.minecraft.nbt.CompoundTag tag) throws java.io.IOException {
        var bytes = new java.io.ByteArrayOutputStream();
        net.minecraft.nbt.NbtIo.writeCompressed(tag, bytes);
        return bytes.size();
    }

    /** Milliseconds one build takes once warmed up: the median of twenty after two hundred. */
    private static double buildMillis(Runnable build) {
        for (int warm = 0; warm < 200; warm++) build.run();
        var times = new double[20];
        for (int at = 0; at < times.length; at++) {
            long start = System.nanoTime();
            build.run();
            times[at] = (System.nanoTime() - start) / 1e6;
        }
        java.util.Arrays.sort(times);
        return times[times.length / 2];
    }

    private static int size(net.minecraft.nbt.CompoundTag tag) throws java.io.IOException {
        var bytes = new java.io.ByteArrayOutputStream();
        net.minecraft.nbt.NbtIo.write(tag, new java.io.DataOutputStream(bytes));
        return bytes.size();
    }
}
