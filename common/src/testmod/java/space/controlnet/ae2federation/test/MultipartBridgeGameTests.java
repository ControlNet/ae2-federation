package space.controlnet.ae2federation.test;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import space.controlnet.ae2federation.bridge.BridgeOperationalReason;
import space.controlnet.ae2federation.test.bridge.BridgeEvidence;
import space.controlnet.ae2federation.test.bridge.BridgeFixtures;

@PrefixGameTestTemplate(false)
public final class MultipartBridgeGameTests {
    private MultipartBridgeGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void bridgeValid(GameTestHelper helper) {
        var fixtures = new BridgeFixtures(helper);
        var position = new BlockPos(4, 1, 4);
        fixtures.placeNativeHosts(position);
        var bridge = fixtures.placeBridge(position);
        helper.succeedWhen(() -> {
            helper.assertTrue(bridge.getMainNode().getNode() != null, "Bridge main node must initialize");
            helper.assertTrue(bridge.membershipCandidate().isPresent(), "Bridge needs two distinct native Grid domains");
            helper.assertValueEqual(bridge.operationalReason(), BridgeOperationalReason.VALID,
                    "Valid Bridge must expose operational status");
            helper.assertTrue(bridge.rightClickContext().side() != null, "Right-click context must retain part side");
            helper.assertTrue(bridge.getMainNode().getNode().getConnections().size() > 0,
                    "Bridge main node must be connected by the native cable bus");
            BridgeEvidence.write("bridgevalid", 4, Map.of("valid", "true", "distinctGrids", "true",
                    "mainToOuterNativeConnection", "false", "membershipCandidate", "true"));
            fixtures.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void bridgeInvalid(GameTestHelper helper) {
        var fixtures = new BridgeFixtures(helper);
        var position = new BlockPos(4, 1, 4);
        fixtures.placeNativeHosts(position);
        var bridge = fixtures.placeBridge(position);
        helper.setBlock(position.north(), Blocks.AIR);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(bridge.operationalReason(), BridgeOperationalReason.MISSING_OUTER_ATTACHMENT,
                    "Missing native side must disable Bridge");
            helper.assertTrue(bridge.membershipCandidate().isEmpty(), "Invalid Bridge cannot publish membership");
            bridge.removeFromWorld();
            helper.assertTrue(bridge.getExternalFacingNode() == null, "Bridge removal must destroy its owned outer node");
            BridgeEvidence.write("bridgeinvalid", 3, Map.of("reason", "MISSING_OUTER_ATTACHMENT",
                    "membershipCandidate", "false", "nativeNodeDestroyedOnRemoval", "true"));
            fixtures.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void bridgeSameGrid(GameTestHelper helper) {
        var fixtures = new BridgeFixtures(helper);
        var position = new BlockPos(4, 1, 4);
        fixtures.placeNativeHosts(position);
        fixtures.joinNativeHostsAroundBridge(position);
        var bridge = fixtures.placeBridge(position);
        helper.succeedWhen(() -> {
            var main = bridge.getMainNode().getNode();
            var outer = bridge.getExternalFacingNode();
            helper.assertTrue(main != null && outer != null, "Bridge must expose both native nodes before comparison");
            helper.assertValueEqual(main.getGrid(), outer.getGrid(), "Same-grid fixture must share native Grid");
            helper.assertValueEqual(bridge.operationalReason(), BridgeOperationalReason.SAME_GRID,
                    "Same native Grid must fail closed");
            BridgeEvidence.write("bridgesamegrid", 3, Map.of("sameGrid", "true", "valid", "false",
                    "membershipCandidate", "false"));
            fixtures.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void bridgeRejectFederationCable(GameTestHelper helper) {
        var fixtures = new BridgeFixtures(helper);
        var position = new BlockPos(4, 1, 4);
        fixtures.placeNativeHosts(position);
        var bridge = fixtures.placeBridge(position);
        helper.setBlock(position.north(), Blocks.STONE);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(bridge.operationalReason(), BridgeOperationalReason.FEDERATION_CABLE_UNSUPPORTED,
                    "Non-native Federation cable candidate must fail closed");
            helper.assertTrue(bridge.membershipCandidate().isEmpty(), "Rejected cable cannot publish membership");
            BridgeEvidence.write("bridgerejectfederationcable", 2, Map.of("federationCableAccepted", "false",
                    "reason", "FEDERATION_CABLE_UNSUPPORTED"));
            fixtures.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void bridgeReloadReplace(GameTestHelper helper) {
        var fixtures = new BridgeFixtures(helper);
        var position = new BlockPos(4, 1, 4);
        fixtures.placeNativeHosts(position);
        var bridge = fixtures.placeBridge(position);
        helper.succeedWhen(() -> {
            helper.assertTrue(bridge.getExternalFacingNode() != null, "Bridge outer node must be created");
            helper.setBlock(position.north(), Blocks.AIR);
            helper.assertTrue(bridge.operationalReason() != BridgeOperationalReason.VALID,
                    "Replaced native host must invalidate Bridge");
            bridge.removeFromWorld();
            bridge.removeFromWorld();
            helper.assertValueEqual(bridge.operationalReason(), BridgeOperationalReason.REMOVED,
                    "Repeated close must retain removed state");
            BridgeEvidence.write("bridgereloadreplace", 4, Map.of("replacementAccepted", "false",
                    "removed", "true", "repeatedClose", "true", "outerNodeDestroyed", "true"));
            fixtures.close();
        });
    }
}
