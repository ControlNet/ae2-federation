package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DropHintTest {
    @Test
    void aFreeEndpointAcceptsTheDropAndWillBeClaimed() {
        var hint = DropHint.of("free", false);
        assertEquals(DropHint.CLAIM, hint);
        assertTrue(hint.accepts());
    }

    @Test
    void anEndpointThisProviderHoldsAcceptsWithoutANewClaim() {
        assertEquals(DropHint.ACCEPT, DropHint.of("in_use", false));
        assertEquals(DropHint.ACCEPT, DropHint.of("retained", false));
    }

    @Test
    void theSameWireTwiceIsRefusedBeforeOwnership() {
        assertEquals(DropHint.EXISTING, DropHint.of("in_use", true));
        assertFalse(DropHint.EXISTING.accepts());
    }

    @Test
    void refusalsFollowTheClaim() {
        assertEquals(DropHint.OCCUPIED, DropHint.of("occupied", false));
        assertEquals(DropHint.LOCAL, DropHint.of("local", false));
        assertEquals(DropHint.UNLOADED, DropHint.of("unobserved", false));
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
    void noRuleIsNeededToMapAnEndpointOfTheDomain() {
        // Every Provider of the domain may use its Endpoints; only the claim and the wire decide, so no hint warns.
        assertEquals(java.util.List.of("claim", "accept", "existing", "occupied", "local", "unloaded"),
                java.util.Arrays.stream(DropHint.values()).map(DropHint::code).toList());
    }
}
