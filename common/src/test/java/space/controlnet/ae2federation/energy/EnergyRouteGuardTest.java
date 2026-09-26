package space.controlnet.ae2federation.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;

final class EnergyRouteGuardTest {
    @Test
    void rejectsCycleWithoutRetainingOperationState() {
        var key = new PolicyKey(new NetworkId(UUID.randomUUID()), new NetworkId(UUID.randomUUID()),
                PolicyCapability.ME_POWER);

        var nested = EnergyRouteGuard.call(key, () -> EnergyRouteGuard.call(key, () -> 4.0));
        var later = EnergyRouteGuard.call(key, () -> 4.0);

        assertEquals(0.0, nested);
        assertEquals(4.0, later);
    }
}
