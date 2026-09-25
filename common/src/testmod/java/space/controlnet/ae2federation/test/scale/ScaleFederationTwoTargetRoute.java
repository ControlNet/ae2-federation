package space.controlnet.ae2federation.test.scale;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.util.AEColor;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import space.controlnet.ae2federation.test.port.NativePortFixtures;
import space.controlnet.ae2federation.test.processing.NativeProviderLaneFixtures;

public final class ScaleFederationTwoTargetRoute implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScaleFederationTwoTargetRoute.class);
    private static final BlockPos[] BRIDGES = { new BlockPos(4, 2, 1), new BlockPos(4, 2, 5) };
    private static final BlockPos[] TARGET_CABLES = { new BlockPos(5, 2, 1), new BlockPos(5, 2, 5) };
    private static final BlockPos[] SOURCE_EXTENSION = { new BlockPos(4, 3, 1), new BlockPos(4, 3, 2),
            new BlockPos(4, 3, 3), new BlockPos(4, 3, 4), new BlockPos(4, 3, 5) };
    private final GameTestHelper helper;
    private final NativeProviderLaneFixtures provider;
    private final IGridNode sourceStorageNode;
    private final List<ScaleFederationIdentityTarget> targets;
    private final NativePortFixtures ports;
    private final IGrid sourceGrid;
    private final NetworkId sourceId;
    private final List<IGrid> targetGrids;
    private final List<NetworkId> targetIds;
    private final List<PolicyKey> policyKeys;
    private final ProviderIdentity identity = ProviderIdentity.create();
    private final MultipartBridgePart[] bridges = new MultipartBridgePart[2];
    private final ProviderTargetRequest[] requests = new ProviderTargetRequest[2];
    private ProviderRuntime runtime;
    private int stage;
    private int extensionIndex;

    public ScaleFederationTwoTargetRoute(GameTestHelper helper, NativeProviderLaneFixtures provider,
            IGridNode sourceStorageNode, ScaleFederationIdentityTarget first, ScaleFederationIdentityTarget second) {
        this.helper = helper;
        this.provider = provider;
        this.sourceStorageNode = sourceStorageNode;
        targets = List.of(first, second);
        ports = new NativePortFixtures(helper);
        sourceGrid = provider.managedNode().getGrid();
        sourceId = FabricRegistryAccess.confirmedNetworkId(sourceGrid).orElseThrow();
        targetGrids = targets.stream().map(ScaleFederationIdentityTarget::grid).toList();
        targetIds = targets.stream().map(ScaleFederationIdentityTarget::anchorId).toList();
        policyKeys = targetIds.stream().map(id -> new PolicyKey(sourceId, id, PolicyCapability.PROCESSING)).toList();
    }

    public boolean tick() {
        assertSeparated();
        if (stage == 0) {
            for (int lane = 0; lane < 2; lane++) {
                helper.assertTrue(!commonFabric(lane) && PolicyService.get(helper.getLevel())
                        .configured(policyKeys.get(lane)).isEmpty(), "Each route starts without Fabric or Policy");
            }
            ports.placeCable(BRIDGES[0], AEColor.RED);
            stage++;
        } else if (stage == 1 && cableReady(BRIDGES[0], sourceGrid, sourceId)) {
            ports.placeCable(TARGET_CABLES[0], AEColor.BLUE);
            stage++;
        } else if (stage == 2 && cableReady(TARGET_CABLES[0], targetGrids.get(0), targetIds.get(0))) {
            bridges[0] = placeBridge(0);
            stage++;
        } else if (stage == 3) {
            refresh(0);
            if (bridgeReady(0)) {
                configurePolicy(0);
                stage++;
            }
        } else if (stage == 4 && policyActive(0)) {
            for (int lane = 0; lane < 2; lane++) claim(lane);
            var sourceNode = provider.managedNode().getNode();
            var edge = sourceNode.getConnections().stream()
                    .filter(connection -> connection.getOtherSide(sourceNode) == sourceStorageNode).findFirst();
            helper.assertTrue(edge.isPresent() && !edge.orElseThrow().isInWorld(),
                    "Source storage must have the fixture-created non-world edge before physical handoff");
            edge.orElseThrow().destroy();
            runtime = new ProviderRuntime(helper.getLevel(), provider.managedNode(), provider.composition(),
                     identity, new ProviderOrientation(ProviderFace.EAST), lane -> requests[lane],
                    new NativeTargetDomainRegistry());
            runtime.settle();
            helper.assertTrue(sourceNode.getConnections().stream().anyMatch(connection ->
                    connection.getOtherSide(sourceNode) == sourceStorageNode && connection.isInWorld()),
                    "Source storage must reconnect through the physical north face");
            stage++;
        } else if (stage == 5) {
            refresh(0);
            if (!bridgeReady(0) || !policyActive(0)) return false;
            ports.placeCable(SOURCE_EXTENSION[0], AEColor.RED);
            stage = 6;
        } else if (stage == 6 && cableReady(SOURCE_EXTENSION[extensionIndex], sourceGrid, sourceId)) {
            extensionIndex++;
            if (extensionIndex < SOURCE_EXTENSION.length) {
                ports.placeCable(SOURCE_EXTENSION[extensionIndex], AEColor.RED);
                return false;
            }
            ports.placeCable(BRIDGES[1], AEColor.RED);
            stage = 7;
        } else if (stage == 7 && cableReady(BRIDGES[1], sourceGrid, sourceId)) {
            ports.placeCable(TARGET_CABLES[1], AEColor.BLUE);
            stage++;
        } else if (stage == 8 && cableReady(TARGET_CABLES[1], targetGrids.get(1), targetIds.get(1))) {
            bridges[1] = placeBridge(1);
            stage++;
        } else if (stage == 9) {
            refresh(1);
            if (bridgeReady(1)) {
                configurePolicy(1);
                stage++;
            }
        } else if (stage == 10) {
            refresh(0);
            refresh(1);
            if (!bridgeReady(0) || !bridgeReady(1) || !policyActive(0) || !policyActive(1)) return false;
            assertReady();
            LOGGER.info("AE2F_SCALE_FEDERATION_TWO_ROUTE sourceId={} targetA={} targetB={} "
                            + "bridgeA={} bridgeB={} policyA=ACTIVE policyB=ACTIVE claimA={} claimB={}",
                    sourceId.value(), targetIds.get(0).value(), targetIds.get(1).value(),
                    bridges[0].operationalReason(), bridges[1].operationalReason(),
                    targets.get(0).endpoint().claimState(), targets.get(1).endpoint().claimState());
            stage++;
        }
        return stage == 11;
    }

    public void assertReady() {
        assertSeparated();
        for (int lane = 0; lane < 2; lane++) {
            var endpoint = targets.get(lane).endpoint();
            helper.assertTrue(bridgeReady(lane) && policyActive(lane) && targets.get(lane).onlyAnchorClaim()
                            && targets.get(lane).settled()
                            && endpoint.claimState().owner().filter(new EndpointOwnerIdentity(identity)::equals).isPresent()
                            && helper.getLevel().getCapability(EndpointTargetCapability.BLOCK,
                                    helper.absolutePos(targets.get(lane).endpointPosition()), Direction.WEST)
                                    == endpoint.binding(),
                    "Each distinct physical Fabric, directional Policy and owned Endpoint must remain active: " + lane);
        }
    }

    public void assertNativeJobLane(int lane, AuthorizedLaneIdentity owner) {
        assertReady();
        helper.assertTrue(runtime.lastResolution() instanceof ProviderTargetResolution.Authorized authorized
                        && authorized.target().position().equals(helper.absolutePos(targets.get(lane).endpointPosition()))
                        && authorized.target().provider().equals(identity)
                        && owner.lane().laneIndex() == lane
                        && authorized.target().laneIdentity().equals(owner),
                "Native Lane must resolve its own claimed physical Endpoint: " + lane + " "
                        + runtime.lastResolution().state());
    }

    public void assertSelectedLaneDestination(int lane, AEItemKey inputKey) {
        if (runtime.lastResolution() instanceof ProviderTargetResolution.Authorized authorized
                && authorized.target().laneIdentity().lane().laneIndex() == lane) {
            helper.assertTrue(authorized.target().position().equals(helper.absolutePos(
                            targets.get(lane).endpointPosition())),
                    "Federation target " + (lane == 1 ? "B" : "A") + " missing input: native Lane " + lane
                            + " resolved the opposite claimed Endpoint at " + authorized.target().position()
                            + " instead of " + helper.absolutePos(targets.get(lane).endpointPosition())
                            + "; selected target cell=" + targets.get(lane).inputAmount(inputKey));
        }
    }

    public String status() {
        return "stage=" + stage + " bridgeA=" + (bridges[0] == null ? "absent" : bridges[0].operationalReason())
                + " bridgeB=" + (bridges[1] == null ? "absent" : bridges[1].operationalReason())
                + " routeA=" + commonFabric(0) + "/" + policyActive(0)
                + " routeB=" + commonFabric(1) + "/" + policyActive(1);
    }

    private void claim(int lane) {
        var target = targets.get(lane);
        var endpoint = target.endpoint();
        helper.assertTrue(endpoint.claim(new ClaimRequest(endpoint.endpointIdentity(), ClaimEpoch.NONE,
                new EndpointOwnerIdentity(identity))) instanceof ClaimResult.Acquired,
                "Each physical Endpoint Claim must be independently acquired: " + lane);
        helper.assertTrue(endpoint.activateFederated(), "Each claimed Endpoint must activate Federated mode");
        requests[lane] = new ProviderTargetRequest(identity, endpoint.endpointIdentity(),
                endpoint.claimState().epoch(), helper.absolutePos(target.endpointPosition()), Direction.WEST, true);
    }

    private MultipartBridgePart placeBridge(int lane) {
        var bridge = PartHelper.setPart(helper.getLevel(), helper.absolutePos(BRIDGES[lane]), Direction.EAST,
                null, BridgeRegistration.MULTIPART_BRIDGE.get());
        helper.assertTrue(bridge != null, "Both physical Bridges must be placed");
        return bridge;
    }

    private void refresh(int lane) {
        bridges[lane].onNeighborChanged(helper.getLevel(), helper.absolutePos(BRIDGES[lane]),
                helper.absolutePos(TARGET_CABLES[lane]));
    }

    private boolean bridgeReady(int lane) {
        if (bridges[lane] == null) return false;
        var candidate = bridges[lane].membershipCandidate();
        return candidate.isPresent() && candidate.orElseThrow().mainGrid() == sourceGrid
                && candidate.orElseThrow().outerGrid() == targetGrids.get(lane) && commonFabric(lane)
                && bridges[lane].getMainNode().getNode().getConnections().stream().noneMatch(connection ->
                        connection.getOtherSide(bridges[lane].getMainNode().getNode())
                                == bridges[lane].getExternalFacingNode());
    }

    private void configurePolicy(int lane) {
        var policies = PolicyService.get(helper.getLevel());
        var key = policyKeys.get(lane);
        helper.assertTrue(policies.edit(new PolicyEdit(key, policies.revision(key),
                PolicyRule.enabled(Set.of(PolicyOperation.EXECUTE, PolicyOperation.SUPPLY))))
                instanceof PolicyMutationResult.Accepted, "Directional EXECUTE/SUPPLY Policy must be accepted");
    }

    private boolean cableReady(BlockPos position, IGrid grid, NetworkId id) {
        var node = GridHelper.getExposedNode(helper.getLevel(), helper.absolutePos(position), Direction.UP);
        return node != null && node.hasGridBooted() && node.isActive() && node.getGrid() == grid
                && FabricRegistryAccess.confirmedNetworkId(grid).filter(id::equals).isPresent();
    }

    private boolean commonFabric(int lane) {
        var registry = FabricRegistryAccess.get(helper.getLevel());
        return registry.fabricsFor(sourceId).stream().anyMatch(registry.fabricsFor(targetIds.get(lane))::contains);
    }

    private boolean policyActive(int lane) {
        return PolicyService.get(helper.getLevel()).activation(policyKeys.get(lane),
                new PolicyRuntimeEndpoints(sourceGrid, targetGrids.get(lane), BackendStatus.READY))
                == PolicyActivationState.ACTIVE;
    }

    private void assertSeparated() {
        helper.assertTrue(provider.managedNode().getGrid() == sourceGrid
                        && sourceGrid != targetGrids.get(0) && sourceGrid != targetGrids.get(1)
                        && targetGrids.get(0) != targetGrids.get(1)
                        && !sourceId.equals(targetIds.get(0)) && !sourceId.equals(targetIds.get(1))
                        && !targetIds.get(0).equals(targetIds.get(1))
                        && FabricRegistryAccess.confirmedNetworkId(sourceGrid).filter(sourceId::equals).isPresent()
                        && FabricRegistryAccess.confirmedNetworkId(targetGrids.get(0))
                                .filter(targetIds.get(0)::equals).isPresent()
                        && FabricRegistryAccess.confirmedNetworkId(targetGrids.get(1))
                                .filter(targetIds.get(1)::equals).isPresent(),
                "Three native Grids and confirmed NetworkIds must remain pairwise distinct");
    }

    @Override
    public void close() {
        var policies = PolicyService.get(helper.getLevel());
        for (var key : policyKeys) {
            helper.assertTrue(policies.delete(new PolicyDelete(key, policies.revision(key)))
                    instanceof PolicyMutationResult.Accepted, "Both Processing Policies must be removed");
        }
        for (var bridge : bridges) {
            if (bridge != null) helper.assertTrue(bridge.getHost().removePart(bridge), "Physical Bridge removal");
        }
        for (var position : List.of(BRIDGES[0], BRIDGES[1], TARGET_CABLES[0], TARGET_CABLES[1])) {
            helper.setBlock(position, Blocks.AIR);
        }
        for (var position : SOURCE_EXTENSION) helper.setBlock(position, Blocks.AIR);
        ports.close();
    }
}
