package space.controlnet.ae2federation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.identity.NetworkId;

final class FederationDomainRegistryTest {
    private static final NetworkId NETWORK_A = network(1);
    private static final NetworkId NETWORK_B = network(2);
    private static final NetworkId NETWORK_C = network(3);

    @Test
    void directBridgesRemainSeparateWhenTheyShareNativeNetworks() {
        var registry = new FederationDomainRegistry(FederationDomainRecomputeBudget.standard());
        var sources = java.util.stream.IntStream.range(0, 4)
                .mapToObj(index -> new FederationDomainSourceId("bridge-" + index))
                .toList();

        sources.forEach(source -> registry.upsertDirectBridge(source, NETWORK_A, NETWORK_B));

        assertEquals(4, registry.snapshot().federationDomains().size());
        assertEquals(4, registry.federationdomainsFor(NETWORK_A).size());
        assertEquals(4, registry.federationdomainsFor(NETWORK_B).size());
    }

    @Test
    void reciprocalTopologyMergesAndSplitsOnlyAffectedComponents() {
        var registry = new FederationDomainRegistry(FederationDomainRecomputeBudget.standard());
        var left = node(1);
        var right = node(2);
        registry.upsertNode(evidence(left, Map.of("east",
                new FederationDomainPortEvidence.Federation(new FederationDomainPortId(right, "west"))), NETWORK_A));
        registry.upsertNode(evidence(right, Map.of("west",
                new FederationDomainPortEvidence.Federation(new FederationDomainPortId(left, "east"))), NETWORK_B));
        var merged = registry.snapshot().federationDomains().values().iterator().next();

        registry.upsertNode(evidence(left, Map.of(), NETWORK_A));
        assertFalse(registry.isCurrent(merged.reference()));
        assertTrue(shared(registry, NETWORK_A, NETWORK_B).isEmpty());
        registry.upsertNode(evidence(right, Map.of(), NETWORK_B));

        assertEquals(2, registry.snapshot().federationDomains().size());
        assertFalse(registry.isCurrent(merged.reference()));
        assertEquals(1, registry.federationdomainsFor(NETWORK_A).size());
        assertEquals(1, registry.federationdomainsFor(NETWORK_B).size());
    }

    @Test
    void membershipFreeNodeResolvesToTheDomainItsFederationPortJoins() {
        var registry = new FederationDomainRegistry(FederationDomainRecomputeBudget.standard());
        var member = node(1);
        var endpoint = node(2);
        registry.upsertNode(evidence(member, Map.of("east",
                new FederationDomainPortEvidence.Federation(new FederationDomainPortId(endpoint, "west"))), NETWORK_A));
        registry.upsertNode(new FederationDomainNodeEvidence(endpoint, Map.of("west",
                new FederationDomainPortEvidence.Federation(new FederationDomainPortId(member, "east")))));

        var domain = registry.federationDomainOf(endpoint).orElseThrow();
        assertTrue(domain.memberships().containsKey(NETWORK_A));
        assertFalse(domain.memberships().containsKey(NETWORK_B));
        assertTrue(registry.federationDomainOf(node(3)).isEmpty());

        registry.upsertNode(new FederationDomainNodeEvidence(endpoint, Map.of()));
        registry.upsertNode(evidence(member, Map.of(), NETWORK_A));
        assertFalse(registry.federationDomainOf(endpoint)
                .map(federationDomain -> federationDomain.memberships().containsKey(NETWORK_A)).orElse(false));
    }

    @Test
    void redundantAttachmentsDeduplicateNetworkMembershipAndRetainSources() {
        var registry = new FederationDomainRegistry(FederationDomainRecomputeBudget.standard());
        var node = node(1);
        var ports = new LinkedHashMap<String, FederationDomainPortEvidence>();
        ports.put("north", nativePort(node, "north", NETWORK_A));
        ports.put("south", nativePort(node, "south", NETWORK_A));

        registry.upsertNode(new FederationDomainNodeEvidence(node, ports));

        var federationDomain = registry.snapshot().federationDomains().values().iterator().next();
        assertEquals(1, federationDomain.memberships().size());
        assertEquals(2, federationDomain.memberships().get(NETWORK_A).size());
        assertEquals(1, registry.federationdomainsFor(NETWORK_A).size());
    }

    @Test
    void partialUnloadInvalidatesMembershipWithoutInventingLoadedEvidence() {
        var registry = new FederationDomainRegistry(FederationDomainRecomputeBudget.standard());
        var left = node(1);
        var cable = node(2);
        var right = node(3);
        registry.upsertNode(evidence(left, Map.of("east",
                new FederationDomainPortEvidence.Federation(new FederationDomainPortId(cable, "west"))), NETWORK_A));
        registry.upsertNode(new FederationDomainNodeEvidence(cable, Map.of(
                "west", new FederationDomainPortEvidence.Federation(new FederationDomainPortId(left, "east")),
                "east", new FederationDomainPortEvidence.Federation(new FederationDomainPortId(right, "west")))));
        registry.upsertNode(evidence(right, Map.of("west",
                new FederationDomainPortEvidence.Federation(new FederationDomainPortId(cable, "east"))), NETWORK_B));

        var merged = registry.federationDomainOf(cable).orElseThrow().reference();

        registry.removeNode(cable);

        assertFalse(registry.isCurrent(merged));
        assertTrue(shared(registry, NETWORK_A, NETWORK_B).isEmpty());
        assertTrue(registry.federationDomainOf(cable).isEmpty());
        assertEquals(FederationDomainInvalidationReason.SOURCE_UNLOADED,
                registry.snapshot().invalidations().get(cable));
    }

    @Test
    void budgetExhaustionInvalidatesStaleReferencesImmediately() {
        var registry = new FederationDomainRegistry(new FederationDomainRecomputeBudget(1, 6));
        var left = node(1);
        var right = node(2);
        registry.upsertNode(evidence(left, Map.of(), NETWORK_A));
        var stale = registry.snapshot().federationDomains().values().iterator().next().reference();

        registry.upsertNode(evidence(left, Map.of("east",
                new FederationDomainPortEvidence.Federation(new FederationDomainPortId(right, "west"))), NETWORK_A));
        registry.upsertNode(evidence(right, Map.of("west",
                new FederationDomainPortEvidence.Federation(new FederationDomainPortId(left, "east"))), NETWORK_C));

        assertFalse(registry.isCurrent(stale));
        assertTrue(registry.federationdomainsFor(NETWORK_A).isEmpty());
        assertTrue(registry.snapshot().invalidations().containsValue(FederationDomainInvalidationReason.BUDGET_EXHAUSTED));
    }

    @Test
    void unsettledIdentityNeverCreatesConfirmedMembership() {
        var registry = new FederationDomainRegistry(FederationDomainRecomputeBudget.standard());
        var node = node(1);

        registry.upsertNode(new FederationDomainNodeEvidence(node, Map.of("north",
                new FederationDomainPortEvidence.Unsettled(new FederationDomainSourceId("native-unsettled"), "AMBIGUOUS_SPLIT"))));

        assertTrue(registry.federationDomainOf(node).orElseThrow().memberships().isEmpty());
        assertTrue(registry.snapshot().networkIndex().isEmpty());
        assertEquals(FederationDomainInvalidationReason.IDENTITY_UNSETTLED,
                registry.snapshot().invalidations().get(node));
    }

    @Test
    void aNewCableJoinsWithoutWithdrawingTheDomainWhenTheExistingSidePublishesFirst() {
        assertNewCableJoinsWithoutWithdrawingTheDomain(true);
    }

    @Test
    void aNewCableJoinsWithoutWithdrawingTheDomainWhenTheNewSidePublishesFirst() {
        assertNewCableJoinsWithoutWithdrawingTheDomain(false);
    }

    @Test
    void removingADeadEndKeepsTheDomainCurrent() {
        var registry = new FederationDomainRegistry(FederationDomainRecomputeBudget.standard());
        var nodes = java.util.List.of(node(1), node(2), node(3));
        publishChain(registry, nodes, Map.of(node(1), NETWORK_A, node(3), NETWORK_B));
        var branch = node(4);
        registry.upsertNode(new FederationDomainNodeEvidence(node(2), Map.of("west", federation(node(1), "east"),
                "east", federation(node(3), "west"), "north", federation(branch, "south"))));
        registry.upsertNode(new FederationDomainNodeEvidence(branch, Map.of("south", federation(node(2), "north"))));
        var domain = registry.federationDomainOf(branch).orElseThrow().reference();

        registry.removeNode(branch);
        assertTrue(registry.isCurrent(domain));
        assertEquals(FederationDomainInvalidationReason.NON_RECIPROCAL_EDGE, registry.snapshot().invalidations().get(node(2)));
        registry.upsertNode(new FederationDomainNodeEvidence(node(2), Map.of("west", federation(node(1), "east"),
                "east", federation(node(3), "west"))));

        assertTrue(registry.isCurrent(domain));
        assertFalse(registry.snapshot().invalidations().containsKey(node(2)));
    }

    @Test
    void aSplitKeepsTheIdAndGenerationOnTheSideThatKeepsEveryMember() {
        var registry = new FederationDomainRegistry(FederationDomainRecomputeBudget.standard());
        var nodes = java.util.stream.LongStream.rangeClosed(1, 6).mapToObj(FederationDomainRegistryTest::node).toList();
        publishChain(registry, nodes, Map.of(node(1), NETWORK_A));
        var domain = registry.federationDomainOf(node(1)).orElseThrow().reference();

        registry.upsertNode(new FederationDomainNodeEvidence(node(2), Map.of("west", federation(node(1), "east"))));
        registry.upsertNode(new FederationDomainNodeEvidence(node(3), Map.of("east", federation(node(4), "west"))));

        assertTrue(registry.isCurrent(domain));
        assertEquals(domain, registry.federationDomainOf(node(2)).orElseThrow().reference());
        var deadEnd = registry.federationDomainOf(node(6)).orElseThrow();
        assertFalse(deadEnd.federationDomainId().equals(domain.federationDomainId()));
        assertEquals(deadEnd.federationDomainId(), registry.federationDomainOf(node(3)).orElseThrow().federationDomainId());
        assertTrue(deadEnd.memberships().isEmpty());
    }

    @Test
    void aMergeBumpsTheGenerationAndRetiresTheAbsorbedDomain() {
        var registry = new FederationDomainRegistry(FederationDomainRecomputeBudget.standard());
        publishChain(registry, java.util.List.of(node(1), node(2), node(3)), Map.of(node(1), NETWORK_A));
        var survivor = registry.federationDomainOf(node(1)).orElseThrow().reference();
        var right = node(4);
        registry.upsertNode(evidence(right, Map.of(), NETWORK_B));
        var absorbed = registry.federationDomainOf(right).orElseThrow().reference();

        registry.upsertNode(new FederationDomainNodeEvidence(node(3), Map.of("west", federation(node(2), "east"),
                "east", federation(right, "west"))));
        assertTrue(registry.isCurrent(survivor));
        registry.upsertNode(evidence(right, Map.of("west", federation(node(3), "east")), NETWORK_B));

        var merged = registry.federationDomainOf(right).orElseThrow();
        assertEquals(survivor.federationDomainId(), merged.federationDomainId());
        assertFalse(registry.isCurrent(survivor));
        assertFalse(registry.isCurrent(absorbed));
        assertEquals(Set.of(merged.federationDomainId()), shared(registry, NETWORK_A, NETWORK_B));
    }

    @Test
    void physicalIdsAreNeverReused() {
        var registry = new FederationDomainRegistry(FederationDomainRecomputeBudget.standard());
        var seen = new java.util.HashSet<FederationDomainId>();
        for (var round = 0; round < 3; round++) {
            registry.upsertNode(evidence(node(1), Map.of(), NETWORK_A));
            assertTrue(seen.add(registry.federationDomainOf(node(1)).orElseThrow().federationDomainId()));
            registry.removeNode(node(1));
        }
    }

    @Test
    void anUnchangedDirectBridgeKeepsItsReference() {
        var registry = new FederationDomainRegistry(FederationDomainRecomputeBudget.standard());
        var source = new FederationDomainSourceId("bridge");
        registry.upsertDirectBridge(source, NETWORK_A, NETWORK_B);
        var reference = registry.federationDomain(FederationDomainId.direct(source)).orElseThrow().reference();
        var revision = registry.snapshot().topologyRevision();

        registry.upsertDirectBridge(source, NETWORK_A, NETWORK_B);
        assertTrue(registry.isCurrent(reference));
        assertEquals(revision, registry.snapshot().topologyRevision());

        registry.upsertDirectBridge(source, NETWORK_A, NETWORK_C);
        assertFalse(registry.isCurrent(reference));
    }

    @Test
    void aHalfPublishedEdgeKeepsTheRestOfTheDomain() {
        var registry = new FederationDomainRegistry(FederationDomainRecomputeBudget.standard());
        var left = node(1);
        var right = node(2);
        registry.upsertNode(evidence(left, Map.of("east", federation(right, "west")), NETWORK_A));
        registry.upsertNode(evidence(right, Map.of("west", federation(left, "east")), NETWORK_B));
        var domain = registry.federationDomainOf(left).orElseThrow().reference();

        registry.upsertNode(evidence(left, Map.of("east", federation(right, "west"), "north", federation(node(9), "south")),
                NETWORK_A));

        assertTrue(registry.isCurrent(domain));
        assertEquals(FederationDomainInvalidationReason.NON_RECIPROCAL_EDGE, registry.snapshot().invalidations().get(left));
    }

    @Test
    void anUnsettledPortWithholdsOnlyItsOwnMembership() {
        var registry = new FederationDomainRegistry(FederationDomainRecomputeBudget.standard());
        var left = node(1);
        var right = node(2);
        registry.upsertNode(evidence(left, Map.of("east", federation(right, "west"), "north",
                new FederationDomainPortEvidence.Unsettled(new FederationDomainSourceId("native-fresh"), "PARTIAL_LOAD")),
                NETWORK_A));
        registry.upsertNode(evidence(right, Map.of("west", federation(left, "east")), NETWORK_B));

        assertEquals(Set.of(NETWORK_A, NETWORK_B), registry.federationDomainOf(left).orElseThrow().memberships().keySet());
        assertEquals(FederationDomainInvalidationReason.IDENTITY_UNSETTLED, registry.snapshot().invalidations().get(left));
    }

    @Test
    void randomTopologyMatchesAFromScratchPartitionOfMutualLinks() {
        var random = new java.util.Random(20260930L);
        var registry = new FederationDomainRegistry(FederationDomainRecomputeBudget.standard());
        var networks = java.util.List.of(NETWORK_A, NETWORK_B, NETWORK_C);
        var published = new java.util.HashMap<FederationDomainNodeId, FederationDomainNodeEvidence>();
        var retired = new java.util.HashSet<FederationDomainId>();
        var previous = registry.snapshot();
        for (var step = 0; step < 4000; step++) {
            var index = random.nextInt(9);
            var id = node(index);
            if (random.nextInt(6) == 0) {
                registry.removeNode(id);
                published.remove(id);
            } else {
                var ports = new LinkedHashMap<String, FederationDomainPortEvidence>();
                for (var peer = 0; peer < 9; peer++) {
                    if (peer != index && random.nextInt(4) == 0) {
                        ports.put("to" + peer, federation(node(peer), "to" + index));
                    }
                }
                switch (random.nextInt(4)) {
                    case 0 -> ports.put("native", nativePort(id, "native", networks.get(random.nextInt(networks.size()))));
                    case 1 -> ports.put("native", new FederationDomainPortEvidence.Unsettled(
                            FederationDomainSourceId.nativePort(new FederationDomainPortId(id, "native")), "PARTIAL_LOAD"));
                    default -> {
                    }
                }
                var evidence = new FederationDomainNodeEvidence(id, ports);
                registry.upsertNode(evidence);
                published.put(id, evidence);
            }
            var current = registry.snapshot();
            assertMatchesMutualLinkPartition(registry, current, published, step);
            assertTrue(current.topologyRevision() >= previous.topologyRevision(), "step " + step);
            for (var domain : current.federationDomains().values()) {
                assertFalse(retired.contains(domain.federationDomainId()), "id reused at step " + step);
                var before = previous.federationDomains().get(domain.federationDomainId());
                if (before == null) {
                    assertTrue(domain.generation() > previous.topologyRevision(), "step " + step);
                } else {
                    assertEquals(before.memberships().keySet().equals(domain.memberships().keySet()),
                            before.generation() == domain.generation(), "step " + step);
                }
            }
            previous.federationDomains().keySet().stream().filter(domain -> !current.federationDomains().containsKey(domain))
                    .forEach(retired::add);
            previous = current;
        }
    }

    private static void assertNewCableJoinsWithoutWithdrawingTheDomain(boolean existingSideFirst) {
        var registry = new FederationDomainRegistry(FederationDomainRecomputeBudget.standard());
        publishChain(registry, java.util.List.of(node(1), node(2), node(3)), Map.of(node(1), NETWORK_A, node(3), NETWORK_B));
        var domain = registry.federationDomainOf(node(2)).orElseThrow().reference();
        var branch = node(4);
        var cableWithBranch = new FederationDomainNodeEvidence(node(2), Map.of("west", federation(node(1), "east"),
                "east", federation(node(3), "west"), "north", federation(branch, "south")));
        var newCable = new FederationDomainNodeEvidence(branch, Map.of("south", federation(node(2), "north")));

        registry.upsertNode(existingSideFirst ? cableWithBranch : newCable);
        assertTrue(registry.isCurrent(domain));
        assertEquals(Set.of(domain.federationDomainId()), shared(registry, NETWORK_A, NETWORK_B));
        registry.upsertNode(existingSideFirst ? newCable : cableWithBranch);

        assertTrue(registry.isCurrent(domain));
        assertEquals(domain.federationDomainId(), registry.federationDomainOf(branch).orElseThrow().federationDomainId());
    }

    private static void assertMatchesMutualLinkPartition(FederationDomainRegistry registry, FederationDomainRegistrySnapshot snapshot,
            Map<FederationDomainNodeId, FederationDomainNodeEvidence> published, int step) {
        var component = new java.util.HashMap<FederationDomainNodeId, FederationDomainNodeId>();
        for (var start : published.keySet()) {
            if (component.containsKey(start)) {
                continue;
            }
            var queue = new java.util.ArrayDeque<FederationDomainNodeId>(java.util.List.of(start));
            component.put(start, start);
            while (!queue.isEmpty()) {
                var current = queue.removeFirst();
                published.get(current).ports().forEach((port, state) -> {
                    if (state instanceof FederationDomainPortEvidence.Federation link && published.containsKey(link.peer().node())
                            && published.get(link.peer().node()).port(link.peer().port())
                                    .equals(new FederationDomainPortEvidence.Federation(new FederationDomainPortId(current, port)))
                            && component.putIfAbsent(link.peer().node(), start) == null) {
                        queue.addLast(link.peer().node());
                    }
                });
            }
        }
        for (var node : published.keySet()) {
            var domain = registry.federationDomainOf(node).orElseThrow(() -> new AssertionError("unassigned node at step " + step));
            var expectedNodes = component.entrySet().stream().filter(entry -> entry.getValue().equals(component.get(node)))
                    .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
            assertEquals(expectedNodes, domain.nodes(), "step " + step);
            var expectedNetworks = expectedNodes.stream().flatMap(member -> published.get(member).ports().values().stream())
                    .filter(FederationDomainPortEvidence.Native.class::isInstance)
                    .map(state -> ((FederationDomainPortEvidence.Native) state).networkId())
                    .collect(java.util.stream.Collectors.toSet());
            assertEquals(expectedNetworks, domain.memberships().keySet(), "step " + step);
        }
        for (var domain : snapshot.federationDomains().values()) {
            assertFalse(domain.nodes().isEmpty(), "empty domain at step " + step);
            domain.nodes().forEach(node -> assertTrue(published.containsKey(node), "stale node at step " + step));
        }
        for (var network : java.util.List.of(NETWORK_A, NETWORK_B, NETWORK_C)) {
            var expected = snapshot.federationDomains().values().stream()
                    .filter(domain -> domain.memberships().containsKey(network))
                    .map(FederationDomainSnapshot::federationDomainId).collect(java.util.stream.Collectors.toSet());
            assertEquals(expected, registry.federationdomainsFor(network), "step " + step);
        }
    }

    /** Each node links east to the next and west to the previous; the given nodes also carry a native member. */
    private static void publishChain(FederationDomainRegistry registry, java.util.List<FederationDomainNodeId> nodes,
            Map<FederationDomainNodeId, NetworkId> members) {
        for (var index = 0; index < nodes.size(); index++) {
            var ports = new LinkedHashMap<String, FederationDomainPortEvidence>();
            if (index > 0) {
                ports.put("west", federation(nodes.get(index - 1), "east"));
            }
            if (index + 1 < nodes.size()) {
                ports.put("east", federation(nodes.get(index + 1), "west"));
            }
            var node = nodes.get(index);
            registry.upsertNode(members.containsKey(node) ? evidence(node, ports, members.get(node))
                    : new FederationDomainNodeEvidence(node, ports));
        }
    }

    private static Set<FederationDomainId> shared(FederationDomainRegistry registry, NetworkId first, NetworkId second) {
        var shared = new java.util.HashSet<>(registry.federationdomainsFor(first));
        shared.retainAll(registry.federationdomainsFor(second));
        return shared;
    }

    private static FederationDomainPortEvidence.Federation federation(FederationDomainNodeId peer, String port) {
        return new FederationDomainPortEvidence.Federation(new FederationDomainPortId(peer, port));
    }

    private static FederationDomainNodeEvidence evidence(FederationDomainNodeId node, Map<String, FederationDomainPortEvidence> federation,
            NetworkId networkId) {
        var ports = new LinkedHashMap<String, FederationDomainPortEvidence>();
        ports.putAll(federation);
        ports.put("up", nativePort(node, "up", networkId));
        return new FederationDomainNodeEvidence(node, ports);
    }

    private static FederationDomainPortEvidence.Native nativePort(FederationDomainNodeId node, String face, NetworkId networkId) {
        return new FederationDomainPortEvidence.Native(FederationDomainSourceId.nativePort(new FederationDomainPortId(node, face)), networkId);
    }

    private static FederationDomainNodeId node(long position) {
        return new FederationDomainNodeId("test:dimension", position);
    }

    private static NetworkId network(long value) {
        return new NetworkId(new UUID(0, value));
    }
}
