package space.controlnet.ae2federation.test.scale;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
import space.controlnet.ae2federation.processing.claim.ClaimState;
import space.controlnet.ae2federation.processing.claim.EndpointOwnerIdentity;
import space.controlnet.ae2federation.processing.claim.NativeTargetDomainRegistry;
import space.controlnet.ae2federation.processing.endpoint.EndpointTargetCapability;
import space.controlnet.ae2federation.processing.provider.ProviderFace;
import space.controlnet.ae2federation.processing.provider.ProviderIdentity;
import space.controlnet.ae2federation.processing.provider.ProviderOrientation;
import space.controlnet.ae2federation.processing.provider.ProviderRuntime;
import space.controlnet.ae2federation.processing.provider.ProviderTargetRequest;
import space.controlnet.ae2federation.processing.provider.ProviderTargetResolution;
import space.controlnet.ae2federation.processing.provider.ProviderTargetState;
import space.controlnet.ae2federation.test.port.NativePortFixtures;
import space.controlnet.ae2federation.test.processing.NativeProviderLaneFixtures;

public final class ScaleFederationRoute implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScaleFederationRoute.class);
    private static final BlockPos BRIDGE = new BlockPos(4, 2, 1);
    private static final BlockPos TARGET_START = new BlockPos(5, 2, 1);
    private final GameTestHelper helper;
    private final NativeProviderLaneFixtures provider;
    private final IGridNode sourceStorageNode;
    private final ScaleFederationIdentityTarget target;
    private final NativePortFixtures ports;
    private final IGrid sourceGrid;
    private final IGrid targetGrid;
    private final NetworkId sourceId;
    private final NetworkId targetId;
    private final PolicyKey policyKey;
    private final boolean directProbe;
    private final ProviderIdentity providerIdentity = ProviderIdentity.create();
    private MultipartBridgePart bridge;
    private ProviderRuntime runtime;
    private boolean policyConfigured;
    private boolean pushAccepted;
    private int stage;

    public ScaleFederationRoute(GameTestHelper helper, NativeProviderLaneFixtures provider,
            ScaleFederationIdentityTarget target, IGridNode sourceStorageNode, boolean directProbe) {
        this.helper = helper;
        this.provider = provider;
        this.target = target;
        this.sourceStorageNode = sourceStorageNode;
        this.directProbe = directProbe;
        ports = new NativePortFixtures(helper);
        sourceGrid = provider.managedNode().getGrid();
        targetGrid = target.grid();
        sourceId = FabricRegistryAccess.confirmedNetworkId(sourceGrid).orElseThrow();
        targetId = target.anchorId();
        policyKey = new PolicyKey(sourceId, targetId, PolicyCapability.PROCESSING);
    }

    public boolean tick() {
        if (stage == 0) {
            helper.assertTrue(!commonFabric() && PolicyService.get(helper.getLevel()).configured(policyKey).isEmpty()
                    && target.endpoint().claimState() instanceof ClaimState.Unclaimed,
                    "Route must start without Fabric, Processing Policy, or Endpoint Claim");
            receipt("absent-fabric-policy-claim");
            ports.placeCable(BRIDGE, AEColor.RED);
            stage = 1;
            return false;
        }
        if (stage == 1) {
            if (!cableReady(BRIDGE, sourceGrid, sourceId)) return false;
            receipt("source-cable-settled");
            ports.placeCable(TARGET_START, AEColor.BLUE);
            stage = 2;
            return false;
        }
        if (stage == 2) {
            if (!cableReady(TARGET_START, targetGrid, targetId)) return false;
            receipt("native-boundaries-settled");
            bridge = PartHelper.setPart(helper.getLevel(), helper.absolutePos(BRIDGE), Direction.EAST, null,
                    BridgeRegistration.MULTIPART_BRIDGE.get());
            helper.assertTrue(bridge != null, "Physical Bridge part must be placed on source cable");
            stage = 5;
            return false;
        }
        if (stage == 5) {
            bridge.onNeighborChanged(helper.getLevel(), helper.absolutePos(BRIDGE), helper.absolutePos(TARGET_START));
            if (bridge.membershipCandidate().isEmpty() || !commonFabric()) return false;
            var candidate = bridge.membershipCandidate().orElseThrow();
            helper.assertTrue(candidate.mainGrid() == sourceGrid && candidate.outerGrid() == targetGrid,
                    "Physical Bridge must join only source and target Fabric domains");
            assertSeparated();
            receipt("physical-fabric-active");
            var policies = PolicyService.get(helper.getLevel());
            helper.assertTrue(policies.edit(new PolicyEdit(policyKey, policies.revision(policyKey),
                    PolicyRule.enabled(Set.of(PolicyOperation.EXECUTE, PolicyOperation.SUPPLY))))
                    instanceof PolicyMutationResult.Accepted, "Production Processing Policy edit must be accepted");
            policyConfigured = true;
            stage = 6;
            return false;
        }
        if (stage == 6) {
            helper.assertTrue(policyActive(), "Directional Processing Policy must activate over the physical Fabric");
            receipt("processing-policy-active");
            var endpoint = target.endpoint();
            helper.assertTrue(endpoint.claim(new ClaimRequest(endpoint.endpointIdentity(), ClaimEpoch.NONE,
                    new EndpointOwnerIdentity(providerIdentity))) instanceof ClaimResult.Acquired,
                    "Physical Endpoint Claim must be acquired by the source Provider identity");
            helper.assertTrue(endpoint.activateFederated(), "Claimed Endpoint must activate Federated mode");
            stage = 7;
            return false;
        }
        if (stage == 7) {
            var endpoint = target.endpoint();
            var owner = new EndpointOwnerIdentity(providerIdentity);
            helper.assertTrue(endpoint.claimState().owner().filter(owner::equals).isPresent(),
                    "Endpoint Claim owner must equal the exact physical Provider runtime identity");
            helper.assertTrue(helper.getLevel().getCapability(EndpointTargetCapability.BLOCK,
                    helper.absolutePos(target.endpointPosition()), Direction.WEST) == endpoint.binding(),
                    "Target access must resolve the physical Endpoint binding on its west face");
            receipt("claim-owner-settled");
            var request = new ProviderTargetRequest(providerIdentity, endpoint.endpointIdentity(),
                    endpoint.claimState().epoch(), helper.absolutePos(target.endpointPosition()), Direction.WEST, true);
            var sourceNode = provider.managedNode().getNode();
            var oldEdge = sourceNode.getConnections().stream()
                    .filter(connection -> connection.getOtherSide(sourceNode) == sourceStorageNode).findFirst();
            helper.assertTrue(oldEdge.isPresent() && !oldEdge.orElseThrow().isInWorld(),
                    "Fixture source-storage edge must be the earlier explicit native connection");
            oldEdge.orElseThrow().destroy();
            runtime = new ProviderRuntime(helper.getLevel(), provider.managedNode(), provider.composition(),
                    providerIdentity, new ProviderOrientation(ProviderFace.EAST), () -> request,
                    new NativeTargetDomainRegistry());
            runtime.settle();
            assertSeparated();
            helper.assertTrue(sourceNode.getConnections().stream()
                            .anyMatch(connection -> connection.getOtherSide(sourceNode) == sourceStorageNode
                                    && connection.isInWorld()),
                    "Source storage must reconnect through the physical north adjacency");
            receipt("native-source-edge-handoff");
            stage = 8;
            return false;
        }
        if (stage == 8) {
            bridge.onNeighborChanged(helper.getLevel(), helper.absolutePos(BRIDGE), helper.absolutePos(TARGET_START));
            if (!commonFabric() || !policyActive()) return false;
            receipt("post-wiring-fabric-active");
            if (!directProbe) {
                helper.assertValueEqual(target.targetCobble(), 0L,
                        "Planner route must start with an empty physical target cell");
                stage = 9;
                receipt("planner-route-ready");
                return true;
            }
            pushAccepted = provider.push(0, 0);
            stage = pushAccepted ? 9 : 10;
            receipt("native-push-attempt");
            helper.assertTrue(pushAccepted, "Real native Lane must resolve the selected physical Endpoint target: "
                    + runtime.lastResolution().state());
            helper.assertTrue(runtime.lastResolution() instanceof ProviderTargetResolution.Authorized authorized
                            && authorized.target().position().equals(helper.absolutePos(target.endpointPosition()))
                            && authorized.target().provider().equals(providerIdentity),
                    "Production target binding must select this claimed Endpoint and Provider");
            helper.assertValueEqual(target.targetCobble(), 1L,
                    "One native push must enter the target physical ME cell without a fabricated output");
            assertSeparated();
            helper.assertTrue(target.onlyAnchorClaim() && target.settled(),
                    "Target registry must retain one settled claim after physical Fabric publication");
            receipt("selected-native-target");
        }
        if (stage == 10) {
            helper.assertTrue(false, "Real native Lane rejected the target: " + runtime.lastResolution().state());
        }
        return stage == 9;
    }

    public String status() {
        return "stage=" + stage + " sourceGrid=" + identity(sourceGrid) + " targetGrid=" + identity(targetGrid)
                + " sourceId=" + sourceId + " targetId=" + targetId
                + " bridge=" + (bridge == null ? "absent" : bridge.operationalReason())
                + " commonFabric=" + commonFabric()
                + " resolution=" + (runtime == null ? "absent" : runtime.lastResolution().state())
                + " pushAccepted=" + pushAccepted + " targetClaims=" + target.targetClaims();
    }

    public void assertNativeJobRoute() {
        helper.assertTrue(!directProbe && runtime.lastResolution() instanceof ProviderTargetResolution.Authorized authorized
                        && authorized.target().position().equals(helper.absolutePos(target.endpointPosition()))
                        && authorized.target().provider().equals(providerIdentity),
                "CPU-submitted native Provider must resolve the claimed physical Endpoint: "
                        + runtime.lastResolution().state());
        assertSeparated();
        helper.assertTrue(commonFabric() && policyActive() && target.onlyAnchorClaim() && target.settled(),
                "Completed native job must retain the authorized physical Federation route");
        receipt("planner-native-job-complete");
    }

    public void assertNativeJobRouteIfSubmitted(int completedJobs) {
        assertSeparated();
        helper.assertTrue(commonFabric() && policyActive() && target.onlyAnchorClaim() && target.settled(),
                "Physical Federation route must remain active throughout the native catalog replay");
        if (completedJobs > 0) {
            helper.assertTrue(!directProbe && runtime.lastResolution() instanceof ProviderTargetResolution.Authorized authorized
                            && authorized.target().position().equals(helper.absolutePos(target.endpointPosition()))
                            && authorized.target().provider().equals(providerIdentity),
                    "Native Provider must retain the authorized physical Endpoint route");
        }
    }

    public void assertNativeJobLane(space.controlnet.ae2federation.processing.provider.AuthorizedLaneIdentity lane) {
        assertNativeJobRoute();
        helper.assertTrue(runtime.lastResolution() instanceof ProviderTargetResolution.Authorized authorized
                        && authorized.target().laneIdentity().equals(lane),
                "Endpoint return owner must match the production-authorized native Lane");
    }

    private boolean cableReady(BlockPos position, IGrid grid, NetworkId id) {
        var node = GridHelper.getExposedNode(helper.getLevel(), helper.absolutePos(position), Direction.UP);
        return node != null && node.hasGridBooted() && node.isActive() && node.getGrid() == grid
                && FabricRegistryAccess.confirmedNetworkId(grid).filter(id::equals).isPresent()
                && target.onlyAnchorClaim() && target.settled();
    }

    private boolean commonFabric() {
        var registry = FabricRegistryAccess.get(helper.getLevel());
        var source = registry.fabricsFor(sourceId);
        var destination = registry.fabricsFor(targetId);
        return source.stream().anyMatch(destination::contains);
    }

    private boolean policyActive() {
        return PolicyService.get(helper.getLevel()).activation(policyKey,
                new PolicyRuntimeEndpoints(sourceGrid, targetGrid, BackendStatus.READY)) == PolicyActivationState.ACTIVE;
    }

    private void assertSeparated() {
        helper.assertTrue(provider.managedNode().getGrid() == sourceGrid && target.grid() == targetGrid
                        && sourceGrid != targetGrid
                        && FabricRegistryAccess.confirmedNetworkId(sourceGrid).filter(sourceId::equals).isPresent()
                        && FabricRegistryAccess.confirmedNetworkId(targetGrid).filter(targetId::equals).isPresent(),
                "Physical Bridge must preserve both settled native Grid identities");
        helper.assertTrue(bridge.getMainNode().getNode().getConnections().stream().noneMatch(connection ->
                connection.getOtherSide(bridge.getMainNode().getNode()) == bridge.getExternalFacingNode()),
                "Physical Bridge must not form a native cross-Grid connection");
    }

    private void receipt(String phase) {
        var registry = FabricRegistryAccess.get(helper.getLevel());
        var policies = PolicyService.get(helper.getLevel());
        var line = "AE2F_SCALE_ROUTE phase=" + phase + " sourceGrid=" + identity(sourceGrid)
                + " sourceId=" + sourceId + " targetGrid=" + identity(targetGrid) + " targetId=" + targetId
                + " bridgeReason=" + (bridge == null ? "absent" : bridge.operationalReason())
                + " mainGrid=" + (bridge == null || bridge.getMainNode().getNode() == null ? "absent"
                        : identity(bridge.getMainNode().getGrid()))
                + " outerGrid=" + (bridge == null || bridge.getExternalFacingNode() == null ? "absent"
                        : identity(bridge.getExternalFacingNode().getGrid()))
                + " sourceFabrics=" + registry.fabricsFor(sourceId)
                + " targetFabrics=" + registry.fabricsFor(targetId)
                + " policy=" + policies.configured(policyKey)
                + " claim=" + target.endpoint().claimState()
                + " binding=" + (target.endpoint().binding() != null)
                + " resolution=" + (runtime == null ? "absent" : runtime.lastResolution().state())
                + " pushAccepted=" + pushAccepted
                + " targetCobble=" + target.targetCobble()
                + " targetClaims=" + target.targetClaims();
        LOGGER.info("{}", line);
        var configured = System.getProperty("ae2federation.nativeEvidenceFile", "");
        if (!configured.isBlank()) {
            var path = Path.of(configured).toAbsolutePath().resolveSibling("scale-small-route.log");
            try {
                Files.createDirectories(path.getParent());
                Files.writeString(path, line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot persist physical route receipt", exception);
            }
        }
    }

    private static String identity(IGrid grid) {
        return Integer.toUnsignedString(System.identityHashCode(grid));
    }

    @Override
    public void close() {
        if (policyConfigured) {
            var policies = PolicyService.get(helper.getLevel());
            helper.assertTrue(policies.delete(new PolicyDelete(policyKey, policies.revision(policyKey)))
                    instanceof PolicyMutationResult.Accepted, "Processing Policy must be removed with the fixture");
        }
        if (bridge != null) {
            helper.assertTrue(bridge.getHost().removePart(bridge), "Physical Bridge part must be removed from its host");
        }
        for (var position : new BlockPos[] { TARGET_START, BRIDGE }) {
            helper.setBlock(position, Blocks.AIR);
        }
        ports.close();
    }
}
