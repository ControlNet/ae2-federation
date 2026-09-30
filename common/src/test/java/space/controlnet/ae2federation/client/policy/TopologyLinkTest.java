package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TopologyLinkTest {
    private static final float EPSILON = 0.0001f;

    @Test
    void sideBySideCardsAreJoinedFromTheirFacingSideEdges() {
        var link = TopologyLink.between(0, 0, 400, 100, 160, 60);
        assertEquals(160, link.start()[0], EPSILON);
        assertEquals(30, link.start()[1], EPSILON);
        assertEquals(400, link.end()[0], EPSILON);
        assertEquals(130, link.end()[1], EPSILON);
        var reverse = TopologyLink.between(400, 100, 0, 0, 160, 60);
        assertEquals(400, reverse.start()[0], EPSILON);
        assertEquals(160, reverse.end()[0], EPSILON);
    }

    @Test
    void stackedCardsAreJoinedFromTheirFacingTopAndBottomEdges() {
        var link = TopologyLink.between(0, 0, 40, 200, 160, 60);
        assertEquals(80, link.start()[0], EPSILON);
        assertEquals(60, link.start()[1], EPSILON);
        assertEquals(120, link.end()[0], EPSILON);
        assertEquals(200, link.end()[1], EPSILON);
    }

    @Test
    void theLabelSitsWhereTheCurvePassesTheMiddle() {
        var link = TopologyLink.between(0, 0, 400, 100, 160, 60);
        assertEquals(280, link.middle()[0], EPSILON);
        assertEquals(80, link.middle()[1], EPSILON);
    }

    @Test
    void dotsTravelTheCurveAndHideUnderTheLabel() {
        var link = TopologyLink.between(0, 0, 400, 0, 160, 60);
        var dots = link.dots(0.25f, 2, false, 0, 0);
        assertEquals(4, dots.length);
        assertEquals(link.curve().at(0.25f)[0], dots[0], EPSILON);
        assertEquals(link.curve().at(0.75f)[0], dots[2], EPSILON);
        var hidden = link.dots(0.5f, 1, false, 20, 10);
        assertEquals(0, hidden.length, "a dot under the label is not drawn");
    }

    @Test
    void returningDotsStartFromTheOtherEnd() {
        var link = TopologyLink.between(0, 0, 400, 0, 160, 60);
        var dots = link.dots(0.1f, 1, true, 0, 0);
        assertEquals(link.curve().at(0.9f)[0], dots[0], EPSILON);
        assertTrue(dots[0] > 300);
    }
}
