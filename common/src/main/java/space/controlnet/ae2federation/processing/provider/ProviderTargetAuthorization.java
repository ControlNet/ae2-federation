package space.controlnet.ae2federation.processing.provider;

import appeng.api.AECapabilities;
import appeng.api.networking.GridHelper;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.processing.claim.EndpointOwnerIdentity;
import space.controlnet.ae2federation.processing.claim.NativeTargetSeparation;
import space.controlnet.ae2federation.processing.endpoint.EndpointTargetCapability;

public final class ProviderTargetAuthorization {
    private ProviderTargetAuthorization() {
    }

    public static ProviderTargetResolution resolve(ProviderAuthorizationContext context) {
        var request = context.request();
        if (!request.rotationSettled()) {
            return paused(ProviderTargetState.ROTATION_PENDING);
        }
        if (!context.provenance().lane().provider().equals(request.provider())) {
            return paused(ProviderTargetState.CLAIM_MISMATCH);
        }
        var level = context.level();
        var position = request.endpointPosition();
        if (!level.isLoaded(position) || !level.isLoaded(position.relative(request.endpointSide()))) {
            return paused(ProviderTargetState.ENDPOINT_OFFLINE);
        }
        var endpoint = level.getCapability(EndpointTargetCapability.BLOCK, position, request.endpointSide());
        if (endpoint == null || !endpoint.endpointIdentity().equals(request.endpoint())) {
            return paused(ProviderTargetState.NATIVE_TARGET_UNAVAILABLE);
        }
        var claim = endpoint.claimState();
        var expectedOwner = new EndpointOwnerIdentity(request.provider());
        var mode = endpoint.federatedMode(request.provider(), request.claimEpoch());
        if (!claim.key().endpoint().equals(request.endpoint()) || !claim.epoch().equals(request.claimEpoch())
                || claim.owner().filter(expectedOwner::equals).isEmpty() || mode.isEmpty()) {
            return paused(ProviderTargetState.CLAIM_MISMATCH);
        }
        var targetNode = endpoint.subnetNode();
        var exposed = GridHelper.getExposedNode(level, position, request.endpointSide());
        var storage = level.getCapability(AECapabilities.ME_STORAGE, position, request.endpointSide());
        if (exposed != targetNode || storage == null || targetNode.getGrid() == null
                || storage != targetNode.getGrid().getStorageService().getInventory()) {
            return paused(ProviderTargetState.NATIVE_TARGET_UNAVAILABLE);
        }
        var sourceGrid = context.sourceNode().getGrid();
        var targetGrid = targetNode.getGrid();
        var separated = NativeTargetSeparation.classify(sourceGrid, targetGrid, true);
        if (separated != ProviderTargetState.ACTIVE) {
            return paused(separated);
        }
        var sourceId = FederationDomainRegistryAccess.confirmedNetworkId(sourceGrid);
        if (sourceId.isEmpty()) {
            return paused(ProviderTargetState.IDENTITY_UNSETTLED);
        }
        var overlap = context.domains().observe(request.endpoint(), targetGrid).state(request.endpoint());
        if (overlap != ProviderTargetState.ACTIVE) {
            return paused(overlap);
        }
        // No rule: a Provider may use every Endpoint of the domain its own Federation face joins. Its network being a
        // member of that domain through another route is not enough.
        var domain = FederationDomainRegistryAccess.get(level)
                .federationDomainOf(FederationDomainRegistryAccess.nodeId(level, position));
        if (domain.isEmpty() || !domain.orElseThrow().nodes().contains(context.federationFace())
                || !domain.orElseThrow().memberships().containsKey(sourceId.orElseThrow())) {
            return paused(ProviderTargetState.FEDERATION_DOMAIN_DISCONNECTED);
        }
        return new ProviderTargetResolution.Authorized(new AuthorizedNativeTarget(level, position,
                request.endpointSide(), mode.orElseThrow(), context.provenance()));
    }

    private static ProviderTargetResolution paused(ProviderTargetState state) {
        return new ProviderTargetResolution.Paused(state);
    }
}
