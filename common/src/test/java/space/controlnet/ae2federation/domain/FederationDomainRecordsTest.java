package space.controlnet.ae2federation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.identity.NetworkId;

/** Domain ids outlive the registry: a new registry over the same records gives a domain its old id back. */
final class FederationDomainRecordsTest {
    private static final String HERE = "test:dimension";
    private static final String OTHER_DIMENSION = "test:other";
    private static final NetworkId NETWORK_A = new NetworkId(new UUID(0, 1));
    private static final NetworkId NETWORK_B = new NetworkId(new UUID(0, 2));
    private static final NetworkId NETWORK_C = new NetworkId(new UUID(0, 3));

    @Test
    void aDomainGetsItsIdBackAfterARestartAndNewIdsContinueTheSequence() {
        var records = new FederationDomainRecords();
        var before = registry(records);
        before.upsertNode(device(node(100), null, null));
        before.federationDomains();
        var line = line(1, "R", "C", "C", "C", "C", "E");
        line.forEach(before::upsertNode);
        before.federationDomains();
        before.removeBrokenNode(node(100));
        var saved = domainOf(before, 1);
        assertEquals(FederationDomainId.physical(2), saved, "a decoy domain took the first id");

        var after = registry(records);
        line.reversed().forEach(after::upsertNode);

        assertEquals(saved, domainOf(after, 3), "the domain is rebuilt from its saved anchors");
        after.upsertNode(device(node(200), null, null));
        assertEquals(FederationDomainId.physical(3), domainOf(after, 200), "a new id never repeats a saved one");
    }

    @Test
    void theFirstPartToLoadKeepsTheIdWhenTheRestJoinsLater() {
        for (var smallPartFirst : List.of(true, false)) {
            var records = new FederationDomainRecords();
            var before = registry(records);
            var line = partialLine();
            line.forEach(before::upsertNode);
            var saved = domainOf(before, 1);

            var after = registry(records);
            var small = line.subList(0, 3);
            var large = line.subList(4, 9);
            (smallPartFirst ? small : large).forEach(after::upsertNode);
            assertEquals(saved, domainOf(after, smallPartFirst ? 1 : 9), "the part loaded first claims the saved id");
            (smallPartFirst ? large : small).forEach(after::upsertNode);
            var later = domainOf(after, smallPartFirst ? 9 : 1);
            assertNotEquals(saved, later, "a saved id is claimed by one component only");

            after.upsertNode(line.get(3));
            assertEquals(saved, domainOf(after, 1), "the joined domain keeps the id that existed before the restart");
            assertEquals(saved, domainOf(after, 9));
            assertEquals(Set.of(saved), ids(after));
            assertEquals(Set.of(node(1), node(5), node(9)), records.anchors(1).orElseThrow().devices());
            assertEquals(1, records.all().size(), "the merged-away id leaves no record");

            var again = registry(records);
            line.forEach(again::upsertNode);
            assertEquals(saved, domainOf(again, 5));
        }
    }

    @Test
    void halvesLoadedTogetherShareOutTheSavedIdOnce() {
        var records = new FederationDomainRecords();
        var before = registry(records);
        var line = line(1, "R", "C", "C", "C", "E");
        line.forEach(before::upsertNode);
        var saved = domainOf(before, 1);

        var after = registry(records);
        List.of(line.get(0), line.get(1), line.get(3), line.get(4)).forEach(after::upsertNode);
        var left = domainOf(after, 1);
        var right = domainOf(after, 5);
        assertNotEquals(left, right);
        assertTrue(left.equals(saved) || right.equals(saved), "one half gets the saved id");

        after.upsertNode(line.get(2));
        assertEquals(saved, domainOf(after, 3), "joined again, the domain has its saved id");
    }

    @Test
    void unloadingKeepsAnchorsAndBreakingDropsThem() {
        var records = new FederationDomainRecords();
        var registry = registry(records);
        var line = line(1, "R", "C", "C", "E");
        line.forEach(registry::upsertNode);
        var saved = domainOf(registry, 1);

        line.forEach(evidence -> registry.removeNode(evidence.nodeId()));
        registry.federationDomains();
        assertEquals(Set.of(node(1), node(4)), records.anchors(1).orElseThrow().devices(), "unloading drops nothing");
        line.forEach(registry::upsertNode);
        assertEquals(saved, domainOf(registry, 2), "a domain unloaded whole gets its id back when it loads again");

        registry.removeBrokenNode(node(4));
        registry.federationDomains();
        assertEquals(Set.of(node(1)), records.anchors(1).orElseThrow().devices());
        registry.removeBrokenNode(node(1));
        registry.federationDomains();
        assertEquals(Set.of(node(2), node(3)), records.anchors(1).orElseThrow().cables(),
                "a domain left with cables only is anchored by its cables");
        registry.removeBrokenNode(node(2));
        registry.removeBrokenNode(node(3));
        registry.federationDomains();
        assertTrue(records.all().isEmpty(), "a domain broken entirely leaves no record");

        var after = registry(records);
        line.forEach(after::upsertNode);
        assertNotEquals(saved, domainOf(after, 1), "blocks placed again where a broken domain was form a new domain");
    }

    @Test
    void closingADimensionKeepsTheAnchorsItTookOut() {
        var records = new FederationDomainRecords();
        var registry = registry(records);
        var here = node(1);
        var there = new FederationDomainNodeId(OTHER_DIMENSION, 1);
        registry.upsertNode(device(here, null, there));
        registry.upsertNode(device(there, here, null));
        var saved = domainOf(registry, 1);

        registry.removeDimension(OTHER_DIMENSION);
        assertEquals(saved, domainOf(registry, 1));
        assertEquals(Set.of(here, there), records.anchors(1).orElseThrow().devices());

        var after = registry(records);
        after.upsertNode(device(there, here, null));
        assertEquals(saved, after.federationDomainOf(there).orElseThrow().federationDomainId(),
                "the other dimension's part alone finds the id");
    }

    @Test
    void aCableOnlyDomainIsAnchoredByItsSmallestCablesAndOtherwiseByItsDevices() {
        var records = new FederationDomainRecords();
        var registry = registry(records);
        var kinds = new String[12];
        java.util.Arrays.fill(kinds, "C");
        var cables = line(1, kinds);
        cables.reversed().forEach(registry::upsertNode);
        registry.federationDomains();
        var anchors = records.anchors(1).orElseThrow();
        assertEquals(LongStream.rangeClosed(1, FederationDomainRecords.CABLE_ANCHORS).mapToObj(FederationDomainRecordsTest::node)
                .collect(Collectors.toSet()), anchors.cables());
        assertTrue(anchors.devices().isEmpty());

        registry.upsertNode(cable(node(12), node(11), node(13)));
        registry.upsertNode(device(node(13), node(12), null));
        registry.federationDomains();
        assertEquals(Set.of(node(13)), records.anchors(1).orElseThrow().devices());
        assertTrue(records.anchors(1).orElseThrow().cables().isEmpty(), "a domain with devices keeps no cables");
    }

    @Test
    void recordsChangeOnlyWhenTheAnchorsDo() {
        var records = new FederationDomainRecords();
        var changes = new int[1];
        records.onChange(() -> changes[0]++);
        var registry = registry(records);
        line(1, "R", "C", "C").forEach(registry::upsertNode);
        registry.federationDomains();
        assertTrue(changes[0] > 0);

        changes[0] = 0;
        registry.upsertNode(cable(node(3), node(2), node(4)));
        registry.upsertNode(cable(node(4), node(3), null));
        registry.federationDomains();
        assertEquals(0, changes[0], "a cable joining a domain with devices changes no record");
        registry.removeNode(node(4));
        registry.federationDomains();
        assertEquals(0, changes[0], "an unloaded cable changes no record");
    }

    private static FederationDomainRegistry registry(FederationDomainRecords records) {
        return new FederationDomainRegistry(FederationDomainRecomputeBudget.standard(), records);
    }

    /**
     * Router 1 (network A), cables 2-4, Router 5 (network C), cables 6-8, Endpoint 9 (network B). Node 4 joins the
     * parts; the larger part, 5-9, has more members than the smaller one, 1-3.
     */
    private static List<FederationDomainNodeEvidence> partialLine() {
        var line = new ArrayList<>(line(1, "R", "C", "C", "C", "R", "C", "C", "C", "E"));
        line.set(0, withMember(line.get(0), NETWORK_A));
        line.set(4, withMember(line.get(4), NETWORK_C));
        line.set(8, withMember(line.get(8), NETWORK_B));
        return line;
    }

    /** Nodes at consecutive positions from {@code first}, each linked to its neighbours; R, E or C for its kind. */
    private static List<FederationDomainNodeEvidence> line(long first, String... kinds) {
        var line = new ArrayList<FederationDomainNodeEvidence>();
        for (var index = 0; index < kinds.length; index++) {
            var position = first + index;
            var west = index == 0 ? null : node(position - 1);
            var east = index + 1 == kinds.length ? null : node(position + 1);
            var kind = switch (kinds[index]) {
                case "R" -> FederationDomainNodeKind.ROUTER;
                case "E" -> FederationDomainNodeKind.ENDPOINT;
                default -> FederationDomainNodeKind.CABLE;
            };
            line.add(new FederationDomainNodeEvidence(node(position), kind, ports(west, east)));
        }
        return line;
    }

    private static FederationDomainNodeEvidence device(FederationDomainNodeId node, FederationDomainNodeId west,
            FederationDomainNodeId east) {
        return new FederationDomainNodeEvidence(node, FederationDomainNodeKind.ROUTER, ports(west, east));
    }

    private static FederationDomainNodeEvidence cable(FederationDomainNodeId node, FederationDomainNodeId west,
            FederationDomainNodeId east) {
        return new FederationDomainNodeEvidence(node, FederationDomainNodeKind.CABLE, ports(west, east));
    }

    private static FederationDomainNodeEvidence withMember(FederationDomainNodeEvidence evidence, NetworkId network) {
        var ports = new LinkedHashMap<>(evidence.ports());
        ports.put("up", new FederationDomainPortEvidence.Native(FederationDomainSourceId.nativePort(
                new FederationDomainPortId(evidence.nodeId(), "up")), network));
        return new FederationDomainNodeEvidence(evidence.nodeId(), evidence.kind(), ports);
    }

    private static Map<String, FederationDomainPortEvidence> ports(FederationDomainNodeId west, FederationDomainNodeId east) {
        var ports = new LinkedHashMap<String, FederationDomainPortEvidence>();
        if (west != null) {
            ports.put("west", new FederationDomainPortEvidence.Federation(new FederationDomainPortId(west, "east")));
        }
        if (east != null) {
            ports.put("east", new FederationDomainPortEvidence.Federation(new FederationDomainPortId(east, "west")));
        }
        return ports;
    }

    private static FederationDomainId domainOf(FederationDomainRegistry registry, long position) {
        return registry.federationDomainOf(node(position)).orElseThrow().federationDomainId();
    }

    private static Set<FederationDomainId> ids(FederationDomainRegistry registry) {
        return registry.federationDomains().stream().map(FederationDomainSnapshot::federationDomainId)
                .collect(Collectors.toSet());
    }

    private static FederationDomainNodeId node(long position) {
        return new FederationDomainNodeId(HERE, position);
    }
}
