package space.controlnet.ae2federation.qa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.test.scale.ScaleTimedWindow;

final class ScaleTimedWindowTest {
    @Test
    void rejectsMissingWorkWhenWindowElapsed() {
        var window = new ScaleTimedWindow(300, 600, 256, 16, 0, 0);

        assertThrows(IllegalStateException.class, () -> window.completedJob(300_000_000_000L, 6000));
    }

    @Test
    void rejectsZeroUnitWorkDeclaration() {
        assertThrows(IllegalArgumentException.class, () -> new ScaleTimedWindow(300, 600, 256, 0, 0, 0));
    }

    @Test
    void rejectsIdleSampleAfterCompleteWarmup() {
        var window = new ScaleTimedWindow(300, 600, 256, 16, 0, 0);
        for (int index = 0; index < 256; index++) {
            window.completedJob(index == 255 ? 300_000_000_000L : index + 1L, index + 1L);
        }

        assertThrows(IllegalStateException.class, () -> window.completedJob(900_000_000_000L, 1256));
    }

    @Test
    void separatesWarmupAndSampleByCompletedNativeJobs() {
        var window = new ScaleTimedWindow(300, 600, 256, 16, 0, 0);

        for (int index = 0; index < 299; index++) {
            assertFalse(window.completedJob((index + 1L) * 1_000_000_000L, index * 20L + 20));
        }
        assertFalse(window.completedJob(300_000_000_000L, 6000));
        var warmup = window.warmup();
        assertEquals(300, warmup.jobs());
        assertEquals(4800, warmup.units());
        assertTrue(warmup.wallNanos() >= 300_000_000_000L);
        assertEquals(6000, warmup.ticks());

        for (int index = 0; index < 599; index++) {
            assertFalse(window.completedJob(300_000_000_000L + (index + 1L) * 1_000_000_000L,
                    6000 + (index + 1L) * 20));
        }
        assertTrue(window.completedJob(900_000_000_000L, 18000));
        var sample = window.sample();
        assertEquals(600, sample.jobs());
        assertEquals(9600, sample.units());
        assertTrue(sample.wallNanos() >= 600_000_000_000L);
        assertEquals(12000, sample.ticks());
    }
}
