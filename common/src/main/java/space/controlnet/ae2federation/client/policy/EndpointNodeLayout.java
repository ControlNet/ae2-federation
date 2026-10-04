package space.controlnet.ae2federation.client.policy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Where the topology draws each Processing Endpoint: a small node on the outer side of the network whose Provider maps
 * it, linked to that card, so nodes stay clear of the links between cards. A network left of the others keeps its
 * nodes on its left, one above them above it, and so on. Endpoints no shown network maps wait, unlinked, in a free row
 * below everything.
 *
 * <p>Each link leaves from its own point along the card's side, in the order of the nodes it reaches, so the links of
 * one card never cross. Beside a card the nodes stand in one column of up to {@value #PER_COLUMN}; more lengthen it
 * until a second column, set half a step lower, can put each of its nodes in a gap of the first, whose links bend
 * between the card and the first column and run straight on through that gap. Above or below a card the same holds
 * for rows of up to {@value #PER_ROW}, with vertical links.
 */
public final class EndpointNodeLayout {
    /** Space between a card and its first nodes. */
    static final float GAP = 40;
    /** Space between nodes, wide enough for a link to pass between two of them. */
    static final float SPACING = 8;
    /** Space between a first and a second column. */
    static final float COLUMN_GAP = 12;
    /** How close to the card's corners a link may leave. */
    static final float ANCHOR_MARGIN = 14;
    static final int PER_COLUMN = 5;
    static final int PER_ROW = 3;

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
            return new Placed(id, x + dx, y + dy, width, link == null ? null : link.translated(dx, dy));
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
            var group = side == Side.LEFT || side == Side.RIGHT ? beside(card, width, height, entry.getValue(), nodeHeight, side)
                    : aboveOrBelow(card, width, height, entry.getValue(), nodeHeight, side);
            for (var node : group) {
                placed.put(node.id(), node);
                bottom = Math.max(bottom, node.y() + nodeHeight);
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

    /** How many of {@code count} nodes the first line takes, so the rest fit in its gaps. */
    private static int firstLine(int count, int perLine) {
        return Math.max(Math.min(count, perLine), (count + 2) / 2);
    }

    /** Each link's point along a side of {@code length} centred on {@code centre}, at most {@code pitch} apart. */
    private static float[] anchors(int count, float centre, float length, float pitch) {
        var anchors = new float[count];
        float step = count <= 1 ? 0 : Math.min(pitch, (length - 2 * ANCHOR_MARGIN) / (count - 1));
        for (int index = 0; index < count; index++) anchors[index] = centre + (index - (count - 1) / 2f) * step;
        return anchors;
    }

    private record Slot(Node node, float y, boolean second) {
    }

    private static List<Placed> beside(Card card, float width, float height, List<Node> group, float nodeHeight, Side side) {
        float pitch = nodeHeight + SPACING;
        int first = firstLine(group.size(), PER_COLUMN);
        float top = card.y() + height / 2 - (first * nodeHeight + (first - 1) * SPACING) / 2;
        var slots = new ArrayList<Slot>();
        for (int index = 0; index < first; index++) slots.add(new Slot(group.get(index), top + index * pitch, false));
        // The second column fills the first's middle gaps, half a step lower than the node above each gap.
        int second = group.size() - first;
        int start = (first - 1 - second) / 2;
        for (int index = 0; index < second; index++) {
            slots.add(new Slot(group.get(first + index), top + (start + index) * pitch + pitch / 2, true));
        }
        slots.sort(Comparator.comparingDouble(Slot::y));
        float firstWidth = (float) group.subList(0, first).stream().mapToDouble(Node::width).max().orElse(0);
        var anchors = anchors(slots.size(), card.y() + height / 2, height, pitch);
        boolean left = side == Side.LEFT;
        float edge = left ? card.x() - GAP : card.x() + width + GAP;
        float fromX = left ? card.x() : card.x() + width;
        var placed = new ArrayList<Placed>();
        for (int index = 0; index < slots.size(); index++) {
            var slot = slots.get(index);
            var node = slot.node();
            float outward = slot.second() ? firstWidth + COLUMN_GAP : 0;
            float x = left ? edge - outward - node.width() : edge + outward;
            float toX = left ? x + node.width() : x;
            var link = new WireCurve(fromX, anchors[index], toX, slot.y() + nodeHeight / 2, false, edge);
            placed.add(new Placed(node.id(), x, slot.y(), node.width(), link));
        }
        return placed;
    }

    private static List<Placed> aboveOrBelow(Card card, float width, float height, List<Node> group, float nodeHeight,
            Side side) {
        int first = firstLine(group.size(), PER_ROW);
        var row = group.subList(0, first);
        float rowWidth = (float) row.stream().mapToDouble(Node::width).sum() + (first - 1) * SPACING;
        boolean above = side == Side.ABOVE;
        float edge = above ? card.y() - GAP : card.y() + height + GAP;
        float y = above ? edge - nodeHeight : edge;
        float fromY = above ? card.y() : card.y() + height;
        // Centres along the row: each first-row node, and in each middle gap the second row's node, one step further out.
        var centres = new ArrayList<float[]>();
        float x = card.x() + width / 2 - rowWidth / 2;
        var gaps = new ArrayList<Float>();
        for (int index = 0; index < first; index++) {
            var node = row.get(index);
            centres.add(new float[] {x + node.width() / 2, 0, index});
            x += node.width() + SPACING;
            gaps.add(x - SPACING / 2);
        }
        int second = group.size() - first;
        int start = (first - 1 - second) / 2;
        for (int index = 0; index < second; index++) centres.add(new float[] {gaps.get(start + index), 1, first + index});
        centres.sort(Comparator.comparingDouble(centre -> centre[0]));
        float averageWidth = rowWidth / first;
        var anchors = anchors(centres.size(), card.x() + width / 2, width, averageWidth + SPACING);
        var placed = new ArrayList<Placed>();
        for (int index = 0; index < centres.size(); index++) {
            var centre = centres.get(index);
            var node = group.get((int) centre[2]);
            float nodeY = centre[1] == 0 ? y : above ? y - nodeHeight - SPACING : y + nodeHeight + SPACING;
            float toY = above ? nodeY + nodeHeight : nodeY;
            var link = new WireCurve(anchors[index], fromY, centre[0], toY, true, edge);
            placed.add(new Placed(node.id(), centre[0] - node.width() / 2, nodeY, node.width(), link));
        }
        return placed;
    }

    /** Whether no placed node covers a card or another node. */
    public static boolean clear(List<Card> cards, float width, float height, List<Placed> placed, float nodeHeight) {
        for (int index = 0; index < placed.size(); index++) {
            var node = placed.get(index);
            for (var card : cards) {
                if (overlaps(node.x(), node.y(), node.width(), nodeHeight, card.x(), card.y(), width, height)) return false;
            }
            for (int other = index + 1; other < placed.size(); other++) {
                var next = placed.get(other);
                if (overlaps(node.x(), node.y(), node.width(), nodeHeight, next.x(), next.y(), next.width(), nodeHeight)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean overlaps(float ax, float ay, float aw, float ah, float bx, float by, float bw, float bh) {
        return ax < bx + bw && bx < ax + aw && ay < by + bh && by < ay + ah;
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
