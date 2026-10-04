package space.controlnet.ae2federation.test;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.api.networking.IGridNode;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.helpers.IGridConnectedBlockEntity;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.FederationDomainSnapshot;
import space.controlnet.ae2federation.domain.port.FederationPortCapability;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.ae2.processing.endpoint.EndpointMode;
import space.controlnet.ae2federation.processing.claim.ClaimRejection;
import space.controlnet.ae2federation.processing.claim.ClaimRequest;
import space.controlnet.ae2federation.processing.claim.ClaimResult;
import space.controlnet.ae2federation.processing.claim.ClaimState;
import space.controlnet.ae2federation.processing.claim.EndpointOwnerIdentity;
import space.controlnet.ae2federation.processing.endpoint.EndpointBlockEntity;
import space.controlnet.ae2federation.processing.endpoint.EndpointModeGeneration;
import space.controlnet.ae2federation.processing.provider.ProviderIdentity;
import space.controlnet.ae2federation.router.RouterRegistration;

/**
 * The Processing Endpoint's Federation face (its FRONT) joins a Federation Domain through a Federation Cable, a Router
 * or a Federation Pattern Provider's front face. The Endpoint then is a node of that domain, while the ME network on its
 * other five faces stays outside the domain: it is only a processing target. A native AE2 Pattern Provider on the
 * Federation face instead selects Local mode.
 */
@PrefixGameTestTemplate(false)
public final class EndpointFederationFaceGameTests {
    static final BlockPos MEMBER_ENERGY = new BlockPos(1, 1, 3);
    static final BlockPos MEMBER_CHEST = new BlockPos(2, 1, 3);
    static final BlockPos HUB = new BlockPos(3, 1, 3);
    static final BlockPos NEAR = new BlockPos(4, 1, 3);
    static final BlockPos FAR = new BlockPos(5, 1, 3);

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

    /**
     * Claiming, activating and releasing an Endpoint change its state, not its Federation link: the domain it is a node
     * of keeps its generation, so workspaces open on that domain stay current. Here the Endpoint's Federation face is on
     * a Federation Cable.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void endpointFederationFaceClaimKeepsDomain(GameTestHelper helper) {
        var scene = new Scene(helper);
        scene.member();
        helper.setBlock(HUB, RouterRegistration.ROUTER.get());
        helper.setBlock(NEAR, RouterRegistration.FEDERATION_CABLE.get());
        scene.endpoint(FAR, Direction.WEST);
        assertClaimKeepsDomain(helper, scene, FAR);
    }

    /** As {@link #endpointFederationFaceClaimKeepsDomain}, with the Federation face directly on a Router face. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void endpointFederationFaceClaimKeepsRouterDomain(GameTestHelper helper) {
        var scene = new Scene(helper);
        scene.member();
        helper.setBlock(HUB, RouterRegistration.ROUTER.get());
        scene.endpoint(NEAR, Direction.WEST);
        assertClaimKeepsDomain(helper, scene, NEAR);
    }

    /** As {@link #endpointFederationFaceClaimKeepsDomain}, with the Federation face directly on a Provider front. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void endpointFederationFaceClaimKeepsProviderDomain(GameTestHelper helper) {
        var scene = new Scene(helper);
        scene.place(MEMBER_CHEST, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), scene.member);
        scene.place(HUB, ProcessingRegistration.PROVIDER.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.EAST), scene.member);
        scene.endpoint(NEAR, Direction.WEST);
        assertClaimKeepsDomain(helper, scene, NEAR);
    }

    private static void assertClaimKeepsDomain(GameTestHelper helper, Scene scene, BlockPos endpointPosition) {
        var owner = new EndpointOwnerIdentity(ProviderIdentity.create());
        // The settled generation, the tick it was first seen, and the tick of the release (-1 before it).
        var seen = new long[] {-1, 0, -1};
        helper.succeedWhen(() -> {
            scene.assertEndpointJoins(HUB, endpointPosition);
            var endpoint = helper.<EndpointBlockEntity>getBlockEntity(endpointPosition);
            var generation = scene.domainOf(HUB).orElseThrow().generation();
            if (seen[2] < 0) {
                if (generation != seen[0]) {
                    seen[0] = generation;
                    seen[1] = helper.getTick();
                }
                helper.assertTrue(helper.getTick() - seen[1] >= 20, "waiting for the domain to settle");
                helper.assertTrue(endpoint.claim(new ClaimRequest(endpoint.endpointIdentity(), endpoint.claimState().epoch(),
                        owner)) instanceof ClaimResult.Acquired, "A Federated Endpoint must accept a Claim");
                helper.assertTrue(endpoint.activateFederated(), "The claimed Endpoint must activate Federated mode");
                // Still the same tick: the claim must not have withdrawn the domain.
                var claimed = scene.domainOf(HUB).map(FederationDomainSnapshot::generation).orElse(-1L);
                helper.assertTrue(endpoint.releaseClaim(owner, endpoint.claimState().epoch()), "The owner must release its Claim");
                seen[2] = helper.getTick();
                helper.assertValueEqual(claimed, seen[0], "Claiming must not withdraw the Endpoint's domain");
                helper.fail("claimed and released; waiting");
            }
            helper.assertValueEqual(generation, seen[0], "Claim and release must not republish the Endpoint's domain");
            helper.assertTrue(helper.getTick() - seen[2] >= 20, "waiting 20 ticks after the release");
        });
    }

    /**
     * The Endpoint's subnet may itself be a member of the domain through another route, here the Router's south face,
     * while its Federation face joins the same domain through a cable. The domain must settle instead of republishing.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void endpointFederationFaceSubnetMember(GameTestHelper helper) {
        var scene = new Scene(helper);
        scene.member();
        helper.setBlock(HUB, RouterRegistration.ROUTER.get());
        helper.setBlock(NEAR, RouterRegistration.FEDERATION_CABLE.get());
        var endpoint = NEAR.south();
        scene.place(endpoint, ProcessingRegistration.ENDPOINT.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.NORTH), scene.subnet);
        scene.place(HUB.south(), AEBlocks.ME_CHEST.block().defaultBlockState(), scene.subnet);
        helper.<MEChestBlockEntity>getBlockEntity(HUB.south()).setCell(AEItems.ITEM_CELL_1K.stack());
        scene.place(endpoint.above(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), scene.subnet);
        // The generation last seen and the tick it was first seen.
        var settled = new long[] {-1, 0};
        helper.succeedWhen(() -> {
            var found = scene.domainOf(HUB);
            helper.assertTrue(found.isPresent(), "the Router must be in a domain");
            var domain = found.get();
            helper.assertTrue(domain.nodes().contains(scene.nodeId(endpoint)), "the Endpoint must be a node of the Router's domain");
            helper.assertTrue(domain.memberships().containsKey(scene.member) && domain.memberships().containsKey(scene.subnet),
                    "both networks on the Router's faces must be members");
            if (settled[0] != domain.generation()) {
                settled[0] = domain.generation();
                settled[1] = helper.getTick();
            }
            helper.assertTrue(helper.getTick() - settled[1] >= 40,
                    "the domain must keep one generation for 40 ticks; now " + domain.generation());
        });
    }

    /**
     * A native AE2 Pattern Provider belongs to another network, so it sits on the Federation face: the Endpoint turns
     * Local, takes the Provider's input into its subnet and refuses Federated Claims. When the native Provider leaves,
     * the Endpoint is Federated again and can be claimed; when it returns, Local takes over and releases that Claim.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void endpointFederationFaceLocal(GameTestHelper helper) {
        var scene = new Scene(helper);
        scene.place(MEMBER_CHEST, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), scene.member);
        scene.nativeProvider(HUB);
        scene.endpoint(NEAR, Direction.WEST);
        var owner = new EndpointOwnerIdentity(ProviderIdentity.create());
        var phase = new int[1];
        helper.succeedWhen(() -> {
            var endpoint = helper.<EndpointBlockEntity>getBlockEntity(NEAR);
            var binding = endpoint.binding();
            helper.assertTrue(binding != null, "Waiting for the Endpoint binding");
            switch (phase[0]) {
                case 0 -> {
                    helper.assertTrue(binding.runtime().mode().orElse(null) instanceof EndpointModeGeneration.Local,
                            "A native Provider on the Federation face must select Local mode");
                    helper.assertTrue(scene.nativePush(HUB), "The native Provider must push into the Federation face");
                    helper.assertValueEqual(scene.subnetCount(NEAR), 1L, "Local input must reach the subnet storage");
                    var rejected = endpoint.claim(new ClaimRequest(endpoint.endpointIdentity(),
                            endpoint.claimState().epoch(), owner));
                    helper.assertTrue(rejected instanceof ClaimResult.Rejected denied
                            && denied.reason() == ClaimRejection.LOCAL_MODE,
                            "A Local Endpoint must refuse a Federated Claim");
                    helper.setBlock(HUB, net.minecraft.world.level.block.Blocks.AIR);
                    phase[0] = 1;
                    helper.fail("removed the native Provider");
                }
                case 1 -> {
                    helper.assertValueEqual(binding.runtime().configuredMode(), EndpointMode.FEDERATED,
                            "Without a native Provider the Endpoint must be Federated");
                    helper.assertTrue(endpoint.claim(new ClaimRequest(endpoint.endpointIdentity(),
                            endpoint.claimState().epoch(), owner)) instanceof ClaimResult.Acquired,
                            "A Federated Endpoint must accept a Claim");
                    helper.assertTrue(endpoint.activateFederated(), "The claimed Endpoint must activate Federated mode");
                    scene.nativeProvider(HUB);
                    phase[0] = 2;
                    helper.fail("placed the native Provider again");
                }
                default -> {
                    helper.assertTrue(binding.runtime().mode().orElse(null) instanceof EndpointModeGeneration.Local,
                            "The returning native Provider must take the Endpoint back to Local mode");
                    helper.assertTrue(endpoint.claimState() instanceof ClaimState.Unclaimed,
                            "Local takeover must release the Federated Claim first");
                }
            }
        });
    }

    /**
     * A claimed Federated Endpoint shares its subnet's energy with the network of the Provider that claims it, as one
     * pool: a subnet with no energy cell of its own is powered while the claim holds and its switch is on, and loses
     * power when the switch is turned off or the Provider releases the claim.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 600, required = true, manualOnly = true)
    public static void endpointSubnetEnergy(GameTestHelper helper) {
        var scene = new Scene(helper);
        scene.place(MEMBER_CHEST, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), scene.member);
        scene.place(HUB, ProcessingRegistration.PROVIDER.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.EAST), scene.member);
        // The subnet has a chest but no energy cell: only the shared pool can power it.
        scene.place(NEAR, ProcessingRegistration.ENDPOINT.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.WEST), scene.subnet);
        scene.place(NEAR.above(), AEBlocks.ME_CHEST.block().defaultBlockState(), scene.subnet);
        var phase = new int[1];
        helper.succeedWhen(() -> {
            var provider = helper.<space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity>
                    getBlockEntity(HUB);
            var endpoint = helper.<EndpointBlockEntity>getBlockEntity(NEAR);
            helper.assertTrue(provider.runtime().isPresent() && endpoint.binding() != null, "waiting for the devices");
            var subnet = scene.node(NEAR);
            helper.assertTrue(subnet != null && subnet.getGrid() != scene.node(HUB).getGrid(),
                    "the subnet must stay its own Grid");
            switch (phase[0]) {
                case 0 -> {
                    helper.assertTrue(endpoint.shareEnergy(), "an Endpoint shares energy by default");
                    helper.assertFalse(subnet.isPowered(), "an unclaimed subnet without a cell must be unpowered");
                    installPattern(provider);
                    var status = provider.toggleEndpoint(provider.mappedProvider().mappingHandle(0), endpoint.binding());
                    helper.assertTrue(status.startsWith("accepted-"), "the Provider must map the Endpoint: " + status);
                    phase[0] = 1;
                    helper.fail("mapped the Endpoint");
                }
                case 1 -> {
                    helper.assertTrue(endpoint.claimState() instanceof ClaimState.Owned, "the mapping must claim the Endpoint");
                    helper.assertTrue(subnet.isPowered(), "the claimed subnet must share the Provider network's power");
                    endpoint.setShareEnergy(false);
                    var saved = endpoint.saveWithoutMetadata(helper.getLevel().registryAccess());
                    helper.assertTrue(saved.contains("endpointShareEnergy") && !saved.getBoolean("endpointShareEnergy"),
                            "the switch must persist");
                    phase[0] = 2;
                    helper.fail("turned sharing off");
                }
                case 2 -> {
                    helper.assertFalse(subnet.isPowered(), "a subnet that stops sharing must lose power");
                    endpoint.setShareEnergy(true);
                    phase[0] = 3;
                    helper.fail("turned sharing on");
                }
                case 3 -> {
                    helper.assertTrue(subnet.isPowered(), "a subnet that shares again must be powered");
                    var status = provider.toggleEndpoint(provider.mappedProvider().mappingHandle(0), endpoint.binding());
                    helper.assertTrue(status.startsWith("accepted-"), "the Provider must unmap the Endpoint: " + status);
                    phase[0] = 4;
                    helper.fail("unmapped the Endpoint");
                }
                case 4 -> {
                    helper.assertTrue(endpoint.claimState() instanceof ClaimState.Unclaimed, "unmapping must release the claim");
                    helper.assertFalse(subnet.isPowered(), "a released subnet must lose power");
                    helper.assertFalse(endpoint.binding().subnetAlone(), "the chest stands behind the Endpoint");
                    helper.setBlock(NEAR.above(), net.minecraft.world.level.block.Blocks.AIR);
                    phase[0] = 5;
                    helper.fail("removed the chest");
                }
                default -> helper.assertTrue(endpoint.binding().subnetAlone(),
                        "with the chest gone nothing stands behind the Endpoint");
            }
        });
    }

    private static void installPattern(space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity
            provider) {
        var inventory = provider.getTerminalPatternInventory();
        if (!inventory.getStackInSlot(0).isEmpty()) return;
        inventory.insertItem(0, PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1)),
                List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1))), false);
    }

    static final class Scene {
        private final GameTestHelper helper;
        final NetworkId member = NetworkId.create();
        final NetworkId subnet = NetworkId.create();

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

        /** A native AE2 Pattern Provider of the member network that pushes east, into the block next to it. */
        void nativeProvider(BlockPos position) {
            place(position, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState()
                    .setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.EAST), member);
        }

        /** Pushes one cobblestone through the native Provider's processing pattern, as its crafting job would. */
        boolean nativePush(BlockPos position) {
            var logic = helper.<PatternProviderBlockEntity>getBlockEntity(position).getLogic();
            if (logic.getAvailablePatterns().isEmpty()) {
                logic.getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                        List.of(new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1)),
                        List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1))));
                logic.updatePatterns();
            }
            var counter = new KeyCounter();
            counter.add(AEItemKey.of(Items.COBBLESTONE), 1);
            return logic.pushPattern(logic.getAvailablePatterns().getFirst(), new KeyCounter[] { counter });
        }

        long subnetCount(BlockPos endpoint) {
            return node(endpoint).getGrid().getStorageService().getInventory().getAvailableStacks()
                    .get(AEItemKey.of(Items.COBBLESTONE));
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
