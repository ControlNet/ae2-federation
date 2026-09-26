package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

final class DeviceDomainAvailabilityTest {
    @Test
    void distinguishesUnknownIdentityFromConfirmedNetworkWithoutDomain() {
        assertEquals(DeviceDomainAvailability.UNCONFIRMED, DeviceDomainAvailability.classify(false, 0));
        assertEquals(DeviceDomainAvailability.NO_DOMAIN, DeviceDomainAvailability.classify(true, 0));
    }

    @Test
    void onlyOneCandidatePermitsAUniqueDomain() {
        assertEquals(DeviceDomainAvailability.AVAILABLE, DeviceDomainAvailability.classify(true, 1));
        assertEquals(DeviceDomainAvailability.AMBIGUOUS, DeviceDomainAvailability.classify(true, 2));
        assertEquals(DeviceDomainAvailability.AMBIGUOUS, DeviceDomainAvailability.classify(true, 7));
    }
}
