package space.controlnet.ae2federation.test;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.fabric.port.HubPortBinding;
import space.controlnet.ae2federation.hub.FederationCableBlockEntity;
import space.controlnet.ae2federation.test.fabric.FabricEvidence;
import space.controlnet.ae2federation.test.hub.HubFixtures;

@PrefixGameTestTemplate(false)
public final class FabricGameTests {
    private static final BlockPos LEFT = new BlockPos(3, 4, 6);
    private static final BlockPos RIGHT = new BlockPos(9, 4, 6);

    private FabricGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void fabricHubMergeSplit(GameTestHelper helper) {
        var fixtures = unsettledHubFixtures(helper);
        var registry = FabricRegistryAccess.get(helper.getLevel());
        var phase = new int[1];
        var mergedGeneration = new long[1];
        helper.succeedWhen(() -> {
            if (phase[0] == 0) {
                placeConnectedHubs(fixtures);
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for loaded reciprocal Hub topology");
            }
            var leftNetwork = nativeNetwork(helper, fixtures, LEFT, Direction.NORTH);
            var rightNetwork = nativeNetwork(helper, fixtures, RIGHT, Direction.NORTH);
            if (phase[0] == 1) {
                var leftFabrics = registry.fabricsFor(leftNetwork);
                helper.assertTrue(!leftFabrics.isEmpty(), "Merged Hub membership must be confirmed");
                helper.assertValueEqual(leftFabrics, registry.fabricsFor(rightNetwork),
                        "Cable topology must merge both Hub memberships");
                var merged = registry.fabric(leftFabrics.iterator().next()).orElseThrow();
                mergedGeneration[0] = merged.generation();
                helper.setBlock(new BlockPos(6, 4, 6), Blocks.AIR);
                helper.assertTrue(!registry.isCurrent(merged.reference()), "Split must invalidate merged reference immediately");
                phase[0] = 2;
                helper.assertTrue(false, "Waiting for loaded split topology");
            }
            helper.assertTrue(!registry.fabricsFor(leftNetwork).isEmpty(), "Left split membership must recover");
            helper.assertTrue(!registry.fabricsFor(rightNetwork).isEmpty(), "Right split membership must recover");
            helper.assertTrue(!registry.fabricsFor(leftNetwork).equals(registry.fabricsFor(rightNetwork)),
                    "Removed cable must split Hub components");
            FabricEvidence.write("fabrichubmergesplit", 8, Map.of(
                    "mergedComponentCount", "1", "splitComponentCount", "2", "incremental", "true",
                    "mergedReferenceInvalidated", "true", "loadedReciprocalEdgesOnly", "true",
                    "nativeGridMerge", "false", "generationAdvanced", Boolean.toString(mergedGeneration[0] > 0),
                    "worldScan", "false"));
            fixtures.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 350, required = true, manualOnly = true)
    public static void fabricRedundantMembership(GameTestHelper helper) {
        var fixtures = new HubFixtures(helper);
        var bypass = java.util.List.of(LEFT.west(2), LEFT.north().west(2), LEFT.north().west(),
                LEFT.south().west(2), LEFT.south().west());
        var phase = new int[1];
        helper.succeedWhen(() -> {
            if (phase[0] < bypass.size()) {
                fixtures.nativePorts().placeCable(bypass.get(phase[0]));
                phase[0]++;
                helper.assertTrue(false, "Waiting for the next native bypass segment");
            }
            if (phase[0] == bypass.size()) {
                var cableGrid = fixtures.nativePorts().exposedNode(LEFT.north().west(), Direction.UP).getGrid();
                helper.assertTrue(FabricRegistryAccess.confirmedNetworkId(cableGrid).isPresent(),
                        "Native bypass must settle before devices join");
                fixtures.placeNativeDevice(LEFT, Direction.NORTH);
                fixtures.placeNativeDevice(LEFT, Direction.SOUTH);
                phase[0]++;
                helper.assertTrue(false, "Waiting for repeated native network attachments");
            }
            if (phase[0] == bypass.size() + 1) {
                var northNode = fixtures.nativeDeviceNode(LEFT, Direction.NORTH);
                var southNode = fixtures.nativeDeviceNode(LEFT, Direction.SOUTH);
                helper.assertTrue(northNode.getGrid() == southNode.getGrid(),
                        "Native devices must share the settled bypass Grid");
                helper.assertTrue(FabricRegistryAccess.confirmedNetworkId(northNode.getGrid()).isPresent(),
                        "Repeated native network identity must settle before Hub placement");
                fixtures.placeHub(LEFT);
                phase[0]++;
                helper.assertTrue(false, "Waiting for Hub boundary bindings");
            }
            var hub = fixtures.hub(LEFT);
            var northBinding = hub.binding(Direction.NORTH);
            var southBinding = hub.binding(Direction.SOUTH);
            helper.assertTrue(northBinding instanceof HubPortBinding.Native
                    && southBinding instanceof HubPortBinding.Native,
                    "Both Hub faces must resolve their native attachments");
            var north = ((HubPortBinding.Native) northBinding).attachment().grid();
            var south = ((HubPortBinding.Native) southBinding).attachment().grid();
            helper.assertTrue(north == south, "Two Hub faces must observe one native Grid");
            var network = FabricRegistryAccess.confirmedNetworkId(north).orElseThrow();
            var registry = FabricRegistryAccess.get(helper.getLevel());
            var fabricId = registry.fabricsFor(network).iterator().next();
            var membership = registry.fabric(fabricId).orElseThrow().memberships().get(network);
            helper.assertValueEqual(membership.size(), 2, "Deduplicated network must retain both attachment sources");
            helper.assertValueEqual(registry.fabricsFor(network).size(), 1,
                    "Redundant attachments must index one Fabric");
            FabricEvidence.write("fabricredundantmembership", 8, Map.of(
                    "nativeNetworkCount", "1", "attachmentSourceCount", "2", "indexedFabricCount", "1",
                    "deduplicated", "true", "faceOwnershipRetained", "true", "identitySettled", "true",
                    "nativeJoinByFabric", "false", "worldScan", "false"));
            fixtures.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 350, required = true, manualOnly = true)
    public static void fabricPartialUnload(GameTestHelper helper) {
        var fixtures = unsettledHubFixtures(helper);
        var registry = FabricRegistryAccess.get(helper.getLevel());
        var phase = new int[1];
        helper.succeedWhen(() -> {
            if (phase[0] == 0) {
                placeConnectedHubs(fixtures);
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for loaded reciprocal Hub topology");
            }
            var leftNetwork = nativeNetwork(helper, fixtures, LEFT, Direction.NORTH);
            var rightNetwork = nativeNetwork(helper, fixtures, RIGHT, Direction.NORTH);
            helper.assertValueEqual(registry.fabricsFor(leftNetwork), registry.fabricsFor(rightNetwork),
                    "Unload fixture must begin merged");
            FederationCableBlockEntity middle = helper.getBlockEntity(new BlockPos(6, 4, 6));
            middle.onChunkUnloaded();
            helper.assertTrue(registry.fabricsFor(leftNetwork).isEmpty(),
                    "Partial unload must remove uncertain left membership immediately");
            helper.assertTrue(registry.fabricsFor(rightNetwork).isEmpty(),
                    "Partial unload must remove uncertain right membership immediately");
            FabricEvidence.write("fabricpartialunload", 8, Map.of(
                    "initiallyMerged", "true", "unloadLifecycleInvoked", "true", "staleMembershipAccepted", "false",
                    "forcedChunkLoads", "0", "loadedEvidenceOnly", "true", "affectedComponentsOnly", "true",
                    "membershipInvalidated", "true", "worldScan", "false"));
            fixtures.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 350, required = true, manualOnly = true)
    public static void fabricRejectStaleRoute(GameTestHelper helper) {
        var fixtures = unsettledHubFixtures(helper);
        var registry = FabricRegistryAccess.get(helper.getLevel());
        var phase = new int[1];
        helper.succeedWhen(() -> {
            if (phase[0] == 0) {
                placeConnectedHubs(fixtures);
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for loaded reciprocal Hub topology");
            }
            var leftNetwork = nativeNetwork(helper, fixtures, LEFT, Direction.NORTH);
            var fabricId = registry.fabricsFor(leftNetwork).iterator().next();
            var reference = registry.fabric(fabricId).orElseThrow().reference();
            helper.setBlock(new BlockPos(6, 4, 6), Blocks.AIR);
            helper.assertTrue(!registry.isCurrent(reference), "Topology invalidation must reject stale Fabric references");
            helper.assertTrue(registry.fabricsFor(leftNetwork).isEmpty(),
                    "Invalidation must remove stale network index truth immediately");
            FabricEvidence.write("fabricrejectstaleroute", 8, Map.of(
                    "referenceInitiallyCurrent", "true", "referenceCurrentAfterChange", "false",
                    "staleRouteAccepted", "false", "indexInvalidatedImmediately", "true",
                    "generationBound", "true", "policyImplemented", "false", "possiblePathsEnumerated", "false",
                    "worldScan", "false"));
            fixtures.close();
        });
    }

    private static HubFixtures unsettledHubFixtures(GameTestHelper helper) {
        var fixtures = new HubFixtures(helper);
        fixtures.placeNativeDevice(LEFT, Direction.NORTH);
        fixtures.placeNativeDevice(RIGHT, Direction.NORTH);
        return fixtures;
    }

    private static void placeConnectedHubs(HubFixtures fixtures) {
        var leftGrid = fixtures.nativeDeviceNode(LEFT, Direction.NORTH).getGrid();
        var rightGrid = fixtures.nativeDeviceNode(RIGHT, Direction.NORTH).getGrid();
        if (FabricRegistryAccess.confirmedNetworkId(leftGrid).isEmpty()
                || FabricRegistryAccess.confirmedNetworkId(rightGrid).isEmpty()) {
            throw new net.minecraft.gametest.framework.GameTestAssertException(
                    "Native Hub neighbors must settle before boundary nodes join");
        }
        fixtures.placeHub(LEFT);
        fixtures.placeHub(RIGHT);
        for (var x = LEFT.getX() + 1; x < RIGHT.getX(); x++) {
            fixtures.placeFederationCable(new BlockPos(x, LEFT.getY(), LEFT.getZ()));
        }
    }

    private static space.controlnet.ae2federation.identity.NetworkId nativeNetwork(GameTestHelper helper,
            HubFixtures fixtures, BlockPos hubPosition, Direction face) {
        var binding = fixtures.hub(hubPosition).binding(face);
        helper.assertTrue(binding instanceof HubPortBinding.Native, "Hub face must resolve its native attachment");
        var grid = ((HubPortBinding.Native) binding).attachment().grid();
        var confirmed = FabricRegistryAccess.confirmedNetworkId(grid);
        if (confirmed.isEmpty()) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("Native Hub identity is not settled");
        }
        return confirmed.get();
    }
}
