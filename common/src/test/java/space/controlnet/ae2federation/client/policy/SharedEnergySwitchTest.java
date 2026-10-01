package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SharedEnergySwitchTest {
    @Test
    void unconfiguredPairReadsFirstToSecond() {
        assertFalse(SharedEnergySwitch.reversed(null, null));
    }

    @Test
    void theRuleThatIsOnRepresentsThePair() {
        assertFalse(SharedEnergySwitch.reversed(true, null));
        assertTrue(SharedEnergySwitch.reversed(null, true));
        assertTrue(SharedEnergySwitch.reversed(false, true));
        assertFalse(SharedEnergySwitch.reversed(true, false));
        assertFalse(SharedEnergySwitch.reversed(true, true));
    }

    @Test
    void otherwiseTheConfiguredRuleDoes() {
        assertTrue(SharedEnergySwitch.reversed(null, false));
        assertFalse(SharedEnergySwitch.reversed(false, null));
        assertFalse(SharedEnergySwitch.reversed(false, false));
    }

    @Test
    void thePairSharesWhenEitherRuleIsActive() {
        assertTrue(SharedEnergySwitch.shares(RuleHealth.ACTIVE, null));
        assertTrue(SharedEnergySwitch.shares(RuleHealth.OFF, RuleHealth.ACTIVE));
        assertFalse(SharedEnergySwitch.shares(RuleHealth.WAITING, RuleHealth.ERROR));
        assertFalse(SharedEnergySwitch.shares(null, null));
    }
}
