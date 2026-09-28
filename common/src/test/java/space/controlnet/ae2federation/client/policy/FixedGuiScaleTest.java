package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FixedGuiScaleTest {
    @Test
    void picksTheLargestScaleThatStillShowsTheWholeWorkspace() {
        assertEquals(2, FixedGuiScale.forFramebuffer(1600, 960));
        assertEquals(2, FixedGuiScale.forFramebuffer(1920, 1080));
        assertEquals(3, FixedGuiScale.forFramebuffer(2560, 1440));
        assertEquals(5, FixedGuiScale.forFramebuffer(3840, 2160));
        assertEquals(2, FixedGuiScale.forFramebuffer(1366, 768));
        assertEquals(2, FixedGuiScale.forFramebuffer(1280, 960));
    }

    @Test
    void aWindowTooSmallForTheWorkspaceFallsBackToOnePixelPerUnit() {
        assertEquals(1, FixedGuiScale.forFramebuffer(960, 720));
        assertEquals(1, FixedGuiScale.forFramebuffer(320, 240));
        assertEquals(1, FixedGuiScale.forFramebuffer(0, 0));
    }

    @Test
    void theLogicalViewportAtTheChosenScaleIsNeverSmallerThanTheMinimumWhenItFits() {
        for (int width = 640; width <= 4096; width += 37) {
            for (int height = 380; height <= 2304; height += 29) {
                int scale = FixedGuiScale.forFramebuffer(width, height);
                assertEquals(true, width / scale >= FixedGuiScale.MIN_WIDTH && height / scale >= FixedGuiScale.MIN_HEIGHT,
                        width + "x" + height);
                assertEquals(false, width / (scale + 1) >= FixedGuiScale.MIN_WIDTH
                        && height / (scale + 1) >= FixedGuiScale.MIN_HEIGHT, width + "x" + height);
            }
        }
    }
}
