package space.controlnet.ae2federation.client.policy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Where the topology draws each Processing Endpoint: a small node on the outer side of the network whose Provider maps
 * it, linked to that card, so nodes stay clear of the links between cards. A network left of the others keeps its
 * nodes on its left, one above them above it, and so on. Endpoints no shown network maps wait, unlinked, in a free row
 * below everything.
 */
public final class EndpointNodeLayout {
    /** Space between a card and its first node, and between nodes. */
    static final float GAP = 18;
    static final float SPACING = 6;
    /** Nodes per column beside a card or per row above or below it before a further one starts. */
    private static final int PER_LINE = 3;

    private EndpointNodeLayout() {
    }

    /** A network card by its top-left corner. */
    public record Card(String id, float x, float y) {
    }

    /** An Endpoint, {@code owner} the id of the network that maps it or empty. */
    public record Node(String id, String owner, float width) {
    }

    /** A placed node's top-left corner, and the link from its owner card, null when it has none. */
    public record Placed(String id, float x, float y, float width, WireCurve link) {
        /** The same node and link moved by {@code (dx, dy)}. */
        public Placed moved(float dx, float dy) {
            return new Placed(id, x + dx, y + dy, width, link == null ? null
                    : new WireCurve(link.fromX() + dx, link.fromY() + dy, link.toX() + dx, link.toY() + dy));
        }
    }

    private enum Side { LEFT, RIGHT, ABOVE, BELOW }

    /** Places {@code nodes} in order, around cards of {@code width} by {@code height}. */
    public static List<Placed> place(List<Card> cards, float width, float height, List<Node> nodes, float nodeHeight) {
        var byId = new HashMap<String, Card>();
        cards.forEach(card -> byId.put(card.id(), card));
        float centerX = (float) cards.stream().mapToDouble(card -> card.x() + width / 2).average().orElse(0);
        float centerY = (float) cards.stream().mapToDouble(card -> card.y() + height / 2).average().orElse(0);
        var owned = new LinkedHashMap<String, List<Node>>();
        for (var node : nodes) if (byId.containsKey(node.owner())) owned.computeIfAbsent(node.owner(), key -> new ArrayList<>()).add(node);
        var placed = new HashMap<String, Placed>();
        float bottom = cards.stream().map(card -> card.y() + height).max(Float::compare).orElse(0f);
        for (var entry : owned.entrySet()) {
            var card = byId.get(entry.getKey());
            var side = side(card, width, height, centerX, centerY, cards.size());
            var group = entry.getValue();
            for (int line = 0; line * PER_LINE < group.size(); line++) {
                var members = group.subList(line * PER_LINE, Math.min(group.size(), (line + 1) * PER_LINE));
                float lineWidth = (float) members.stream().mapToDouble(Node::width).max().orElse(0);
                // Columns beside a card step outward by the widest node of the columns before.
                float outward = GAP + line * (lineWidth + SPACING);
                if (side == Side.LEFT || side == Side.RIGHT) {
                    float columnHeight = members.size() * nodeHeight + (members.size() - 1) * SPACING;
                    float top = card.y() + height / 2 - columnHeight / 2;
                    for (int index = 0; index < members.size(); index++) {
                        var node = members.get(index);
                        float y = top + index * (nodeHeight + SPACING);
                        float x = side == Side.LEFT ? card.x() - outward - node.width() : card.x() + width + outward;
                        var link = side == Side.LEFT
                                ? new WireCurve(card.x(), card.y() + height / 2, x + node.width(), y + nodeHeight / 2)
                                : new WireCurve(card.x() + width, card.y() + height / 2, x, y + nodeHeight / 2);
                        placed.put(node.id(), new Placed(node.id(), x, y, node.width(), link));
                        bottom = Math.max(bottom, y + nodeHeight);
                    }
                } else {
                    float rowWidth = (float) members.stream().mapToDouble(Node::width).sum() + (members.size() - 1) * SPACING;
                    float left = card.x() + width / 2 - rowWidth / 2;
                    float y = side == Side.ABOVE ? card.y() - GAP - line * (nodeHeight + SPACING) - nodeHeight
                            : card.y() + height + GAP + line * (nodeHeight + SPACING);
                    for (var node : members) {
                        var link = side == Side.ABOVE
                                ? new WireCurve(card.x() + width / 2, card.y(), left + node.width() / 2, y + nodeHeight)
                                : new WireCurve(card.x() + width / 2, card.y() + height, left + node.width() / 2, y);
                        placed.put(node.id(), new Placed(node.id(), left, y, node.width(), link));
                        left += node.width() + SPACING;
                    }
                    bottom = Math.max(bottom, y + nodeHeight);
                }
            }
        }
        float left = cards.stream().map(Card::x).min(Float::compare).orElse(0f);
        float right = Math.max(left + width, cards.stream().map(card -> card.x() + width).max(Float::compare).orElse(0f));
        float x = left;
        float y = bottom + GAP;
        for (var node : nodes) {
            if (placed.containsKey(node.id())) continue;
            if (x > left && x + node.width() > right) {
                x = left;
                y += nodeHeight + SPACING;
            }
            placed.put(node.id(), new Placed(node.id(), x, y, node.width(), null));
            x += node.width() + SPACING;
        }
        return nodes.stream().map(node -> placed.get(node.id())).toList();
    }

    /** The side of the card that faces away from the other cards; below when it is alone. */
    private static Side side(Card card, float width, float height, float centerX, float centerY, int cards) {
        if (cards <= 1) return Side.BELOW;
        float dx = card.x() + width / 2 - centerX;
        float dy = card.y() + height / 2 - centerY;
        if (Math.abs(dx) >= Math.abs(dy)) return dx < 0 ? Side.LEFT : Side.RIGHT;
        return dy < 0 ? Side.ABOVE : Side.BELOW;
    }
}
