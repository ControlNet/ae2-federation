package space.controlnet.ae2federation.client.menu;

import java.util.UUID;

/** Matches each reply to one request and waits for authoritative state after accepted dispatch. */
public final class ActionRequestProgress {
    private UUID requestId;
    private long sequence;
    private long observedSequence = -1;
    private boolean accepted;
    private FederationDomainPolicyActionResult rejection;

    public boolean begin(UUID id, long sequence) {
        if (pending()) return false;
        this.requestId = id;
        this.sequence = sequence;
        accepted = false;
        rejection = null;
        return true;
    }

    public void observe(long sequence) {
        observedSequence = sequence;
        finishAccepted();
    }

    public void reply(UUID id, long sequence, FederationDomainPolicyActionResult result) {
        if (requestId == null || !requestId.equals(id) || this.sequence != sequence) return;
        if (result == FederationDomainPolicyActionResult.ACCEPTED) {
            accepted = true;
            finishAccepted();
        } else {
            rejection = result;
            requestId = null;
        }
    }

    private void finishAccepted() {
        if (accepted && observedSequence > sequence) requestId = null;
    }

    public boolean pending() { return requestId != null; }
    public FederationDomainPolicyActionResult rejection() { return rejection; }
}
