package space.controlnet.ae2federation.test.processing;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.fabric.FabricSourceId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
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
import space.controlnet.ae2federation.test.processing.endpoint.EndpointModeEvidence;
import space.controlnet.ae2federation.test.processing.endpoint.EndpointPersistenceObservation;

public final class ProviderTargetRuntimeFixtures implements AutoCloseable {
    private static final Direction ENDPOINT_SIDE = Direction.WEST;
    private final GameTestHelper helper;
    private final NativeProviderLaneFixtures provider;
    private final ProviderIdentity providerIdentity;
    private final EndpointIdentity endpointIdentity;
    private final EndpointClaimAuthority fixtureClaims;
    private final boolean productionEndpoint;
    private final NativeTargetDomainRegistry domains = new NativeTargetDomainRegistry();
    private final AtomicReference<ProviderTargetRequest> request;
    private final FabricSourceId fabricSource;
    private EndpointTargetBinding endpointBinding;
    private ProviderRuntime runtime;
    private boolean registered;
    private boolean fabricConnected;
    private final Boolean[] acceptedPushes = new Boolean[3];
    private String status = "created";

    public ProviderTargetRuntimeFixtures(GameTestHelper helper) {
        this(helper, false);
    }

    public ProviderTargetRuntimeFixtures(GameTestHelper helper, boolean federationEndpoint) {
        this(helper, federationEndpoint, ProviderIdentity.create());
    }

    public ProviderTargetRuntimeFixtures(GameTestHelper helper, boolean federationEndpoint,
            ProviderIdentity providerIdentity) {
        this.helper = helper;
        this.providerIdentity = Objects.requireNonNull(providerIdentity);
        productionEndpoint = federationEndpoint;
        ProviderTargetObservation.reset();
        provider = new NativeProviderLaneFixtures(helper, NativeProviderLaneFixtures.sharedPatternAssignments(), true);
        if (federationEndpoint) {
            provider.installFederationEndpointTarget();
        } else {
            provider.installEndpointTarget();
        }
        endpointIdentity = federationEndpoint ? productionEndpoint().endpointIdentity() : EndpointIdentity.create();
        fixtureClaims = federationEndpoint ? null : new EndpointClaimAuthority(endpointIdentity);
        var claimRequest = new ClaimRequest(endpointIdentity, ClaimEpoch.NONE,
                new EndpointOwnerIdentity(providerIdentity));
        if (federationEndpoint) {
            productionEndpoint().claim(claimRequest);
            productionEndpoint().activateFederated();
        } else {
            fixtureClaims.compareAndSet(claimRequest);
        }
        request = new AtomicReference<>(new ProviderTargetRequest(providerIdentity, endpointIdentity,
                claimState().epoch(), provider.endpointTargetPosition(), ENDPOINT_SIDE, true));
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
        return pushOnce(0);
    }

    public boolean pushOnce(int laneIndex) {
        if (acceptedPushes[laneIndex] == null) {
            acceptedPushes[laneIndex] = provider.push(laneIndex, 0);
        }
        return acceptedPushes[laneIndex];
    }

    public boolean pushLane(int laneIndex) {
        return provider.push(laneIndex, 0);
    }

    public boolean pushLaneWithInputs(int laneIndex, java.util.List<GenericStack> inputs) {
        return provider.pushInputs(laneIndex, 0, inputs);
    }

    public void installPattern(java.util.List<GenericStack> inputs, java.util.List<GenericStack> outputs) {
        provider.installPattern(0, inputs, outputs);
    }

    public void armAuthorizedReplay(int sourceLaneIndex, int targetLaneIndex) {
        ProviderRuntimeReplayControl.arm(runtime, providerLogic(sourceLaneIndex), providerLogic(targetLaneIndex),
                endpointBinding.runtime());
    }

    public ProviderRuntimeReplayControl.ReplayTrace finishAuthorizedReplay(String phase) {
        return ProviderRuntimeReplayControl.finish(runtime, endpointBinding.runtime(), phase);
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
        updateRequest(claimState().epoch(), provider.endpointTargetPosition(), true);
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

    public FederatedSavedState reloadProductionEndpoint() {
        if (!productionEndpoint || !(productionEndpoint().claimState() instanceof ClaimState.Owned owned)) {
            throw new IllegalStateException("A production Endpoint with an owned Claim is required");
        }
        var endpoint = productionEndpoint();
        var generation = endpointBinding.runtime().generation();
        var oldBindingIdentity = EndpointModeEvidence.identity(endpointBinding);
        EndpointPersistenceObservation.begin("endpointfederated", endpoint);
        var savedTag = endpoint.saveWithFullMetadata(helper.getLevel().registryAccess());
        var serialized = savedTag.toString();
        var runtimeReferencesSerialized = serialized.contains("EndpointTargetBinding")
                || serialized.contains("IGridNode") || serialized.contains("PatternProviderLogic")
                || serialized.contains("GenericStackItemStorage") || serialized.contains("GenericStackFluidStorage");
        endpoint.onChunkUnloaded();
        helper.getLevel().setBlockAndUpdate(provider.endpointTargetPosition(), Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(provider.endpointTargetPosition(), ProcessingRegistration.ENDPOINT.get()
                .defaultBlockState());
        productionEndpoint().loadWithComponents(savedTag, helper.getLevel().registryAccess());
        productionEndpoint().setChanged();
        endpointBinding = null;
        return new FederatedSavedState(endpoint.endpointIdentity(), owned, generation, oldBindingIdentity,
                runtimeReferencesSerialized);
    }

    public EndpointPersistenceObservation.Snapshot finishPersistenceObservation() {
        return EndpointPersistenceObservation.finish(productionEndpoint());
    }

    public EndpointBlockEntity productionEndpointEntity() {
        if (!productionEndpoint) {
            throw new IllegalStateException("This fixture does not use a production Endpoint");
        }
        return productionEndpoint();
    }

    public boolean targetCapabilityIsCurrentBinding() {
        return helper.getLevel().getCapability(EndpointTargetCapability.BLOCK, provider.endpointTargetPosition(),
                ENDPOINT_SIDE) == endpointBinding;
    }

    public EndpointIdentity endpointIdentity() {
        return endpointIdentity;
    }

    public EndpointClaimAuthority claims() {
        if (fixtureClaims == null) {
            throw new IllegalStateException("Production Endpoint owns its Claim authority");
        }
        return fixtureClaims;
    }

    public Object nativeRemainderDestination() {
        return nativeRemainderDestination(0);
    }

    public Object nativeRemainderDestination(int laneIndex) {
        return provider.lane(laneIndex).getReturnInv();
    }

    public long nativeRemainderAmount(int laneIndex, int slot) {
        return provider.lane(laneIndex).getReturnInv().getAmount(slot);
    }

    public EndpointTargetBinding endpointBinding() {
        return endpointBinding;
    }

    public appeng.helpers.patternprovider.PatternProviderLogic providerLogic() {
        return providerLogic(0);
    }

    public appeng.helpers.patternprovider.PatternProviderLogic providerLogic(int laneIndex) {
        return provider.lane(laneIndex);
    }

    public long targetItemCount() {
        return provider.endpointTargetItemCount();
    }

    public long targetAmount(AEKey key) {
        return targetGrid().getStorageService().getInventory().extract(key, Long.MAX_VALUE,
                Actionable.SIMULATE, IActionSource.empty());
    }

    public String targetSnapshot() {
        return "minecraft:cobblestone:" + targetAmount(AEItemKey.of(Items.COBBLESTONE))
                + ",minecraft:dirt:" + targetAmount(AEItemKey.of(Items.DIRT));
    }

    public void leaveOneSharedTargetSlot() {
        var storage = targetGrid().getStorageService().getInventory();
        storage.insert(AEItemKey.of(Items.DIRT), 1, Actionable.MODULATE, IActionSource.empty());
        storage.insert(AEItemKey.of(Items.COBBLESTONE), Long.MAX_VALUE,
                Actionable.MODULATE, IActionSource.empty());
        storage.extract(AEItemKey.of(Items.COBBLESTONE), 1, Actionable.MODULATE, IActionSource.empty());
    }

    public int bindingCount() {
        return ProviderTargetObservation.bindings();
    }

    public int capabilityLookupCount() {
        return ProviderTargetObservation.capabilityLookups();
    }

    public int mixinLookupCount() {
        return ProviderTargetObservation.mixinLookups();
    }

    public int nativeTargetLookupCount() {
        return ProviderTargetObservation.nativeTargetLookups();
    }

    public int nativeTargetFoundCount() {
        return ProviderTargetObservation.nativeTargetsFound();
    }

    public int authorizationCount(ProviderTargetState state) {
        return ProviderTargetObservation.authorizations(state);
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

    private space.controlnet.ae2federation.processing.claim.ClaimState claimState() {
        return productionEndpoint ? productionEndpoint().claimState() : fixtureClaims.state();
    }

    private EndpointBlockEntity productionEndpoint() {
        return (EndpointBlockEntity) helper.getLevel().getBlockEntity(provider.endpointTargetPosition());
    }

    @Override
    public void close() {
        if (runtime != null) {
            ProviderRuntimeReplayControl.clear(runtime);
        }
        if (endpointBinding != null && !productionEndpoint) {
            endpointBinding.close();
        }
        if (fabricConnected) {
            disconnectFabric();
        }
        provider.close();
        EndpointPersistenceObservation.clear();
    }

    public record FederatedSavedState(EndpointIdentity endpoint, ClaimState.Owned claim, long generation,
            String bindingIdentity, boolean runtimeReferencesSerialized) {
    }
}
