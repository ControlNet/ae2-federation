package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DropHintTest {
    @Test
    void aFreeEndpointAcceptsTheDropAndWillBeClaimed() {
        var hint = DropHint.of("free", false, true);
        assertEquals(DropHint.CLAIM, hint);
        assertTrue(hint.accepts());
    }

    @Test
    void anEndpointThisProviderHoldsAcceptsWithoutANewClaim() {
        assertEquals(DropHint.ACCEPT, DropHint.of("in_use", false, true));
        assertEquals(DropHint.ACCEPT, DropHint.of("retained", false, true));
    }

    @Test
    void theSameWireTwiceIsRefusedBeforeOwnership() {
        assertEquals(DropHint.EXISTING, DropHint.of("in_use", true, true));
        assertFalse(DropHint.EXISTING.accepts());
    }

    @Test
    void refusalsFollowTheClaim() {
        assertEquals(DropHint.OCCUPIED, DropHint.of("occupied", false, true));
        assertEquals(DropHint.LOCAL, DropHint.of("local", false, true));
        assertEquals(DropHint.UNLOADED, DropHint.of("unobserved", false, true));
        for (var hint : new DropHint[] {DropHint.OCCUPIED, DropHint.LOCAL, DropHint.UNLOADED}) assertFalse(hint.accepts());
    }

    @Test
    void anOccupiedEndpointIsRedAndUnusableOnesAreGrey() {
        assertEquals(DropHint.Tone.OK, DropHint.CLAIM.tone());
        assertEquals(DropHint.Tone.ERROR, DropHint.OCCUPIED.tone());
        assertEquals(DropHint.Tone.MUTED, DropHint.UNLOADED.tone());
        assertEquals(DropHint.Tone.MUTED, DropHint.EXISTING.tone());
    }

    @Test
    void withoutAnEnabledProcessingRuleTheDropIsAllowedButWarnsThatDispatchPauses() {
        var hint = DropHint.of("free", false, false);
        assertEquals(DropHint.NO_RULE, hint);
        assertTrue(hint.accepts());
        assertEquals(DropHint.Tone.WARN, hint.tone());
        assertEquals(DropHint.OCCUPIED, DropHint.of("occupied", false, false));
        assertEquals(DropHint.EXISTING, DropHint.of("in_use", true, false));
    }
}
