package space.controlnet.ae2federation.test.processing;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import space.controlnet.ae2federation.ae2.processing.ProviderTargetTrace;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.fabric.FabricSourceId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.processing.claim.ClaimEpoch;
import space.controlnet.ae2federation.processing.claim.ClaimRequest;
import space.controlnet.ae2federation.processing.claim.EndpointClaimAuthority;
import space.controlnet.ae2federation.processing.claim.EndpointIdentity;
import space.controlnet.ae2federation.processing.claim.EndpointOwnerIdentity;
import space.controlnet.ae2federation.processing.claim.NativeTargetDomainRegistry;
import space.controlnet.ae2federation.processing.endpoint.EndpointTargetBinding;
import space.controlnet.ae2federation.processing.endpoint.EndpointTargetCapability;
import space.controlnet.ae2federation.processing.provider.ProviderFace;
import space.controlnet.ae2federation.processing.provider.ProviderIdentity;
import space.controlnet.ae2federation.processing.provider.ProviderOrientation;
import space.controlnet.ae2federation.processing.provider.ProviderRuntime;
import space.controlnet.ae2federation.processing.provider.ProviderTargetRequest;
import space.controlnet.ae2federation.processing.provider.ProviderTargetState;

public final class ProviderTargetRuntimeFixtures implements AutoCloseable {
    private static final Direction ENDPOINT_SIDE = Direction.WEST;
    private final GameTestHelper helper;
    private final NativeProviderLaneFixtures provider;
    private final ProviderIdentity providerIdentity = ProviderIdentity.create();
    private final EndpointIdentity endpointIdentity = EndpointIdentity.create();
    private final EndpointClaimAuthority claims = new EndpointClaimAuthority(endpointIdentity);
    private final NativeTargetDomainRegistry domains = new NativeTargetDomainRegistry();
    private final AtomicReference<ProviderTargetRequest> request;
    private final FabricSourceId fabricSource;
    private EndpointTargetBinding endpointBinding;
    private ProviderRuntime runtime;
    private boolean registered;
    private boolean fabricConnected;
    private Boolean acceptedPush;
    private String status = "created";

    public ProviderTargetRuntimeFixtures(GameTestHelper helper) {
        this.helper = helper;
        ProviderTargetTrace.reset();
        provider = new NativeProviderLaneFixtures(helper, NativeProviderLaneFixtures.sharedPatternAssignments(), true);
        provider.installEndpointTarget();
        claims.compareAndSet(new ClaimRequest(endpointIdentity, ClaimEpoch.NONE,
                new EndpointOwnerIdentity(providerIdentity)));
        request = new AtomicReference<>(new ProviderTargetRequest(providerIdentity, endpointIdentity,
                claims.state().epoch(), provider.endpointTargetPosition(), ENDPOINT_SIDE, true));
        fabricSource = new FabricSourceId("task17:" + endpointIdentity.id().value());
    }

    public boolean initialize() {
        if (!provider.connectEnergy() || !provider.composition().isActive()) {
            status = "source-not-active";
            return false;
        }
        var targetNode = provider.endpointTargetNode();
        if (targetNode == null || !targetNode.isActive()) {
            status = "target-not-active";
            return false;
        }
        if (FabricRegistryAccess.confirmedNetworkId(sourceGrid()).isEmpty()) {
            status = "source-identity-unsettled";
            return false;
        }
        if (FabricRegistryAccess.confirmedNetworkId(targetGrid()).isEmpty()) {
            status = "target-identity-unsettled";
            return false;
        }
        if (runtime == null) {
            endpointBinding = new EndpointTargetBinding(helper.getLevel(), provider.endpointTargetPosition(),
                    ENDPOINT_SIDE, claims, targetNode);
            runtime = new ProviderRuntime(helper.getLevel(), provider.managedNode(), provider.composition(),
                    providerIdentity, new ProviderOrientation(ProviderFace.EAST), request::get, domains);
            runtime.settle();
            provider.installPatterns(1);
        }
        if (!registered) {
            provider.register();
            registered = true;
        }
        var capabilityReady = helper.getLevel().getCapability(EndpointTargetCapability.BLOCK,
                provider.endpointTargetPosition(), ENDPOINT_SIDE) == endpointBinding;
        status = capabilityReady ? "ready" : "endpoint-capability-missing";
        return capabilityReady;
    }

    public String status() {
        return status;
    }

    public boolean enablePolicy(Set<PolicyOperation> operations) {
        var service = PolicyService.get(helper.getLevel());
        var result = service.edit(new PolicyEdit(policyKey(), service.revision(policyKey()),
                PolicyRule.enabled(operations)));
        return result instanceof PolicyMutationResult.Accepted;
    }

    public void connectFabric() {
        FabricRegistryAccess.get(helper.getLevel()).upsertDirectBridge(fabricSource, sourceNetwork(), targetNetwork());
        fabricConnected = true;
    }

    public void disconnectFabric() {
        FabricRegistryAccess.get(helper.getLevel()).invalidateDirectBridge(fabricSource);
        fabricConnected = false;
    }

    public boolean push() {
        return provider.push(0, 0);
    }

    public boolean pushOnce() {
        if (acceptedPush == null) {
            acceptedPush = push();
        }
        return acceptedPush;
    }

    public ProviderTargetState state() {
        return runtime.lastResolution().state();
    }

    public void useClaimEpoch(ClaimEpoch epoch) {
        updateRequest(epoch, request.get().endpointPosition(), request.get().rotationSettled());
    }

    public void useUnloadedTarget() {
        updateRequest(request.get().claimEpoch(), new BlockPos(20_000_000, 0, 20_000_000), true);
    }

    public void restoreTargetRequest() {
        updateRequest(claims.state().epoch(), provider.endpointTargetPosition(), true);
    }

    public void unbindEndpoint() {
        endpointBinding.close();
        endpointBinding = null;
    }

    public void overlapWith(EndpointIdentity other) {
        domains.observe(other, targetGrid());
    }

    public void joinSourceAndTargetGrids() {
        GridHelper.createConnection(provider.managedNode().getNode(), provider.endpointTargetNode());
    }

    public void rotate(ProviderFace face) {
        runtime.rotate(new ProviderOrientation(face));
    }

    public void settleRotation() {
        runtime.settle();
    }

    public ProviderRuntime runtime() {
        return runtime;
    }

    public ProviderIdentity providerIdentity() {
        return providerIdentity;
    }

    public EndpointIdentity endpointIdentity() {
        return endpointIdentity;
    }

    public EndpointClaimAuthority claims() {
        return claims;
    }

    public Object nativeRemainderDestination() {
        return provider.lane(0).getReturnInv();
    }

    public long targetItemCount() {
        return provider.endpointTargetItemCount();
    }

    public int bindingCount() {
        return ProviderTargetTrace.bindings();
    }

    public int capabilityLookupCount() {
        return ProviderTargetTrace.capabilityLookups();
    }

    public int mixinLookupCount() {
        return ProviderTargetTrace.mixinLookups();
    }

    public int nativeTargetLookupCount() {
        return ProviderTargetTrace.nativeTargetLookups();
    }

    public int authorizationCount(ProviderTargetState state) {
        return ProviderTargetTrace.authorizations(state);
    }

    public IGrid sourceGrid() {
        return provider.managedNode().getGrid();
    }

    public IGrid targetGrid() {
        return provider.endpointTargetNode().getGrid();
    }

    private PolicyKey policyKey() {
        return new PolicyKey(sourceNetwork(), targetNetwork(), PolicyCapability.PROCESSING);
    }

    private space.controlnet.ae2federation.identity.NetworkId sourceNetwork() {
        return FabricRegistryAccess.confirmedNetworkId(sourceGrid()).orElseThrow();
    }

    private space.controlnet.ae2federation.identity.NetworkId targetNetwork() {
        return FabricRegistryAccess.confirmedNetworkId(targetGrid()).orElseThrow();
    }

    private void updateRequest(ClaimEpoch epoch, BlockPos position, boolean rotationSettled) {
        request.set(new ProviderTargetRequest(providerIdentity, endpointIdentity, epoch, position, ENDPOINT_SIDE,
                rotationSettled));
    }

    @Override
    public void close() {
        if (endpointBinding != null) {
            endpointBinding.close();
        }
        if (fabricConnected) {
            disconnectFabric();
        }
        provider.close();
    }
}
