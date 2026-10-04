package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class EndpointNodeLayoutTest {
    private static final float W = 200;
    private static final float H = 88;
    private static final float NODE_H = 16;

    private static List<EndpointNodeLayout.Placed> place(List<EndpointNodeLayout.Card> cards, EndpointNodeLayout.Node... nodes) {
        return EndpointNodeLayout.place(cards, W, H, List.of(nodes), NODE_H);
    }

    @Test
    void sideBySideNetworksKeepTheirEndpointsOnTheOuterSide() {
        var cards = List.of(new EndpointNodeLayout.Card("left", 0, 0), new EndpointNodeLayout.Card("right", 400, 0));
        var placed = place(cards, new EndpointNodeLayout.Node("a", "left", 80), new EndpointNodeLayout.Node("b", "right", 60));
        var a = placed.get(0);
        var b = placed.get(1);
        assertTrue(a.x() + a.width() < 0, "left network's endpoint sits left of its card");
        assertTrue(b.x() > 400 + W, "right network's endpoint sits right of its card");
        // The link runs from the card's outer edge to the node's facing edge.
        assertEquals(0, a.link().fromX());
        assertEquals(a.x() + a.width(), a.link().toX());
        assertEquals(400 + W, b.link().fromX());
        assertEquals(b.x(), b.link().toX());
    }

    @Test
    void endpointsOfOneNetworkStackWithoutOverlapping() {
        var cards = List.of(new EndpointNodeLayout.Card("left", 0, 0), new EndpointNodeLayout.Card("right", 400, 0));
        var placed = place(cards, new EndpointNodeLayout.Node("a", "left", 80), new EndpointNodeLayout.Node("b", "left", 80),
                new EndpointNodeLayout.Node("c", "left", 80));
        for (int i = 0; i < placed.size(); i++) {
            for (int j = i + 1; j < placed.size(); j++) {
                assertTrue(apart(placed.get(i), placed.get(j)), placed.get(i) + " overlaps " + placed.get(j));
            }
        }
    }

    @Test
    void aNetworkAboveTheOthersKeepsItsEndpointsAbove() {
        var cards = List.of(new EndpointNodeLayout.Card("left", 0, 200), new EndpointNodeLayout.Card("top", 300, 0),
                new EndpointNodeLayout.Card("right", 600, 200), new EndpointNodeLayout.Card("bottom", 300, 400));
        var placed = place(cards, new EndpointNodeLayout.Node("a", "top", 80), new EndpointNodeLayout.Node("b", "bottom", 80));
        assertTrue(placed.get(0).y() + NODE_H < 0, "above the top card");
        assertEquals(300 + W / 2, placed.get(0).link().fromX());
        assertEquals(0, placed.get(0).link().fromY());
        assertTrue(placed.get(1).y() > 400 + H, "below the bottom card");
    }

    @Test
    void unmappedEndpointsWaitInAFreeRowBelowWithoutALink() {
        var cards = List.of(new EndpointNodeLayout.Card("left", 0, 0), new EndpointNodeLayout.Card("right", 400, 0));
        var placed = place(cards, new EndpointNodeLayout.Node("a", "", 80), new EndpointNodeLayout.Node("b", "gone", 80),
                new EndpointNodeLayout.Node("c", "left", 80));
        assertNull(placed.get(0).link());
        assertNull(placed.get(1).link(), "an owner outside the shown networks draws no link");
        assertNotNull(placed.get(2).link());
        float below = Math.max(H, placed.get(2).y() + NODE_H);
        assertTrue(placed.get(0).y() > below && placed.get(1).y() == placed.get(0).y());
        assertTrue(apart(placed.get(0), placed.get(1)));
    }

    @Test
    void aSingleNetworkKeepsItsEndpointsBelow() {
        var placed = place(List.of(new EndpointNodeLayout.Card("only", 0, 0)), new EndpointNodeLayout.Node("a", "only", 80));
        assertTrue(placed.getFirst().y() > H);
        assertEquals(W / 2, placed.getFirst().link().fromX());
        assertEquals(H, placed.getFirst().link().fromY());
    }

    private static final float GAP = 40;
    private static final List<EndpointNodeLayout.Card> SIDE_BY_SIDE = List.of(new EndpointNodeLayout.Card("left", 0, 0),
            new EndpointNodeLayout.Card("right", 600, 0));

    private static List<EndpointNodeLayout.Placed> beside(String owner, int count) {
        var nodes = new EndpointNodeLayout.Node[count];
        for (int i = 0; i < count; i++) nodes[i] = new EndpointNodeLayout.Node("e" + i, owner, 120 + i % 3 * 6);
        return place(SIDE_BY_SIDE, nodes);
    }

    private static float centreY(EndpointNodeLayout.Placed node) {
        return node.y() + NODE_H / 2;
    }

    @Test
    void threeBesideACardGetStraightLinksFromTheirOwnPoints() {
        var placed = beside("left", 3);
        for (var node : placed) {
            assertEquals(-GAP, node.x() + node.width(), 1e-4, "each node stands " + GAP + " from the card");
            assertEquals(0, node.link().fromX(), 1e-4);
            assertEquals(centreY(node), node.link().fromY(), 1e-4, "three fit beside the card, so each link is level");
            assertEquals(centreY(node), node.link().toY(), 1e-4);
        }
        assertTrue(placed.get(0).link().fromY() < placed.get(1).link().fromY()
                && placed.get(1).link().fromY() < placed.get(2).link().fromY(), "each link leaves from its own point");
    }

    @Test
    void fiveStandInOneColumnAndTheirLinksDoNotCross() {
        var placed = beside("left", 5);
        for (int i = 0; i < placed.size(); i++) {
            var node = placed.get(i);
            assertEquals(-GAP, node.x() + node.width(), 1e-4, "one column");
            assertTrue(node.link().fromY() >= 14 - 1e-4 && node.link().fromY() <= H - 14 + 1e-4, "links leave from the card's side");
            if (i > 0) {
                assertTrue(node.y() >= placed.get(i - 1).y() + NODE_H + 8 - 1e-4, "eight between nodes");
                assertTrue(node.link().fromY() > placed.get(i - 1).link().fromY(), "links leave in the nodes' order");
            }
        }
    }

    @Test
    void sevenPutTheLastTwoInGapsOfTheFirstColumn() {
        assertSecondColumnInGaps(beside("left", 7), 5, false);
    }

    @Test
    void theRightSideMirrorsTheLeft() {
        assertSecondColumnInGaps(beside("right", 7), 5, true);
    }

    @Test
    void manyLengthenTheFirstColumnSoTheSecondStillFitsItsGaps() {
        assertSecondColumnInGaps(beside("left", 12), 7, false);
    }

    private static void assertSecondColumnInGaps(List<EndpointNodeLayout.Placed> placed, int firstColumn, boolean right) {
        float edge = right ? 600 + W + GAP : -GAP;
        var first = placed.subList(0, firstColumn);
        var second = placed.subList(firstColumn, placed.size());
        for (var node : first) assertEquals(edge, right ? node.x() : node.x() + node.width(), 1e-4, "first column " + node);
        for (var node : second) {
            assertTrue(right ? node.x() > first.stream().mapToDouble(n -> n.x() + n.width()).max().orElseThrow()
                    : node.x() + node.width() < first.stream().mapToDouble(EndpointNodeLayout.Placed::x).min().orElseThrow(),
                    "second column outside the first: " + node);
            // Its link bends between the card and the first column, then runs straight through a gap of the first.
            assertEquals(edge, node.link().bendEnd(), 1e-4);
            assertEquals(right ? node.x() : node.x() + node.width(), node.link().toX(), 1e-4);
            float y = node.link().toY();
            assertTrue(first.stream().noneMatch(n -> y >= n.y() - 1 && y <= n.y() + NODE_H + 1),
                    "the run passes between first-column nodes: " + node);
        }
        for (int i = 0; i < placed.size(); i++) {
            for (int j = i + 1; j < placed.size(); j++) {
                assertTrue(apart(placed.get(i), placed.get(j)), placed.get(i) + " overlaps " + placed.get(j));
                var a = placed.get(i).link();
                var b = placed.get(j).link();
                assertTrue((a.fromY() - b.fromY()) * (a.toY() - b.toY()) > 0, "links keep their order, so none cross");
            }
        }
    }

    @Test
    void belowACardLinksLeaveFromPointsAlongItsEdgeAndRunVertically() {
        var placed = place(List.of(new EndpointNodeLayout.Card("only", 0, 0)), new EndpointNodeLayout.Node("a", "only", 120),
                new EndpointNodeLayout.Node("b", "only", 120), new EndpointNodeLayout.Node("c", "only", 120));
        for (int i = 0; i < placed.size(); i++) {
            var node = placed.get(i);
            assertEquals(H + GAP, node.y(), 1e-4);
            assertTrue(node.link().vertical());
            assertEquals(H, node.link().fromY(), 1e-4);
            assertEquals(node.x() + node.width() / 2, node.link().toX(), 1e-4);
            assertTrue(node.link().fromX() >= 14 - 1e-4 && node.link().fromX() <= W - 14 + 1e-4);
            if (i > 0) assertTrue(node.link().fromX() > placed.get(i - 1).link().fromX());
        }
    }

    @Test
    void movingAPlacedNodeKeepsItsLinkShape() {
        var node = beside("left", 7).get(6);
        var moved = node.moved(10, 20);
        assertEquals(node.link().translated(10, 20), moved.link());
        assertEquals(node.link().bendEnd() + 10, moved.link().bendEnd(), 1e-4);
    }

    @Test
    void clearWhenNoNodeCoversACardOrAnotherNode() {
        var cards = List.of(new EndpointNodeLayout.Card("a", 0, 0), new EndpointNodeLayout.Card("b", 300, 0));
        var free = new EndpointNodeLayout.Placed("x", -160, 30, 120, null);
        var onCard = new EndpointNodeLayout.Placed("y", 250, 30, 120, null);
        var onNode = new EndpointNodeLayout.Placed("z", -100, 40, 120, null);
        assertTrue(EndpointNodeLayout.clear(cards, W, H, List.of(free), NODE_H));
        assertFalse(EndpointNodeLayout.clear(cards, W, H, List.of(free, onCard), NODE_H), "a node over another card");
        assertFalse(EndpointNodeLayout.clear(cards, W, H, List.of(free, onNode), NODE_H), "two nodes over each other");
    }

    private static boolean apart(EndpointNodeLayout.Placed a, EndpointNodeLayout.Placed b) {
        return a.x() + a.width() <= b.x() || b.x() + b.width() <= a.x() || a.y() + NODE_H <= b.y() || b.y() + NODE_H <= a.y();
    }
}
