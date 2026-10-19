package space.controlnet.ae2federation.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** An installation's argument as the text an {@code install} or {@code args} request takes back. */
final class InstallationTextTest {
    @Test
    void valuesReadAsTheyAreTyped() {
        assertEquals("minecraft:iron_ingot", Installation.text("minecraft:iron_ingot"));
        assertEquals("a name with 'quotes'", Installation.text("a name with 'quotes'"));
        assertEquals("256", Installation.text(256L));
        assertEquals("-3", Installation.text(-3L));
        assertEquals("1.5", Installation.text(1.5));
        assertEquals("True", Installation.text(true));
        assertEquals("False", Installation.text(false));
        assertEquals("", Installation.text(null));
    }
}
