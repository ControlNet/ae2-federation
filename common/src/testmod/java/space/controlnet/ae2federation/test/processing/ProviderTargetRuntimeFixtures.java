package space.controlnet.ae2federation.test.processing;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.GridHelper;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.util.Objects;
import java.util.Set;
import net.minecraft.gametest.framework.GameTestHelper;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyDelete;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.processing.claim.ClaimEpoch;
import space.controlnet.ae2federation.processing.claim.ClaimState;
import space.controlnet.ae2federation.processing.claim.EndpointClaimAuthority;
import space.controlnet.ae2federation.processing.claim.EndpointIdentity;
import space.controlnet.ae2federation.processing.endpoint.EndpointBlockEntity;
import space.controlnet.ae2federation.processing.endpoint.EndpointTargetBinding;
import space.controlnet.ae2federation.processing.provider.ProviderFace;
import space.controlnet.ae2federation.processing.provider.ProviderIdentity;
import space.controlnet.ae2federation.processing.provider.ProviderRuntime;
import space.controlnet.ae2federation.processing.provider.ProviderTargetState;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.test.processing.endpoint.EndpointPersistenceObservation;

public final class ProviderTargetRuntimeFixtures implements AutoCloseable {
    private final NativeProviderLaneFixtures provider;
    private final ProviderTargetFixtureView view;
    private final ProviderTargetLifecycle lifecycle;
    private final Boolean[] acceptedPushes;

    public ProviderTargetRuntimeFixtures(GameTestHelper helper) {
        this(helper, false);
    }

    public ProviderTargetRuntimeFixtures(GameTestHelper helper, boolean federationEndpoint) {
        this(helper, federationEndpoint, ProviderIdentity.create());
    }

    public ProviderTargetRuntimeFixtures(GameTestHelper helper, boolean federationEndpoint,
            NetworkId sourceNetwork, NetworkId targetNetwork) {
        Objects.requireNonNull(sourceNetwork);
        Objects.requireNonNull(targetNetwork);
        var providerIdentity = ProviderIdentity.create();
        ProviderTargetObservation.reset();
        var assignments = NativeProviderLaneFixtures.sharedPatternAssignments(3);
        provider = new NativeProviderLaneFixtures(helper, assignments, true, 3, sourceNetwork);
        view = new ProviderTargetFixtureView(provider);
        lifecycle = new ProviderTargetLifecycle(helper, provider, federationEndpoint, providerIdentity);
        if (!federationEndpoint) {
            throw new IllegalArgumentException("Seeded target identity requires a production Endpoint");
        }
        provider.seedFederationEndpointTarget(targetNetwork);
        acceptedPushes = new Boolean[assignments.size()];
    }

    public ProviderTargetRuntimeFixtures(GameTestHelper helper, boolean federationEndpoint,
            ProviderIdentity providerIdentity) {
        this(helper, federationEndpoint, providerIdentity, 3);
    }

    public ProviderTargetRuntimeFixtures(GameTestHelper helper, boolean federationEndpoint,
            ProviderIdentity providerIdentity, int laneCount) {
        this(helper, federationEndpoint, providerIdentity,
                NativeProviderLaneFixtures.sharedPatternAssignments(laneCount), 3);
    }

    public ProviderTargetRuntimeFixtures(GameTestHelper helper, boolean federationEndpoint,
            ProviderIdentity providerIdentity, java.util.List<java.util.function.IntPredicate> assignments,
            int patternSlots) {
        Objects.requireNonNull(providerIdentity);
        ProviderTargetObservation.reset();
        provider = new NativeProviderLaneFixtures(helper, assignments, true, patternSlots);
        view = new ProviderTargetFixtureView(provider);
        lifecycle = new ProviderTargetLifecycle(helper, provider, federationEndpoint, providerIdentity);
        acceptedPushes = new Boolean[assignments.size()];
    }

    public boolean initialize() { return lifecycle.initialize(); }

    public String status() { return lifecycle.status(); }

    public boolean enablePolicy(Set<PolicyOperation> operations) {
        return lifecycle.enablePolicy(operations);
    }

    public void connectFabric() { lifecycle.connectFabric(); }

    public void disconnectFabric() { lifecycle.disconnectFabric(); }

    public void connectSourceTo(IGridNode node) {
        provider.connectTo(node);
    }

    public boolean connectTargetTo(IGridNode node) {
        var target = provider.endpointTargetNode();
        if (target == null) {
            return false;
        }
        if (target.getGrid() != node.getGrid()) {
            GridHelper.createConnection(target, node);
        }
        return true;
    }

    public boolean deletePolicy() {
        var service = PolicyService.get(provider.helper().getLevel());
        var key = new space.controlnet.ae2federation.policy.PolicyKey(
                space.controlnet.ae2federation.fabric.FabricRegistryAccess.confirmedNetworkId(sourceGrid()).orElseThrow(),
                space.controlnet.ae2federation.fabric.FabricRegistryAccess.confirmedNetworkId(targetGrid()).orElseThrow(),
                space.controlnet.ae2federation.policy.PolicyCapability.PROCESSING);
        return service.delete(new PolicyDelete(key, service.revision(key))) instanceof PolicyMutationResult.Accepted;
    }

    public boolean push() { return provider.push(0, 0); }

    public boolean pushOnce() { return pushOnce(0); }

    public boolean pushOnce(int laneIndex) {
        if (acceptedPushes[laneIndex] == null) {
            acceptedPushes[laneIndex] = provider.push(laneIndex, 0);
        }
        return acceptedPushes[laneIndex];
    }

    public boolean pushLane(int laneIndex) { return provider.push(laneIndex, 0); }

    public boolean pushLaneWithInputs(int laneIndex, java.util.List<GenericStack> inputs) {
        return provider.pushInputs(laneIndex, 0, inputs);
    }

    public void installPattern(java.util.List<GenericStack> inputs, java.util.List<GenericStack> outputs) {
        provider.installPattern(0, inputs, outputs);
    }

    public void setPattern(int slot, java.util.List<GenericStack> inputs, java.util.List<GenericStack> outputs) {
        provider.setPattern(slot, inputs, outputs);
    }

    public void refreshPatterns() { provider.refreshPatterns(); }

    public void armAuthorizedReplay(int sourceLaneIndex, int targetLaneIndex) {
        ProviderRuntimeReplayControl.arm(lifecycle.runtime(), providerLogic(sourceLaneIndex), providerLogic(targetLaneIndex),
                lifecycle.endpointBinding().runtime());
    }

    public ProviderRuntimeReplayControl.ReplayTrace finishAuthorizedReplay(String phase) {
        return ProviderRuntimeReplayControl.finish(lifecycle.runtime(), lifecycle.endpointBinding().runtime(), phase);
    }

    public ProviderTargetState state() { return lifecycle.state(); }

    public void useClaimEpoch(ClaimEpoch epoch) { lifecycle.useClaimEpoch(epoch); }

    public void useUnloadedTarget() { lifecycle.useUnloadedTarget(); }

    public void restoreTargetRequest() { lifecycle.restoreTargetRequest(); }

    public void unbindEndpoint() { lifecycle.unbindEndpoint(); }

    public void overlapWith(EndpointIdentity other) {
        lifecycle.overlapWith(other);
    }

    public void joinSourceAndTargetGrids() { lifecycle.joinSourceAndTargetGrids(); }

    public void rotate(ProviderFace face) {
        lifecycle.rotate(face);
    }

    public void settleRotation() { lifecycle.settleRotation(); }

    public ProviderRuntime runtime() { return lifecycle.runtime(); }

    public ProviderIdentity providerIdentity() { return lifecycle.providerIdentity(); }

    public FederatedSavedState reloadProductionEndpoint() {
        var saved = lifecycle.reloadProductionEndpoint();
        return new FederatedSavedState(saved.endpoint(), saved.claim(), saved.generation(), saved.bindingIdentity(),
                saved.runtimeReferencesSerialized());
    }

    public EndpointPersistenceObservation.Snapshot finishPersistenceObservation() {
        return lifecycle.finishPersistenceObservation();
    }

    public EndpointBlockEntity productionEndpointEntity() {
        return lifecycle.productionEndpointEntity();
    }

    public boolean targetCapabilityIsCurrentBinding() { return lifecycle.targetCapabilityIsCurrentBinding(); }

    public EndpointIdentity endpointIdentity() { return lifecycle.endpointIdentity(); }

    public EndpointClaimAuthority claims() { return lifecycle.claims(); }

    public Object nativeRemainderDestination() {
        return nativeRemainderDestination(0);
    }

    public Object nativeRemainderDestination(int laneIndex) {
        return view.nativeRemainderDestination(laneIndex);
    }

    public long nativeRemainderAmount(int laneIndex, int slot) {
        return view.nativeRemainderAmount(laneIndex, slot);
    }

    public EndpointTargetBinding endpointBinding() { return lifecycle.endpointBinding(); }

    public appeng.helpers.patternprovider.PatternProviderLogic providerLogic() {
        return providerLogic(0);
    }

    public appeng.helpers.patternprovider.PatternProviderLogic providerLogic(int laneIndex) {
        return view.providerLogic(laneIndex);
    }

    public long targetItemCount() { return view.targetItemCount(); }

    public long targetAmount(AEKey key) {
        return view.targetAmount(key);
    }

    public void fillTarget(AEKey key) {
        view.fillTarget(key);
    }

    public void clearTarget(AEKey key) {
        view.clearTarget(key);
    }

    public long extractTarget(AEKey key, long amount) {
        return view.extractTarget(key, amount);
    }

    public int laneCount() { return view.laneCount(); }

    public boolean wakeProviderTicker() { return view.wakeProviderTicker(); }

    public String targetSnapshot() { return view.targetSnapshot(); }

    public void leaveOneSharedTargetSlot() { view.leaveOneSharedTargetSlot(); }

    public int bindingCount() { return ProviderTargetObservation.bindings(); }

    public int capabilityLookupCount() { return ProviderTargetObservation.capabilityLookups(); }

    public int mixinLookupCount() { return ProviderTargetObservation.mixinLookups(); }

    public int nativeTargetLookupCount() { return ProviderTargetObservation.nativeTargetLookups(); }

    public int nativeTargetFoundCount() { return ProviderTargetObservation.nativeTargetsFound(); }

    public int authorizationCount(ProviderTargetState state) {
        return ProviderTargetObservation.authorizations(state);
    }

    public IGrid sourceGrid() { return view.sourceGrid(); }

    IGridNode sourceNode() { return view.sourceNode(); }

    public IGrid targetGrid() { return view.targetGrid(); }

    @Override
    public void close() {
        lifecycle.close();
        provider.close();
    }

    public record FederatedSavedState(EndpointIdentity endpoint, ClaimState.Owned claim, long generation,
            String bindingIdentity, boolean runtimeReferencesSerialized) {
    }
}
