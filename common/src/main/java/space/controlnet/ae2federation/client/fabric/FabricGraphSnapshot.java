package space.controlnet.ae2federation.client.fabric;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record FabricGraphSnapshot(long topologyRevision, long dataRevision, List<Node> nodes, List<Edge> edges,
        List<String> patterns) {
    private static final int WIRE_VERSION = 1;
    private static final int MAX_ENTRIES = 512;

    public FabricGraphSnapshot {
        if (topologyRevision < 0 || dataRevision < 0) {
            throw new IllegalArgumentException("Graph revisions cannot be negative");
        }
        nodes = canonicalNodes(nodes);
        edges = canonicalEdges(edges, nodes);
        patterns = canonicalPatterns(patterns);
    }

    public static FabricGraphSnapshot empty() {
        return new FabricGraphSnapshot(0, 0, List.of(), List.of(), List.of());
    }

    public String encode() {
        var result = new StringBuilder().append(WIRE_VERSION).append('\t').append(topologyRevision).append('\t')
                .append(dataRevision).append('\n');
        nodes.forEach(node -> result.append("N\t").append(node.kind()).append('\t').append(encoded(node.id()))
                .append('\t').append(encoded(node.status())).append('\n'));
        edges.forEach(edge -> result.append("E\t").append(edge.layer()).append('\t').append(encoded(edge.from()))
                .append('\t').append(encoded(edge.to())).append('\t').append(encoded(edge.status())).append('\n'));
        patterns.forEach(pattern -> result.append("P\t").append(encoded(pattern)).append('\n'));
        return result.toString();
    }

    public String structuralSignature() {
        var signature = new StringBuilder();
        nodes.forEach(node -> signature.append("N\t").append(node.kind()).append('\t').append(node.id()).append('\n'));
        edges.forEach(edge -> signature.append("E\t").append(edge.layer()).append('\t').append(edge.from())
                .append('\t').append(edge.to()).append('\n'));
        return signature.toString();
    }

    public static FabricGraphSnapshot decode(String encoded) {
        Objects.requireNonNull(encoded);
        var lines = encoded.lines().toList();
        if (lines.isEmpty()) {
            return empty();
        }
        var header = lines.getFirst().split("\\t", -1);
        if (header.length != 3 || Integer.parseInt(header[0]) != WIRE_VERSION) {
            throw new IllegalArgumentException("Unsupported Fabric graph wire version");
        }
        var nodes = new ArrayList<Node>();
        var edges = new ArrayList<Edge>();
        var patterns = new ArrayList<String>();
        for (var index = 1; index < lines.size(); index++) {
            var fields = lines.get(index).split("\\t", -1);
            switch (fields[0]) {
                case "N" -> {
                    requireFields(fields, 4);
                    nodes.add(new Node(decoded(fields[2]), FabricGraphNodeKind.valueOf(fields[1]), decoded(fields[3])));
                }
                case "E" -> {
                    requireFields(fields, 5);
                    edges.add(new Edge(decoded(fields[2]), decoded(fields[3]), FabricGraphLayer.valueOf(fields[1]),
                            decoded(fields[4])));
                }
                case "P" -> {
                    requireFields(fields, 2);
                    patterns.add(decoded(fields[1]));
                }
                default -> throw new IllegalArgumentException("Unknown Fabric graph wire entry");
            }
        }
        return new FabricGraphSnapshot(Long.parseLong(header[1]), Long.parseLong(header[2]), nodes, edges, patterns);
    }

    private static List<Node> canonicalNodes(List<Node> values) {
        Objects.requireNonNull(values);
        if (values.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Too many graph nodes");
        }
        var ids = new HashSet<String>();
        values.forEach(node -> {
            Objects.requireNonNull(node);
            if (!ids.add(node.id())) {
                throw new IllegalArgumentException("Duplicate graph node ID");
            }
        });
        return values.stream().sorted(Comparator.comparing(Node::kind).thenComparing(Node::id)).toList();
    }

    private static List<Edge> canonicalEdges(List<Edge> values, List<Node> nodes) {
        Objects.requireNonNull(values);
        if (values.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Too many graph edges");
        }
        var ids = nodes.stream().map(Node::id).collect(java.util.stream.Collectors.toSet());
        values.forEach(edge -> {
            Objects.requireNonNull(edge);
            if (!ids.contains(edge.from()) || !ids.contains(edge.to())) {
                throw new IllegalArgumentException("Graph edge references an unknown node");
            }
        });
        return values.stream().distinct().sorted(Comparator.comparing(Edge::layer).thenComparing(Edge::from)
                .thenComparing(Edge::to)).toList();
    }

    private static List<String> canonicalPatterns(List<String> values) {
        Objects.requireNonNull(values);
        if (values.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Too many graph Pattern rows");
        }
        values.forEach(FabricGraphSnapshot::requireText);
        return List.copyOf(values);
    }

    private static String encoded(String value) {
        requireText(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decoded(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static void requireFields(String[] fields, int expected) {
        if (fields.length != expected) {
            throw new IllegalArgumentException("Malformed Fabric graph wire entry");
        }
    }

    private static void requireText(String value) {
        Objects.requireNonNull(value);
        if (value.length() > 512) {
            throw new IllegalArgumentException("Fabric graph text is too long");
        }
    }

    public record Node(String id, FabricGraphNodeKind kind, String status) {
        public Node {
            requireText(id);
            Objects.requireNonNull(kind);
            requireText(status);
        }
    }

    public record Edge(String from, String to, FabricGraphLayer layer, String status) {
        public Edge {
            requireText(from);
            requireText(to);
            Objects.requireNonNull(layer);
            requireText(status);
        }
    }
}
