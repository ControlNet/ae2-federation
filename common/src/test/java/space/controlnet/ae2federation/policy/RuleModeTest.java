package space.controlnet.ae2federation.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RuleModeTest {
    @Test
    void aRuleIsDisabledEnabledOrEnabledWithReexport() {
        var rule = PolicyRule.storageDefaults();
        assertEquals(RuleMode.ENABLED, RuleMode.of(rule));
        assertEquals(RuleMode.REEXPORT, RuleMode.of(rule.withMode(RuleMode.REEXPORT)));
        assertEquals(RuleMode.DISABLED, RuleMode.of(rule.withMode(RuleMode.DISABLED)));
    }

    @Test
    void disablingClearsReexportSoNoRuleIsOffButPassingOn() {
        var disabled = PolicyRule.storageDefaults().withMode(RuleMode.REEXPORT).withMode(RuleMode.DISABLED);
        assertFalse(disabled.enabled());
        assertFalse(disabled.allowReexport());
        // A rule saved off with re-export set reads as off.
        var saved = new PolicyRule(false, disabled.operations(), disabled.filter(), true);
        assertEquals(RuleMode.DISABLED, RuleMode.of(saved));
    }

    @Test
    void modeKeepsOperationsAndFilter() {
        var rule = PolicyRule.storageDefaults();
        var reexport = rule.withMode(RuleMode.REEXPORT);
        assertTrue(reexport.enabled() && reexport.allowReexport());
        assertEquals(rule.operations(), reexport.operations());
        assertEquals(rule.filter(), reexport.filter());
    }

    @Test
    void stepsForwardAndBackAroundTheThreeStates() {
        assertEquals(RuleMode.ENABLED, RuleMode.DISABLED.next());
        assertEquals(RuleMode.REEXPORT, RuleMode.ENABLED.next());
        assertEquals(RuleMode.DISABLED, RuleMode.REEXPORT.next());
        assertEquals(RuleMode.REEXPORT, RuleMode.DISABLED.previous());
        assertEquals(RuleMode.DISABLED, RuleMode.ENABLED.previous());
        assertEquals(RuleMode.ENABLED, RuleMode.REEXPORT.previous());
    }

    @Test
    void onlyStorageAndCraftingPassCapabilitiesOn() {
        assertTrue(RuleMode.REEXPORT.allowedFor(PolicyCapability.STORAGE));
        assertTrue(RuleMode.REEXPORT.allowedFor(PolicyCapability.CRAFTING));
        // Shared energy is one pool across the pair; there is nothing to pass on.
        assertFalse(RuleMode.REEXPORT.allowedFor(PolicyCapability.ME_POWER));
        assertTrue(RuleMode.ENABLED.allowedFor(PolicyCapability.ME_POWER));
    }
}
