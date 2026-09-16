package space.controlnet.ae2federation.test.processing;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
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
import space.controlnet.ae2federation.processing.claim.ClaimEpoch;
import space.controlnet.ae2federation.processing.claim.ClaimRequest;
import space.controlnet.ae2federation.processing.claim.EndpointOwnerIdentity;
import space.controlnet.ae2federation.processing.claim.NativeTargetDomainRegistry;
import space.controlnet.ae2federation.processing.endpoint.EndpointBlockEntity;
import space.controlnet.ae2federation.processing.endpoint.EndpointTargetBinding;
import space.controlnet.ae2federation.processing.provider.ProviderFace;
import space.controlnet.ae2federation.processing.provider.ProviderIdentity;
import space.controlnet.ae2federation.processing.provider.ProviderOrientation;
import space.controlnet.ae2federation.processing.provider.ProviderRuntime;
import space.controlnet.ae2federation.processing.provider.ProviderTargetRequest;

final class GeneratedFederationTargets implements AutoCloseable {
    private static final Direction TARGET_SIDE = Direction.WEST;
    private final GameTestHelper helper;
    private final NativeProviderLaneFixtures provider;
    private final ProviderIdentity providerIdentity = ProviderIdentity.create();
    private final NativeTargetDomainRegistry domains = new NativeTargetDomainRegistry();
    private final List<BlockPos> positions;
    private final List<ProviderTargetRequest> requests = new ArrayList<>();
    private final List<FabricSourceId> fabricSources = new ArrayList<>();
    private ProviderRuntime runtime;
    private boolean registered;
    private boolean relationshipsActive;
    private String status = "created";

    GeneratedFederationTargets(GameTestHelper helper, List<IntPredicate> assignments, int patternSlots,
            List<BlockPos> positions) {
        this.helper = helper;
        provider = new NativeProviderLaneFixtures(helper, assignments, true, patternSlots);
        this.positions = List.copyOf(positions);
        positions.forEach(provider::installFederationEndpointTarget);
    }

    boolean initialize() {
        if (!provider.connectEnergy() || !provider.composition().isActive()) {
            status = "source-not-active";
            return false;
        }
        if (FabricRegistryAccess.confirmedNetworkId(sourceGrid()).isEmpty()) {
            status = "source-identity-pending";
            return false;
        }
        for (var position : positions) {
            var node = provider.endpointTargetNode(position);
            if (node == null || !node.isActive() || FabricRegistryAccess.confirmedNetworkId(node.getGrid()).isEmpty()) {
                status = "target-identity-pending";
                return false;
            }
            var endpoint = endpoint(position);
            if (endpoint.binding() == null) {
                status = "endpoint-binding-pending";
                return false;
            }
        }
        if (runtime == null) {
            claimTargets();
            runtime = new ProviderRuntime(helper.getLevel(), provider.managedNode(), provider.composition(),
                    providerIdentity, new ProviderOrientation(ProviderFace.EAST), requests::get, domains);
            runtime.settle();
        }
        if (!registered) {
            provider.register();
            registered = true;
        }
        if (!relationshipsActive) {
            activateRelationships();
            relationshipsActive = true;
            return false;
        }
        status = "ready";
        return true;
    }

    String status() {
        return status;
    }

    void setPattern(int slot, List<GenericStack> inputs, List<GenericStack> outputs) {
        provider.setPattern(slot, inputs, outputs);
    }

    void refreshPatterns() {
        provider.refreshPatterns();
    }

    void lockUntilResult(int laneIndex) {
        provider.lockUntilResult(laneIndex);
    }

    void mapPattern(int slot, Set<Integer> lanes) {
        if (!provider.composition().replaceMapping(provider.composition().mappingHandle(slot), lanes)) {
            throw new IllegalStateException("Generated Pattern Lane mapping changed concurrently");
        }
    }

    long mappingRevision(int slot) {
        return provider.composition().mappingHandle(slot).generation();
    }

    IGrid sourceGrid() {
        return provider.managedNode().getGrid();
    }

    IGridNode sourceNode() {
        return provider.managedNode().getNode();
    }

    List<IGrid> targetGrids() {
        return positions.stream().map(provider::endpointTargetNode).map(IGridNode::getGrid).toList();
    }

    appeng.helpers.patternprovider.PatternProviderLogic providerLogic(int laneIndex) {
        return provider.lane(laneIndex);
    }

    int laneCount() {
        return provider.laneCount();
    }

    EndpointTargetBinding endpointBinding(int laneIndex) {
        return endpoint(positions.get(laneIndex)).binding();
    }

    String endpointIdentity(int laneIndex) {
        return endpoint(positions.get(laneIndex)).endpointIdentity().id().value().toString();
    }

    long targetAmount(AEKey key) {
        return targetGrids().stream().mapToLong(grid -> grid.getStorageService().getInventory().extract(key,
                Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty())).sum();
    }

    long extractTarget(AEKey key, long amount) {
        long extracted = 0;
        for (var grid : targetGrids()) {
            extracted += grid.getStorageService().getInventory().extract(key, amount - extracted,
                    Actionable.MODULATE, IActionSource.empty());
            if (extracted == amount) {
                break;
            }
        }
        return extracted;
    }

    void fillTargets(AEKey key) {
        targetGrids().forEach(grid -> grid.getStorageService().getInventory().insert(key, Long.MAX_VALUE,
                Actionable.MODULATE, IActionSource.empty()));
    }

    void clearTargets(AEKey key) {
        targetGrids().forEach(grid -> grid.getStorageService().getInventory().extract(key, Long.MAX_VALUE,
                Actionable.MODULATE, IActionSource.empty()));
    }

    boolean wakeProviderTicker() {
        return provider.wakeNativeTicker();
    }

    private void claimTargets() {
        for (var position : positions) {
            var endpoint = endpoint(position);
            endpoint.claim(new ClaimRequest(endpoint.endpointIdentity(), ClaimEpoch.NONE,
                    new EndpointOwnerIdentity(providerIdentity)));
            endpoint.activateFederated();
            requests.add(new ProviderTargetRequest(providerIdentity, endpoint.endpointIdentity(),
                    endpoint.claimState().epoch(), helper.absolutePos(position), TARGET_SIDE, true));
        }
    }

    private void activateRelationships() {
        var sourceNetwork = FabricRegistryAccess.confirmedNetworkId(sourceGrid()).orElseThrow();
        var policies = PolicyService.get(helper.getLevel());
        for (int laneIndex = 0; laneIndex < positions.size(); laneIndex++) {
            var targetNetwork = FabricRegistryAccess.confirmedNetworkId(targetGrids().get(laneIndex)).orElseThrow();
            var key = new PolicyKey(sourceNetwork, targetNetwork, PolicyCapability.PROCESSING);
            var result = policies.edit(new PolicyEdit(key, policies.revision(key),
                    PolicyRule.enabled(Set.of(PolicyOperation.EXECUTE, PolicyOperation.SUPPLY))));
            if (!(result instanceof PolicyMutationResult.Accepted)) {
                throw new IllegalStateException("Generated target Policy did not activate");
            }
            var source = new FabricSourceId("task20:" + endpoint(positions.get(laneIndex)).endpointIdentity().id().value());
            FabricRegistryAccess.get(helper.getLevel()).upsertDirectBridge(source, sourceNetwork, targetNetwork);
            fabricSources.add(source);
        }
    }

    private EndpointBlockEntity endpoint(BlockPos position) {
        return (EndpointBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(position));
    }

    @Override
    public void close() {
        fabricSources.forEach(source -> FabricRegistryAccess.get(helper.getLevel()).invalidateDirectBridge(source));
        provider.close();
        positions.forEach(position -> {
            helper.setBlock(position, Blocks.AIR);
            helper.setBlock(position.north(), Blocks.AIR);
            helper.setBlock(position.north().below(), Blocks.AIR);
        });
    }
}
