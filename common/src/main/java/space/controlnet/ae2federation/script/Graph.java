package space.controlnet.ae2federation.script;

import java.util.List;

/** A function compiled to its nodes; execution starts at node 0. */
public record Graph(String file, FunctionDef function, List<Node> nodes) {
    public Graph {
        nodes = List.copyOf(nodes);
    }

    /** The nodes control can pass to from node {@code i}. */
    public List<Integer> successors(int i) {
        return switch (nodes.get(i)) {
            case Node.Branch b -> List.of(i + 1, b.otherwise());
            case Node.Jump j -> List.of(j.target());
            case Node.Next n -> List.of(i + 1, n.exit());
            case Node.Return r -> List.of();
            default -> List.of(i + 1);
        };
    }
}
