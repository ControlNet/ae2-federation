package space.controlnet.ae2federation.client.menu;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActionRequestProgressTest {
    @Test void acceptedReplyWaitsForNewerState() {
        var progress = new ActionRequestProgress();
        var id = UUID.randomUUID();
        assertTrue(progress.begin(id, 4));
        assertFalse(progress.begin(UUID.randomUUID(), 4));
        progress.reply(id, 4, FederationDomainPolicyActionResult.ACCEPTED);
        progress.observe(4);
        assertTrue(progress.pending());
        progress.observe(5);
        assertFalse(progress.pending());
    }

    @Test void stateBeforeReplyStillWaitsForMatchingReply() {
        var progress = new ActionRequestProgress();
        var id = UUID.randomUUID();
        progress.begin(id, 4);
        progress.observe(5);
        assertTrue(progress.pending());
        progress.reply(UUID.randomUUID(), 4, FederationDomainPolicyActionResult.ACCEPTED);
        assertTrue(progress.pending());
        progress.reply(id, 4, FederationDomainPolicyActionResult.ACCEPTED);
        assertFalse(progress.pending());
    }

    @Test void rejectionUnblocksWithoutSequenceAdvanceAndOldReplyCannotFinishRetry() {
        var progress = new ActionRequestProgress();
        var first = UUID.randomUUID();
        progress.begin(first, 4);
        progress.reply(first, 4, FederationDomainPolicyActionResult.INVALID_TARGET);
        assertFalse(progress.pending());
        assertEquals(FederationDomainPolicyActionResult.INVALID_TARGET, progress.rejection());
        var retry = UUID.randomUUID();
        assertTrue(progress.begin(retry, 4));
        assertNull(progress.rejection());
        progress.reply(first, 4, FederationDomainPolicyActionResult.INVALID_TARGET);
        progress.reply(retry, 3, FederationDomainPolicyActionResult.INVALID_TARGET);
        assertTrue(progress.pending());
        progress.reply(retry, 4, FederationDomainPolicyActionResult.INVALID_TARGET);
        assertFalse(progress.pending());
    }
}
