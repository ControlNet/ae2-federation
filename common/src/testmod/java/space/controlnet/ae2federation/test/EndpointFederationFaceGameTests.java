package space.controlnet.ae2federation.test;

import appeng.api.networking.IGridNode;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.helpers.IGridConnectedBlockEntity;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.FederationDomainSnapshot;
import space.controlnet.ae2federation.domain.port.FederationPortCapability;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.processing.endpoint.EndpointBlockEntity;
import space.controlnet.ae2federation.router.RouterRegistration;

/**
 * The Processing Endpoint's Federation face (its FRONT) joins a Federation Domain through a Federation Cable, a Router
 * or a Federation Pattern Provider's front face. The Endpoint then is a node of that domain, while the ME network on its
 * other five faces stays outside the domain: it is only a processing target.
 */
@PrefixGameTestTemplate(false)
public final class EndpointFederationFaceGameTests {
    private static final BlockPos MEMBER_ENERGY = new BlockPos(1, 1, 3);
    private static final BlockPos MEMBER_CHEST = new BlockPos(2, 1, 3);
    private static final BlockPos HUB = new BlockPos(3, 1, 3);
    private static final BlockPos NEAR = new BlockPos(4, 1, 3);
    private static final BlockPos FAR = new BlockPos(5, 1, 3);

    private EndpointFederationFaceGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void endpointFederationFaceCable(GameTestHelper helper) {
        var scene = new Scene(helper);
        scene.member();
        helper.setBlock(HUB, RouterRegistration.ROUTER.get());
        helper.setBlock(NEAR, RouterRegistration.FEDERATION_CABLE.get());
        scene.endpoint(FAR, Direction.WEST);
        helper.succeedWhen(() -> scene.assertEndpointJoins(HUB, FAR));
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void endpointFederationFaceRouter(GameTestHelper helper) {
        var scene = new Scene(helper);
        scene.member();
        helper.setBlock(HUB, RouterRegistration.ROUTER.get());
        scene.endpoint(NEAR, Direction.WEST);
        helper.succeedWhen(() -> scene.assertEndpointJoins(HUB, NEAR));
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void endpointFederationFaceProvider(GameTestHelper helper) {
        var scene = new Scene(helper);
        scene.place(MEMBER_CHEST, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), scene.member);
        scene.place(HUB, ProcessingRegistration.PROVIDER.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.EAST), scene.member);
        scene.endpoint(NEAR, Direction.WEST);
        helper.succeedWhen(() -> scene.assertEndpointJoins(HUB, NEAR));
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void endpointFederationFaceRotate(GameTestHelper helper) {
        var scene = new Scene(helper);
        scene.member();
        helper.setBlock(HUB, RouterRegistration.ROUTER.get());
        helper.setBlock(NEAR, RouterRegistration.FEDERATION_CABLE.get());
        scene.endpoint(FAR, Direction.WEST);
        var rotated = new boolean[1];
        helper.succeedWhen(() -> {
            if (!rotated[0]) {
                scene.assertEndpointJoins(HUB, FAR);
                var level = helper.getLevel();
                var position = helper.absolutePos(FAR);
                level.setBlockAndUpdate(position, level.getBlockState(position)
                        .setValue(BlockStateProperties.FACING, Direction.NORTH));
                rotated[0] = true;
                helper.fail("rotated; waiting for the domain to drop the Endpoint");
            }
            var domain = scene.domainOf(HUB).orElseThrow(() -> new AssertionError("the Router's domain must remain"));
            helper.assertFalse(domain.nodes().contains(scene.nodeId(FAR)),
                    "an Endpoint whose Federation face turned away must leave the domain");
            helper.assertTrue(scene.port(FAR, Direction.NORTH) && !scene.port(FAR, Direction.WEST),
                    "the Federation port must follow the Endpoint's front");
        });
    }

    private static final class Scene {
        private final GameTestHelper helper;
        private final NetworkId member = NetworkId.create();
        private final NetworkId subnet = NetworkId.create();

        Scene(GameTestHelper helper) {
            this.helper = helper;
        }

        /** An ME network on the hub's west face, so the domain has a member. */
        void member() {
            place(MEMBER_ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), member);
            place(MEMBER_CHEST, AEBlocks.ME_CHEST.block().defaultBlockState(), member);
            helper.<MEChestBlockEntity>getBlockEntity(MEMBER_CHEST).setCell(AEItems.ITEM_CELL_1K.stack());
        }

        /** An Endpoint whose Federation face looks at {@code front}, with its own subnet (chest + energy) above. */
        void endpoint(BlockPos position, Direction front) {
            place(position, ProcessingRegistration.ENDPOINT.get().defaultBlockState()
                    .setValue(BlockStateProperties.FACING, front), subnet);
            place(position.above(), AEBlocks.ME_CHEST.block().defaultBlockState(), subnet);
            helper.<MEChestBlockEntity>getBlockEntity(position.above()).setCell(AEItems.ITEM_CELL_1K.stack());
            place(position.above(2), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), subnet);
        }

        void place(BlockPos position, BlockState state, NetworkId network) {
            helper.setBlock(position, state);
            var entity = (IGridConnectedBlockEntity) helper.getBlockEntity(position);
            entity.getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", network));
        }

        void assertEndpointJoins(BlockPos hub, BlockPos endpoint) {
            var endpointNode = node(endpoint);
            helper.assertTrue(endpointNode != null && endpointNode.isActive(), "Endpoint subnet must be powered");
            helper.assertTrue(helper.getBlockEntity(endpoint) instanceof EndpointBlockEntity entity
                    && entity.federationFace() == helper.getLevel().getBlockState(helper.absolutePos(endpoint))
                            .getValue(BlockStateProperties.FACING), "the Federation face must be the block's facing");
            var domain = domainOf(hub).orElseThrow(() -> new AssertionError("the hub must be in a domain"));
            helper.assertTrue(domain.nodes().contains(nodeId(endpoint)), "the Endpoint must be a node of the hub's domain");
            helper.assertTrue(domain.memberships().containsKey(member), "the member network must be a domain member");
            helper.assertFalse(domain.memberships().containsKey(subnet),
                    "the Endpoint's subnet must not join the domain");
            var subnetGrid = endpointNode.getGrid();
            var memberGrid = node(MEMBER_CHEST) == null ? null : node(MEMBER_CHEST).getGrid();
            helper.assertTrue(subnetGrid != memberGrid, "the Endpoint must not merge its subnet into the member network");
        }

        Optional<FederationDomainSnapshot> domainOf(BlockPos position) {
            var id = nodeId(position);
            return FederationDomainRegistryAccess.get(helper.getLevel()).snapshot().federationDomains().values().stream()
                    .filter(domain -> domain.nodes().contains(id)).findFirst();
        }

        space.controlnet.ae2federation.domain.FederationDomainNodeId nodeId(BlockPos position) {
            return FederationDomainRegistryAccess.nodeId(helper.getLevel(), helper.absolutePos(position));
        }

        boolean port(BlockPos position, Direction face) {
            return helper.getLevel().getCapability(FederationPortCapability.BLOCK, helper.absolutePos(position), face) != null;
        }

        IGridNode node(BlockPos position) {
            return helper.getBlockEntity(position) instanceof IGridConnectedBlockEntity entity
                    ? entity.getMainNode().getNode() : null;
        }
    }
}
