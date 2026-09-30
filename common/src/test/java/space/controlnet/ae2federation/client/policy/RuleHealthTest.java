package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RuleHealthTest {
    @Test
    void publishedRulesAreActive() {
        assertEquals(RuleHealth.ACTIVE, RuleHealth.of(true, "published", ""));
    }

    @Test
    void anEnabledRuleThatCannotWorkIsAnError() {
        assertEquals(RuleHealth.ERROR, RuleHealth.of(true, "operation_missing", ""));
        for (var backend : new String[] {"crafting_cycle", "energy_source_missing", "crafting_cpu_missing",
                "crafting_provider_missing", "consumer_energy_interface_missing", "domain_reference_missing"}) {
            assertEquals(RuleHealth.ERROR, RuleHealth.of(true, "unobserved", backend), backend);
        }
    }

    @Test
    void transientReasonsOnlyWait() {
        assertEquals(RuleHealth.WAITING, RuleHealth.of(true, "unobserved", ""));
        for (var backend : new String[] {"identity_unconfirmed", "backend_unready", "network_pair_disconnected",
                "policy_unconfigured", "policy_disabled"}) {
            assertEquals(RuleHealth.WAITING, RuleHealth.of(true, "unobserved", backend), backend);
        }
    }

    @Test
    void disabledRulesAreOffWhateverTheLastObservation() {
        assertEquals(RuleHealth.OFF, RuleHealth.of(false, "operation_missing", "crafting_cycle"));
    }
}
