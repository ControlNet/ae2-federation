package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.util.AEColor;
import appeng.me.helpers.IGridConnectedBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.bridge.BridgeOperationalReason;
import space.controlnet.ae2federation.bridge.MultipartBridgePart;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.IdentityStatus;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.identity.NetworkIdentityService;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.router.RouterBlockEntity;
import space.controlnet.ae2federation.test.bridge.BridgeFixtures;
import space.controlnet.ae2federation.test.router.RouterFixtures;

/**
 * Player-order regressions: a Federation boundary (Router face, Bridge outer side) that exists before the native network
 * it later attaches to must not contribute an identity of its own to that network.
 */
@PrefixGameTestTemplate(false)
public final class BoundaryIdentityGameTests {
    private BoundaryIdentityGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void identityRouterBeforeCable(GameTestHelper helper) {
        var chest = new BlockPos(1, 1, 1);
        var cable = new BlockPos(2, 1, 1);
        var routerPos = new BlockPos(3, 1, 1);
        var routers = new RouterFixtures(helper);
        helper.setBlock(chest.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        routers.nativePorts().placeChest(chest);
        routers.placeSwitch(routerPos);
        var phase = new int[1];
        var original = new NetworkId[1];
        helper.succeedWhen(() -> {
            var chestNode = routers.nativePorts().chestNode(chest);
            if (phase[0] == 0) {
                helper.assertTrue(routers.router(routerPos).boundaryNode(Direction.WEST) != null,
                        "Router face node must exist before the cable is placed");
                original[0] = settled(helper, chestNode.getGrid(), "Native network must settle before the Router joins");
                routers.nativePorts().placeCable(cable);
                phase[0] = 1;
                helper.fail("Waiting for the cable to join the Router face");
            }
            if (phase[0] == 1) {
                var face = routers.router(routerPos).boundaryNode(Direction.WEST);
                helper.assertTrue(face != null && face.getGrid() == chestNode.getGrid(),
                        "Router face must join the native network through the new cable");
                helper.assertValueEqual(settled(helper, chestNode.getGrid(), "Router face must not make the network ambiguous"),
                        original[0], "Router face must not change the network identity");
                helper.setBlock(routerPos, Blocks.AIR);
                phase[0] = 2;
                helper.fail("Waiting for the Router removal");
            }
            helper.assertTrue(!(helper.getLevel().getBlockEntity(helper.absolutePos(routerPos)) instanceof RouterBlockEntity),
                    "Router must be removed");
            helper.assertValueEqual(settled(helper, chestNode.getGrid(), "Network must stay settled after the Router leaves"),
                    original[0], "Removing the Router must keep the original identity");
            routers.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void identityBridgeBeforeOuterNetwork(GameTestHelper helper) {
        var mainChest = new BlockPos(1, 1, 1);
        var bridgePos = new BlockPos(2, 1, 1);
        var joinCable = new BlockPos(3, 1, 1);
        var outerChest = new BlockPos(4, 1, 1);
        var bridges = new BridgeFixtures(helper);
        helper.setBlock(mainChest.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        bridges.nativePorts().placeChest(mainChest);
        bridges.nativePorts().placeCable(bridgePos);
        helper.setBlock(outerChest.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        bridges.nativePorts().placeChest(outerChest);
        var phase = new int[1];
        var bridge = new MultipartBridgePart[1];
        var originalOuter = new NetworkId[1];
        helper.succeedWhen(() -> {
            var outerNode = bridges.nativePorts().chestNode(outerChest);
            if (phase[0] == 0) {
                settled(helper, bridges.nativePorts().chestNode(mainChest).getGrid(), "Main network must settle first");
                originalOuter[0] = settled(helper, outerNode.getGrid(), "Outer network must settle first");
                bridge[0] = bridges.placeBridge(bridgePos, Direction.EAST);
                phase[0] = 1;
                helper.fail("Waiting for the Bridge outer node");
            }
            if (phase[0] == 1) {
                helper.assertTrue(bridge[0].getExternalFacingNode() != null, "Bridge outer node must exist");
                bridges.nativePorts().placeCable(joinCable);
                phase[0] = 2;
                helper.fail("Waiting for the outer network to reach the Bridge");
            }
            helper.assertTrue(bridge[0].getExternalFacingNode().getGrid() == outerNode.getGrid(),
                    "Bridge outer node must join the outer network");
            helper.assertValueEqual(settled(helper, outerNode.getGrid(), "Bridge outer node must not make the network ambiguous"),
                    originalOuter[0], "Bridge outer node must not change the outer identity");
            helper.assertValueEqual(bridge[0].operationalReason(), BridgeOperationalReason.VALID, "Bridge must be valid");
            bridges.close();
        });
    }

    /** The reported player layout: creative cell + controller + drive with a cell on each side of one Bridge. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void storageControllerDriveBridge(GameTestHelper helper) {
        var consumerController = new BlockPos(0, 1, 1);
        var consumerDrive = new BlockPos(1, 1, 1);
        var bridgePos = new BlockPos(2, 1, 1);
        var providerCable = new BlockPos(3, 1, 1);
        var providerDrive = new BlockPos(4, 1, 1);
        var providerController = new BlockPos(5, 1, 1);
        var bridges = new BridgeFixtures(helper);
        helper.setBlock(consumerController.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(consumerController, AEBlocks.CONTROLLER.block());
        helper.setBlock(consumerDrive, AEBlocks.DRIVE.block());
        bridges.nativePorts().placeCable(bridgePos, AEColor.RED);
        helper.setBlock(providerController.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(providerController, AEBlocks.CONTROLLER.block());
        helper.setBlock(providerDrive, AEBlocks.DRIVE.block());
        bridges.nativePorts().placeCable(providerCable, AEColor.BLUE);
        var phase = new int[1];
        var bridge = new MultipartBridgePart[1];
        var key = new PolicyKey[1];
        var diamond = AEItemKey.of(Items.DIAMOND);
        helper.succeedWhen(() -> {
            var consumer = node(helper, consumerDrive).getGrid();
            var provider = node(helper, providerDrive).getGrid();
            if (phase[0] == 0) {
                helper.<DriveBlockEntity>getBlockEntity(consumerDrive).getInternalInventory()
                        .setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
                helper.<DriveBlockEntity>getBlockEntity(providerDrive).getInternalInventory()
                        .setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
                phase[0] = 1;
                helper.fail("Waiting for drive cells to mount");
            }
            if (phase[0] == 1) {
                helper.assertTrue(consumer != provider, "Networks must be separate");
                helper.assertTrue(node(helper, providerDrive).isActive(), "Provider drive must be powered and online");
                settled(helper, consumer, "Consumer network must settle");
                settled(helper, provider, "Provider network must settle");
                helper.assertValueEqual(provider.getStorageService().getInventory().insert(diamond, 7,
                        Actionable.MODULATE, IActionSource.empty()), 7L, "Provider drive must accept the fixture items");
                bridge[0] = bridges.placeBridge(bridgePos, Direction.EAST);
                phase[0] = 2;
                helper.fail("Waiting for the Bridge");
            }
            if (phase[0] == 2) {
                helper.assertValueEqual(bridge[0].operationalReason(), BridgeOperationalReason.VALID, "Bridge must be valid");
                key[0] = new PolicyKey(settled(helper, consumer, "Consumer must stay settled"),
                        settled(helper, provider, "Provider must stay settled"), PolicyCapability.STORAGE);
                var result = PolicyService.get(helper.getLevel())
                        .edit(new PolicyEdit(key[0], PolicyRevision.NONE, PolicyRule.storageDefaults()));
                helper.assertTrue(result instanceof PolicyMutationResult.Accepted, "Storage policy must be accepted: " + result);
                phase[0] = 3;
                helper.fail("Waiting for the storage mount");
            }
            helper.assertValueEqual(consumer.getStorageService().getInventory().getAvailableStacks().get(diamond), 7L,
                    "Consumer terminal view must show the provider drive contents");
            bridges.close();
        });
    }

    /** The reported Router layout: a controller + drive network on each of two Router faces, Router placed first. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void storageControllerDriveRouter(GameTestHelper helper) {
        var consumerController = new BlockPos(0, 1, 1);
        var consumerDrive = new BlockPos(1, 1, 1);
        var consumerCable = new BlockPos(2, 1, 1);
        var routerPos = new BlockPos(3, 1, 1);
        var providerCable = new BlockPos(4, 1, 1);
        var providerDrive = new BlockPos(5, 1, 1);
        var providerController = new BlockPos(6, 1, 1);
        var routers = new RouterFixtures(helper);
        routers.placeSwitch(routerPos);
        helper.setBlock(consumerController.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(consumerController, AEBlocks.CONTROLLER.block());
        helper.setBlock(consumerDrive, AEBlocks.DRIVE.block());
        helper.setBlock(providerController.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(providerController, AEBlocks.CONTROLLER.block());
        helper.setBlock(providerDrive, AEBlocks.DRIVE.block());
        var phase = new int[1];
        var key = new PolicyKey[1];
        var diamond = AEItemKey.of(Items.DIAMOND);
        helper.succeedWhen(() -> {
            var consumer = node(helper, consumerDrive).getGrid();
            var provider = node(helper, providerDrive).getGrid();
            if (phase[0] == 0) {
                helper.assertTrue(routers.router(routerPos).boundaryNode(Direction.WEST) != null, "Router must be ready");
                helper.<DriveBlockEntity>getBlockEntity(consumerDrive).getInternalInventory()
                        .setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
                helper.<DriveBlockEntity>getBlockEntity(providerDrive).getInternalInventory()
                        .setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
                phase[0] = 1;
                helper.fail("Waiting for drive cells to mount");
            }
            if (phase[0] == 1) {
                helper.assertTrue(node(helper, providerDrive).isActive(), "Provider drive must be powered and online");
                settled(helper, consumer, "Consumer network must settle");
                settled(helper, provider, "Provider network must settle");
                helper.assertValueEqual(provider.getStorageService().getInventory().insert(diamond, 7,
                        Actionable.MODULATE, IActionSource.empty()), 7L, "Provider drive must accept the fixture items");
                routers.nativePorts().placeCable(consumerCable);
                routers.nativePorts().placeCable(providerCable);
                phase[0] = 2;
                helper.fail("Waiting for the cables to reach the Router");
            }
            if (phase[0] == 2) {
                helper.assertTrue(consumer != provider, "Router must not join the native networks");
                var consumerId = settled(helper, consumer, "Consumer must stay settled behind the Router");
                var providerId = settled(helper, provider, "Provider must stay settled behind the Router");
                var nodeId = FederationDomainRegistryAccess.nodeId(helper.getLevel(), helper.absolutePos(routerPos));
                var registry = FederationDomainRegistryAccess.get(helper.getLevel()).snapshot();
                var domains = registry.federationDomains().values().stream()
                        .filter(domain -> domain.nodes().contains(nodeId)).toList();
                helper.assertValueEqual(domains.size(), 1, "Router must publish one domain; invalidations="
                        + registry.invalidations().get(nodeId) + " bindings=" + routers.router(routerPos).bindings());
                helper.assertTrue(domains.getFirst().memberships().keySet().containsAll(java.util.Set.of(consumerId, providerId)),
                        "Router domain must contain both networks: members=" + domains.getFirst().memberships()
                                + " consumer=" + consumerId + " provider=" + providerId
                                + " bindings=" + routers.router(routerPos).bindings());
                key[0] = new PolicyKey(consumerId, providerId, PolicyCapability.STORAGE);
                var result = PolicyService.get(helper.getLevel())
                        .edit(new PolicyEdit(key[0], PolicyRevision.NONE, PolicyRule.storageDefaults()));
                helper.assertTrue(result instanceof PolicyMutationResult.Accepted, "Storage policy must be accepted: " + result);
                phase[0] = 3;
                helper.fail("Waiting for the storage mount");
            }
            helper.assertValueEqual(consumer.getStorageService().getInventory().getAvailableStacks().get(diamond), 7L,
                    "Consumer terminal view must show the provider drive contents");
            routers.close();
        });
    }

    private static NetworkId settled(GameTestHelper helper, IGrid grid, String message) {
        var settlement = grid.getService(NetworkIdentityService.class).settlement();
        helper.assertValueEqual(settlement.status(), IdentityStatus.SETTLED, message);
        return FederationDomainRegistryAccess.confirmedNetworkId(grid).orElseThrow();
    }

    private static IGridNode node(GameTestHelper helper, BlockPos position) {
        var entity = helper.getBlockEntity(position);
        helper.assertTrue(entity instanceof IGridConnectedBlockEntity, "Expected a native AE2 networked block");
        var node = ((IGridConnectedBlockEntity) entity).getMainNode().getNode();
        helper.assertTrue(node != null, "Expected an initialized native AE2 node");
        return node;
    }
}
