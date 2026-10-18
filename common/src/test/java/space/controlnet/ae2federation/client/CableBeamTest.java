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
