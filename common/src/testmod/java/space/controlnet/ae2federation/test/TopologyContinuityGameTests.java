package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.core.definitions.AEBlocks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.domain.FederationDomainId;
import space.controlnet.ae2federation.domain.FederationDomainReference;
import space.controlnet.ae2federation.domain.FederationDomainRegistry;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.port.RouterPortBinding;
import space.controlnet.ae2federation.energy.EnergySharingService;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.test.domain.FederationDomainEvidence;
import space.controlnet.ae2federation.test.router.RouterFixtures;
import space.controlnet.ae2federation.test.world.MockServerPlayers;

/**
 * Network A (with a creative cell) supplies ME power to network B through Router, Federation Cable and Router. Changing
 * the domain around a working link must not interrupt it, and cutting the link must stop it at once. AE2 publishes a
 * lost network as unpowered on the same tick and as powered again only after 30 ticks, so even a one-tick gap in the
 * domain is visible for over a second; each placement is therefore checked in the tick it happens and sampled after.
 */
@PrefixGameTestTemplate(false)
public final class TopologyContinuityGameTests {
    private static final BlockPos LEFT = new BlockPos(3, 4, 6);
    private static final BlockPos RIGHT = new BlockPos(9, 4, 6);
    private static final BlockPos PATH_CABLE = new BlockPos(6, 4, 6);
    private static final int SAMPLE_TICKS = 40;

    private TopologyContinuityGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1200, required = true, manualOnly = true)
    public static void topologyPlacementKeepsPower(GameTestHelper helper) {
        var scene = new PoweredDomain(helper);
        var endpoint = ProcessingRegistration.ENDPOINT.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.NORTH);
        var provider = ProcessingRegistration.PROVIDER.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.NORTH);
        var placements = List.<Placement>of(
                new Placement("Federation Cable beside the path", true,
                        () -> scene.routers.placeFederationCable(new BlockPos(6, 4, 7))),
                new Placement("plain block beside the path", true, () -> helper.setBlock(new BlockPos(5, 4, 5), Blocks.STONE)),
                new Placement("Router beside the path", true, () -> scene.routers.placeRouter(new BlockPos(7, 4, 7))),
                new Placement("Processing Endpoint facing the path", true, () -> helper.setBlock(new BlockPos(4, 4, 7), endpoint)),
                // The Provider's own network joins the domain, which is a new member and so a new generation.
                new Placement("Federation Pattern Provider facing the path", false,
                        () -> helper.setBlock(new BlockPos(8, 4, 7), provider)));
        samplePlacements(helper, scene, placements, "topologyplacementkeepspower", Map.of());
    }

    /**
     * The same changes made the way a player makes them: {@code BlockItem.useOn} on a face of the domain, so the block
     * takes its orientation from the clicked face and AE2 sets its owner, which saves the node again after placement.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1200, required = true, manualOnly = true)
    public static void topologyPlayerPlacementKeepsPower(GameTestHelper helper) {
        var scene = new PoweredDomain(helper);
        var player = MockServerPlayers.inLevel(helper);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(6, 8, 2))));
        var placements = List.<Placement>of(
                new Placement("Federation Cable placed by a player", true, () -> scene.use(player,
                        RouterRegistration.FEDERATION_CABLE_ITEM.get(), PATH_CABLE, Direction.SOUTH, null)),
                new Placement("plain block placed by a player", true, () -> scene.use(player, Items.STONE,
                        new BlockPos(5, 4, 6), Direction.NORTH, null)),
                new Placement("Router placed by a player", true, () -> scene.use(player,
                        RouterRegistration.ROUTER_ITEM.get(), new BlockPos(7, 4, 6), Direction.SOUTH, null)),
                // Clicking the cable's south face turns the new block's front north, onto the cable.
                new Placement("Processing Endpoint placed by a player", true, () -> scene.use(player,
                        ProcessingRegistration.ENDPOINT_ITEM.get(), new BlockPos(4, 4, 6), Direction.SOUTH,
                        Direction.NORTH)),
                new Placement("Federation Pattern Provider placed by a player", false, () -> scene.use(player,
                        ProcessingRegistration.PROVIDER_ITEM.get(), new BlockPos(8, 4, 6), Direction.SOUTH,
                        Direction.NORTH)));
        samplePlacements(helper, scene, placements, "topologyplayerplacementkeepspower", Map.of("placedBy", "player"));
    }

    /**
     * Runs each placement in its own tick, checks the link in that tick, then samples B's power for
     * {@link #SAMPLE_TICKS} ticks; A and B must keep sharing energy throughout.
     */
    private static void samplePlacements(GameTestHelper helper, PoweredDomain scene, List<Placement> placements,
            String testId, Map<String, String> extraFacts) {
        var violations = new ArrayList<String>();
        var step = new int[] { -1 };
        var samples = new int[1];
        var unpowered = new int[1];
        helper.succeedWhen(() -> {
            if (step[0] < 0) {
                scene.awaitPowered();
                step[0] = 0;
            }
            if (step[0] < placements.size()) {
                if (samples[0] == 0) {
                    var placement = placements.get(step[0]);
                    var before = scene.reference();
                    placement.action().run();
                    scene.checkLinkNow(placement.name(), placement.keepsGeneration() ? before : null, violations);
                } else if (!scene.consumerPowered()) {
                    unpowered[0]++;
                }
                if (!scene.shares()) {
                    violations.add("A and B stopped sharing energy after the " + placements.get(step[0]).name());
                }
                if (++samples[0] > SAMPLE_TICKS) {
                    if (unpowered[0] > 0) {
                        violations.add("B unpowered for " + unpowered[0] + " of " + SAMPLE_TICKS + " ticks after the "
                                + placements.get(step[0]).name());
                    }
                    unpowered[0] = 0;
                    samples[0] = 0;
                    step[0]++;
                }
                throw new GameTestAssertException("Sampling B's power after each domain change");
            }
            helper.assertTrue(violations.isEmpty(), String.join("; ", violations));
            var facts = new java.util.LinkedHashMap<String, String>(Map.of(
                    "placements", Integer.toString(placements.size()), "sampledTicks", Integer.toString(SAMPLE_TICKS),
                    "sharingWithdrawn", "false", "consumerUnpowered", "false", "referenceKept", "true",
                    "worldScan", "false"));
            facts.putAll(extraFacts);
            FederationDomainEvidence.write(testId, facts.size(), facts);
            scene.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 800, required = true, manualOnly = true)
    public static void topologyBreakCutsPower(GameTestHelper helper) {
        var scene = new PoweredDomain(helper);
        var phase = new int[1];
        helper.succeedWhen(() -> {
            if (phase[0] == 0) {
                scene.awaitPowered();
                var reference = scene.reference();
                helper.setBlock(PATH_CABLE, Blocks.AIR);
                helper.assertTrue(!scene.registry().isCurrent(reference), "Cutting the path must invalidate the domain at once");
                helper.assertTrue(scene.sharedDomains().isEmpty(), "Cutting the path must separate A and B at once");
                helper.assertTrue(!scene.shares(), "Cutting the path must stop A and B sharing energy at once");
                helper.assertValueEqual(scene.consumerAvailable(), 0.0, "B must reach no power through a cut path");
                phase[0] = 1;
                throw new GameTestAssertException("Waiting for AE2 to publish B's power state");
            }
            if (phase[0] == 1) {
                phase[0] = 2;
                helper.assertTrue(!scene.consumerPowered(), "B must be unpowered on the tick after the cut");
            }
            FederationDomainEvidence.write("topologybreakcutspower", 5, Map.of(
                    "referenceCurrentAfterCut", "false", "domainShared", "false", "sharingWithdrawn", "true",
                    "consumerUnpowered", "true", "worldScan", "false"));
            scene.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1000, required = true, manualOnly = true)
    public static void topologyUnsettledAttachKeepsDomain(GameTestHelper helper) {
        var scene = new PoweredDomain(helper);
        var phase = new int[1];
        var samples = new int[1];
        var violations = new ArrayList<String>();
        var unpowered = new int[1];
        helper.succeedWhen(() -> {
            if (phase[0] == 0) {
                scene.awaitPowered();
                var before = scene.reference();
                // A fresh native network on a spare Router face is unsettled until AE2 has built its Grid.
                scene.routers.placeNativeDevice(LEFT, Direction.SOUTH);
                scene.checkLinkNow("fresh native network on a Router face", before, violations);
                phase[0] = 1;
                throw new GameTestAssertException("Sampling B's power while the new network settles");
            }
            if (phase[0] == 1) {
                if (!scene.consumerPowered()) {
                    unpowered[0]++;
                }
                if (++samples[0] <= SAMPLE_TICKS) {
                    throw new GameTestAssertException("Sampling B's power while the new network settles");
                }
                if (unpowered[0] > 0) {
                    violations.add("B unpowered for " + unpowered[0] + " of " + SAMPLE_TICKS
                            + " ticks after a fresh native network attached");
                }
                phase[0] = 2;
            }
            helper.assertTrue(violations.isEmpty(), String.join("; ", violations));
            var fresh = scene.routers.router(LEFT).binding(Direction.SOUTH);
            helper.assertTrue(fresh instanceof RouterPortBinding.Native, "The new network must attach natively");
            var network = FederationDomainRegistryAccess.confirmedNetworkId(((RouterPortBinding.Native) fresh).attachment().grid());
            helper.assertTrue(network.isPresent(), "The new network must settle");
            helper.assertTrue(scene.registry().federationdomainsFor(network.get()).containsAll(scene.sharedDomains()),
                    "The settled network must join the domain it attached to");
            FederationDomainEvidence.write("topologyunsettledattachkeepsdomain", 5, Map.of(
                    "sharingWithdrawn", "false", "consumerUnpowered", "false", "freshNetworkJoined", "true",
                    "referenceKept", "true", "worldScan", "false"));
            scene.close();
        });
    }

    private record Placement(String name, boolean keepsGeneration, Runnable action) {
    }

    /** LEFT carries A (ME Chest + creative cell) on its north face, RIGHT carries B, cable x = 4..8 joins them. */
    private static final class PoweredDomain implements AutoCloseable {
        private final GameTestHelper helper;
        private final RouterFixtures routers;
        private int stage;

        PoweredDomain(GameTestHelper helper) {
            this.helper = helper;
            routers = new RouterFixtures(helper);
            routers.placeNativeDevice(LEFT, Direction.NORTH);
            helper.setBlock(LEFT.north().above(), AEBlocks.CREATIVE_ENERGY_CELL.block());
            routers.placeNativeDevice(RIGHT, Direction.NORTH);
        }

        /** Advances the setup one step per tick and throws until B draws A's power through the domain. */
        void awaitPowered() {
            if (stage == 0) {
                if (FederationDomainRegistryAccess.confirmedNetworkId(grid(LEFT)).isEmpty()
                        || FederationDomainRegistryAccess.confirmedNetworkId(grid(RIGHT)).isEmpty()) {
                    throw new GameTestAssertException("Waiting for A and B to settle");
                }
                routers.placeSwitch(LEFT);
                routers.placeSwitch(RIGHT);
                for (var x = LEFT.getX() + 1; x < RIGHT.getX(); x++) {
                    routers.placeFederationCable(new BlockPos(x, LEFT.getY(), LEFT.getZ()));
                }
                stage = 1;
                throw new GameTestAssertException("Waiting for the Routers to join A and B");
            }
            if (stage == 1) {
                if (!(routers.router(LEFT).binding(Direction.NORTH) instanceof RouterPortBinding.Native)
                        || !(routers.router(RIGHT).binding(Direction.NORTH) instanceof RouterPortBinding.Native)
                        || sharedDomains().isEmpty()) {
                    throw new GameTestAssertException("Waiting for A and B to share a domain");
                }
                var policies = PolicyService.get(helper.getLevel());
                var result = policies.edit(new PolicyEdit(key(), policies.revision(key()),
                        PolicyRule.enabled(Set.of(PolicyOperation.SUPPLY))));
                helper.assertTrue(result instanceof PolicyMutationResult.Accepted, "B's ME power rule on A must be accepted");
                stage = 2;
                throw new GameTestAssertException("Waiting for B to be powered through the domain");
            }
            if (!consumerPowered() || !shares()) {
                throw new GameTestAssertException("Waiting for B to be powered through the domain");
            }
        }

        /** The link must hold in the very tick of the change, before any block entity or AE2 tick runs. */
        void checkLinkNow(String change, FederationDomainReference before, List<String> violations) {
            if (before != null && !registry().isCurrent(before)) {
                violations.add("the domain reference went stale on the " + change);
            }
            if (sharedDomains().isEmpty()) {
                violations.add("A and B stopped sharing a domain on the " + change);
            }
            if (!shares()) {
                violations.add("A and B stopped sharing energy on the " + change);
            }
            if (consumerAvailable() <= 0) {
                violations.add("B could reach no power on the " + change);
            }
        }

        /** Places {@code item} as {@code player} by clicking {@code face} of {@code clicked}, and checks the result. */
        void use(ServerPlayer player, Item item, BlockPos clicked, Direction face, Direction expectedFacing) {
            var stack = new ItemStack(item);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            var absolute = helper.absolutePos(clicked);
            var hit = new BlockHitResult(Vec3.atCenterOf(absolute).relative(face, 0.5), face, absolute, false);
            var result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            helper.assertTrue(result.consumesAction(), "The player must place " + item + " on " + clicked + " " + face);
            var placed = helper.getBlockState(clicked.relative(face));
            helper.assertTrue(placed.is(((net.minecraft.world.item.BlockItem) item).getBlock()),
                    item + " must stand beside " + clicked);
            if (expectedFacing != null) {
                helper.assertValueEqual(placed.getValue(BlockStateProperties.FACING), expectedFacing,
                        item + " must face the block it was placed against");
            }
        }

        FederationDomainReference reference() {
            var shared = sharedDomains();
            helper.assertValueEqual(shared.size(), 1, "A and B must share one domain");
            return registry().federationDomain(shared.iterator().next()).orElseThrow().reference();
        }

        Set<FederationDomainId> sharedDomains() {
            var shared = new HashSet<>(registry().federationdomainsFor(network(LEFT)));
            shared.retainAll(registry().federationdomainsFor(network(RIGHT)));
            return shared;
        }

        PolicyKey key() {
            return new PolicyKey(network(RIGHT), network(LEFT), PolicyCapability.ME_POWER);
        }

        boolean consumerPowered() {
            return grid(RIGHT).getEnergyService().isNetworkPowered();
        }

        double consumerAvailable() {
            return grid(RIGHT).getEnergyService().extractAEPower(1.0, Actionable.SIMULATE, PowerMultiplier.ONE);
        }

        FederationDomainRegistry registry() {
            return FederationDomainRegistryAccess.get(helper.getLevel());
        }

        boolean shares() {
            return EnergySharingService.shares(helper.getLevel(), key());
        }

        private IGrid grid(BlockPos router) {
            return routers.nativeDeviceNode(router, Direction.NORTH).getGrid();
        }

        private NetworkId network(BlockPos router) {
            return FederationDomainRegistryAccess.confirmedNetworkId(grid(router))
                    .orElseThrow(() -> new GameTestAssertException("Native network identity is not settled"));
        }

        @Override
        public void close() {
            routers.close();
        }
    }
}
