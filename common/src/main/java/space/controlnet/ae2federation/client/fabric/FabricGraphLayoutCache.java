package space.controlnet.ae2federation.client.fabric;

import java.util.ArrayList;
import org.jetbrains.annotations.Nullable;

public final class FabricGraphLayoutCache {
    private long topologyRevision = -1;
    private String structuralSignature = "";
    private @Nullable FabricGraphLayout cached;

    public synchronized FabricGraphLayout layout(FabricGraphSnapshot snapshot) {
        var currentSignature = snapshot.structuralSignature();
        if (cached != null && topologyRevision == snapshot.topologyRevision()
                && structuralSignature.equals(currentSignature)) {
            return cached;
        }
        var rows = new int[FabricGraphNodeKind.values().length];
        var nodes = new ArrayList<FabricGraphLayout.Node>(snapshot.nodes().size());
        for (var node : snapshot.nodes()) {
            var x = switch (node.kind()) {
                case MEMBER -> 20;
                case PROVIDER -> 150;
                case ENDPOINT -> 280;
            };
            var row = rows[node.kind().ordinal()]++;
            nodes.add(new FabricGraphLayout.Node(node.id(), node.kind(), x, 20 + row * 38));
        }
        var edges = snapshot.edges().stream()
                .map(edge -> new FabricGraphLayout.Edge(edge.from(), edge.to(), edge.layer())).toList();
        topologyRevision = snapshot.topologyRevision();
        structuralSignature = currentSignature;
        cached = new FabricGraphLayout(topologyRevision, nodes, edges);
        return cached;
    }
}
