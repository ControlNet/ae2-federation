package space.controlnet.ae2federation.function;

import java.util.HashMap;
import java.util.Map;

/**
 * Where the graph's nodes stand, by node ID, kept apart from the program as Videnoa keeps its node positions apart from
 * the workflow. A node the player placed keeps its place for as long as its ID lives ({@link Reconciler}); a new node
 * is placed beside the node it is wired to.
 */
public final class Layout {
    public record Pos(int x, int y) {
    }

    private static final int COLUMN = 220;
    private static final int ROW = 90;

    private Layout() {
    }

    /** Positions for every node of {@code graph}: the earlier ones where they were, new ones beside a neighbour. */
    public static Map<String, Pos> update(Map<String, Pos> earlier, GraphView.Graph graph) {
        var placed = new HashMap<String, Pos>();
        for (var node : graph.nodes()) {
            var pos = earlier.get(node.id());
            if (pos != null) placed.put(node.id(), pos);
        }
        boolean progress = true;
        while (progress && placed.size() < graph.nodes().size()) {
            progress = false;
            for (var wire : graph.wires()) {
                // A value goes to the left of what reads it; a statement to the right of what runs before it.
                boolean value = wire.fromPort().equals("value");
                var known = placed.get(value ? wire.toNode() : wire.fromNode());
                var unknown = value ? wire.fromNode() : wire.toNode();
                if (known == null || placed.containsKey(unknown)) continue;
                placed.put(unknown, free(placed, new Pos(known.x() + (value ? -COLUMN : COLUMN), known.y())));
                progress = true;
            }
        }
        int row = 0;
        for (var node : graph.nodes()) {
            if (!placed.containsKey(node.id())) placed.put(node.id(), free(placed, new Pos(0, ROW * row++)));
        }
        return placed;
    }

    /** {@code wanted}, or the first free place below it. */
    private static Pos free(Map<String, Pos> placed, Pos wanted) {
        var pos = wanted;
        while (placed.containsValue(pos)) pos = new Pos(pos.x(), pos.y() + ROW);
        return pos;
    }
}
