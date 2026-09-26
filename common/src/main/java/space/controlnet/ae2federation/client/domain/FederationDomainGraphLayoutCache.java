package space.controlnet.ae2federation.client.domain;

import java.util.ArrayList;
import org.jetbrains.annotations.Nullable;

public final class FederationDomainGraphLayoutCache {
    private long topologyRevision = -1;
    private String structuralSignature = "";
    private @Nullable FederationDomainGraphLayout cached;

    public synchronized FederationDomainGraphLayout layout(FederationDomainGraphSnapshot snapshot) {
        var currentSignature = snapshot.structuralSignature();
        if (cached != null && topologyRevision == snapshot.topologyRevision()
                && structuralSignature.equals(currentSignature)) {
            return cached;
        }
        var rows = new int[FederationDomainGraphNodeKind.values().length];
        var nodes = new ArrayList<FederationDomainGraphLayout.Node>(snapshot.nodes().size());
        for (var node : snapshot.nodes()) {
            var x = switch (node.kind()) {
                case MEMBER -> 20;
                case PROVIDER -> 150;
                case ENDPOINT -> 280;
            };
            var row = rows[node.kind().ordinal()]++;
            nodes.add(new FederationDomainGraphLayout.Node(node.id(), node.kind(), x, 20 + row * 38));
        }
        var edges = snapshot.edges().stream()
                .map(edge -> new FederationDomainGraphLayout.Edge(edge.from(), edge.to(), edge.layer())).toList();
        topologyRevision = snapshot.topologyRevision();
        structuralSignature = currentSignature;
        cached = new FederationDomainGraphLayout(topologyRevision, nodes, edges);
        return cached;
    }
}
