package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

final class PolicyEditorSessionStateTest {
    @Test
    void staleContextRejectionIsTerminalForSelectorActions() {
        var state = PolicyEditorSessionState.ready();

        state.reject(PolicyEditorSessionState.Status.STALE_CONTEXT);

        assertFalse(state.selectionChanged());
        assertFalse(state.editingAllowed());
        assertEquals(PolicyEditorSessionState.Status.STALE_CONTEXT, state.status());
    }

    @Test
    void staleRevisionRejectionIsTerminalForSelectorActions() {
        var state = PolicyEditorSessionState.ready();

        state.reject(PolicyEditorSessionState.Status.STALE_REVISION);

        assertFalse(state.selectionChanged());
        assertFalse(state.editingAllowed());
        assertEquals(PolicyEditorSessionState.Status.STALE_REVISION, state.status());
    }
}
