package space.controlnet.ae2federation.client.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class FederationDomainGraphSnapshotTest {
    @Test
    void wireFormRoundTripsUnicodeAndLargeQuantities() {
        var snapshot = new FederationDomainGraphSnapshot(23, 91,
                List.of(new FederationDomainGraphSnapshot.Node("provider:main", FederationDomainGraphNodeKind.PROVIDER,
                        "主基地超长样板供应器 9,223,372,036,854,775,807")),
                List.of(), List.of("超长高炉加工映射 -> 端点 A、B、C"));

        assertEquals(snapshot, FederationDomainGraphSnapshot.decode(snapshot.encode()));
    }

    @Test
    void duplicateNodeIdsAreRejected() {
        var node = new FederationDomainGraphSnapshot.Node("same", FederationDomainGraphNodeKind.MEMBER, "online");
        assertThrows(IllegalArgumentException.class,
                () -> new FederationDomainGraphSnapshot(1, 1, List.of(node, node), List.of(), List.of()));
    }
    @Test
    void aGraphPastItsBoundKeepsItsFirstNodesAndTheEdgesBetweenThem() {
        var nodes = new ArrayList<FederationDomainGraphSnapshot.Node>();
        var edges = new ArrayList<FederationDomainGraphSnapshot.Edge>();
        for (int i = 0; i < 300; i++) {
            nodes.add(new FederationDomainGraphSnapshot.Node("member:" + i, FederationDomainGraphNodeKind.MEMBER, "online"));
        }
        for (int i = 0; i < 300; i++) {
            nodes.add(new FederationDomainGraphSnapshot.Node("endpoint:" + i, FederationDomainGraphNodeKind.ENDPOINT, "on"));
            edges.add(new FederationDomainGraphSnapshot.Edge("member:" + i, "endpoint:" + i,
                    FederationDomainGraphLayer.PHYSICAL, "on"));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new FederationDomainGraphSnapshot(1, 1, nodes, edges, List.of()));

        var graph = FederationDomainGraphSnapshot.bounded(1, 1, nodes, edges, List.of());

        assertEquals(512, graph.nodes().size());
        assertEquals(300, graph.nodes().stream().filter(node -> node.kind() == FederationDomainGraphNodeKind.MEMBER).count());
        assertEquals(212, graph.edges().size());
        var ids = graph.nodes().stream().map(FederationDomainGraphSnapshot.Node::id).toList();
        assertTrue(graph.edges().stream().allMatch(edge -> ids.contains(edge.from()) && ids.contains(edge.to())));
        assertEquals(graph, FederationDomainGraphSnapshot.decode(graph.encode()));
    }
}
