package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TopologySpacingTest {
    private static final float WIDTH = 200;
    private static final float HEIGHT = 88;

    @Test
    void keepsALayoutThatAlreadyHasRoom() {
        float[][] cards = {{-400, 0}, {400, 0}};
        var labels = List.of(new TopologySpacing.Label(0, 1, 60, 12));
        assertEquals(1f, TopologySpacing.factor(cards, labels, WIDTH, HEIGHT, 6));
    }

    @Test
    void spreadsCardsUntilAWideLabelClearsThem() {
        // The three-network ellipse: one card on the left, two on the right, their link label wider than the gap.
        float[][] cards = {{-202, 0}, {101, -104}, {101, 104}};
        var labels = List.of(new TopologySpacing.Label(0, 1, 100, 18), new TopologySpacing.Label(1, 2, 70, 18));
        assertTrue(TopologySpacing.overlaps(cards, 1f, labels, WIDTH, HEIGHT, 6));
        float factor = TopologySpacing.factor(cards, labels, WIDTH, HEIGHT, 6);
        assertTrue(factor > 1f);
        assertFalse(TopologySpacing.overlaps(cards, factor, labels, WIDTH, HEIGHT, 6));
    }

    @Test
    void separatesOverlappingCardsWithoutLabels() {
        float[][] cards = {{0, 0}, {120, 40}};
        float factor = TopologySpacing.factor(cards, List.of(), WIDTH, HEIGHT, 6);
        assertFalse(TopologySpacing.overlaps(cards, factor, List.of(), WIDTH, HEIGHT, 6));
    }

    @Test
    void stopsAtTheLargestSpread() {
        // Two cards on the same spot never separate by scaling; the spread is capped instead of looping forever.
        float[][] cards = {{0, 0}, {0, 0}};
        assertEquals(TopologySpacing.MAX_FACTOR, TopologySpacing.factor(cards, List.of(), WIDTH, HEIGHT, 6));
    }

    @Test
    void labelsOfLinksThatCrossMoveApartAlongTheirLinks() {
        // Four networks in a diamond: the two diagonals cross in the middle, where both labels would sit.
        float[][] cards = {{-500, 0}, {0, -300}, {500, 0}, {0, 300}};
        var labels = List.of(new TopologySpacing.Label(0, 2, 60, 12), new TopologySpacing.Label(1, 3, 60, 12));
        var spots = TopologySpacing.labelSpots(cards, labels, WIDTH, HEIGHT, 6);
        assertEquals(0.5f, spots[0]);
        assertTrue(spots[1] != 0.5f);
        var first = TopologyLink.between(-500, 0, 500, 0, WIDTH, HEIGHT).withLabelAt(spots[0]).label();
        var second = TopologyLink.between(0, -300, 0, 300, WIDTH, HEIGHT).withLabelAt(spots[1]).label();
        assertTrue(Math.abs(first[0] - second[0]) >= 120 + 6 || Math.abs(first[1] - second[1]) >= 24 + 6);
    }

    @Test
    void aLabelWithRoomStaysInTheMiddle() {
        float[][] cards = {{-400, 0}, {400, 0}};
        var spots = TopologySpacing.labelSpots(cards, List.of(new TopologySpacing.Label(0, 1, 60, 12)), WIDTH, HEIGHT, 6);
        assertEquals(0.5f, spots[0]);
    }
}
