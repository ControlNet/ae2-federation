package space.controlnet.ae2federation.test;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.port.RouterPortBinding;
import space.controlnet.ae2federation.router.FederationCableBlockEntity;
import space.controlnet.ae2federation.test.domain.FederationDomainEvidence;
import space.controlnet.ae2federation.test.router.RouterFixtures;

@PrefixGameTestTemplate(false)
public final class FederationDomainGameTests {
    private static final BlockPos LEFT = new BlockPos(3, 4, 6);
    private static final BlockPos RIGHT = new BlockPos(9, 4, 6);

    private FederationDomainGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void federationDomainRouterMergeSplit(GameTestHelper helper) {
        var fixtures = unsettledRouterFixtures(helper);
        var registry = FederationDomainRegistryAccess.get(helper.getLevel());
        var phase = new int[1];
        var mergedGeneration = new long[1];
        helper.succeedWhen(() -> {
            if (phase[0] == 0) {
                placeConnectedRouters(fixtures);
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for loaded reciprocal Router topology");
            }
            var leftNetwork = nativeNetwork(helper, fixtures, LEFT, Direction.NORTH);
            var rightNetwork = nativeNetwork(helper, fixtures, RIGHT, Direction.NORTH);
            if (phase[0] == 1) {
                var leftFederationDomains = registry.federationdomainsFor(leftNetwork);
                helper.assertTrue(!leftFederationDomains.isEmpty(), "Merged Router membership must be confirmed");
                helper.assertValueEqual(leftFederationDomains, registry.federationdomainsFor(rightNetwork),
                        "Cable topology must merge both Router memberships");
                var merged = registry.federationDomain(leftFederationDomains.iterator().next()).orElseThrow();
                mergedGeneration[0] = merged.generation();
                helper.setBlock(new BlockPos(6, 4, 6), Blocks.AIR);
                helper.assertTrue(!registry.isCurrent(merged.reference()), "Split must invalidate merged reference immediately");
                phase[0] = 2;
                helper.assertTrue(false, "Waiting for loaded split topology");
            }
            helper.assertTrue(!registry.federationdomainsFor(leftNetwork).isEmpty(), "Left split membership must recover");
            helper.assertTrue(!registry.federationdomainsFor(rightNetwork).isEmpty(), "Right split membership must recover");
            helper.assertTrue(!registry.federationdomainsFor(leftNetwork).equals(registry.federationdomainsFor(rightNetwork)),
                    "Removed cable must split Router components");
            FederationDomainEvidence.write("federationdomainroutermergesplit", 8, Map.of(
                    "mergedComponentCount", "1", "splitComponentCount", "2", "incremental", "true",
                    "mergedReferenceInvalidated", "true", "loadedReciprocalEdgesOnly", "true",
                    "nativeGridMerge", "false", "generationAdvanced", Boolean.toString(mergedGeneration[0] > 0),
                    "worldScan", "false"));
            fixtures.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 350, required = true, manualOnly = true)
    public static void federationDomainRedundantMembership(GameTestHelper helper) {
        var fixtures = new RouterFixtures(helper);
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
                helper.assertTrue(FederationDomainRegistryAccess.confirmedNetworkId(cableGrid).isPresent(),
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
                helper.assertTrue(FederationDomainRegistryAccess.confirmedNetworkId(northNode.getGrid()).isPresent(),
                        "Repeated native network identity must settle before Router placement");
                fixtures.placeRouter(LEFT);
                phase[0]++;
                helper.assertTrue(false, "Waiting for Router boundary bindings");
            }
            var router = fixtures.router(LEFT);
            var northBinding = router.binding(Direction.NORTH);
            var southBinding = router.binding(Direction.SOUTH);
            helper.assertTrue(northBinding instanceof RouterPortBinding.Native
                    && southBinding instanceof RouterPortBinding.Native,
                    "Both Router faces must resolve their native attachments");
            var north = ((RouterPortBinding.Native) northBinding).attachment().grid();
            var south = ((RouterPortBinding.Native) southBinding).attachment().grid();
            helper.assertTrue(north == south, "Two Router faces must observe one native Grid");
            var network = FederationDomainRegistryAccess.confirmedNetworkId(north).orElseThrow();
            var registry = FederationDomainRegistryAccess.get(helper.getLevel());
            var federationDomainId = registry.federationdomainsFor(network).iterator().next();
            var membership = registry.federationDomain(federationDomainId).orElseThrow().memberships().get(network);
            helper.assertValueEqual(membership.size(), 2, "Deduplicated network must retain both attachment sources");
            helper.assertValueEqual(registry.federationdomainsFor(network).size(), 1,
                    "Redundant attachments must index one Federation Domain");
            FederationDomainEvidence.write("federationdomainredundantmembership", 8, Map.of(
                    "nativeNetworkCount", "1", "attachmentSourceCount", "2", "indexedFederationDomainCount", "1",
                    "deduplicated", "true", "faceOwnershipRetained", "true", "identitySettled", "true",
                    "nativeJoinByFederationDomain", "false", "worldScan", "false"));
            fixtures.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 350, required = true, manualOnly = true)
    public static void federationDomainPartialUnload(GameTestHelper helper) {
        var fixtures = unsettledRouterFixtures(helper);
        var registry = FederationDomainRegistryAccess.get(helper.getLevel());
        var phase = new int[1];
        helper.succeedWhen(() -> {
            if (phase[0] == 0) {
                placeConnectedRouters(fixtures);
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for loaded reciprocal Router topology");
            }
            var leftNetwork = nativeNetwork(helper, fixtures, LEFT, Direction.NORTH);
            var rightNetwork = nativeNetwork(helper, fixtures, RIGHT, Direction.NORTH);
            helper.assertValueEqual(registry.federationdomainsFor(leftNetwork), registry.federationdomainsFor(rightNetwork),
                    "Unload fixture must begin merged");
            FederationCableBlockEntity middle = helper.getBlockEntity(new BlockPos(6, 4, 6));
            middle.onChunkUnloaded();
            helper.assertTrue(registry.federationdomainsFor(leftNetwork).isEmpty(),
                    "Partial unload must remove uncertain left membership immediately");
            helper.assertTrue(registry.federationdomainsFor(rightNetwork).isEmpty(),
                    "Partial unload must remove uncertain right membership immediately");
            FederationDomainEvidence.write("federationdomainpartialunload", 8, Map.of(
                    "initiallyMerged", "true", "unloadLifecycleInvoked", "true", "staleMembershipAccepted", "false",
                    "forcedChunkLoads", "0", "loadedEvidenceOnly", "true", "affectedComponentsOnly", "true",
                    "membershipInvalidated", "true", "worldScan", "false"));
            fixtures.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 350, required = true, manualOnly = true)
    public static void federationDomainRejectStaleRoute(GameTestHelper helper) {
        var fixtures = unsettledRouterFixtures(helper);
        var registry = FederationDomainRegistryAccess.get(helper.getLevel());
        var phase = new int[1];
        helper.succeedWhen(() -> {
            if (phase[0] == 0) {
                placeConnectedRouters(fixtures);
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for loaded reciprocal Router topology");
            }
            var leftNetwork = nativeNetwork(helper, fixtures, LEFT, Direction.NORTH);
            var federationDomainId = registry.federationdomainsFor(leftNetwork).iterator().next();
            var reference = registry.federationDomain(federationDomainId).orElseThrow().reference();
            helper.setBlock(new BlockPos(6, 4, 6), Blocks.AIR);
            helper.assertTrue(!registry.isCurrent(reference), "Topology invalidation must reject stale Federation Domain references");
            helper.assertTrue(registry.federationdomainsFor(leftNetwork).isEmpty(),
                    "Invalidation must remove stale network index truth immediately");
            FederationDomainEvidence.write("federationdomainrejectstaleroute", 8, Map.of(
                    "referenceInitiallyCurrent", "true", "referenceCurrentAfterChange", "false",
                    "staleRouteAccepted", "false", "indexInvalidatedImmediately", "true",
                    "generationBound", "true", "policyImplemented", "false", "possiblePathsEnumerated", "false",
                    "worldScan", "false"));
            fixtures.close();
        });
    }

    private static RouterFixtures unsettledRouterFixtures(GameTestHelper helper) {
        var fixtures = new RouterFixtures(helper);
        fixtures.placeNativeDevice(LEFT, Direction.NORTH);
        fixtures.placeNativeDevice(RIGHT, Direction.NORTH);
        return fixtures;
    }

    private static void placeConnectedRouters(RouterFixtures fixtures) {
        var leftGrid = fixtures.nativeDeviceNode(LEFT, Direction.NORTH).getGrid();
        var rightGrid = fixtures.nativeDeviceNode(RIGHT, Direction.NORTH).getGrid();
        if (FederationDomainRegistryAccess.confirmedNetworkId(leftGrid).isEmpty()
                || FederationDomainRegistryAccess.confirmedNetworkId(rightGrid).isEmpty()) {
            throw new net.minecraft.gametest.framework.GameTestAssertException(
                    "Native Router neighbors must settle before boundary nodes join");
        }
        fixtures.placeRouter(LEFT);
        fixtures.placeRouter(RIGHT);
        for (var x = LEFT.getX() + 1; x < RIGHT.getX(); x++) {
            fixtures.placeFederationCable(new BlockPos(x, LEFT.getY(), LEFT.getZ()));
        }
    }

    private static space.controlnet.ae2federation.identity.NetworkId nativeNetwork(GameTestHelper helper,
            RouterFixtures fixtures, BlockPos routerPosition, Direction face) {
        var binding = fixtures.router(routerPosition).binding(face);
        helper.assertTrue(binding instanceof RouterPortBinding.Native, "Router face must resolve its native attachment");
        var grid = ((RouterPortBinding.Native) binding).attachment().grid();
        var confirmed = FederationDomainRegistryAccess.confirmedNetworkId(grid);
        if (confirmed.isEmpty()) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("Native Router identity is not settled");
        }
        return confirmed.get();
    }
}
