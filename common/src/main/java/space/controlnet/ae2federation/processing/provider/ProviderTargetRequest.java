package space.controlnet.ae2federation.processing.provider;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import space.controlnet.ae2federation.processing.claim.ClaimEpoch;
import space.controlnet.ae2federation.processing.claim.EndpointIdentity;

public record ProviderTargetRequest(ProviderIdentity provider, EndpointIdentity endpoint, ClaimEpoch claimEpoch,
        BlockPos endpointPosition, Direction endpointSide, boolean rotationSettled) {
    public ProviderTargetRequest {
        Objects.requireNonNull(provider);
        Objects.requireNonNull(endpoint);
        Objects.requireNonNull(claimEpoch);
        endpointPosition = Objects.requireNonNull(endpointPosition).immutable();
        Objects.requireNonNull(endpointSide);
        if (claimEpoch.equals(ClaimEpoch.NONE)) {
            throw new IllegalArgumentException("Provider target binding requires an owned Claim epoch");
        }
    }
}
