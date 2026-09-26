package space.controlnet.ae2federation.client.domain;

import java.util.List;

public record FederationDomainGraphLayout(long topologyRevision, List<Node> nodes, List<Edge> edges) {
    public FederationDomainGraphLayout {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    public record Node(String id, FederationDomainGraphNodeKind kind, int x, int y) {
    }

    public record Edge(String from, String to, FederationDomainGraphLayer layer) {
    }
}
