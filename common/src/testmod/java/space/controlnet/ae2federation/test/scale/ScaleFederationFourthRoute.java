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
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
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
import space.controlnet.ae2federation.test.port.NativePortFixtures;
import space.controlnet.ae2federation.test.processing.NativeProviderLaneFixtures;
import space.controlnet.ae2federation.test.processing.ProcessingRegressionFixtures;

public final class ScaleFederationFourthRoute implements AutoCloseable {
    public static final BlockPos HOST = new BlockPos(9, 2, 7);
    public static final BlockPos ENDPOINT = new BlockPos(11, 2, 7);
    private static final BlockPos RED = new BlockPos(9, 2, 6);
    private static final BlockPos BLUE = new BlockPos(10, 2, 6);
    private static final List<BlockPos> TRUNK = List.of(new BlockPos(8, 2, 4), new BlockPos(8, 2, 5),
            new BlockPos(8, 2, 6), new BlockPos(8, 2, 7));
    private final GameTestHelper helper;
    private final IGrid sourceGrid;
    private final IGrid targetGrid;
    private final NetworkId sourceId;
    private final NetworkId targetId;
    private final ScaleFederationIdentityTarget target;
    private final NativePortFixtures ports;
    private final ProviderIdentity identity = ProviderIdentity.create();
    private final PolicyKey policyKey;
    private final boolean catalogReplay;
    private NativeProviderLaneFixtures remote;
    private ProviderRuntime runtime;
    private ProviderTargetRequest request;
    private MultipartBridgePart bridge;
    private CreativeEnergyCellBlockEntity sourcePower;
    private appeng.api.networking.IGridNode sourcePowerNode;
    private CompoundTag sourcePowerState;
    private int stage;
    private int index;

    public ScaleFederationFourthRoute(GameTestHelper helper, IGrid sourceGrid, ScaleFederationIdentityTarget target) {
        this(helper, sourceGrid, target, false);
    }

    public ScaleFederationFourthRoute(GameTestHelper helper, IGrid sourceGrid, ScaleFederationIdentityTarget target,
            boolean catalogReplay) {
        this.helper = helper;
        this.catalogReplay = catalogReplay;
        this.sourceGrid = sourceGrid;
        this.target = target;
        targetGrid = target.grid();
        sourceId = FederationDomainRegistryAccess.confirmedNetworkId(sourceGrid).orElseThrow();
        targetId = target.anchorId();
        policyKey = new PolicyKey(sourceId, targetId, PolicyCapability.PROCESSING);
        ports = new NativePortFixtures(helper);
    }

    public static List<BlockPos> footprint() {
        return java.util.stream.Stream.concat(TRUNK.stream(), List.of(HOST, HOST.east(),
                ENDPOINT, ENDPOINT.north(), ENDPOINT.north().below(), ENDPOINT.south(),
                ENDPOINT.south().east(), RED, BLUE).stream()).toList();
    }

    public boolean tick() {
        if (stage == 0) {
            helper.assertTrue(target.anchorReady(sourceGrid) && target.endpointReady()
                            && target.exportBusReady() && target.onlyAnchorClaim() && target.settled(),
                    "D anchor, Endpoint and Export Bus must settle independently");
            stage = 1;
        }
        if (stage == 1) {
            if (index > 0) {
                var previous = TRUNK.get(index - 1);
                if (!cableReady(previous, sourceGrid, sourceId)) return false;
                var node = GridHelper.getExposedNode(helper.getLevel(), helper.absolutePos(previous), Direction.UP);
                helper.assertTrue(node.getInWorldConnections().containsKey(Direction.NORTH),
                        "D trunk must keep a physical predecessor edge: " + previous);
            }
            if (index < TRUNK.size()) {
                placeCable(TRUNK.get(index++), AEColor.RED);
                return false;
            }
            sourcePower = helper.getBlockEntity(NativeProviderLaneFixtures.HOST_POS.west(2));
            sourcePowerNode = sourcePower.getMainNode().getNode();
            sourcePowerState = new CompoundTag();
            sourcePower.getMainNode().saveToNBT(sourcePowerState);
            remote = new NativeProviderLaneFixtures(helper,
                    List.of(slot -> catalogReplay ? slot % 4 == 3 : slot == 0), false,
                    catalogReplay ? ScaleProcessingCatalog.SIZE : 1, sourceId, List.of(Direction.EAST), HOST);
            if (catalogReplay) ScaleProcessingCatalog.install(remote, slot -> slot % 4 == 3);
            else {
                var recipe = ScaleProcessingCatalog.recipes().get(3);
                remote.installPattern(0, List.of(ProcessingRegressionFixtures.item(recipe.input(), 1)),
                        List.of(ProcessingRegressionFixtures.item(recipe.output(), 1)));
            }
            stage = 2;
            return false;
        }
        var host = helper.<PatternProviderBlockEntity>getBlockEntity(HOST).getMainNode().getNode();
        var currentPowerState = new CompoundTag();
        sourcePower.getMainNode().saveToNBT(currentPowerState);
        helper.assertTrue(sourcePower == helper.getBlockEntity(NativeProviderLaneFixtures.HOST_POS.west(2))
                        && sourcePowerNode == sourcePower.getMainNode().getNode()
                        && sourcePowerState.equals(currentPowerState),
                "H2 must not reload H0's already-live source power node");
        if (host == null || !host.hasGridBooted()) return false;
        helper.assertTrue(host.isActive() && host.getGrid() == sourceGrid
                        && host.getInWorldConnections().containsKey(Direction.WEST)
                        && host.getInWorldConnections().get(Direction.WEST).getOtherSide(host).getGrid() == sourceGrid
                        && FederationDomainRegistryAccess.confirmedNetworkId(sourceGrid).filter(sourceId::equals).isPresent(),
                "H2 must join the source through its own physical WEST edge");
        if (stage == 2) {
            remote.register();
            runtime = new ProviderRuntime(helper.getLevel(), remote.managedNode(), remote.composition(), identity,
                    new ProviderOrientation(ProviderFace.EAST), lane -> request, new NativeTargetDomainRegistry());
            runtime.settle();
            placeCable(RED, AEColor.RED);
            stage = 3;
            return false;
        }
        if (stage == 3 && cableReady(RED, sourceGrid, sourceId)) {
            placeCable(BLUE, AEColor.BLUE);
            stage = 4;
        }
        if (stage == 4 && cableReady(BLUE, targetGrid, targetId)) {
            bridge = PartHelper.setPart(helper.getLevel(), helper.absolutePos(RED), Direction.EAST,
                    null, BridgeRegistration.BRIDGE.get());
            helper.assertTrue(bridge != null, "Physical D Bridge must be placeable");
            stage = 5;
        }
        if (stage == 5) {
            bridge.onNeighborChanged(helper.getLevel(), helper.absolutePos(RED), helper.absolutePos(BLUE));
            if (!bridgeReady()) return false;
            var policies = PolicyService.get(helper.getLevel());
            helper.assertTrue(policies.edit(new PolicyEdit(policyKey, policies.revision(policyKey),
                    PolicyRule.enabled(Set.of(PolicyOperation.EXECUTE, PolicyOperation.SUPPLY))))
                    instanceof PolicyMutationResult.Accepted, "D directional Policy must be accepted");
            stage = 6;
        }
        if (stage == 6 && policyActive()) {
            var endpoint = target.endpoint();
            helper.assertTrue(endpoint.claim(new ClaimRequest(endpoint.endpointIdentity(), ClaimEpoch.NONE,
                    new EndpointOwnerIdentity(identity))) instanceof ClaimResult.Acquired
                    && endpoint.activateFederated(), "H2 alone must own D Endpoint Claim");
            request = new ProviderTargetRequest(identity, endpoint.endpointIdentity(), endpoint.claimState().epoch(),
                    helper.absolutePos(ENDPOINT), Direction.WEST, true);
            stage = 7;
        }
        if (stage == 7) assertReady();
        return stage == 7;
    }

    public NativeProviderLaneFixtures remote() {
        return remote;
    }

    public void assertReady() {
        var endpoint = target.endpoint();
        var host = helper.<PatternProviderBlockEntity>getBlockEntity(HOST).getMainNode().getNode();
        helper.assertTrue(bridgeReady() && policyActive() && target.grid() == targetGrid
                        && PolicyService.get(helper.getLevel()).configured(policyKey)
                                .filter(configured -> configured.rule().equals(PolicyRule.enabled(
                                        Set.of(PolicyOperation.EXECUTE, PolicyOperation.SUPPLY)))).isPresent()
                        && host.getGrid() == sourceGrid && host.getInWorldConnections().containsKey(Direction.WEST)
                        && host.getInWorldConnections().get(Direction.WEST).getOtherSide(host).getGrid() == sourceGrid
                        && target.onlyAnchorClaim() && target.settled()
                        && endpoint.claimState().owner().filter(new EndpointOwnerIdentity(identity)::equals).isPresent()
                        && helper.getLevel().getCapability(EndpointTargetCapability.BLOCK,
                                helper.absolutePos(ENDPOINT), Direction.WEST) == endpoint.binding(),
                "D physical Bridge/Federation Domain/Policy, Claim and Endpoint binding must remain active");
    }

    public void assertNativeJobLane(AuthorizedLaneIdentity owner) {
        assertReady();
        helper.assertTrue(runtime.lastResolution() instanceof ProviderTargetResolution.Authorized authorized
                        && authorized.target().position().equals(helper.absolutePos(ENDPOINT))
                        && authorized.target().provider().equals(identity)
                        && owner.lane().laneIndex() == 0 && authorized.target().laneIdentity().equals(owner),
                "H2 native Lane must resolve D and own its return: " + runtime.lastResolution().state());
    }

    private void placeCable(BlockPos position, AEColor color) {
        var absolute = helper.absolutePos(position);
        helper.assertTrue(helper.getLevel().isLoaded(absolute)
                        && (helper.getBlockState(position).isAir() || helper.getBlockState(position).is(Blocks.BARRIER))
                        && helper.getLevel().getBlockEntity(absolute) == null,
                "D cable must not overwrite a device or cross target path: " + position);
        ports.placeCable(position, color);
    }

    private boolean cableReady(BlockPos position, IGrid grid, NetworkId id) {
        var node = GridHelper.getExposedNode(helper.getLevel(), helper.absolutePos(position), Direction.UP);
        return node != null && node.hasGridBooted() && node.isActive() && node.getGrid() == grid
                && FederationDomainRegistryAccess.confirmedNetworkId(grid).filter(id::equals).isPresent();
    }

    private boolean bridgeReady() {
        if (bridge == null) return false;
        var candidate = bridge.membershipCandidate();
        var registry = FederationDomainRegistryAccess.get(helper.getLevel());
        return candidate.isPresent() && candidate.orElseThrow().mainGrid() == sourceGrid
                && candidate.orElseThrow().outerGrid() == targetGrid
                && registry.federationdomainsFor(sourceId).stream().anyMatch(registry.federationdomainsFor(targetId)::contains)
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
                instanceof PolicyMutationResult.Accepted, "D Policy removal");
        if (bridge != null) helper.assertTrue(bridge.getHost().removePart(bridge), "D Bridge removal");
        helper.setBlock(RED, Blocks.AIR);
        helper.setBlock(BLUE, Blocks.AIR);
        remote.close();
        helper.setBlock(HOST, Blocks.AIR);
        for (var position : TRUNK) helper.setBlock(position, Blocks.AIR);
        ports.close();
    }
}
