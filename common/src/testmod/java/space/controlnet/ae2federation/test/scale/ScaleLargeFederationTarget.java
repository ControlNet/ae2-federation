package space.controlnet.ae2federation.test.scale;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
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

final class ScaleLargeFederationTarget implements AutoCloseable {
    private final GameTestHelper helper;
    private final NativeProviderLaneFixtures provider;
    private final IGrid sourceGrid;
    private final NetworkId sourceId;
    private final BlockPos host;
    private final BlockPos upper;
    private final BlockPos upperEast;
    private final BlockPos bridgePosition;
    private final BlockPos bluePosition;
    private final BlockPos endpointPosition;
    private final ScaleFederationIdentityTarget target;
    private final NativePortFixtures ports;
    private final ProviderIdentity identity = ProviderIdentity.create();
    private MultipartBridgePart bridge;
    private ProviderRuntime runtime;
    private ProviderTargetRequest request;
    private IGrid targetGrid;
    private NetworkId targetId;
    private boolean endpointDomainJoined;
    private int stage;

    ScaleLargeFederationTarget(GameTestHelper helper, ScaleSourceHosts.Scene source, int index, BlockPos host) {
        this.helper = helper;
        this.host = host;
        provider = source.hosts().get(index);
        sourceGrid = source.grid();
        sourceId = source.id();
        upper = host.above();
        upperEast = upper.east();
        bridgePosition = host.east();
        bluePosition = host.east(2);
        endpointPosition = host.east(3).south();
        ports = new NativePortFixtures(helper);
        target = new ScaleFederationIdentityTarget(helper, true, endpointPosition);
    }

    static List<BlockPos> footprint(BlockPos host) {
        var endpoint = host.east(3).south();
        return List.of(host.above(), host.east().above(), host.east(), host.east(2),
                endpoint.north(), endpoint.north().below(), endpoint, endpoint.south(), endpoint.south().east());
    }

    boolean tick() {
        if (stage == 0) {
            if (!target.anchorReady(sourceGrid)) return false;
            targetGrid = target.grid();
            targetId = target.anchorId();
            target.placeEndpoint();
            stage = 1;
            return false;
        }
        if (stage == 1) {
            if (!target.endpointReady() || !target.sameLineage() || !target.settled()) return false;
            target.placeExportBus();
            stage = 2;
            return false;
        }
        if (stage == 2) {
            if (!target.connectExportBus() || !target.exportBusReady() || !target.busLineage()
                    || !target.onlyAnchorClaim()) return false;
            runtime = new ProviderRuntime(helper.getLevel(), provider.managedNode(), provider.composition(), identity,
                    new ProviderOrientation(ProviderFace.EAST), lane -> request, new NativeTargetDomainRegistry());
            runtime.settle();
            ports.placeCable(upper, AEColor.RED);
            stage = 3;
            return false;
        }
        if (stage == 3) {
            if (!cableReady(upper, sourceGrid, sourceId)) return false;
            var providerNode = helper.<PatternProviderBlockEntity>getBlockEntity(host).getMainNode().getNode();
            var upperNode = GridHelper.getExposedNode(helper.getLevel(), helper.absolutePos(upper), Direction.UP);
            helper.assertTrue(providerNode.getGrid() == sourceGrid && providerNode.isActive()
                            && providerNode.meetsChannelRequirements() && providerNode.getUsedChannels() > 0
                            && providerNode.getInWorldConnections().containsKey(Direction.WEST)
                            && providerNode.getInWorldConnections().containsKey(Direction.UP)
                            && upperNode.getInWorldConnections().containsKey(Direction.DOWN)
                            && upperNode.getInWorldConnections().get(Direction.DOWN).getOtherSide(upperNode)
                                    == providerNode,
                    "Federation source leg must use the selected Provider's physical UP edge and keep WEST channel");
            ports.placeCable(upperEast, AEColor.RED);
            stage = 4;
            return false;
        }
        if (stage == 4) {
            if (!cableReady(upperEast, sourceGrid, sourceId)) return false;
            var node = GridHelper.getExposedNode(helper.getLevel(), helper.absolutePos(upperEast), Direction.UP);
            helper.assertTrue(node.getInWorldConnections().containsKey(Direction.WEST),
                    "Federation upper source cable must retain a real predecessor edge");
            ports.placeCable(bridgePosition, AEColor.RED);
            stage = 5;
            return false;
        }
        if (stage == 5) {
            if (!cableReady(bridgePosition, sourceGrid, sourceId)) return false;
            var node = GridHelper.getExposedNode(helper.getLevel(), helper.absolutePos(bridgePosition), Direction.UP);
            helper.assertTrue(node.getInWorldConnections().containsKey(Direction.UP),
                    "Physical Bridge host must be connected to the source upper cable");
            ports.placeCable(bluePosition, AEColor.BLUE);
            stage = 6;
            return false;
        }
        if (stage == 6) {
            if (!cableReady(bluePosition, targetGrid, targetId)) return false;
            var node = GridHelper.getExposedNode(helper.getLevel(), helper.absolutePos(bluePosition), Direction.UP);
            helper.assertTrue(node.getInWorldConnections().containsKey(Direction.EAST),
                    "Blue target cable must join its physical anchor Chest");
            bridge = PartHelper.setPart(helper.getLevel(), helper.absolutePos(bridgePosition), Direction.EAST,
                    null, BridgeRegistration.BRIDGE.get());
            helper.assertTrue(bridge != null, "Physical Federation Bridge must be placeable");
            stage = 7;
            return false;
        }
        if (stage == 7) {
            bridge.onNeighborChanged(helper.getLevel(), helper.absolutePos(bridgePosition),
                    helper.absolutePos(bluePosition));
            if (!bridgeReady()) return false;
            // TEST-ONLY synthetic hub: a Bridge domain has no nodes, so the Endpoint joins the source's domain this way.
            endpointDomainJoined = true;
            helper.assertTrue(endpointInSourceDomain(), "Each target Endpoint must join a domain of the source network");
            stage = 8;
            return false;
        }
        if (stage == 8) {
            if (!endpointInSourceDomain()) return false;
            var endpoint = target.endpoint();
            helper.assertTrue(endpoint.claim(new ClaimRequest(endpoint.endpointIdentity(), ClaimEpoch.NONE,
                    new EndpointOwnerIdentity(identity))) instanceof ClaimResult.Acquired
                    && endpoint.activateFederated(), "Each target Endpoint needs its exact source Provider Claim");
            request = new ProviderTargetRequest(identity, endpoint.endpointIdentity(), endpoint.claimState().epoch(),
                    helper.absolutePos(endpointPosition), Direction.WEST, true);
            stage = 9;
        }
        assertReady();
        return true;
    }

    void assertReady() {
        var endpoint = target.endpoint();
        var providerNode = helper.<PatternProviderBlockEntity>getBlockEntity(host).getMainNode().getNode();
        helper.assertTrue(bridgeReady() && endpointInSourceDomain() && target.grid() == targetGrid
                        && FederationDomainRegistryAccess.confirmedNetworkId(sourceGrid).filter(sourceId::equals).isPresent()
                        && FederationDomainRegistryAccess.confirmedNetworkId(targetGrid).filter(targetId::equals).isPresent()
                        && providerNode.getGrid() == sourceGrid && providerNode.meetsChannelRequirements()
                        && providerNode.getUsedChannels() > 0
                        && providerNode.getInWorldConnections().containsKey(Direction.WEST)
                        && providerNode.getInWorldConnections().containsKey(Direction.UP)
                        && target.onlyAnchorClaim() && target.settled() && target.endpointReady()
                        && target.exportBusReady()
                        && endpoint.claimState().owner().filter(new EndpointOwnerIdentity(identity)::equals).isPresent()
                        && helper.getLevel().getCapability(EndpointTargetCapability.BLOCK,
                                helper.absolutePos(endpointPosition), Direction.WEST) == endpoint.binding(),
                "Each physical Bridge/Federation Domain, Endpoint domain and Claim must remain active without native Grid merge: " + host);
    }

    void assertAuthorized(AuthorizedLaneIdentity owner) {
        assertReady();
        helper.assertTrue(runtime.lastResolution() instanceof ProviderTargetResolution.Authorized authorized
                        && authorized.target().position().equals(helper.absolutePos(endpointPosition))
                        && authorized.target().provider().equals(identity)
                        && authorized.target().laneIdentity().equals(owner)
                        && owner.lane().laneIndex() == 0,
                "Native Lane must resolve its own physical claimed target: " + host + " "
                        + runtime.lastResolution().state());
    }

    ScaleFederationIdentityTarget target() {
        return target;
    }

    IGrid grid() {
        return targetGrid;
    }

    NetworkId id() {
        return targetId;
    }

    BlockPos bridgePosition() {
        return bridgePosition;
    }

    BlockPos endpointPosition() {
        return endpointPosition;
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

    /** Whether the Endpoint is in a domain of the source network; the Endpoint republishes its node when it reloads. */
    private boolean endpointInSourceDomain() {
        return endpointDomainJoined
                && SyntheticEndpointDomain.ensure(helper.getLevel(), helper.absolutePos(endpointPosition), sourceId);
    }

    @Override
    public void close() {
        if (endpointDomainJoined) SyntheticEndpointDomain.remove(helper.getLevel(), helper.absolutePos(endpointPosition), true);
        helper.assertTrue(bridge.getHost().removePart(bridge), "Physical Federation Bridge removal");
        for (var position : List.of(bluePosition, bridgePosition, upperEast, upper)) {
            helper.setBlock(position, Blocks.AIR);
        }
        ports.close();
        target.close();
    }
}
