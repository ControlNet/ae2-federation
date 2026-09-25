package space.controlnet.ae2federation.client.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.junit.jupiter.api.Test;

final class FederationDomainGraphLayoutCacheTest {
    @Test
    void dataOnlyUpdatesReuseTheTopologyLayout() {
        var cache = new FederationDomainGraphLayoutCache();
        var first = cache.layout(snapshot(7, 11, "online"));
        var dataUpdate = cache.layout(snapshot(7, 12, "busy"));

        assertSame(first, dataUpdate);
        assertEquals(List.of("member-a", "provider-a", "endpoint-a"),
                first.nodes().stream().map(FederationDomainGraphLayout.Node::id).toList());
    }

    @Test
    void topologyRevisionProducesDeterministicGroupedCoordinates() {
        var cache = new FederationDomainGraphLayoutCache();
        var first = cache.layout(snapshot(7, 11, "online"));
        var changed = cache.layout(snapshot(8, 12, "online"));

        assertNotSame(first, changed);
        assertEquals(first.nodes(), changed.nodes());
        assertEquals(20, changed.nodes().get(0).x());
        assertEquals(150, changed.nodes().get(1).x());
        assertEquals(280, changed.nodes().get(2).x());
    }

    @Test
    void projectedStructureInvalidatesLayoutWithoutTopologyRevisionChange() {
        var cache = new FederationDomainGraphLayoutCache();
        var first = cache.layout(snapshot(7, 11, "online"));
        var structuralUpdate = cache.layout(new FederationDomainGraphSnapshot(7, 12,
                List.of(new FederationDomainGraphSnapshot.Node("member-a", FederationDomainGraphNodeKind.MEMBER, "online"),
                        new FederationDomainGraphSnapshot.Node("provider-b", FederationDomainGraphNodeKind.PROVIDER, "online")),
                List.of(new FederationDomainGraphSnapshot.Edge("member-a", "provider-b", FederationDomainGraphLayer.PHYSICAL, "online")),
                List.of()));

        assertNotSame(first, structuralUpdate);
        assertEquals(List.of("member-a", "provider-b"),
                structuralUpdate.nodes().stream().map(FederationDomainGraphLayout.Node::id).toList());
    }

    private static FederationDomainGraphSnapshot snapshot(long topologyRevision, long dataRevision, String providerStatus) {
        return new FederationDomainGraphSnapshot(topologyRevision, dataRevision,
                List.of(new FederationDomainGraphSnapshot.Node("member-a", FederationDomainGraphNodeKind.MEMBER, "online"),
                        new FederationDomainGraphSnapshot.Node("provider-a", FederationDomainGraphNodeKind.PROVIDER, providerStatus),
                        new FederationDomainGraphSnapshot.Node("endpoint-a", FederationDomainGraphNodeKind.ENDPOINT, "federated")),
                List.of(new FederationDomainGraphSnapshot.Edge("member-a", "provider-a", FederationDomainGraphLayer.PHYSICAL, "online"),
                        new FederationDomainGraphSnapshot.Edge("provider-a", "endpoint-a", FederationDomainGraphLayer.CAPABILITY,
                                providerStatus)),
                List.of("Pattern 1 -> Lane 1"));
    }
}
