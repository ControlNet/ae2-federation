package space.controlnet.ae2federation.processing.provider;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.processing.claim.ClaimEpoch;
import space.controlnet.ae2federation.processing.claim.EndpointIdentity;

public record AuthorizedNativeTarget(ServerLevel level, BlockPos position, Direction side,
        EndpointIdentity endpoint, ClaimEpoch claimEpoch) {
    public AuthorizedNativeTarget {
        Objects.requireNonNull(level);
        position = Objects.requireNonNull(position).immutable();
        Objects.requireNonNull(side);
        Objects.requireNonNull(endpoint);
        Objects.requireNonNull(claimEpoch);
    }
}
