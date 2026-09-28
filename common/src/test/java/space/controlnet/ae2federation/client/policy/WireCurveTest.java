package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WireCurveTest {
    @Test
    void theCurveStartsAtThePortAndEndsAtTheCard() {
        var curve = new WireCurve(10, 20, 110, 80);
        var points = curve.points(16);
        assertEquals(34, points.length);
        assertEquals(10, points[0], 1e-4);
        assertEquals(20, points[1], 1e-4);
        assertEquals(110, points[32], 1e-4);
        assertEquals(80, points[33], 1e-4);
    }

    @Test
    void itLeavesThePortAndEntersTheCardHorizontally() {
        var curve = new WireCurve(0, 0, 100, 60);
        var start = curve.at(0.02f);
        var end = curve.at(0.98f);
        assertTrue(Math.abs(start[1]) < 1, "leaves the port level");
        assertTrue(Math.abs(end[1] - 60) < 1, "arrives at the card level");
        assertEquals(50, curve.at(0.5f)[0], 1e-4);
        assertEquals(30, curve.at(0.5f)[1], 1e-4);
    }

    @Test
    void itMovesLeftToRightWithoutLoopingBack() {
        var curve = new WireCurve(0, 100, 40, 0);
        var points = curve.points(32);
        for (int index = 2; index < points.length; index += 2) {
            assertTrue(points[index] >= points[index - 2] - 1e-4, "x never decreases");
        }
    }

    @Test
    void distanceIsMeasuredToTheCurveNotTheChord() {
        var curve = new WireCurve(0, 0, 100, 100);
        var middle = curve.at(0.5f);
        assertEquals(0, curve.distance(middle[0], middle[1], 32), 0.5);
        var quarter = curve.at(0.25f);
        assertTrue(curve.distance(quarter[0], quarter[1], 32) < 0.5);
        assertTrue(curve.distance(0, 100, 32) > 30);
    }
}
