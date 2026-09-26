package space.controlnet.ae2federation.processing.claim;

import java.util.Objects;

public record ClaimKey(EndpointIdentity endpoint) {
    public ClaimKey {
        Objects.requireNonNull(endpoint);
    }
}
