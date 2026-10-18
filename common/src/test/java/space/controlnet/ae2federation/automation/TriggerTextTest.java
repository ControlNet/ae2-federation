package space.controlnet.ae2federation.automation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Triggers as one line of text, both ways. */
final class TriggerTextTest {
    @Test
    void everyKindReadsBackAsWritten() {
        for (var text : List.of("manual", "redstone", "every 40", "when is_low minecraft:iron_ingot 64",
                "on block_changed interface-k7m2qx 2", "on block_changed \"front gate\"", "on network_boot")) {
            assertEquals(text, TriggerText.format(TriggerText.parse(text)), text);
        }
        assertEquals(new Installation.Trigger.Event("block_changed", List.of("front gate")),
                TriggerText.parse("on block_changed \"front gate\""));
    }

    @Test
    void mistakesAreNamed() {
        assertEquals("every takes a number of ticks, not soon",
                assertThrows(IllegalArgumentException.class, () -> TriggerText.parse("every soon")).getMessage());
        assertEquals("redstone takes no arguments",
                assertThrows(IllegalArgumentException.class, () -> TriggerText.parse("redstone 2")).getMessage());
        assertEquals("a trigger is manual, redstone, every, when or on, not daily",
                assertThrows(IllegalArgumentException.class, () -> TriggerText.parse("daily")).getMessage());
        assertThrows(IllegalArgumentException.class, () -> TriggerText.parse(" "));
    }
}
