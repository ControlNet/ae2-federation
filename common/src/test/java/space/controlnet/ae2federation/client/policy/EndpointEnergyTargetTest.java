package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EndpointEnergyTargetTest {
    private static final String ENDPOINT = "0b7a3a1e-6f0e-4d53-9a55-3b4f2f7d9c10:7";

    @Test
    void roundTripsAnExplicitState() {
        var target = new EndpointEnergyTarget(ENDPOINT, true);
        assertEquals(ENDPOINT + "/1", target.encode());
        assertEquals(target, EndpointEnergyTarget.parse(target.encode()).orElseThrow());
        assertFalse(EndpointEnergyTarget.parse(ENDPOINT + "/0").orElseThrow().on());
    }

    @Test
    void rejectsMalformedRequests() {
        assertTrue(EndpointEnergyTarget.parse(ENDPOINT).isEmpty());
        assertTrue(EndpointEnergyTarget.parse(ENDPOINT + "/2").isEmpty());
        assertTrue(EndpointEnergyTarget.parse("not-an-endpoint/1").isEmpty());
        assertTrue(EndpointEnergyTarget.parse(ENDPOINT + "/1/extra").isEmpty());
    }
}
