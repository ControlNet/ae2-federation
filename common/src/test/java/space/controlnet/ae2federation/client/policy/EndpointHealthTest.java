package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EndpointHealthTest {
    @Test
    void aClaimedEndpointWithAReadySubnetIsActive() {
        assertEquals(EndpointHealth.ACTIVE, EndpointHealth.of("FEDERATED", true, true, false));
    }

    @Test
    void aClaimedEndpointWaitsForItsNodeOrSomethingBehindIt() {
        assertEquals(EndpointHealth.WAITING, EndpointHealth.of("FEDERATED", true, false, false));
        assertEquals(EndpointHealth.WAITING, EndpointHealth.of("FEDERATED", true, true, true));
        assertEquals(EndpointHealth.WAITING, EndpointHealth.of("FEDERATED", true, false, true));
    }

    @Test
    void anEndpointNoShownNetworkUsesIsOffWhateverItsSubnet() {
        for (boolean ready : new boolean[] {true, false}) {
            for (boolean alone : new boolean[] {true, false}) {
                assertEquals(EndpointHealth.OFF, EndpointHealth.of("FEDERATED", false, ready, alone));
                assertEquals(EndpointHealth.OFF, EndpointHealth.of("LOCAL", true, ready, alone));
            }
        }
    }

    @Test
    void eachHealthHasItsUndrawnClass() {
        assertEquals("health-active", EndpointHealth.ACTIVE.cssClass());
        assertEquals("health-waiting", EndpointHealth.WAITING.cssClass());
        assertEquals("health-off", EndpointHealth.OFF.cssClass());
    }
}
