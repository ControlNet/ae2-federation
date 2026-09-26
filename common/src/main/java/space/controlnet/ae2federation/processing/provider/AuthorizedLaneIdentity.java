package space.controlnet.ae2federation.processing.provider;

import java.util.Objects;
import space.controlnet.ae2federation.processing.claim.ClaimEpoch;
import space.controlnet.ae2federation.processing.claim.EndpointIdentity;

public record AuthorizedLaneIdentity(ProviderLaneIdentity lane, EndpointIdentity endpoint,
        ClaimEpoch claimEpoch, long modeGeneration) {
    public AuthorizedLaneIdentity {
        Objects.requireNonNull(lane);
        Objects.requireNonNull(endpoint);
        Objects.requireNonNull(claimEpoch);
        if (modeGeneration < 1) {
            throw new IllegalArgumentException("Authorized Lane mode generation must be positive");
        }
    }
}
