package space.controlnet.ae2federation.test.scale;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.util.AEColor;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.controlnet.ae2federation.bridge.BridgeRegistration;
import space.controlnet.ae2federation.bridge.MultipartBridgePart;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
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
import space.controlnet.ae2federation.test.processing.SyntheticEndpointDomain;

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
    private final boolean[] endpointDomainJoined = new boolean[2];
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
        sourceId = FederationDomainRegistryAccess.confirmedNetworkId(sourceGrid).orElseThrow();
        targetGrids = targets.stream().map(ScaleFederationIdentityTarget::grid).toList();
        targetIds = targets.stream().map(ScaleFederationIdentityTarget::anchorId).toList();
    }

    public boolean tick() {
        assertSeparated();
        if (stage == 0) {
            for (int lane = 0; lane < 2; lane++) {
                helper.assertTrue(!commonFederationDomain(lane) && !endpointDomainPresent(lane),
                        "Each route starts without Federation Domain or Endpoint domain");
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
                joinEndpointDomain(0);
                stage++;
            }
        } else if (stage == 4 && endpointInSourceDomain(0)) {
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
            if (!bridgeReady(0) || !endpointInSourceDomain(0)) return false;
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
                joinEndpointDomain(1);
                stage++;
            }
        } else if (stage == 10) {
            refresh(0);
            refresh(1);
            if (!bridgeReady(0) || !bridgeReady(1) || !endpointInSourceDomain(0) || !endpointInSourceDomain(1)) return false;
            assertReady();
            LOGGER.info("AE2F_SCALE_FEDERATION_TWO_ROUTE sourceId={} targetA={} targetB={} "
                            + "bridgeA={} bridgeB={} endpointDomainA=ACTIVE endpointDomainB=ACTIVE claimA={} claimB={}",
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
            helper.assertTrue(bridgeReady(lane) && endpointInSourceDomain(lane) && targets.get(lane).onlyAnchorClaim()
                            && targets.get(lane).settled()
                            && endpoint.claimState().owner().filter(new EndpointOwnerIdentity(identity)::equals).isPresent()
                            && helper.getLevel().getCapability(EndpointTargetCapability.BLOCK,
                                    helper.absolutePos(targets.get(lane).endpointPosition()), Direction.WEST)
                                    == endpoint.binding(),
                    "Each distinct physical Federation Domain, Endpoint domain and owned Endpoint must remain active: " + lane);
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
                + " routeA=" + commonFederationDomain(0) + "/" + endpointDomainPresent(0)
                + " routeB=" + commonFederationDomain(1) + "/" + endpointDomainPresent(1);
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
                null, BridgeRegistration.BRIDGE.get());
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
                && candidate.orElseThrow().outerGrid() == targetGrids.get(lane) && commonFederationDomain(lane)
                && bridges[lane].getMainNode().getNode().getConnections().stream().noneMatch(connection ->
                        connection.getOtherSide(bridges[lane].getMainNode().getNode())
                                == bridges[lane].getExternalFacingNode());
    }

    /** TEST-ONLY synthetic hub: a Bridge domain has no nodes, so the Endpoint joins the source's domain this way. */
    private void joinEndpointDomain(int lane) {
        endpointDomainJoined[lane] = true;
        helper.assertTrue(endpointInSourceDomain(lane), "The Endpoint must join a domain of the source network: " + lane);
    }

    private boolean cableReady(BlockPos position, IGrid grid, NetworkId id) {
        var node = GridHelper.getExposedNode(helper.getLevel(), helper.absolutePos(position), Direction.UP);
        return node != null && node.hasGridBooted() && node.isActive() && node.getGrid() == grid
                && FederationDomainRegistryAccess.confirmedNetworkId(grid).filter(id::equals).isPresent();
    }

    private boolean commonFederationDomain(int lane) {
        var registry = FederationDomainRegistryAccess.get(helper.getLevel());
        return registry.federationdomainsFor(sourceId).stream().anyMatch(registry.federationdomainsFor(targetIds.get(lane))::contains);
    }

    /** Whether the lane's Endpoint is in a domain of the source network; the Endpoint republishes its node when it reloads. */
    private boolean endpointInSourceDomain(int lane) {
        return endpointDomainJoined[lane] && SyntheticEndpointDomain.ensure(helper.getLevel(),
                helper.absolutePos(targets.get(lane).endpointPosition()), sourceId);
    }

    private boolean endpointDomainPresent(int lane) {
        return SyntheticEndpointDomain.domain(helper.getLevel(), helper.absolutePos(targets.get(lane).endpointPosition()),
                sourceId).isPresent();
    }

    private void assertSeparated() {
        helper.assertTrue(provider.managedNode().getGrid() == sourceGrid
                        && sourceGrid != targetGrids.get(0) && sourceGrid != targetGrids.get(1)
                        && targetGrids.get(0) != targetGrids.get(1)
                        && !sourceId.equals(targetIds.get(0)) && !sourceId.equals(targetIds.get(1))
                        && !targetIds.get(0).equals(targetIds.get(1))
                        && FederationDomainRegistryAccess.confirmedNetworkId(sourceGrid).filter(sourceId::equals).isPresent()
                        && FederationDomainRegistryAccess.confirmedNetworkId(targetGrids.get(0))
                                .filter(targetIds.get(0)::equals).isPresent()
                        && FederationDomainRegistryAccess.confirmedNetworkId(targetGrids.get(1))
                                .filter(targetIds.get(1)::equals).isPresent(),
                "Three native Grids and confirmed NetworkIds must remain pairwise distinct");
    }

    @Override
    public void close() {
        for (int lane = 0; lane < 2; lane++) {
            if (endpointDomainJoined[lane]) SyntheticEndpointDomain.remove(helper.getLevel(),
                    helper.absolutePos(targets.get(lane).endpointPosition()), true);
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
