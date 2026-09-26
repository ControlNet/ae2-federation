package space.controlnet.ae2federation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
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
        assertTrue(registry.federationdomainsFor(NETWORK_A).isEmpty());
        registry.upsertNode(evidence(right, Map.of(), NETWORK_B));

        assertEquals(2, registry.snapshot().federationDomains().size());
        assertFalse(registry.isCurrent(merged.reference()));
        assertEquals(1, registry.federationdomainsFor(NETWORK_A).size());
        assertEquals(1, registry.federationdomainsFor(NETWORK_B).size());
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

        registry.removeNode(cable);

        assertTrue(registry.federationdomainsFor(NETWORK_A).isEmpty());
        assertTrue(registry.federationdomainsFor(NETWORK_B).isEmpty());
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

        assertTrue(registry.snapshot().federationDomains().isEmpty());
        assertEquals(FederationDomainInvalidationReason.IDENTITY_UNSETTLED,
                registry.snapshot().invalidations().get(node));
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
