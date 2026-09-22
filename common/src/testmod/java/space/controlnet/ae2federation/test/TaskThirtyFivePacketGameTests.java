package space.controlnet.ae2federation.test;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.client.menu.FabricPolicyAction;
import space.controlnet.ae2federation.client.menu.FabricPolicyActionRequest;
import space.controlnet.ae2federation.client.menu.FabricPolicyActionResult;
import space.controlnet.ae2federation.client.menu.FabricPolicyMenu;
import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.neoforge.network.FabricPolicyActionPayload;
import space.controlnet.ae2federation.neoforge.network.FabricPolicyActionPayloads;
import space.controlnet.ae2federation.observability.LevelObservabilityService;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.test.energy.NativeEnergyEvidence;
import space.controlnet.ae2federation.test.policy.PolicyBridgeFixtures;

@PrefixGameTestTemplate(false)
public final class TaskThirtyFivePacketGameTests {
    private TaskThirtyFivePacketGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            timeoutTicks = 400)
    public static void packetAuthority(GameTestHelper helper) {
        var fixtures = new PolicyBridgeFixtures(helper, new BlockPos(5, 3, 5));
        var bridgePlaced = new boolean[1];
        helper.succeedWhen(() -> {
            if (!bridgePlaced[0] && fixtures.networksSettled()) {
                fixtures.placeFirstBridge();
                bridgePlaced[0] = true;
                helper.assertTrue(false, "Waiting for Task 35 confirmed Fabric");
            }
            helper.assertTrue(fixtures.firstBridgeReady(), "Waiting for Task 35 policy context");
            var player = helper.makeMockServerPlayerInLevel();
            player.setPos(Vec3.atCenterOf(fixtures.firstBridgeContext().position()));
            ObservationGameTestPlayerTransport.install(player);
            helper.assertTrue(FabricPolicyMenu.openBridge(player, fixtures.firstBridgeContext()),
                    "Menu A must open");
            var requestA = FabricPolicyMenu.currentRequest(player, FabricPolicyAction.TOGGLE_POLICY)
                    .orElseThrow();
            player.doCloseContainer();
            helper.assertTrue(FabricPolicyMenu.openBridge(player, fixtures.firstBridgeContext()),
                    "Menu B must open");
            var requestB = FabricPolicyMenu.currentRequest(player, FabricPolicyAction.TOGGLE_POLICY)
                    .orElseThrow();
            var receiptBefore = FabricPolicyMenu.currentReceipt(player);
            var policy = PolicyService.get(helper.getLevel());
            var configuredBefore = policy.configuredCount();
            var meter = LevelObservabilityService.get(helper.getLevel()).transportMeter();
            var meterBefore = meter.window(requestB.context());
            var inventoryBefore = player.getInventory().getItem(0).copy();
            var worldBefore = helper.getLevel().getBlockState(fixtures.firstBridgeContext().position());

            helper.assertValueEqual(dispatch(player, requestA),
                    FabricPolicyActionResult.STALE_CONTAINER, "Menu A replay must be rejected by menu B");
            var wrongContext = new FabricPolicyActionRequest(requestB.action(), requestB.containerId(),
                    requestB.menuNonce(), requestB.menuSequence(),
                    new FabricReference(requestB.context().fabricId(), requestB.context().generation() + 1),
                    requestB.expectedRevision());
            helper.assertValueEqual(dispatch(player, wrongContext),
                    FabricPolicyActionResult.STALE_CONTEXT, "Cross-generation action must be rejected");
            var staleRevision = new FabricPolicyActionRequest(requestB.action(), requestB.containerId(),
                    requestB.menuNonce(), requestB.menuSequence(), requestB.context(),
                    new PolicyRevision(requestB.expectedRevision().value() + 1));
            helper.assertValueEqual(dispatch(player, staleRevision),
                    FabricPolicyActionResult.STALE_REVISION, "Stale Policy revision must be rejected");
            helper.assertValueEqual(policy.configuredCount(), configuredBefore,
                    "Rejected packets must not configure Policy");
            helper.assertValueEqual(FabricPolicyMenu.currentReceipt(player), receiptBefore,
                    "Rejected packets must not mutate menu or mapping session state");
            helper.assertValueEqual(meter.window(requestB.context()), meterBefore,
                    "Rejected packets must not mutate transport meter state");
            helper.assertValueEqual(player.getInventory().getItem(0), inventoryBefore,
                    "Rejected packets must not mutate inventory");
            helper.assertValueEqual(helper.getLevel().getBlockState(fixtures.firstBridgeContext().position()), worldBefore,
                    "Rejected packets must not mutate world state");

            helper.assertValueEqual(dispatch(player, requestB), FabricPolicyActionResult.ACCEPTED,
                    "Valid menu B action must execute on the server thread");
            helper.assertValueEqual(policy.configuredCount(), configuredBefore + 1,
                    "Valid menu B action must configure exactly one Policy");
            helper.assertValueEqual(FabricPolicyMenu.currentRequest(player, FabricPolicyAction.TOGGLE_POLICY)
                    .orElseThrow().expectedRevision().value(), requestB.expectedRevision().value() + 1,
                    "Valid menu B action must advance its selected Policy revision exactly once");
            helper.assertValueEqual(dispatch(player, requestB),
                    FabricPolicyActionResult.STALE_SEQUENCE, "Replayed menu B action must not execute twice");
            helper.assertValueEqual(policy.configuredCount(), configuredBefore + 1,
                    "Replayed menu B action must leave exactly one configured Policy");

            NativeEnergyEvidence.write("task35packetauthority", 13, Map.ofEntries(
                    Map.entry("menuAReplayRejected", "true"), Map.entry("contextRejected", "true"),
                    Map.entry("revisionRejected", "true"), Map.entry("policyUnchangedOnReject", "true"),
                    Map.entry("mappingSessionUnchangedOnReject", "true"), Map.entry("meterUnchangedOnReject", "true"),
                    Map.entry("inventoryUnchangedOnReject", "true"), Map.entry("worldUnchangedOnReject", "true"),
                    Map.entry("validAccepted", "true"), Map.entry("validExactlyOnce", "true"),
                    Map.entry("serverThread", "true"), Map.entry("containerChanged",
                            Boolean.toString(requestA.containerId() != requestB.containerId())),
                    Map.entry("nonceChanged", Boolean.toString(!requestA.menuNonce().equals(requestB.menuNonce())))));
            player.doCloseContainer();
            fixtures.close();
        });
    }

    private static FabricPolicyActionResult dispatch(net.minecraft.server.level.ServerPlayer player,
            FabricPolicyActionRequest request) {
        return FabricPolicyActionPayloads.handle(player, new FabricPolicyActionPayload(request));
    }
}
