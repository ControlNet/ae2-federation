package space.controlnet.ae2federation.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.identity.NetworkId;

final class FabricRegistryTest {
    private static final NetworkId NETWORK_A = network(1);
    private static final NetworkId NETWORK_B = network(2);
    private static final NetworkId NETWORK_C = network(3);

    @Test
    void directBridgesRemainSeparateWhenTheyShareNativeNetworks() {
        var registry = new FabricRegistry(FabricRecomputeBudget.standard());
        var sources = java.util.stream.IntStream.range(0, 4)
                .mapToObj(index -> new FabricSourceId("bridge-" + index))
                .toList();

        sources.forEach(source -> registry.upsertDirectBridge(source, NETWORK_A, NETWORK_B));

        assertEquals(4, registry.snapshot().fabrics().size());
        assertEquals(4, registry.fabricsFor(NETWORK_A).size());
        assertEquals(4, registry.fabricsFor(NETWORK_B).size());
    }

    @Test
    void reciprocalTopologyMergesAndSplitsOnlyAffectedComponents() {
        var registry = new FabricRegistry(FabricRecomputeBudget.standard());
        var left = node(1);
        var right = node(2);
        registry.upsertNode(evidence(left, Map.of("east",
                new FabricPortEvidence.Federation(new FabricPortId(right, "west"))), NETWORK_A));
        registry.upsertNode(evidence(right, Map.of("west",
                new FabricPortEvidence.Federation(new FabricPortId(left, "east"))), NETWORK_B));
        var merged = registry.snapshot().fabrics().values().iterator().next();

        registry.upsertNode(evidence(left, Map.of(), NETWORK_A));
        assertTrue(registry.fabricsFor(NETWORK_A).isEmpty());
        registry.upsertNode(evidence(right, Map.of(), NETWORK_B));

        assertEquals(2, registry.snapshot().fabrics().size());
        assertFalse(registry.isCurrent(merged.reference()));
        assertEquals(1, registry.fabricsFor(NETWORK_A).size());
        assertEquals(1, registry.fabricsFor(NETWORK_B).size());
    }

    @Test
    void redundantAttachmentsDeduplicateNetworkMembershipAndRetainSources() {
        var registry = new FabricRegistry(FabricRecomputeBudget.standard());
        var node = node(1);
        var ports = new LinkedHashMap<String, FabricPortEvidence>();
        ports.put("north", nativePort(node, "north", NETWORK_A));
        ports.put("south", nativePort(node, "south", NETWORK_A));

        registry.upsertNode(new FabricNodeEvidence(node, ports));

        var fabric = registry.snapshot().fabrics().values().iterator().next();
        assertEquals(1, fabric.memberships().size());
        assertEquals(2, fabric.memberships().get(NETWORK_A).size());
        assertEquals(1, registry.fabricsFor(NETWORK_A).size());
    }

    @Test
    void partialUnloadInvalidatesMembershipWithoutInventingLoadedEvidence() {
        var registry = new FabricRegistry(FabricRecomputeBudget.standard());
        var left = node(1);
        var cable = node(2);
        var right = node(3);
        registry.upsertNode(evidence(left, Map.of("east",
                new FabricPortEvidence.Federation(new FabricPortId(cable, "west"))), NETWORK_A));
        registry.upsertNode(new FabricNodeEvidence(cable, Map.of(
                "west", new FabricPortEvidence.Federation(new FabricPortId(left, "east")),
                "east", new FabricPortEvidence.Federation(new FabricPortId(right, "west")))));
        registry.upsertNode(evidence(right, Map.of("west",
                new FabricPortEvidence.Federation(new FabricPortId(cable, "east"))), NETWORK_B));

        registry.removeNode(cable);

        assertTrue(registry.fabricsFor(NETWORK_A).isEmpty());
        assertTrue(registry.fabricsFor(NETWORK_B).isEmpty());
        assertEquals(FabricInvalidationReason.SOURCE_UNLOADED,
                registry.snapshot().invalidations().get(cable));
    }

    @Test
    void budgetExhaustionInvalidatesStaleReferencesImmediately() {
        var registry = new FabricRegistry(new FabricRecomputeBudget(1, 6));
        var left = node(1);
        var right = node(2);
        registry.upsertNode(evidence(left, Map.of(), NETWORK_A));
        var stale = registry.snapshot().fabrics().values().iterator().next().reference();

        registry.upsertNode(evidence(left, Map.of("east",
                new FabricPortEvidence.Federation(new FabricPortId(right, "west"))), NETWORK_A));
        registry.upsertNode(evidence(right, Map.of("west",
                new FabricPortEvidence.Federation(new FabricPortId(left, "east"))), NETWORK_C));

        assertFalse(registry.isCurrent(stale));
        assertTrue(registry.fabricsFor(NETWORK_A).isEmpty());
        assertTrue(registry.snapshot().invalidations().containsValue(FabricInvalidationReason.BUDGET_EXHAUSTED));
    }

    @Test
    void unsettledIdentityNeverCreatesConfirmedMembership() {
        var registry = new FabricRegistry(FabricRecomputeBudget.standard());
        var node = node(1);

        registry.upsertNode(new FabricNodeEvidence(node, Map.of("north",
                new FabricPortEvidence.Unsettled(new FabricSourceId("native-unsettled"), "AMBIGUOUS_SPLIT"))));

        assertTrue(registry.snapshot().fabrics().isEmpty());
        assertEquals(FabricInvalidationReason.IDENTITY_UNSETTLED,
                registry.snapshot().invalidations().get(node));
    }

    private static FabricNodeEvidence evidence(FabricNodeId node, Map<String, FabricPortEvidence> federation,
            NetworkId networkId) {
        var ports = new LinkedHashMap<String, FabricPortEvidence>();
        ports.putAll(federation);
        ports.put("up", nativePort(node, "up", networkId));
        return new FabricNodeEvidence(node, ports);
    }

    private static FabricPortEvidence.Native nativePort(FabricNodeId node, String face, NetworkId networkId) {
        return new FabricPortEvidence.Native(FabricSourceId.nativePort(new FabricPortId(node, face)), networkId);
    }

    private static FabricNodeId node(long position) {
        return new FabricNodeId("test:dimension", position);
    }

    private static NetworkId network(long value) {
        return new NetworkId(new UUID(0, value));
    }
}
