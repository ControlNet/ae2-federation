package space.controlnet.ae2federation.client.automation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The caret blinks from the moment it last moved, so a moved caret shows at once. */
final class CaretBlinkTest {
    @Test
    void aMovedCaretShowsAtOnceThenBlinks() {
        // Moved at 1700 ms: LDLib2's clock-based blink would hide it there (1700 % 1000 >= 500).
        assertTrue(CaretBlink.shown(1700, 1700));
        assertTrue(CaretBlink.shown(2199, 1700));
        assertFalse(CaretBlink.shown(2200, 1700));
        assertTrue(CaretBlink.shown(2700, 1700));
    }
}
