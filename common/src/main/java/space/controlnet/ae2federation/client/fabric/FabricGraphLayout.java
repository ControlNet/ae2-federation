package space.controlnet.ae2federation.client.fabric;

import java.util.List;

public record FabricGraphLayout(long topologyRevision, List<Node> nodes, List<Edge> edges) {
    public FabricGraphLayout {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    public record Node(String id, FabricGraphNodeKind kind, int x, int y) {
    }

    public record Edge(String from, String to, FabricGraphLayer layer) {
    }
}
