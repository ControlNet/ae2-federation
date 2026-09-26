package space.controlnet.ae2federation.processing.provider;

import appeng.api.AECapabilities;
import appeng.api.networking.GridHelper;
import java.util.Set;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.policy.BackendStatus;
import space.controlnet.ae2federation.policy.PolicyActivationState;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRuntimeEndpoints;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.processing.claim.EndpointOwnerIdentity;
import space.controlnet.ae2federation.processing.claim.NativeTargetSeparation;
import space.controlnet.ae2federation.processing.endpoint.EndpointTargetCapability;

public final class ProviderTargetAuthorization {
    private static final Set<PolicyOperation> REQUIRED_OPERATIONS = Set.of(PolicyOperation.EXECUTE,
            PolicyOperation.SUPPLY);

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
        var targetId = FederationDomainRegistryAccess.confirmedNetworkId(targetGrid);
        if (sourceId.isEmpty() || targetId.isEmpty()) {
            return paused(ProviderTargetState.IDENTITY_UNSETTLED);
        }
        var overlap = context.domains().observe(request.endpoint(), targetGrid).state(request.endpoint());
        if (overlap != ProviderTargetState.ACTIVE) {
            return paused(overlap);
        }
        var key = new PolicyKey(sourceId.orElseThrow(), targetId.orElseThrow(), PolicyCapability.PROCESSING);
        var service = PolicyService.get(level);
        var configured = service.configured(key);
        if (configured.isEmpty() || !configured.orElseThrow().rule().operations().containsAll(REQUIRED_OPERATIONS)) {
            return paused(ProviderTargetState.POLICY_DENIED);
        }
        var activation = service.activation(key, new PolicyRuntimeEndpoints(sourceGrid, targetGrid, BackendStatus.READY));
        if (activation == PolicyActivationState.OFF || activation == PolicyActivationState.UNCONFIGURED) {
            return paused(ProviderTargetState.POLICY_DENIED);
        }
        if (activation != PolicyActivationState.ACTIVE) {
            return paused(ProviderTargetState.FEDERATION_DOMAIN_DISCONNECTED);
        }
        return new ProviderTargetResolution.Authorized(new AuthorizedNativeTarget(level, position,
                request.endpointSide(), mode.orElseThrow(), context.provenance()));
    }

    private static ProviderTargetResolution paused(ProviderTargetState state) {
        return new ProviderTargetResolution.Paused(state);
    }
}
