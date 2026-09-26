package space.controlnet.ae2federation.test.processing;

import appeng.api.networking.GridHelper;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.FederationDomainSourceId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.processing.claim.ClaimEpoch;
import space.controlnet.ae2federation.processing.claim.ClaimRequest;
import space.controlnet.ae2federation.processing.claim.ClaimState;
import space.controlnet.ae2federation.processing.claim.EndpointClaimAuthority;
import space.controlnet.ae2federation.processing.claim.EndpointIdentity;
import space.controlnet.ae2federation.processing.claim.EndpointOwnerIdentity;
import space.controlnet.ae2federation.processing.claim.NativeTargetDomainRegistry;
import space.controlnet.ae2federation.processing.endpoint.EndpointBlockEntity;
import space.controlnet.ae2federation.processing.endpoint.EndpointTargetBinding;
import space.controlnet.ae2federation.processing.endpoint.EndpointTargetCapability;
import space.controlnet.ae2federation.processing.provider.ProviderFace;
import space.controlnet.ae2federation.processing.provider.ProviderIdentity;
import space.controlnet.ae2federation.processing.provider.ProviderOrientation;
import space.controlnet.ae2federation.processing.provider.ProviderRuntime;
import space.controlnet.ae2federation.processing.provider.ProviderTargetRequest;
import space.controlnet.ae2federation.processing.provider.ProviderTargetState;
import space.controlnet.ae2federation.test.processing.endpoint.EndpointPersistenceObservation;

final class ProviderTargetLifecycle implements AutoCloseable {
    private static final Direction ENDPOINT_SIDE = Direction.WEST;
    private final GameTestHelper helper;
    private final NativeProviderLaneFixtures provider;
    private final ProviderIdentity providerIdentity;
    private final EndpointIdentity endpointIdentity;
    private final EndpointClaimAuthority fixtureClaims;
    private final boolean productionEndpoint;
    private final NativeTargetDomainRegistry domains = new NativeTargetDomainRegistry();
    private final AtomicReference<ProviderTargetRequest> request;
    private final FederationDomainSourceId federationDomainSource;
    private EndpointTargetBinding endpointBinding;
    private ProviderRuntime runtime;
    private boolean registered;
    private boolean federationDomainConnected;
    private String status = "created";

    ProviderTargetLifecycle(GameTestHelper helper, NativeProviderLaneFixtures provider, boolean productionEndpoint,
            ProviderIdentity providerIdentity) {
        this.helper = helper;
        this.provider = provider;
        this.productionEndpoint = productionEndpoint;
        this.providerIdentity = providerIdentity;
        if (productionEndpoint) {
            provider.installFederationEndpointTarget();
        } else {
            provider.installEndpointTarget();
        }
        endpointIdentity = productionEndpoint ? productionEndpoint().endpointIdentity() : EndpointIdentity.create();
        fixtureClaims = productionEndpoint ? null : new EndpointClaimAuthority(endpointIdentity);
        var claim = new ClaimRequest(endpointIdentity, ClaimEpoch.NONE, new EndpointOwnerIdentity(providerIdentity));
        if (productionEndpoint) {
            productionEndpoint().claim(claim);
            productionEndpoint().activateFederated();
        } else {
            fixtureClaims.compareAndSet(claim);
        }
        request = new AtomicReference<>(new ProviderTargetRequest(providerIdentity, endpointIdentity,
                claimState().epoch(), provider.endpointTargetPosition(), ENDPOINT_SIDE, true));
        federationDomainSource = new FederationDomainSourceId("task17:" + endpointIdentity.id().value());
    }

    boolean initialize() {
        if (!provider.connectEnergy() || !provider.composition().isActive()) {
            status = "source-not-active";
            return false;
        }
        var targetNode = provider.endpointTargetNode();
        if (targetNode == null || !targetNode.isActive()) {
            status = "target-not-active";
            return false;
        }
        if (FederationDomainRegistryAccess.confirmedNetworkId(sourceGrid()).isEmpty()) {
            status = "source-identity-pending";
            return false;
        }
        if (FederationDomainRegistryAccess.confirmedNetworkId(targetGrid()).isEmpty()) {
            status = "target-identity-unsettled";
            return false;
        }
        if (productionEndpoint) {
            endpointBinding = productionEndpoint().binding();
            if (endpointBinding == null) {
                status = "endpoint-binding-pending";
                return false;
            }
        }
        if (runtime == null) {
            if (!productionEndpoint) {
                endpointBinding = new EndpointTargetBinding(helper.getLevel(), provider.endpointTargetPosition(),
                        ENDPOINT_SIDE, fixtureClaims, targetNode);
            }
            runtime = new ProviderRuntime(helper.getLevel(), provider.managedNode(), provider.composition(),
                    providerIdentity, new ProviderOrientation(ProviderFace.EAST), request::get, domains);
            runtime.settle();
            provider.installPatterns(1);
        }
        if (!registered) {
            provider.register();
            registered = true;
        }
        var ready = helper.getLevel().getCapability(EndpointTargetCapability.BLOCK,
                provider.endpointTargetPosition(), ENDPOINT_SIDE) == endpointBinding;
        status = ready ? "ready" : "endpoint-capability-missing";
        return ready;
    }

    String status() { return status; }

    boolean enablePolicy(Set<PolicyOperation> operations) {
        var service = PolicyService.get(helper.getLevel());
        var key = new PolicyKey(sourceNetwork(), targetNetwork(), PolicyCapability.PROCESSING);
        return service.edit(new PolicyEdit(key, service.revision(key), PolicyRule.enabled(operations)))
                instanceof PolicyMutationResult.Accepted;
    }

    void connectFederationDomain() {
        FederationDomainRegistryAccess.get(helper.getLevel()).upsertDirectBridge(federationDomainSource, sourceNetwork(), targetNetwork());
        federationDomainConnected = true;
    }

    void disconnectFederationDomain() {
        FederationDomainRegistryAccess.get(helper.getLevel()).invalidateDirectBridge(federationDomainSource);
        federationDomainConnected = false;
    }

    ProviderRuntime runtime() { return runtime; }

    EndpointTargetBinding endpointBinding() { return endpointBinding; }

    ProviderTargetState state() { return runtime.lastResolution().state(); }

    void useClaimEpoch(ClaimEpoch epoch) {
        updateRequest(epoch, request.get().endpointPosition(), request.get().rotationSettled());
    }

    void useUnloadedTarget() {
        updateRequest(request.get().claimEpoch(), new BlockPos(20_000_000, 0, 20_000_000), true);
    }

    void restoreTargetRequest() { updateRequest(claimState().epoch(), provider.endpointTargetPosition(), true); }

    void unbindEndpoint() {
        endpointBinding.close();
        endpointBinding = null;
    }

    void overlapWith(EndpointIdentity other) { domains.observe(other, targetGrid()); }

    void joinSourceAndTargetGrids() {
        GridHelper.createConnection(provider.managedNode().getNode(), provider.endpointTargetNode());
    }

    ProviderEndpointPersistence.ReloadedEndpoint reloadProductionEndpoint() {
        if (!productionEndpoint) {
            throw new IllegalStateException("A production Endpoint with an owned Claim is required");
        }
        var reloaded = ProviderEndpointPersistence.reload(helper, provider, endpointBinding, productionEndpoint());
        endpointBinding = null;
        return reloaded;
    }

    EndpointPersistenceObservation.Snapshot finishPersistenceObservation() {
        return EndpointPersistenceObservation.finish(productionEndpoint());
    }

    EndpointBlockEntity productionEndpointEntity() {
        if (!productionEndpoint) {
            throw new IllegalStateException("This fixture does not use a production Endpoint");
        }
        return productionEndpoint();
    }

    boolean targetCapabilityIsCurrentBinding() {
        return helper.getLevel().getCapability(EndpointTargetCapability.BLOCK, provider.endpointTargetPosition(),
                ENDPOINT_SIDE) == endpointBinding;
    }

    EndpointIdentity endpointIdentity() { return endpointIdentity; }

    EndpointClaimAuthority claims() {
        if (fixtureClaims == null) {
            throw new IllegalStateException("Production Endpoint owns its Claim authority");
        }
        return fixtureClaims;
    }

    ProviderIdentity providerIdentity() { return providerIdentity; }

    void rotate(ProviderFace face) { runtime.rotate(new ProviderOrientation(face)); }

    void settleRotation() { runtime.settle(); }

    private void updateRequest(ClaimEpoch epoch, BlockPos position, boolean rotationSettled) {
        request.set(new ProviderTargetRequest(providerIdentity, endpointIdentity, epoch, position, ENDPOINT_SIDE,
                rotationSettled));
    }

    private ClaimState claimState() {
        return productionEndpoint ? productionEndpoint().claimState() : fixtureClaims.state();
    }

    private EndpointBlockEntity productionEndpoint() {
        return (EndpointBlockEntity) helper.getLevel().getBlockEntity(provider.endpointTargetPosition());
    }

    private appeng.api.networking.IGrid sourceGrid() { return provider.managedNode().getGrid(); }

    private appeng.api.networking.IGrid targetGrid() { return provider.endpointTargetNode().getGrid(); }

    private space.controlnet.ae2federation.identity.NetworkId sourceNetwork() {
        return FederationDomainRegistryAccess.confirmedNetworkId(sourceGrid()).orElseThrow();
    }

    private space.controlnet.ae2federation.identity.NetworkId targetNetwork() {
        return FederationDomainRegistryAccess.confirmedNetworkId(targetGrid()).orElseThrow();
    }

    @Override
    public void close() {
        if (runtime != null) {
            ProviderRuntimeReplayControl.clear(runtime);
        }
        if (endpointBinding != null && !productionEndpoint) {
            endpointBinding.close();
        }
        if (federationDomainConnected) {
            disconnectFederationDomain();
        }
        EndpointPersistenceObservation.clear();
    }

}
