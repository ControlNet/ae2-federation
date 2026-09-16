package space.controlnet.ae2federation.processing.provider;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.processing.endpoint.EndpointModeGeneration;

public record AuthorizedNativeTarget(ServerLevel level, BlockPos position, Direction side,
        EndpointModeGeneration.Federated mode, ProviderLogicProvenance provenance) {
    public AuthorizedNativeTarget {
        Objects.requireNonNull(level);
        position = Objects.requireNonNull(position).immutable();
        Objects.requireNonNull(side);
        Objects.requireNonNull(mode);
        Objects.requireNonNull(provenance);
    }

    public space.controlnet.ae2federation.processing.claim.EndpointIdentity endpoint() {
        return mode.endpoint();
    }

    public space.controlnet.ae2federation.processing.claim.ClaimEpoch claimEpoch() {
        return mode.claimEpoch();
    }

    public ProviderIdentity provider() {
        return mode.owner().provider();
    }

    public AuthorizedLaneIdentity laneIdentity() {
        return new AuthorizedLaneIdentity(provenance.lane(), endpoint(), claimEpoch(), mode.generation());
    }
}
