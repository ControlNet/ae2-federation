package space.controlnet.ae2federation.processing.claim;

import java.util.Objects;

public final class EndpointClaimAuthority {
    private final EndpointIdentity endpoint;
    private ClaimState state;
    private String lastResultCode = "NONE";
    private boolean online = true;

    public EndpointClaimAuthority(EndpointIdentity endpoint) {
        this(endpoint, new ClaimState.Unclaimed(new ClaimKey(endpoint), ClaimEpoch.NONE));
    }

    public EndpointClaimAuthority(EndpointIdentity endpoint, ClaimState restored) {
        this.endpoint = Objects.requireNonNull(endpoint);
        state = Objects.requireNonNull(restored);
        if (!restored.key().endpoint().equals(endpoint)) {
            throw new IllegalArgumentException("Restored Claim belongs to another Endpoint");
        }
    }

    public synchronized ClaimResult compareAndSet(ClaimRequest request) {
        ClaimResult result;
        if (!request.endpoint().equals(endpoint)) {
            result = new ClaimResult.Rejected(state, ClaimRejection.WRONG_ENDPOINT);
        } else if (!request.expectedEpoch().equals(state.epoch())) {
            result = new ClaimResult.Rejected(state, ClaimRejection.STALE_EPOCH);
        } else if (state instanceof ClaimState.Owned owned) {
            result = owned.ownerIdentity().equals(request.requestedOwner())
                    ? new ClaimResult.Retained(owned)
                    : new ClaimResult.Rejected(state, ClaimRejection.OWNER_CONFLICT);
        } else {
            var acquired = new ClaimState.Owned(state.key(), state.epoch().next(), request.requestedOwner());
            state = acquired;
            result = new ClaimResult.Acquired(acquired);
        }
        lastResultCode = result instanceof ClaimResult.Rejected rejected
                ? rejected.reason().name() : result.getClass().getSimpleName().toUpperCase(java.util.Locale.ROOT);
        return result;
    }

    public synchronized ClaimState state() {
        return state;
    }

    public EndpointIdentity endpoint() {
        return endpoint;
    }

    public synchronized String lastResultCode() {
        return lastResultCode;
    }

    public synchronized EndpointClaimAuthority withOnline(boolean value) {
        online = value;
        return this;
    }

    public synchronized boolean online() {
        return online;
    }
}
