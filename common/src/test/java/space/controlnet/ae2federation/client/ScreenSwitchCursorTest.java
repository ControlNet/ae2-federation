package space.controlnet.ae2federation.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScreenSwitchCursorTest {
    @Test
    void aScreenOpenedRightAfterTheLastOneClosedGetsItsCursorBack() {
        var cursor = new ScreenSwitchCursor();
        cursor.left(412.5, 230, 1_000);
        assertArrayEquals(new double[] {412.5, 230}, cursor.arrived(1_050).orElseThrow());
    }

    @Test
    void theCursorIsGivenBackOnce() {
        var cursor = new ScreenSwitchCursor();
        cursor.left(10, 20, 1_000);
        cursor.arrived(1_010);
        // A later init of the same screen, such as a resize, keeps the cursor where the player put it.
        assertTrue(cursor.arrived(1_020).isEmpty());
    }

    @Test
    void aScreenOpenedLaterByThePlayerStartsCentredAsUsual() {
        var cursor = new ScreenSwitchCursor();
        cursor.left(10, 20, 1_000);
        assertTrue(cursor.arrived(1_000 + ScreenSwitchCursor.SWITCH_MILLIS + 1).isEmpty());
    }

    @Test
    void nothingIsGivenBackWithoutAClosedScreen() {
        assertTrue(new ScreenSwitchCursor().arrived(1_000).isEmpty());
    }

    @Test
    void anotherScreenInBetweenDropsTheCursor() {
        var cursor = new ScreenSwitchCursor();
        cursor.left(10, 20, 1_000);
        cursor.forget();
        assertTrue(cursor.arrived(1_010).isEmpty());
    }
}
