package space.controlnet.ae2federation.client.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.junit.jupiter.api.Test;

final class FabricGraphLayoutCacheTest {
    @Test
    void dataOnlyUpdatesReuseTheTopologyLayout() {
        var cache = new FabricGraphLayoutCache();
        var first = cache.layout(snapshot(7, 11, "online"));
        var dataUpdate = cache.layout(snapshot(7, 12, "busy"));

        assertSame(first, dataUpdate);
        assertEquals(List.of("member-a", "provider-a", "endpoint-a"),
                first.nodes().stream().map(FabricGraphLayout.Node::id).toList());
    }

    @Test
    void topologyRevisionProducesDeterministicGroupedCoordinates() {
        var cache = new FabricGraphLayoutCache();
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
        var cache = new FabricGraphLayoutCache();
        var first = cache.layout(snapshot(7, 11, "online"));
        var structuralUpdate = cache.layout(new FabricGraphSnapshot(7, 12,
                List.of(new FabricGraphSnapshot.Node("member-a", FabricGraphNodeKind.MEMBER, "online"),
                        new FabricGraphSnapshot.Node("provider-b", FabricGraphNodeKind.PROVIDER, "online")),
                List.of(new FabricGraphSnapshot.Edge("member-a", "provider-b", FabricGraphLayer.PHYSICAL, "online")),
                List.of()));

        assertNotSame(first, structuralUpdate);
        assertEquals(List.of("member-a", "provider-b"),
                structuralUpdate.nodes().stream().map(FabricGraphLayout.Node::id).toList());
    }

    private static FabricGraphSnapshot snapshot(long topologyRevision, long dataRevision, String providerStatus) {
        return new FabricGraphSnapshot(topologyRevision, dataRevision,
                List.of(new FabricGraphSnapshot.Node("member-a", FabricGraphNodeKind.MEMBER, "online"),
                        new FabricGraphSnapshot.Node("provider-a", FabricGraphNodeKind.PROVIDER, providerStatus),
                        new FabricGraphSnapshot.Node("endpoint-a", FabricGraphNodeKind.ENDPOINT, "federated")),
                List.of(new FabricGraphSnapshot.Edge("member-a", "provider-a", FabricGraphLayer.PHYSICAL, "online"),
                        new FabricGraphSnapshot.Edge("provider-a", "endpoint-a", FabricGraphLayer.CAPABILITY,
                                providerStatus)),
                List.of("Pattern 1 -> Lane 1"));
    }
}
