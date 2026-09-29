package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static boolean apart(EndpointNodeLayout.Placed a, EndpointNodeLayout.Placed b) {
        return a.x() + a.width() <= b.x() || b.x() + b.width() <= a.x() || a.y() + NODE_H <= b.y() || b.y() + NODE_H <= a.y();
    }
}
