package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MappingWireTargetTest {
    private static final String ENDPOINT = "0b7a3a1e-6f0e-4d53-9a55-3b4f2f7d9c10:7";

    @Test
    void roundTripsAnExplicitState() {
        var target = new MappingWireTarget("3", ENDPOINT, true);
        assertEquals("3/" + ENDPOINT + "/1", target.encode());
        assertEquals(target, MappingWireTarget.parse(target.encode()).orElseThrow());
        assertEquals(3, target.slotIndex());
        assertFalse(MappingWireTarget.parse("3/" + ENDPOINT + "/0").orElseThrow().mapped());
    }

    @Test
    void rejectsMalformedRequests() {
        assertTrue(MappingWireTarget.parse("3/" + ENDPOINT).isEmpty());
        assertTrue(MappingWireTarget.parse("3/" + ENDPOINT + "/2").isEmpty());
        assertTrue(MappingWireTarget.parse("-1/" + ENDPOINT + "/1").isEmpty());
        assertTrue(MappingWireTarget.parse("3/not-an-endpoint/1").isEmpty());
        assertTrue(MappingWireTarget.parse("3/" + ENDPOINT + "/1/extra").isEmpty());
    }
}
