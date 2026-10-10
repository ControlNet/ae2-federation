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
            helper.assertTrue(unsupportedRouter.boundaryNode(Direction.NORTH).getConnections().isEmpty(),
                    "Unsupported neighbor must not create a native edge");
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
                helper.assertTrue(first.boundaryNode(Direction.EAST).getConnections().isEmpty()
                        && adjacent.boundaryNode(Direction.WEST).getConnections().isEmpty(),
                        "Touching Router faces must not form a native Grid between them");
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
