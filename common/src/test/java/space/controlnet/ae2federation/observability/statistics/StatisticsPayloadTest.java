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

    private static StatisticsPayload.Snapshot<String> roundTrip(StatisticsBook<String> book, List<NetworkId> members,
            StatisticsRange range, long now) {
        var tag = StatisticsPayload.encode(book, members, range, now, true, false, StringTag::valueOf,
                key -> key.startsWith("fluid:") ? "ae2:f" : "ae2:i");
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
        var tag = StatisticsPayload.encode(book, List.of(A), StatisticsRange.SECONDS, 119, false, false,
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
        var tag = StatisticsPayload.encode(book, members, StatisticsRange.SECONDS, 119, true, true, StringTag::valueOf,
                key -> key.startsWith("fluid:") ? "ae2:f" : "ae2:i");
        var snapshot = StatisticsPayload.decode(tag, value -> value.getAsString());
        assertEquals(8 * 7 * 2, snapshot.moved().size());
        assertEquals(8 * 7 * (StatisticsPayload.RANKED_PER_KIND + 1), snapshot.ranking().size());
        var bytes = new java.io.ByteArrayOutputStream();
        net.minecraft.nbt.NbtIo.write(tag, new java.io.DataOutputStream(bytes));
        // Every pair moving items and fluids every second is the worst a domain of eight sends: about 140 KB, sent
        // once a second while the page shows the last two minutes.
        assertTrue(bytes.size() < 160_000, "payload of " + bytes.size() + " bytes");
    }
}
