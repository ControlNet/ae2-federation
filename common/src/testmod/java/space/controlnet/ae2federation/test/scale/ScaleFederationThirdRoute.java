package space.controlnet.ae2federation.test.scale;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import space.controlnet.ae2federation.bridge.BridgeRegistration;
import space.controlnet.ae2federation.bridge.MultipartBridgePart;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.BackendStatus;
import space.controlnet.ae2federation.policy.PolicyActivationState;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyDelete;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyRuntimeEndpoints;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.processing.claim.ClaimEpoch;
import space.controlnet.ae2federation.processing.claim.ClaimRequest;
import space.controlnet.ae2federation.processing.claim.ClaimResult;
import space.controlnet.ae2federation.processing.claim.EndpointOwnerIdentity;
import space.controlnet.ae2federation.processing.claim.NativeTargetDomainRegistry;
import space.controlnet.ae2federation.processing.endpoint.EndpointTargetCapability;
import space.controlnet.ae2federation.processing.provider.AuthorizedLaneIdentity;
import space.controlnet.ae2federation.processing.provider.ProviderFace;
import space.controlnet.ae2federation.processing.provider.ProviderIdentity;
import space.controlnet.ae2federation.processing.provider.ProviderOrientation;
import space.controlnet.ae2federation.processing.provider.ProviderRuntime;
import space.controlnet.ae2federation.processing.provider.ProviderTargetRequest;
import space.controlnet.ae2federation.processing.provider.ProviderTargetResolution;
import space.controlnet.ae2federation.processing.provider.ProviderTargetState;
import space.controlnet.ae2federation.test.port.NativePortFixtures;
import space.controlnet.ae2federation.test.processing.NativeProviderLaneFixtures;

public final class ScaleFederationThirdRoute implements AutoCloseable {
    public static final BlockPos HOST = new BlockPos(9, 2, 3);
    private static final BlockPos RED = new BlockPos(4, 2, 9);
    private static final BlockPos BLUE = new BlockPos(5, 2, 9);
    private static final List<BlockPos> SOURCE = List.of(new BlockPos(3, 3, 2), new BlockPos(3, 4, 2),
            new BlockPos(4, 4, 2), new BlockPos(5, 4, 2), new BlockPos(6, 4, 2),
            new BlockPos(7, 4, 2), new BlockPos(8, 4, 2), new BlockPos(8, 3, 2),
            new BlockPos(8, 2, 2), new BlockPos(8, 2, 3));
    private static final List<BlockPos> EXTENSION = List.of(new BlockPos(4, 3, 6), new BlockPos(4, 3, 7),
            new BlockPos(4, 3, 8), new BlockPos(4, 3, 9));
    private final GameTestHelper helper;
    private final ScaleFederationIdentityTarget target;
    private final IGrid sourceGrid;
    private final IGrid targetGrid;
    private final NetworkId sourceId;
    private final NetworkId targetId;
    private final NativePortFixtures ports;
    private final ProviderIdentity identity = ProviderIdentity.create();
    private final PolicyKey policyKey;
    private final boolean catalogReplay;
    private final boolean fourTargetReplay;
    private NativeProviderLaneFixtures remote;
    private ProviderRuntime runtime;
    private MultipartBridgePart bridge;
    private ProviderTargetRequest request;
    private int stage;
    private int index;
    private CreativeEnergyCellBlockEntity sourcePower;
    private appeng.api.networking.IGridNode sourcePowerNode;
    private CompoundTag sourcePowerState;

    public ScaleFederationThirdRoute(GameTestHelper helper, IGrid sourceGrid,
            ScaleFederationIdentityTarget target) {
        this(helper, sourceGrid, target, false);
    }

    public ScaleFederationThirdRoute(GameTestHelper helper, IGrid sourceGrid,
            ScaleFederationIdentityTarget target, boolean catalogReplay) {
        this(helper, sourceGrid, target, catalogReplay, false);
    }

    public ScaleFederationThirdRoute(GameTestHelper helper, IGrid sourceGrid,
            ScaleFederationIdentityTarget target, boolean catalogReplay, boolean fourTargetReplay) {
        this.helper = helper;
        this.catalogReplay = catalogReplay;
        this.fourTargetReplay = fourTargetReplay;
        this.target = target;
        this.sourceGrid = sourceGrid;
        targetGrid = target.grid();
        sourceId = FabricRegistryAccess.confirmedNetworkId(sourceGrid).orElseThrow();
        targetId = target.anchorId();
        policyKey = new PolicyKey(sourceId, targetId, PolicyCapability.PROCESSING);
        ports = new NativePortFixtures(helper);
    }

    public boolean tick() {
        if (stage == 0) {
            helper.assertTrue(target.anchorReady(sourceGrid) && target.endpointReady()
                            && target.exportBusReady() && target.onlyAnchorClaim() && target.settled(),
                    "Third target must settle independently before source wiring");
            stage = 1;
        }
        if (stage == 1) {
            if (index > 0) {
                var previous = SOURCE.get(index - 1);
                if (!cableReady(previous, sourceGrid, sourceId)) return false;
                var previousNode = GridHelper.getExposedNode(helper.getLevel(), helper.absolutePos(previous), Direction.UP);
                var previousFace = index == 1 ? Direction.DOWN : Direction.fromDelta(
                        SOURCE.get(index - 2).getX() - previous.getX(),
                        SOURCE.get(index - 2).getY() - previous.getY(),
                        SOURCE.get(index - 2).getZ() - previous.getZ());
                helper.assertTrue(previousNode.getInWorldConnections().containsKey(previousFace),
                        "Each H1 trunk cable must have a real predecessor edge: " + previous);
            }
            if (index < SOURCE.size()) {
                placeCable(SOURCE.get(index++), AEColor.RED);
                return false;
            }
            sourcePower = helper.getBlockEntity(NativeProviderLaneFixtures.HOST_POS.west(2));
            sourcePowerNode = sourcePower.getMainNode().getNode();
            sourcePowerState = new CompoundTag();
            sourcePower.getMainNode().saveToNBT(sourcePowerState);
            remote = new NativeProviderLaneFixtures(helper,
                     List.of(slot -> catalogReplay ? slot % (fourTargetReplay ? 4 : 3) == 2 : slot == 0), false,
                     catalogReplay ? ScaleProcessingCatalog.SIZE : 1, sourceId, List.of(Direction.EAST), HOST);
            if (catalogReplay) ScaleProcessingCatalog.install(remote, slot -> slot % (fourTargetReplay ? 4 : 3) == 2);
            else {
                var recipe = ScaleProcessingCatalog.recipes().get(2);
                remote.installPattern(0, List.of(space.controlnet.ae2federation.test.processing.ProcessingRegressionFixtures
                                .item(recipe.input(), 1)),
                        List.of(space.controlnet.ae2federation.test.processing.ProcessingRegressionFixtures
                                .item(recipe.output(), 1)));
            }
            stage = 2;
            return false;
        }
        var h1 = helper.<PatternProviderBlockEntity>getBlockEntity(HOST).getMainNode().getNode();
        var powerState = new CompoundTag();
        sourcePower.getMainNode().saveToNBT(powerState);
        helper.assertTrue(sourcePower == helper.getBlockEntity(NativeProviderLaneFixtures.HOST_POS.west(2))
                        && sourcePowerNode == sourcePower.getMainNode().getNode()
                        && sourcePowerState.equals(powerState), "H1 must not reseed H0 physical source power");
        if (h1 == null || !h1.hasGridBooted()) return false;
        helper.assertTrue(h1.isActive() && h1.getGrid() == sourceGrid
                        && h1.getInWorldConnections().containsKey(Direction.WEST)
                        && h1.getInWorldConnections().get(Direction.WEST).getOtherSide(h1).getGrid() == sourceGrid
                        && FabricRegistryAccess.confirmedNetworkId(sourceGrid).filter(sourceId::equals).isPresent(),
                "H1 must have a real WEST in-world edge on the exact source Grid");
        if (stage == 2) {
            remote.register();
            runtime = new ProviderRuntime(helper.getLevel(), remote.managedNode(), remote.composition(), identity,
                    new ProviderOrientation(ProviderFace.EAST), lane -> request, new NativeTargetDomainRegistry());
            runtime.settle();
            index = 0;
            stage = 3;
        }
        if (stage == 3) {
            if (index > 0 && !cableReady(EXTENSION.get(index - 1), sourceGrid, sourceId)) return false;
            if (index < EXTENSION.size()) {
                placeCable(EXTENSION.get(index++), AEColor.RED);
                return false;
            }
            placeCable(RED, AEColor.RED);
            stage = 4;
            return false;
        }
        if (stage == 4 && cableReady(RED, sourceGrid, sourceId)) {
            placeCable(BLUE, AEColor.BLUE);
            stage = 5;
        }
        if (stage == 5 && cableReady(BLUE, targetGrid, targetId)) {
            bridge = PartHelper.setPart(helper.getLevel(), helper.absolutePos(RED), Direction.EAST,
                    null, BridgeRegistration.MULTIPART_BRIDGE.get());
            helper.assertTrue(bridge != null, "Physical C Bridge must be placeable");
            stage = 6;
        }
        if (stage == 6) {
            bridge.onNeighborChanged(helper.getLevel(), helper.absolutePos(RED), helper.absolutePos(BLUE));
            if (!bridgeReady()) return false;
            var policies = PolicyService.get(helper.getLevel());
            helper.assertTrue(policies.edit(new PolicyEdit(policyKey, policies.revision(policyKey),
                    PolicyRule.enabled(Set.of(PolicyOperation.EXECUTE, PolicyOperation.SUPPLY))))
                    instanceof PolicyMutationResult.Accepted, "C directional Policy must be accepted");
            stage = 7;
        }
        if (stage == 7 && policyActive()) {
            var endpoint = target.endpoint();
            helper.assertTrue(endpoint.claim(new ClaimRequest(endpoint.endpointIdentity(), ClaimEpoch.NONE,
                    new EndpointOwnerIdentity(identity))) instanceof ClaimResult.Acquired
                    && endpoint.activateFederated(), "H1 alone must acquire C Claim");
            request = new ProviderTargetRequest(identity, endpoint.endpointIdentity(),
                    endpoint.claimState().epoch(), helper.absolutePos(target.endpointPosition()), Direction.WEST, true);
            var policies = PolicyService.get(helper.getLevel());
            helper.assertTrue(policies.delete(new PolicyDelete(policyKey, policies.revision(policyKey)))
                    instanceof PolicyMutationResult.Accepted, "Temporarily withdraw C Policy for negative gate");
            var thirdInput = appeng.api.stacks.AEItemKey.of(ScaleProcessingCatalog.recipes().get(2).input());
            helper.assertTrue(!policyActive() && !remote.push(0, 0)
                            && runtime.lastResolution().state() == ProviderTargetState.POLICY_DENIED
                            && target.inputAmount(thirdInput) == 0,
                    "Missing C Policy must deny H1's real native Lane push before C input/export");
            org.slf4j.LoggerFactory.getLogger(getClass()).info(
                    "AE2F_SCALE_FEDERATION_C_NEGATIVE policy=ABSENT resolution={} targetInput={} pushAccepted=false",
                    runtime.lastResolution().state(), target.inputAmount(thirdInput));
            helper.assertTrue(policies.edit(new PolicyEdit(policyKey, policies.revision(policyKey),
                    PolicyRule.enabled(Set.of(PolicyOperation.EXECUTE, PolicyOperation.SUPPLY))))
                    instanceof PolicyMutationResult.Accepted, "Restore C Policy after negative gate");
            stage = 8;
        }
        if (stage == 8 && policyActive()) {
            assertReady();
            org.slf4j.LoggerFactory.getLogger(getClass()).info(
                    "AE2F_SCALE_FEDERATION_THIRD_ROUTE source={} targetC={} sourceGrid={} targetCGrid={} bridge={} policy=ACTIVE claim={} h1West=true",
                    sourceId.value(), targetId.value(), System.identityHashCode(sourceGrid),
                    System.identityHashCode(targetGrid), bridge.operationalReason(), target.endpoint().claimState());
            stage = 9;
        }
        return stage == 9;
    }

    public NativeProviderLaneFixtures remote() {
        return remote;
    }

    public void assertReady() {
        var endpoint = target.endpoint();
        var h1 = helper.<PatternProviderBlockEntity>getBlockEntity(HOST).getMainNode().getNode();
        helper.assertTrue(bridgeReady() && policyActive() && target.grid() == targetGrid
                        && h1.getGrid() == sourceGrid && h1.getInWorldConnections().containsKey(Direction.WEST)
                        && h1.getInWorldConnections().get(Direction.WEST).getOtherSide(h1).getGrid() == sourceGrid
                        && target.onlyAnchorClaim() && target.settled()
                        && endpoint.claimState().owner().filter(new EndpointOwnerIdentity(identity)::equals).isPresent()
                        && helper.getLevel().getCapability(EndpointTargetCapability.BLOCK,
                                helper.absolutePos(target.endpointPosition()), Direction.WEST) == endpoint.binding(),
                "Third physical Bridge/Fabric/Policy and H1-owned C Claim must remain active");
    }

    public void assertNativeJobLane(AuthorizedLaneIdentity owner) {
        assertReady();
        helper.assertTrue(runtime.lastResolution() instanceof ProviderTargetResolution.Authorized authorized
                        && authorized.target().position().equals(helper.absolutePos(target.endpointPosition()))
                        && authorized.target().provider().equals(identity)
                        && owner.lane().laneIndex() == 0 && authorized.target().laneIdentity().equals(owner),
                "H1's only Lane must resolve exactly its C Endpoint: " + runtime.lastResolution().state());
    }

    private void placeCable(BlockPos position, AEColor color) {
        var absolute = helper.absolutePos(position);
        helper.assertTrue(helper.getLevel().isLoaded(absolute)
                        && (helper.getBlockState(position).isAir() || helper.getBlockState(position).is(Blocks.BARRIER))
                        && helper.getLevel().getBlockEntity(absolute) == null,
                "Third route cable cannot overwrite a device or target: " + position
                        + " block=" + helper.getBlockState(position));
        ports.placeCable(position, color);
    }

    private boolean cableReady(BlockPos position, IGrid grid, NetworkId id) {
        var node = GridHelper.getExposedNode(helper.getLevel(), helper.absolutePos(position), Direction.UP);
        return node != null && node.hasGridBooted() && node.isActive() && node.getGrid() == grid
                && FabricRegistryAccess.confirmedNetworkId(grid).filter(id::equals).isPresent();
    }

    private boolean bridgeReady() {
        if (bridge == null) return false;
        var candidate = bridge.membershipCandidate();
        var registry = FabricRegistryAccess.get(helper.getLevel());
        return candidate.isPresent() && candidate.orElseThrow().mainGrid() == sourceGrid
                && candidate.orElseThrow().outerGrid() == targetGrid
                && registry.fabricsFor(sourceId).stream().anyMatch(registry.fabricsFor(targetId)::contains)
                && bridge.getMainNode().getNode().getConnections().stream().noneMatch(connection ->
                        connection.getOtherSide(bridge.getMainNode().getNode()) == bridge.getExternalFacingNode());
    }

    private boolean policyActive() {
        return PolicyService.get(helper.getLevel()).activation(policyKey,
                new PolicyRuntimeEndpoints(sourceGrid, targetGrid, BackendStatus.READY))
                == PolicyActivationState.ACTIVE;
    }

    @Override
    public void close() {
        var policies = PolicyService.get(helper.getLevel());
        helper.assertTrue(policies.delete(new PolicyDelete(policyKey, policies.revision(policyKey)))
                instanceof PolicyMutationResult.Accepted, "C Policy removal");
        if (bridge != null) helper.assertTrue(bridge.getHost().removePart(bridge), "C Bridge removal");
        for (var position : List.of(RED, BLUE)) helper.setBlock(position, Blocks.AIR);
        for (var position : EXTENSION) helper.setBlock(position, Blocks.AIR);
        remote.close();
        helper.setBlock(HOST, Blocks.AIR);
        for (var position : SOURCE) helper.setBlock(position, Blocks.AIR);
        ports.close();
    }
}
