package space.controlnet.ae2federation.client.policy;

final class PolicyEditorSessionState {
    enum Status {
        READY,
        PENDING,
        DISABLED,
        ACCEPTED,
        STALE_CONTEXT,
        STALE_REVISION,
        /** One switched rule changed elsewhere; the session stays editable and shows the current rule. */
        CONFLICT
    }

    private Status status;
    private boolean editingAllowed;

    PolicyEditorSessionState(Status status, boolean editingAllowed) {
        this.status = status;
        this.editingAllowed = editingAllowed;
    }

    static PolicyEditorSessionState ready() {
        return new PolicyEditorSessionState(Status.READY, true);
    }

    Status status() {
        return status;
    }

    boolean editingAllowed() {
        return editingAllowed;
    }

    boolean selectionChanged() {
        if (!editingAllowed) {
            return false;
        }
        status = Status.READY;
        return true;
    }

    void accepted() {
        status = Status.ACCEPTED;
    }

    void conflicted() {
        if (editingAllowed) status = Status.CONFLICT;
    }

    void reject(Status rejectedStatus) {
        if (rejectedStatus != Status.STALE_CONTEXT && rejectedStatus != Status.STALE_REVISION) {
            throw new IllegalArgumentException("Only stale states can reject an editor session");
        }
        status = rejectedStatus;
        editingAllowed = false;
    }
}
