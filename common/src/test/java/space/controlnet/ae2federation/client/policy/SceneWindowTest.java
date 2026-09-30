package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SceneWindowTest {
    private static BlockMarks.Mark mark(int x, int y, int z) {
        return new BlockMarks.Mark(x, y, z);
    }

    @Test
    void aSingleDeviceShowsASquareAroundItWithGroundBelowAndAirAbove() {
        var window = SceneWindow.around(mark(10, 64, -5), List.of(mark(10, 64, -5)), 5, 8);
        assertEquals(new SceneWindow(5, 60, -10, 15, 66, 0), window);
    }

    @Test
    void theHeightFollowsTheNetworksBlocksButStaysWithinTheLimit() {
        var marks = List.of(mark(0, 60, 0), mark(1, 62, 1), mark(2, 90, 2));
        var window = SceneWindow.around(mark(0, 60, 0), marks, 4, 8);
        assertEquals(56, window.minY());
        assertEquals(68, window.maxY(), "a far-away block above the base does not stretch the window past the limit");
        assertTrue(window.contains(1, 62, 1));
        assertTrue(!window.contains(2, 90, 2));
    }

    @Test
    void theHorizontalRadiusIsAlwaysTheGivenOne() {
        var window = SceneWindow.around(mark(0, 0, 0), List.of(mark(40, 0, 40)), 6, 8);
        assertEquals(-6, window.minX());
        assertEquals(6, window.maxX());
        assertEquals(-6, window.minZ());
        assertEquals(6, window.maxZ());
        assertEquals(13 * 13 * (window.maxY() - window.minY() + 1), window.volume());
    }
}
