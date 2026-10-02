package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RuleHealthTest {
    @Test
    void publishedRulesAreActive() {
        assertEquals(RuleHealth.ACTIVE, RuleHealth.of(true, "published", "", ""));
    }

    @Test
    void anEnabledRuleThatCannotWorkIsAnError() {
        assertEquals(RuleHealth.ERROR, RuleHealth.of(true, "operation_missing", "", ""));
        for (var backend : new String[] {"crafting_storage_required", "energy_connection_missing",
                "domain_reference_missing", "storage_access_none", "storage_compile_budget"}) {
            assertEquals(RuleHealth.ERROR, RuleHealth.of(true, "unobserved", backend, ""), backend);
        }
    }

    @Test
    void transientReasonsOnlyWait() {
        assertEquals(RuleHealth.WAITING, RuleHealth.of(true, "unobserved", "", ""));
        for (var backend : new String[] {"identity_unconfirmed", "backend_unready", "network_pair_disconnected",
                "policy_unconfigured", "policy_disabled", "storage_source_empty"}) {
            assertEquals(RuleHealth.WAITING, RuleHealth.of(true, "unobserved", backend, ""), backend);
        }
        assertEquals(RuleHealth.WAITING, RuleHealth.of(true, "unobserved", "", "unsettled_origin"));
    }

    @Test
    void aRejectedStorageSourceIsAnErrorUntilTheRuleIsInEffect() {
        assertEquals(RuleHealth.ERROR, RuleHealth.of(true, "unobserved", "", "native_mount_table_unavailable"));
        assertEquals(RuleHealth.ACTIVE, RuleHealth.of(true, "published", "", "complete_aggregate"));
    }

    @Test
    void disabledRulesAreOffWhateverTheLastObservation() {
        assertEquals(RuleHealth.OFF, RuleHealth.of(false, "operation_missing", "crafting_storage_required", ""));
    }
}
