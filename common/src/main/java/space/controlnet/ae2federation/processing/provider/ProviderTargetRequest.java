package space.controlnet.ae2federation.processing.provider;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.processing.claim.ClaimEpoch;
import space.controlnet.ae2federation.processing.claim.EndpointIdentity;

/**
 * Where a Lane pushes: the Endpoint's position in {@code endpointDimension}, or in the Provider's own level when that is
 * null. A domain can span dimensions, so the Endpoint may be in another one.
 */
public record ProviderTargetRequest(ProviderIdentity provider, EndpointIdentity endpoint, ClaimEpoch claimEpoch,
        BlockPos endpointPosition, Direction endpointSide, boolean rotationSettled,
        @Nullable ResourceKey<Level> endpointDimension) {
    /** An Endpoint in the Provider's own level. */
    public ProviderTargetRequest(ProviderIdentity provider, EndpointIdentity endpoint, ClaimEpoch claimEpoch,
            BlockPos endpointPosition, Direction endpointSide, boolean rotationSettled) {
        this(provider, endpoint, claimEpoch, endpointPosition, endpointSide, rotationSettled, null);
    }

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
