package space.controlnet.ae2federation.processing.claim;

import java.util.Objects;

public final class EndpointClaimAuthority {
    private final EndpointIdentity endpoint;
    private ClaimState state;
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
        if (!request.endpoint().equals(endpoint)) {
            return new ClaimResult.Rejected(state, ClaimRejection.WRONG_ENDPOINT);
        }
        if (!request.expectedEpoch().equals(state.epoch())) {
            return new ClaimResult.Rejected(state, ClaimRejection.STALE_EPOCH);
        }
        if (state instanceof ClaimState.Owned owned) {
            return owned.ownerIdentity().equals(request.requestedOwner())
                    ? new ClaimResult.Retained(owned)
                    : new ClaimResult.Rejected(state, ClaimRejection.OWNER_CONFLICT);
        }
        var acquired = new ClaimState.Owned(state.key(), state.epoch().next(), request.requestedOwner());
        state = acquired;
        return new ClaimResult.Acquired(acquired);
    }

    public synchronized ClaimState state() {
        return state;
    }

    public EndpointIdentity endpoint() {
        return endpoint;
    }

    public synchronized EndpointClaimAuthority withOnline(boolean value) {
        online = value;
        return this;
    }

    public synchronized boolean online() {
        return online;
    }
}
