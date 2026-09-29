package space.controlnet.ae2federation.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class PolicyCapabilityTest {
    @Test
    void savedRulesKeepTheirCapability() {
        for (var capability : PolicyCapability.values()) {
            assertEquals(Optional.of(capability), PolicyCapability.persisted(capability.name()));
        }
    }

    @Test
    void processingIsNoLongerARuleCapability() {
        // Endpoints are authorized by the domain their Federation face joins; a saved Processing rule is dropped.
        assertEquals(Optional.empty(), PolicyCapability.persisted("PROCESSING"));
        assertEquals(java.util.List.of(PolicyCapability.STORAGE, PolicyCapability.CRAFTING, PolicyCapability.ME_POWER),
                java.util.List.of(PolicyCapability.values()));
    }

    @Test
    void anUnknownCapabilityIsStillMalformed() {
        assertThrows(IllegalArgumentException.class, () -> PolicyCapability.persisted("TELEPORT"));
    }
}
