package space.controlnet.ae2federation.processing.endpoint;

import appeng.api.networking.IGridNode;
import space.controlnet.ae2federation.processing.claim.ClaimState;
import space.controlnet.ae2federation.processing.claim.EndpointIdentity;

public interface EndpointTargetAccess {
    EndpointIdentity endpointIdentity();

    ClaimState claimState();

    IGridNode subnetNode();
}
