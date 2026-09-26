package space.controlnet.ae2federation.processing.endpoint;

import appeng.api.networking.IGridNode;
import java.util.Optional;
import space.controlnet.ae2federation.processing.claim.ClaimState;
import space.controlnet.ae2federation.processing.claim.EndpointIdentity;
import space.controlnet.ae2federation.processing.claim.ClaimEpoch;
import space.controlnet.ae2federation.processing.provider.ProviderIdentity;

public interface EndpointTargetAccess {
    EndpointIdentity endpointIdentity();

    ClaimState claimState();

    IGridNode subnetNode();

    Optional<EndpointModeGeneration.Federated> federatedMode(ProviderIdentity provider, ClaimEpoch claimEpoch);
}
