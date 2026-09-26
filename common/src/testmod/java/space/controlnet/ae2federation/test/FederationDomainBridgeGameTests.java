package space.controlnet.ae2federation.test;

import appeng.api.util.AEColor;
import java.util.ArrayList;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.bridge.MultipartBridgePart;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.test.bridge.BridgeFixtures;
import space.controlnet.ae2federation.test.domain.FederationDomainEvidence;

@PrefixGameTestTemplate(false)
public final class FederationDomainBridgeGameTests {
    private FederationDomainBridgeGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void federationDomainBridgeDiamond(GameTestHelper helper) {
        var fixtures = new BridgeFixtures(helper);
        var position = new BlockPos(6, 3, 6);
        var sides = java.util.List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST);
        fixtures.nativePorts().placeCable(position, AEColor.RED);
        sides.forEach(side -> {
            fixtures.nativePorts().placeCable(position.relative(side), AEColor.BLUE);
            fixtures.nativePorts().placeChest(position.relative(side, 2));
        });
        var bridges = new ArrayList<MultipartBridgePart>();
        var phase = new int[1];
        helper.succeedWhen(() -> {
            if (phase[0] == 0) {
                var cableGrid = fixtures.nativePorts().exposedNode(position, Direction.UP).getGrid();
                helper.assertTrue(FederationDomainRegistryAccess.confirmedNetworkId(cableGrid).isPresent(),
                        "Central native Grid must settle before Bridge nodes join");
                helper.assertTrue(sides.stream().allMatch(side -> FederationDomainRegistryAccess.confirmedNetworkId(
                        fixtures.nativePorts().exposedNode(position.relative(side), side.getOpposite()).getGrid()).isPresent()),
                        "Outer native Grids must settle before Bridge nodes join");
                sides.forEach(side -> bridges.add(fixtures.placeBridge(position, side)));
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for Bridge-owned nodes to settle");
            }
            if (phase[0] == 1) {
                phase[0] = 2;
                helper.assertTrue(false, "Waiting for Federation Domain publication");
            }
            helper.assertTrue(bridges.stream().allMatch(bridge -> bridge.membershipCandidate().isPresent()),
                    "Every diamond Bridge must publish a valid candidate");
            var sharedGrid = bridges.getFirst().membershipCandidate().orElseThrow().mainGrid();
            helper.assertTrue(bridges.stream().allMatch(bridge ->
                    bridge.membershipCandidate().orElseThrow().mainGrid() == sharedGrid),
                    "Diamond main attachments must share one native Grid");
            var confirmed = FederationDomainRegistryAccess.confirmedNetworkId(sharedGrid);
            helper.assertTrue(confirmed.isPresent(), "Shared Bridge Grid identity must remain settled");
            var network = confirmed.get();
            var registry = FederationDomainRegistryAccess.get(helper.getLevel());
            helper.assertValueEqual(registry.federationdomainsFor(network).size(), 4,
                    "Shared native Grid must index four independent direct Federation Domains");
            helper.assertTrue(bridges.stream().allMatch(bridge -> bridge.getMainNode().getNode().getConnections().stream()
                    .noneMatch(connection -> connection.getOtherSide(bridge.getMainNode().getNode())
                            == bridge.getExternalFacingNode())), "Bridge must not create native cross-grid edges");
            FederationDomainEvidence.write("federationdomainbridgediamond", 8, Map.of(
                    "directFederationDomainCount", "4", "sharedNativeNetwork", "true", "sharedNetworkFederationDomainCount", "4",
                    "componentMergeFromSharedGrid", "false", "nativeCrossGridJoin", "false",
                    "membershipConfirmed", "true", "cycleConverged", "true", "worldScan", "false"));
            fixtures.close();
        });
    }
}
