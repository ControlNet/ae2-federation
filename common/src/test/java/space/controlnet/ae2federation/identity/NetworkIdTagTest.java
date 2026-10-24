package space.controlnet.ae2federation.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The digits a player knows a network by. */
final class NetworkIdTagTest {
    @Test
    void aTagIsTheFirstSixDigitsInCapitals() {
        assertEquals("082A5C", NetworkId.tag("082a5c1e-9d2b-4f00-8a1b-2c3d4e5f6a7b"));
    }

    @Test
    void aTagSkipsTheDashesOfTheText() {
        assertEquals("082A5C", NetworkId.tag("082a-5c1e"));
        assertEquals("08", NetworkId.tag("08"));
    }
}
