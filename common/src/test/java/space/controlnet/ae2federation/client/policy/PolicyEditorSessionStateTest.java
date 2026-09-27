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
    void switchConflictKeepsTheSessionEditable() {
        var state = PolicyEditorSessionState.ready();

        state.conflicted();

        org.junit.jupiter.api.Assertions.assertTrue(state.editingAllowed());
        assertEquals(PolicyEditorSessionState.Status.CONFLICT, state.status());
        state.reject(PolicyEditorSessionState.Status.STALE_CONTEXT);
        state.conflicted();
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
