package space.controlnet.ae2federation.test;

import appeng.api.networking.GridHelper;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.domain.port.RouterPortBinding;
import space.controlnet.ae2federation.domain.port.RouterPortKind;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.test.router.RouterEvidence;
import space.controlnet.ae2federation.test.router.RouterFixtures;

@PrefixGameTestTemplate(false)
public final class RouterGameTests {
    private static final BlockPos CENTER = new BlockPos(6, 6, 6);

    private RouterGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void routerMixedSixFaces(GameTestHelper helper) {
        var fixtures = new RouterFixtures(helper);
        var router = fixtures.placeSwitch(CENTER);
        fixtures.placeNativeDevice(CENTER, Direction.DOWN);
        fixtures.placeNativeCable(CENTER, Direction.UP);
        fixtures.placeNativeDevice(CENTER, Direction.NORTH);
        fixtures.placeFederationCable(CENTER, Direction.SOUTH);
        fixtures.placeFederationCable(CENTER, Direction.WEST);
        fixtures.placeFederationCable(CENTER, Direction.EAST);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(fixtures.count(router, RouterPortKind.NATIVE_ME), 3L,
                    "Mixed Router must resolve three native faces");
            helper.assertValueEqual(fixtures.count(router, RouterPortKind.FEDERATION), 3L,
                    "Mixed Router must resolve three Federation faces");
            helper.assertTrue(!fixtures.hasInternalNativeConnection(router), "Router must not join boundary nodes");
            helper.assertTrue(fixtures.allFederationLinksAreReciprocal(router),
                    "Federation faces must form reciprocal custom links");
            helper.assertTrue(fixtures.allBoundaryNodesUseZeroIdlePower(router), "Router ports must use no Federation Domain power");
            var downOwner = ((RouterPortBinding.Native) router.binding(Direction.DOWN)).attachment().neighborNode().getOwner();
            var upOwner = ((RouterPortBinding.Native) router.binding(Direction.UP)).attachment().neighborNode().getOwner();
            helper.assertTrue(downOwner.getClass() != upOwner.getClass(), "Native devices and cables must both resolve");
            RouterEvidence.write("routermixedsixfaces", 9, Map.of(
                    "faceCount", "6", "nativeCount", "3", "federationCount", "3",
                    "disconnectedCount", "0", "nativeGridCount", "3", "nativeJoin", "false",
                    "federationCustomLinks", "3", "federationDomainPowerRequired", "false",
                    "cableAndDeviceAccepted", "true"));
            fixtures.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void routerSixIndependentMe(GameTestHelper helper) {
        var fixtures = new RouterFixtures(helper);
        var router = fixtures.placeSwitch(CENTER);
        for (var face : Direction.values()) {
            fixtures.placeNativeDevice(CENTER, face);
        }
        helper.succeedWhen(() -> {
            helper.assertValueEqual(fixtures.count(router, RouterPortKind.NATIVE_ME), 6L,
                    "Every Router face must resolve independently");
            helper.assertValueEqual(router.nativeFacesByGrid().size(), 6, "Six native neighbors must retain six Grids");
            helper.assertTrue(!fixtures.hasInternalNativeConnection(router), "Router must not create native cross-face edges");
            var facts = new LinkedHashMap<String, String>();
            facts.put("faceCount", "6");
            facts.put("nativeCount", "6");
            facts.put("distinctGridCount", "6");
            facts.put("nativeJoin", "false");
            for (var face : Direction.values()) {
                var grid = ((RouterPortBinding.Native) router.binding(face)).attachment().grid();
                facts.put("face." + face.getSerializedName(), identity(grid));
            }
            RouterEvidence.write("routersixindependentme", 10, facts);
            fixtures.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void routerRepeatNetwork(GameTestHelper helper) {
        var fixtures = new RouterFixtures(helper);
        var router = fixtures.placeSwitch(CENTER);
        for (var face : Direction.values()) {
            fixtures.placeNativeDevice(CENTER, face);
        }
        var joined = new boolean[1];
        helper.succeedWhen(() -> {
            if (!joined[0]) {
                GridHelper.createConnection(fixtures.nativeDeviceNode(CENTER, Direction.NORTH),
                        fixtures.nativeDeviceNode(CENTER, Direction.SOUTH));
                joined[0] = true;
                helper.assertTrue(false, "Waiting for repeated native Grid identity to settle");
            }
            helper.assertValueEqual(fixtures.count(router, RouterPortKind.NATIVE_ME), 6L,
                    "Repeated Grid must not remove either face");
            helper.assertValueEqual(router.nativeFacesByGrid().size(), 5,
                    "Repeated Grid membership must deduplicate by identity");
            var north = ((RouterPortBinding.Native) router.binding(Direction.NORTH)).attachment().grid();
            var south = ((RouterPortBinding.Native) router.binding(Direction.SOUTH)).attachment().grid();
            helper.assertTrue(north == south, "North and south must retain ownership of the repeated Grid");
            helper.assertTrue(!fixtures.hasInternalNativeConnection(router), "Repeated membership must not be a native join");
            RouterEvidence.write("routerrepeatnetwork", 8, Map.of(
                    "faceCount", "6", "nativeCount", "6", "distinctGridCount", "5",
                    "repeatedFacesOwned", "2", "repeatDeduplicated", "true", "nativeJoin", "false",
                    "face.north", identity(north), "face.south", identity(south)));
            fixtures.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void routerPortReplacement(GameTestHelper helper) {
        var fixtures = new RouterFixtures(helper);
        var router = fixtures.placeSwitch(CENTER);
        fixtures.placeNativeDevice(CENTER, Direction.EAST);
        for (var face : Direction.values()) {
            if (face != Direction.EAST) {
                fixtures.placeFederationCable(CENTER, face);
            }
        }
        var phase = new int[1];
        var unaffected = new RouterPortBinding[1];
        helper.succeedWhen(() -> {
            if (phase[0] == 0) {
                helper.assertValueEqual(router.binding(Direction.EAST).kind(), RouterPortKind.NATIVE_ME,
                        "Replacement fixture must begin with a native attachment");
                unaffected[0] = router.binding(Direction.WEST);
                fixtures.placeFederationCable(CENTER, Direction.EAST);
                // The face resolves again in the block update, so it may already see the cable, never the old chest.
                helper.assertTrue(router.binding(Direction.EAST).kind() != RouterPortKind.NATIVE_ME,
                        "Replaced native binding must invalidate immediately");
                helper.assertTrue(router.binding(Direction.WEST) == unaffected[0],
                        "Unchanged face binding must retain ownership");
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for Federation replacement binding");
            }
            if (phase[0] == 1) {
                helper.assertValueEqual(router.binding(Direction.EAST).kind(), RouterPortKind.FEDERATION,
                        "Affected face must recreate as a Federation binding");
                fixtures.placeUnsupported(CENTER, Direction.EAST);
                helper.assertValueEqual(router.binding(Direction.EAST).kind(), RouterPortKind.DISCONNECTED,
                        "Unsupported replacement must invalidate immediately");
                helper.assertTrue(router.binding(Direction.WEST) == unaffected[0],
                        "Unsupported replacement must not recreate another face");
                phase[0] = 2;
                helper.assertTrue(false, "Waiting for unsupported replacement settlement");
            }
            helper.assertValueEqual(router.binding(Direction.EAST).kind(), RouterPortKind.DISCONNECTED,
                    "Unsupported replacement must remain disconnected");
            RouterEvidence.write("routerportreplacement", 8, Map.of(
                    "initialNative", "true", "invalidatedImmediately", "true",
                    "federationReplacement", "true", "unsupportedReplacementAccepted", "false",
                    "unaffectedBindingPreserved", "true", "staleNativeAccepted", "false",
                    "affectedFace", "east", "nativeJoin", "false"));
            fixtures.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void routerRejectUnsupported(GameTestHelper helper) {
        var fixtures = new RouterFixtures(helper);
        var router = fixtures.placeSwitch(CENTER);
        for (var face : Direction.values()) {
            fixtures.placeFederationCable(CENTER, face);
        }
        var unsupportedPosition = new BlockPos(2, 6, 2);
        var unsupportedRouter = fixtures.placeSwitch(unsupportedPosition);
        fixtures.placeUnsupported(unsupportedPosition, Direction.NORTH);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(fixtures.count(router, RouterPortKind.FEDERATION), 6L,
                    "All six Router faces must accept Federation Cable capabilities");
            helper.assertValueEqual(unsupportedRouter.binding(Direction.NORTH).kind(), RouterPortKind.DISCONNECTED,
                    "Unsupported neighbor must fail closed");
            helper.assertTrue(unsupportedRouter.boundaryNode(Direction.NORTH) == null,
                    "Unsupported neighbor must not create a node, so no native edge");
            helper.assertTrue(!fixtures.hasInternalNativeConnection(router), "Federation layout must not join native Grids");
            helper.assertTrue(fixtures.allBoundaryNodesUseZeroIdlePower(router), "Federation topology must need no power");
            RouterEvidence.write("routerrejectunsupported", 8, Map.of(
                    "allSixFederation", "true", "customFaceCount", "6",
                    "unsupportedAccepted", "false", "unsupportedKind", "disconnected",
                    "unsupportedNativeEdge", "false", "nativeJoin", "false",
                    "loadedNeighborOnly", "true", "federationDomainPowerRequired", "false"));
            fixtures.close();
        });
    }

    /**
     * Two Switches placed face to face link directly, as through Federation Cable: the touching faces resolve as
     * Federation, their boundary nodes stay unconnected (no two-node native Grid between them), and the networks on
     * the far faces share a domain. Replacing the second Switch with a native device turns the face native again.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void routerAdjacentFederation(GameTestHelper helper) {
        var fixtures = new RouterFixtures(helper);
        var second = CENTER.east();
        fixtures.placeNativeDevice(CENTER, Direction.WEST);
        fixtures.placeNativeDevice(second, Direction.EAST);
        var registry = space.controlnet.ae2federation.domain.FederationDomainRegistryAccess.get(helper.getLevel());
        var phase = new int[1];
        helper.succeedWhen(() -> {
            if (phase[0] == 0) {
                for (var grid : new appeng.api.networking.IGrid[] {
                        fixtures.nativeDeviceNode(CENTER, Direction.WEST).getGrid(),
                        fixtures.nativeDeviceNode(second, Direction.EAST).getGrid()}) {
                    helper.assertTrue(space.controlnet.ae2federation.domain.FederationDomainRegistryAccess
                            .confirmedNetworkId(grid).isPresent(), "Waiting for native identities to settle");
                }
                fixtures.placeSwitch(CENTER);
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for the first Router");
            }
            var first = fixtures.router(CENTER);
            if (phase[0] == 1) {
                helper.assertValueEqual(first.binding(Direction.WEST).kind(), RouterPortKind.NATIVE_ME,
                        "The first Router must join its native network");
                fixtures.placeSwitch(second);
                phase[0] = 2;
                helper.assertTrue(false, "Waiting for the adjacent Router");
            }
            if (phase[0] == 2) {
                var adjacent = fixtures.router(second);
                helper.assertValueEqual(first.binding(Direction.EAST).kind(), RouterPortKind.FEDERATION,
                        "The existing Router's face must link to the adjacent Router");
                helper.assertValueEqual(adjacent.binding(Direction.WEST).kind(), RouterPortKind.FEDERATION,
                        "The new Router's face must link to the existing Router");
                helper.assertTrue(first.boundaryNode(Direction.EAST) == null
                        && adjacent.boundaryNode(Direction.WEST) == null,
                        "Touching Router faces must have no node, so no native Grid between them");
                helper.assertTrue(fixtures.allFederationLinksAreReciprocal(first)
                        && fixtures.allFederationLinksAreReciprocal(adjacent), "The direct link must be reciprocal");
                helper.assertTrue(!fixtures.hasInternalNativeConnection(first)
                        && !fixtures.hasInternalNativeConnection(adjacent), "Routers must not join native Grids");
                var left = network(helper, first, Direction.WEST);
                var right = network(helper, adjacent, Direction.EAST);
                helper.assertTrue(!registry.federationdomainsFor(left).isEmpty()
                        && registry.federationdomainsFor(left).equals(registry.federationdomainsFor(right)),
                        "Networks on adjacent Routers must share a Federation domain");
                fixtures.nativePorts().placeChest(second);
                phase[0] = 3;
                helper.assertTrue(false, "Waiting for the native replacement");
            }
            helper.assertValueEqual(first.binding(Direction.EAST).kind(), RouterPortKind.NATIVE_ME,
                    "A native device replacing the adjacent Router must turn the face native");
            helper.assertTrue(!first.boundaryNode(Direction.EAST).getConnections().isEmpty(),
                    "The face must expose its node again once no Router faces it");
            RouterEvidence.write("routeradjacentfederation", 9, Map.of(
                    "adjacentFederation", "true", "reciprocal", "true", "touchingNativeEdge", "false",
                    "sharedDomain", "true", "nativeJoin", "false", "replacedNative", "true",
                    "placement", "sequential"));
            fixtures.close();
        });
    }

    /**
     * A Router attaches Federation ports only: an ME chest and an ME cable beside it stay disconnected and unconnected,
     * as the Router has no boundary node on any face, while a Federation Cable on another face links.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void routerRefusesMeNetworks(GameTestHelper helper) {
        var fixtures = new RouterFixtures(helper);
        var router = fixtures.placeRouter(CENTER);
        fixtures.placeNativeDevice(CENTER, Direction.DOWN);
        fixtures.placeNativeCable(CENTER, Direction.UP);
        fixtures.placeFederationCable(CENTER, Direction.SOUTH);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(router.binding(Direction.SOUTH).kind(), RouterPortKind.FEDERATION,
                    "The Router must link to the Federation Cable");
            helper.assertValueEqual(router.binding(Direction.DOWN).kind(), RouterPortKind.DISCONNECTED,
                    "The Router must not attach the ME chest");
            helper.assertValueEqual(router.binding(Direction.UP).kind(), RouterPortKind.DISCONNECTED,
                    "The Router must not attach the ME cable");
            helper.assertValueEqual(fixtures.count(router, RouterPortKind.NATIVE_ME), 0L,
                    "No Router face may resolve native");
            var noNodes = true;
            for (var face : Direction.values()) {
                noNodes &= router.boundaryNode(face) == null && router.getGridNode(face) == null;
            }
            helper.assertTrue(noNodes, "The Router must expose no grid node on any face");
            helper.assertTrue(fixtures.nativeDeviceNode(CENTER, Direction.DOWN).getConnections().isEmpty(),
                    "The ME chest must have no connection toward the Router");
            RouterEvidence.write("routerrefusesmenetworks", 6, Map.of(
                    "federationFace", "south", "nativeCount", "0", "chestAttached", "false",
                    "cableAttached", "false", "gridNodes", "0"));
            fixtures.close();
        });
    }

    /**
     * ME networks reach a Federation domain through Switches, and Routers carry it between them: two Switches, each
     * with an ME chest, joined by cable through a Router, put both networks in one domain, and the Router's faces are
     * all Federation.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void switchesJoinThroughRouter(GameTestHelper helper) {
        var fixtures = new RouterFixtures(helper);
        var left = CENTER.west(3);
        var right = CENTER.east(3);
        var leftSwitch = fixtures.placeSwitch(left);
        var rightSwitch = fixtures.placeSwitch(right);
        fixtures.placeNativeDevice(left, Direction.WEST);
        fixtures.placeNativeDevice(right, Direction.EAST);
        for (var position : new BlockPos[] {CENTER.west(2), CENTER.west(), CENTER.east(), CENTER.east(2)}) {
            fixtures.placeFederationCable(position);
        }
        var router = fixtures.placeRouter(CENTER);
        var registry = space.controlnet.ae2federation.domain.FederationDomainRegistryAccess.get(helper.getLevel());
        helper.succeedWhen(() -> {
            helper.assertValueEqual(router.binding(Direction.WEST).kind(), RouterPortKind.FEDERATION,
                    "The Router must link to the western cable");
            helper.assertValueEqual(router.binding(Direction.EAST).kind(), RouterPortKind.FEDERATION,
                    "The Router must link to the eastern cable");
            helper.assertValueEqual(fixtures.count(router, RouterPortKind.NATIVE_ME), 0L,
                    "The Router must attach no ME network");
            var leftNetwork = network(helper, leftSwitch, Direction.WEST);
            var rightNetwork = network(helper, rightSwitch, Direction.EAST);
            helper.assertTrue(!leftNetwork.equals(rightNetwork), "The two chests must be separate networks");
            helper.assertTrue(!registry.federationdomainsFor(leftNetwork).isEmpty()
                    && registry.federationdomainsFor(leftNetwork).equals(registry.federationdomainsFor(rightNetwork)),
                    "Networks on Switches joined through a Router must share a Federation domain");
            RouterEvidence.write("switchesjointhroughrouter", 5, Map.of(
                    "routerFederationFaces", "2", "routerNativeFaces", "0", "switchNativeFaces", "2",
                    "sharedDomain", "true"));
            fixtures.close();
        });
    }

    /**
     * AE2 asks a node host for its node on no side (a null direction) when it wants every node of a block, as
     * {@code /ae2 grids export} does. Router and Switch have nodes on their faces only, so they answer null.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void routerNullSideNode(GameTestHelper helper) {
        var fixtures = new RouterFixtures(helper);
        var left = CENTER.west(2);
        var switchBlock = fixtures.placeSwitch(left);
        fixtures.placeNativeDevice(left, Direction.WEST);
        fixtures.placeFederationCable(CENTER.west());
        fixtures.placeRouter(CENTER);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(switchBlock.binding(Direction.WEST).kind(), RouterPortKind.NATIVE_ME,
                    "The Switch must attach the ME network on its western face");
            var switchHost = GridHelper.getNodeHost(helper.getLevel(), helper.absolutePos(left));
            var routerHost = GridHelper.getNodeHost(helper.getLevel(), helper.absolutePos(CENTER));
            helper.assertTrue(switchHost != null && routerHost != null, "Router and Switch must be AE2 node hosts");
            helper.assertTrue(switchHost.getGridNode(Direction.WEST) != null,
                    "The Switch must still expose its node on an attached face");
            helper.assertTrue(switchHost.getGridNode(null) == null, "The Switch has no node on no side");
            helper.assertTrue(routerHost.getGridNode(null) == null, "The Router has no node on no side");
            RouterEvidence.write("routernullsidenode", 5, Map.of(
                    "switchNullSide", "null", "routerNullSide", "null", "switchFaceNode", "present"));
            fixtures.close();
        });
    }

    /**
     * A domain of one ME network still shows it: the Switch's screen lists the network for the topology, though
     * there is no pair for rules yet.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void aDomainOfOneNetworkShowsItsNetwork(GameTestHelper helper) {
        BlockPos.betweenClosed(CENTER.offset(-3, -1, -1), CENTER.offset(3, 2, 1))
                .forEach(position -> helper.setBlock(position, net.minecraft.world.level.block.Blocks.AIR));
        var fixtures = new RouterFixtures(helper);
        var switchEntity = fixtures.placeSwitch(CENTER);
        fixtures.placeNativeDevice(CENTER, Direction.WEST);
        fixtures.placeFederationCable(CENTER.east());
        var player = space.controlnet.ae2federation.test.world.MockServerPlayers.inLevel(helper);
        var at = helper.absolutePos(CENTER);
        player.moveTo(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5);
        helper.succeedWhen(() -> {
            var network = network(helper, switchEntity, Direction.WEST);
            var session = space.controlnet.ae2federation.client.policy.FederationDomainPolicySession
                    .forRouter(player, at);
            var root = com.google.gson.JsonParser.parseString(session.workspaceChoices()).getAsJsonObject();
            var networks = root.getAsJsonArray("networks");
            helper.assertTrue(networks != null && networks.size() == 1,
                    "The workspace lists the domain's one network, got " + networks);
            helper.assertValueEqual(networks.get(0).getAsJsonObject().get("id").getAsString(),
                    network.value().toString(), "The listed network is the Switch's");
            helper.assertValueEqual(session.statusCode(), "ready", "A domain of one network is not pending");
            RouterEvidence.write("adomainofonenetworkshowsitsnetwork", 3, Map.of("network", network.toString()));
            fixtures.close();
        });
    }

    /**
     * The device models have gaps, so their neighbours must keep the faces seen through them: all six around the
     * Router, the one in front of a Provider or Endpoint. Behind those two the model is closed and still hides it.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 100, required = true, manualOnly = true)
    public static void deviceOcclusion(GameTestHelper helper) {
        var stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        helper.setBlock(CENTER, RouterRegistration.ROUTER.get());
        for (var face : Direction.values()) {
            helper.setBlock(CENTER.relative(face), stone);
        }
        var facing = net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;
        var provider = CENTER.north(3);
        var endpoint = CENTER.south(3);
        helper.setBlock(provider, space.controlnet.ae2federation.processing.ProcessingRegistration.PROVIDER.get()
                .defaultBlockState().setValue(facing, Direction.EAST));
        helper.setBlock(endpoint, space.controlnet.ae2federation.processing.ProcessingRegistration.ENDPOINT.get()
                .defaultBlockState().setValue(facing, Direction.EAST));
        for (var device : new BlockPos[] {provider, endpoint}) {
            helper.setBlock(device.east(), stone);
            helper.setBlock(device.west(), stone);
        }
        var assertions = 0;
        for (var face : Direction.values()) {
            helper.assertTrue(faceDrawn(helper, CENTER.relative(face), face.getOpposite()),
                    "The Router's frame has gaps; the block on its " + face.getName() + " must keep its face");
            assertions++;
        }
        for (var device : new BlockPos[] {provider, endpoint}) {
            helper.assertTrue(faceDrawn(helper, device.east(), Direction.WEST),
                    "The recessed front must not hide the block in front of it");
            helper.assertTrue(!faceDrawn(helper, device.west(), Direction.EAST),
                    "The closed back still hides the block behind it");
            assertions += 2;
        }
        RouterEvidence.write("deviceocclusion", assertions, Map.of(
                "routerFacesDrawn", "6", "frontFaceDrawn", "2", "backFaceHidden", "2"));
        helper.succeed();
    }

    /**
     * A Switch face has a node, and so an AE2 Grid of its own, only while an AE2 block is beside it: a lone Switch has
     * none, a cable placed beside it attaches through a new node, and the node goes with the cable.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void switchFaceNodeFollowsNeighbour(GameTestHelper helper) {
        var fixtures = new RouterFixtures(helper);
        var router = fixtures.placeSwitch(CENTER);
        var phase = new int[1];
        var start = helper.getTick();
        helper.succeedWhen(() -> {
            if (phase[0] == 0) {
                helper.assertTrue(helper.getTick() >= start + 3, "Waiting for the Switch's first ticks");
                helper.assertValueEqual(faceNodes(router), 0, "A lone Switch must have no face node");
                fixtures.placeNativeCable(CENTER, Direction.WEST);
                phase[0] = 1;
                helper.fail("Waiting for the cable to attach");
            }
            if (phase[0] == 1) {
                helper.assertValueEqual(router.binding(Direction.WEST).kind(), RouterPortKind.NATIVE_ME,
                        "The cable must attach to the Switch");
                helper.assertValueEqual(faceNodes(router), 1, "Only the face beside the cable has a node");
                helper.assertValueEqual(GridHelper.getExposedNode(helper.getLevel(), helper.absolutePos(CENTER.west()),
                        Direction.EAST).getGrid(), router.boundaryNode(Direction.WEST).getGrid(),
                        "The face node joins the cable's Grid");
                helper.setBlock(CENTER.west(), net.minecraft.world.level.block.Blocks.AIR);
                phase[0] = 2;
                helper.fail("Waiting for the cable removal");
            }
            if (phase[0] == 2) {
                helper.assertValueEqual(router.binding(Direction.WEST).kind(), RouterPortKind.DISCONNECTED,
                        "The face must disconnect with the cable gone");
                helper.assertValueEqual(faceNodes(router), 0, "The face node must go with the cable");
                fixtures.placeNativeCable(CENTER, Direction.WEST);
                phase[0] = 3;
                helper.fail("Waiting for the cable to attach again");
            }
            helper.assertValueEqual(router.binding(Direction.WEST).kind(), RouterPortKind.NATIVE_ME,
                    "A cable placed again must attach again");
            helper.assertValueEqual(faceNodes(router), 1, "The face has a node again");
            RouterEvidence.write("switchfacenodefollowsneighbour", 8, Map.of(
                    "loneSwitchNodes", "0", "attached", "native", "nodesWithCable", "1",
                    "removed", "disconnected", "nodesAfterRemoval", "0", "reattached", "native"));
            fixtures.close();
        });
    }

    /**
     * A Switch whose chunk border lies between it and its cable keeps its face across a real unload and load of both
     * chunks, which load in either order, and across a reload of the block entities in one tick: the face attaches the
     * same network again, and the faces with nothing beside them still have no node.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1200, required = true, manualOnly = true)
    public static void switchFaceNodeSurvivesChunkReload(GameTestHelper helper) {
        var absoluteX = helper.absolutePos(BlockPos.ZERO).getX();
        // The Switch is the last block of its chunk along x; the cable and the ME chest are in the next chunk.
        var switchPosition = new BlockPos(Math.floorMod(15 - absoluteX, 16), 1, 1);
        var cable = switchPosition.east();
        var chest = cable.east();
        var site = space.controlnet.ae2federation.test.world.OtherDimensionSite.nether(helper,
                new BlockPos(switchPosition.getX() + 4, 3, 3));
        var phase = new int[1];
        var original = new space.controlnet.ae2federation.identity.NetworkId[1];
        helper.succeedWhen(() -> {
            if (phase[0] == 0) {
                helper.assertTrue(site.ready(), "Waiting for the nether site to tick: " + site.tickDiagnostics());
                site.setBlock(chest.below(), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                site.setBlock(chest, appeng.core.definitions.AEBlocks.ME_CHEST.block().defaultBlockState());
                helper.assertTrue(space.controlnet.ae2federation.test.p2p.P2PTunnels.placeCable(site.level(),
                        site.absolute(cable)), "An AE2 cable must be placed");
                site.setBlock(switchPosition, RouterRegistration.SWITCH.get().defaultBlockState());
                phase[0] = 1;
                helper.fail("Waiting for the Switch to attach");
            }
            if (phase[0] == 1) {
                original[0] = attachedNetwork(helper, site, switchPosition, chest);
                site.releaseTickets();
                phase[0] = 2;
                helper.fail("Waiting for the nether chunks to unload");
            }
            if (phase[0] == 2) {
                helper.assertFalse(site.loaded(), "Waiting for the nether chunks to unload");
                site.forceAgain();
                phase[0] = 3;
                helper.fail("Waiting for the nether chunks to load again");
            }
            if (phase[0] == 3) {
                helper.assertTrue(site.ticking(), "Waiting for the nether chunks to tick: " + site.tickDiagnostics());
                helper.assertValueEqual(attachedNetwork(helper, site, switchPosition, chest), original[0],
                        "The reloaded face must attach the same network");
                site.reloadBlockEntities();
                phase[0] = 4;
                helper.fail("Waiting for the reloaded block entities");
            }
            helper.assertValueEqual(attachedNetwork(helper, site, switchPosition, chest), original[0],
                    "The face must attach the same network after a reload in one tick");
            RouterEvidence.write("switchfacenodesurviveschunkreload", 4, Map.of(
                    "attached", "native", "chunkReload", "same network", "blockEntityReload", "same network",
                    "otherFaceNodes", "0"));
            site.close();
        });
    }

    /**
     * A Federation Cable does no work while nothing around it changes, as an AE2 cable does not tick: once its domain
     * settles, every cable sleeps. A third network joining wakes them, their capacity count follows, and they sleep again.
     * A new capacity number wakes them too, even while the limit is not enforced, as the beam still shows the load.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void cableSleepsWhileIdle(GameTestHelper helper) {
        var fixtures = new RouterFixtures(helper);
        var west = new BlockPos(2, 2, 2);
        var east = new BlockPos(6, 2, 2);
        var cables = new BlockPos[] {west.east(), west.east(2), west.east(3)};
        fixtures.placeSwitch(west);
        fixtures.placeSwitch(east);
        fixtures.placeNativeDevice(west, Direction.WEST);
        fixtures.placeNativeDevice(east, Direction.EAST);
        for (var cable : cables) fixtures.placeFederationCable(cable);
        var phase = new int[1];
        var load = new double[1];
        // The capacity rule is global: the test changes it from what it finds and puts that back, also on a timeout.
        var originalCapacity = space.controlnet.ae2federation.domain.CableCapacityLimit.capacity();
        var originalEnforced = space.controlnet.ae2federation.domain.CableCapacityLimit.current().isPresent();
        var changedCapacity = originalCapacity * 2 <= space.controlnet.ae2federation.domain.CableCapacityLimit.MAX_CAPACITY
                ? originalCapacity * 2 : originalCapacity / 2;
        Runnable restore = () -> space.controlnet.ae2federation.domain.CableCapacityLimit.set(originalEnforced,
                originalCapacity);
        var changedAt = new long[1];
        helper.succeedWhen(() -> {
            var registry = space.controlnet.ae2federation.domain.FederationDomainRegistryAccess.get(helper.getLevel());
            var left = network(helper, fixtures.router(west), Direction.WEST);
            var right = network(helper, fixtures.router(east), Direction.EAST);
            helper.assertTrue(registry.shareFederationDomain(left, right), "Waiting for the domain");
            for (var cable : cables) {
                helper.assertTrue(!space.controlnet.ae2federation.router.FederationCableTicks.awake(
                        helper.getBlockEntity(cable)), "Waiting for the cable at " + cable + " to sleep");
            }
            space.controlnet.ae2federation.router.FederationCableBlockEntity middle = helper.getBlockEntity(cables[1]);
            if (phase[0] == 0) {
                load[0] = middle.capacityLoad();
                helper.assertTrue(load[0] > 0, "The cable has counted the networks it carries");
                fixtures.placeNativeDevice(west, Direction.NORTH);
                phase[0] = 1;
                helper.fail("Waiting for the third network");
            }
            if (phase[0] == 1) {
                helper.assertValueEqual(fixtures.router(west).binding(Direction.NORTH).kind(), RouterPortKind.NATIVE_ME,
                        "The third network attaches");
                helper.assertTrue(middle.capacityLoad() > load[0], "Waiting for the cable to count the third network");
                load[0] = middle.capacityLoad();
                space.controlnet.ae2federation.domain.CableCapacityLimit.set(false, changedCapacity);
                changedAt[0] = helper.getTick();
                phase[0] = 2;
                helper.fail("Waiting for the new capacity number");
            }
            var shown = Math.abs(middle.capacityLoad() - load[0] * originalCapacity / changedCapacity) < 1e-9;
            if (shown || helper.getTick() > changedAt[0] + 200) restore.run();
            if (!shown && helper.getTick() > changedAt[0] + 200) {
                throw new IllegalStateException("The sleeping cable never showed the new capacity");
            }
            helper.assertTrue(shown, "Waiting for the sleeping cable to show the new capacity");
            RouterEvidence.write("cablesleepswhileidle", 6, Map.of(
                    "domain", "formed", "asleep", "true", "thirdNetwork", "attached", "counted", "true",
                    "asleepAgain", "true", "unenforcedCapacityShown", "true"));
            fixtures.close();
        });
    }

    /**
     * The settled network the Switch's east face attaches, after checking that it is the ME chest's network and that
     * no other face has a node.
     */
    private static space.controlnet.ae2federation.identity.NetworkId attachedNetwork(GameTestHelper helper,
            space.controlnet.ae2federation.test.world.OtherDimensionSite site, BlockPos switchPosition, BlockPos chest) {
        helper.assertTrue(site.getBlockEntity(switchPosition) instanceof space.controlnet.ae2federation.router.RouterBlockEntity,
                "Waiting for the Switch");
        space.controlnet.ae2federation.router.RouterBlockEntity router = site.getBlockEntity(switchPosition);
        helper.assertTrue(router.binding(Direction.EAST) instanceof RouterPortBinding.Native,
                "Waiting for the Switch's east face to attach");
        helper.assertValueEqual(faceNodes(router), 1, "Only the face beside the cable has a node");
        appeng.blockentity.storage.MEChestBlockEntity chestEntity = site.getBlockEntity(chest);
        var chestNode = chestEntity.getMainNode().getNode();
        helper.assertTrue(chestNode != null && chestNode.getGrid() == router.boundaryNode(Direction.EAST).getGrid(),
                "Waiting for the face to join the chest's Grid");
        return network(helper, router, Direction.EAST);
    }

    private static int faceNodes(space.controlnet.ae2federation.router.RouterBlockEntity router) {
        var count = 0;
        for (var face : Direction.values()) {
            if (router.boundaryNode(face) != null) count++;
        }
        return count;
    }

    /** Whether the block at {@code position} draws its face toward {@code face}, as the chunk mesher decides. */
    private static boolean faceDrawn(GameTestHelper helper, BlockPos position, Direction face) {
        var absolute = helper.absolutePos(position);
        var level = helper.getLevel();
        return net.minecraft.world.level.block.Block.shouldRenderFace(level.getBlockState(absolute), level, absolute,
                face, absolute.relative(face));
    }

    private static space.controlnet.ae2federation.identity.NetworkId network(GameTestHelper helper,
            space.controlnet.ae2federation.router.RouterBlockEntity router, Direction face) {
        var binding = router.binding(face);
        helper.assertTrue(binding instanceof RouterPortBinding.Native, "Router face must resolve its native attachment");
        return space.controlnet.ae2federation.domain.FederationDomainRegistryAccess
                .confirmedNetworkId(((RouterPortBinding.Native) binding).attachment().grid())
                .orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException(
                        "Native Router identity is not settled"));
    }

    private static String identity(Object value) {
        return Integer.toUnsignedString(System.identityHashCode(value));
    }
}
