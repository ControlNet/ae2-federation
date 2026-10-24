package space.controlnet.ae2federation.client;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class CableBeamTest {
    @Test
    void loadMappingGrowsWithLoadAndDoesNotMarkFullAsOverloaded() {
        var idle = CableBeamStyle.fromLoad(0);
        var medium = CableBeamStyle.fromLoad(.6);
        var full = CableBeamStyle.fromLoad(1);
        var overload = CableBeamStyle.fromLoad(1.25);
        assertEquals(2.2, medium.pulseDensity(), 1e-6);
        assertEquals(1.38, medium.emission(), 1e-6);
        assertTrue(idle.emission() < medium.emission() && medium.emission() < full.emission());
        assertFalse(full.overloaded());
        assertTrue(overload.overloaded());
        assertEquals(full.emission(), overload.emission());
        assertEquals(full.pulseDensity(), CableBeamStyle.fromLoad(100).pulseDensity());
        for (double invalid : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> CableBeamStyle.fromLoad(invalid));
        }
        for (int tick = 0; tick < 64; tick++) {
            assertTrue(overload.strength(tick, .5f) >= 1.39);
            assertTrue(overload.strength(tick, .5f) <= 2.21);
            // A breathing alarm swells by at most a tenth: R / LITE is sized to the 1x1 glass with that margin.
            assertTrue(overload.breath(tick, .5f) <= 1.1f + 1e-6);
        }
    }

    @Test
    void aCableShinesByItsCapacityCountAndAlarmsOnlyWhileTheLimitStopsItsDomain() {
        assertEquals(0, CableBeamStyle.loadOf(0, 4, true), "an idle cable keeps only its carrier");
        assertEquals(.75, CableBeamStyle.loadOf(3, 4, true), 1e-9);
        assertFalse(CableBeamStyle.fromLoad(CableBeamStyle.loadOf(4, 4, true)).overloaded(), "a full cable still runs");
        assertTrue(CableBeamStyle.fromLoad(CableBeamStyle.loadOf(5, 4, true)).overloaded());
        assertEquals(1, CableBeamStyle.loadOf(5, 4, false), "a limit that is not enforced stops nothing, so no alarm");
        assertEquals(.5, CableBeamStyle.loadOf(2, 4, false), 1e-9);
        assertEquals(0, CableBeamStyle.loadOf(3, 0, true), "a count not yet received shows an idle carrier");
    }

    @Test
    void distanceTiersTheBeamsDetail() {
        assertEquals(0, CableBeamMesh.detail(20 * 20));
        assertEquals(1, CableBeamMesh.detail(30 * 30));
        assertEquals(2, CableBeamMesh.detail(60 * 60));
    }

    @Test
    void anEasingLoadReachesItsTargetExactly() {
        // Unsnapped, the exponential stalled 15 to 48 ulps short of the target for good.
        for (double[] ease : new double[][] {{.5, .6}, {.7, .6}, {.6, 0}, {0, .6}, {1.3, 1.25}}) {
            double value = ease[0];
            double target = ease[1];
            int frames = 0;
            while (value != target && frames < 120 * 60) {
                value = CableBeamStyle.smooth(value, target, 1 / 120.0);
                frames++;
            }
            assertEquals(target, value, 0, "from " + ease[0]);
            assertTrue(frames < 120 * 10, ease[0] + " -> " + target + " took " + frames + " frames");
        }
        // Short of the snap it still eases.
        double near = CableBeamStyle.smooth(.6 + 2 * CableBeamStyle.SNAP, .6, 1 / 120.0);
        assertTrue(near > .6 + CableBeamStyle.SNAP, String.valueOf(near));
    }

    @Test
    void cablesEasingTogetherDrawTheSameLoad() {
        assertEquals(.6, CableBeamStyle.drawn(.6, .6), 0, "a settled load is drawn exactly");
        assertEquals(1 / 9.0, CableBeamStyle.drawn(1 / 9.0, 1 / 9.0), 0);
        double step = CableBeamStyle.DRAWN_STEP;
        for (double eased = 0; eased < 1.3; eased += .0123) {
            double drawn = CableBeamStyle.drawn(eased, 2);
            assertTrue(Math.abs(drawn - eased) <= step / 2, eased + " " + drawn);
            assertEquals(0, drawn % step, 0, "in steps: " + drawn);
        }
        // Two cables of one domain read the clock microseconds apart: their eased loads differ in the last digits,
        // so unrounded each would compute its own beam. Rounded they draw the same load almost every frame.
        double a = .6, b = .6;
        int frames = 0, same = 0;
        while (a != 0 || b != 0) {
            a = CableBeamStyle.smooth(a, 0, 1 / 120.0);
            b = CableBeamStyle.smooth(b, 0, 1 / 120.0 + 3e-6);
            frames++;
            if (CableBeamStyle.drawn(a, 0) == CableBeamStyle.drawn(b, 0)) same++;
        }
        assertTrue(same >= frames * .95, same + " of " + frames);
    }

    @Test
    void smoothingIsIndependentOfFrameRate() {
        for (double target : new double[] {0, 1.25}) {
            double start = target == 0 ? 1.25 : 0;
            double expected = CableBeamStyle.smooth(start, target, 1);
            for (int fps : new int[] {20, 60, 144}) {
                double value = start;
                for (int i = 0; i < fps; i++) value = CableBeamStyle.smooth(value, target, 1.0 / fps);
                assertEquals(expected, value, 1e-10);
            }
        }
    }
}
