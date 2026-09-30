package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.policy.PolicyFilter;
import space.controlnet.ae2federation.policy.PolicyFilterMode;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRule;

class RuleTermsTest {
    @Test
    void storageDefaultsReadAsAllOperationsAllResourcesNoReexport() {
        var terms = RuleTerms.of(PolicyRule.storageDefaults());
        assertEquals(List.of("view", "insert", "extract"), terms.operations());
        assertEquals("all", terms.filter());
        assertEquals(0, terms.filterEntries());
        assertEquals(false, terms.reexport());
    }

    @Test
    void operationsKeepTheirFixedOrderWhateverTheSetOrder() {
        var terms = RuleTerms.of(PolicyRule.enabled(Set.of(PolicyOperation.EXTRACT, PolicyOperation.VIEW)));
        assertEquals(List.of("view", "extract"), terms.operations());
    }

    @Test
    void listModeAndReexportAreReported() {
        var rule = new PolicyRule(true, Set.of(PolicyOperation.VIEW), new PolicyFilter(PolicyFilterMode.DENY_LIST, Set.of()), true);
        var terms = RuleTerms.of(rule);
        assertEquals("deny_list", terms.filter());
        assertEquals(0, terms.filterEntries());
        assertEquals(true, terms.reexport());
    }
}
