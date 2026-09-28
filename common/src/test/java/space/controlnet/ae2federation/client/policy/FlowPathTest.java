package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FlowPathTest {
    private static final float EPSILON = 0.0001f;

    @Test
    void horizontalPathRunsBetweenTheFacingCardEdges() {
        var segment = FlowPath.visible(80, 29, 480, 29, 80, 29, 0, 0).getFirst();
        assertEquals(160, segment.fromX(), EPSILON);
        assertEquals(29, segment.fromY(), EPSILON);
        assertEquals(400, segment.toX(), EPSILON);
        assertEquals(29, segment.toY(), EPSILON);
    }

    @Test
    void steepPathLeavesThroughTheTopAndBottomEdges() {
        var segment = FlowPath.visible(100, 0, 110, 200, 80, 29, 0, 0).getFirst();
        assertEquals(29, segment.fromY(), EPSILON);
        assertEquals(1.45f + 100, segment.fromX(), EPSILON);
        assertEquals(171, segment.toY(), EPSILON);
        assertEquals(110 - 1.45f, segment.toX(), EPSILON);
    }

    @Test
    void overlappingCardsHaveNoVisiblePath() {
        assertTrue(FlowPath.visible(0, 0, 100, 0, 80, 29, 0, 0).isEmpty());
        assertTrue(FlowPath.visible(0, 0, 0, 0, 80, 29, 0, 0).isEmpty());
    }

    @Test
    void dotsAreSpreadEvenlyAndWrapWithThePhase() {
        var segment = new FlowPath.Segment(0, 0, 90, 0);
        var dots = segment.dots(0.5f, 3);
        assertEquals(3, dots.length / 2);
        assertEquals(45, dots[0], EPSILON);
        assertEquals(75, dots[2], EPSILON);
        assertEquals(15, dots[4], EPSILON);
        assertEquals(0, dots[1], EPSILON);
    }

    @Test
    void visibleStubsLeaveOutTheCardsAndTheLabelAtTheMidpoint() {
        // Centres 560 apart, cards 160 wide, a 200-wide label in the middle: stubs 160..260 and 460..560.
        var stubs = FlowPath.visible(80, 29, 640, 29, 80, 29, 100, 8);
        assertEquals(2, stubs.size());
        assertEquals(160, stubs.get(0).fromX(), EPSILON);
        assertEquals(260, stubs.get(0).toX(), EPSILON);
        assertEquals(460, stubs.get(1).fromX(), EPSILON);
        assertEquals(560, stubs.get(1).toX(), EPSILON);
    }

    @Test
    void aLabelCoveringTheWholeGapLeavesNothingVisible() {
        assertTrue(FlowPath.visible(80, 29, 480, 29, 80, 29, 200, 8).isEmpty());
    }

    @Test
    void withoutALabelTheWholeGapIsOneStub() {
        var stubs = FlowPath.visible(80, 29, 480, 29, 80, 29, 0, 0);
        assertEquals(1, stubs.size());
        assertEquals(new FlowPath.Segment(160, 29, 400, 29), stubs.getFirst());
    }
}
