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
        var router = fixtures.placeRouter(CENTER);
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
        var router = fixtures.placeRouter(CENTER);
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
        var router = fixtures.placeRouter(CENTER);
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
        var router = fixtures.placeRouter(CENTER);
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
                helper.assertValueEqual(router.binding(Direction.EAST).kind(), RouterPortKind.DISCONNECTED,
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
        var router = fixtures.placeRouter(CENTER);
        for (var face : Direction.values()) {
            fixtures.placeFederationCable(CENTER, face);
        }
        var unsupportedPosition = new BlockPos(2, 6, 2);
        var unsupportedRouter = fixtures.placeRouter(unsupportedPosition);
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

    private static String identity(Object value) {
        return Integer.toUnsignedString(System.identityHashCode(value));
    }
}
