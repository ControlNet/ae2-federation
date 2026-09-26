package space.controlnet.ae2federation.processing.claim;

import java.util.Objects;

public record ClaimRequest(EndpointIdentity endpoint, ClaimEpoch expectedEpoch,
        EndpointOwnerIdentity requestedOwner) {
    public ClaimRequest {
        Objects.requireNonNull(endpoint);
        Objects.requireNonNull(expectedEpoch);
        Objects.requireNonNull(requestedOwner);
    }
}
