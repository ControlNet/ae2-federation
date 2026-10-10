package space.controlnet.ae2federation.test;

import static space.controlnet.ae2federation.test.p2p.FederationP2PScene.CARRIER_POWER;
import static space.controlnet.ae2federation.test.p2p.FederationP2PScene.LEFT_BUS;
import static space.controlnet.ae2federation.test.p2p.FederationP2PScene.LEFT_CABLE;
import static space.controlnet.ae2federation.test.p2p.FederationP2PScene.RIGHT_BUS;
import static space.controlnet.ae2federation.test.p2p.FederationP2PScene.RIGHT_CABLE;

import appeng.core.definitions.AEBlocks;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.identity.IdentityStatus;
import space.controlnet.ae2federation.p2p.FederationP2PTunnelPart;
import space.controlnet.ae2federation.processing.claim.ClaimState;
import space.controlnet.ae2federation.router.CableVisualConnections;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.test.p2p.FederationP2PScene;
import space.controlnet.ae2federation.test.p2p.QuantumP2POutpostScene;
import space.controlnet.ae2federation.test.p2p.QuantumP2PProviderScene;
import space.controlnet.ae2federation.test.p2p.QuantumP2PRouterScene;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;
import space.controlnet.ae2federation.test.world.BlockEntityReload;

/**
 * A Federation P2P tunnel is Federation cable over an ME network: two networks whose Routers reach each other only
 * through tunnels share storage, the network carrying the tunnels never joins their domain, and the link follows the
 * tunnels' power, channel and input like AE2's own tunnels. Over a Quantum Bridge the same holds across dimensions.
 */
@PrefixGameTestTemplate(false)
public final class FederationP2PGameTests {
    private FederationP2PGameTests() {
    }

    /**
     * An ME P2P tunnel attuned with a Federation cable becomes a Federation P2P tunnel; paired with a memory card, it
     * joins the consumer's and the provider's Routers into one domain without the carrier, and cable reaches its front.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void p2pTunnelSharesStorage(GameTestHelper helper) {
        var scene = new FederationP2PScene(helper);
        var pair = new Pair(helper, scene);
        helper.succeedWhen(() -> {
            pair.advance();
            helper.assertValueEqual(scene.consumerIron(), 9L, "Waiting for the consumer to see the iron through the tunnel");
            helper.assertTrue(scene.carrierIsNoMember(pair.left.getMainNode().getGrid()),
                    "The network carrying the tunnels must not join their domain");
            var domain = scene.domainOf(pair.left).orElseThrow();
            helper.assertTrue(scene.consumerDomains().contains(domain.federationDomainId())
                    && scene.domainOf(pair.right).orElseThrow().federationDomainId().equals(domain.federationDomainId()),
                    "Both tunnels must be nodes of the consumer's domain");
            helper.assertTrue(arm(helper, LEFT_CABLE, Direction.EAST) && arm(helper, RIGHT_CABLE, Direction.WEST),
                    "The Federation cable must reach toward each tunnel's front");
            PolicyEvidence.write("p2ptunnelsharesstorage", 8, Map.of("consumerIron", "9", "carrierMember", "false",
                    "tunnelsInDomain", "2"));
            scene.close();
            helper.succeed();
        });
    }

    /**
     * Two outputs of one input link to each other through it: the consumer and the provider each meet an output, and
     * they share only while the input is on the carrier.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 500, required = true, manualOnly = true)
    public static void p2pOutputsLinkThroughInput(GameTestHelper helper) {
        var scene = new FederationP2PScene(helper);
        var inputBus = new BlockPos(6, 4, 8);
        var inputLink = new BlockPos(6, 4, 7);
        var tunnels = new FederationP2PTunnelPart[3];
        var step = new int[1];
        helper.succeedWhen(() -> {
            if (step[0] == 0) {
                helper.assertTrue(scene.networks().networksSettled(), "Waiting for the networks to settle");
                scene.placeCable(LEFT_BUS);
                scene.placeCable(RIGHT_BUS);
                scene.placeCable(inputLink);
                scene.placeCable(inputBus);
                tunnels[0] = scene.attunedTunnel(inputBus, Direction.SOUTH);
                tunnels[1] = scene.attunedTunnel(LEFT_BUS, Direction.WEST);
                tunnels[2] = scene.attunedTunnel(RIGHT_BUS, Direction.EAST);
                step[0] = 1;
            }
            if (step[0] == 1) {
                helper.assertTrue(FederationP2PScene.online(tunnels), "Waiting for the tunnels' channels");
                scene.pair(tunnels[0], List.of(tunnels[1], tunnels[2]));
                scene.placeRouters();
                step[0] = 2;
            }
            if (step[0] == 2) {
                helper.assertTrue(scene.networks().connected(), "Waiting for the outputs to join the two Routers");
                scene.enableStorage();
                step[0] = 3;
            }
            if (step[0] == 3) {
                helper.assertValueEqual(scene.consumerIron(), 9L, "Waiting for the consumer to see the iron");
                scene.remove(inputLink);
                step[0] = 4;
            }
            if (step[0] == 4) {
                helper.assertFalse(scene.networks().connected(), "Waiting for the outputs to part without their input");
                helper.assertValueEqual(scene.consumerIron(), 0L, "Waiting for the consumer to lose the iron");
                scene.placeCable(inputLink);
                step[0] = 5;
            }
            helper.assertTrue(scene.networks().connected(), "Waiting for the input to link its outputs again");
            helper.assertValueEqual(scene.consumerIron(), 9L, "Waiting for the consumer to see the iron again");
            PolicyEvidence.write("p2poutputslinkthroughinput", 7, Map.of("outputs", "2", "withoutInput", "0",
                    "restored", "9"));
            scene.close();
            helper.succeed();
        });
    }

    /**
     * Tunnels of two frequencies on the same buses stay two nodes in two domains, and an output of the same frequency
     * on another carrier links to nothing.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void p2pTunnelsStayApart(GameTestHelper helper) {
        var scene = new FederationP2PScene(helper);
        var pair = new Pair(helper, scene);
        var otherBus = new BlockPos(1, 4, 8);
        var second = new FederationP2PTunnelPart[3];
        var placed = new boolean[1];
        helper.succeedWhen(() -> {
            if (!placed[0]) {
                helper.assertTrue(scene.networks().networksSettled(), "Waiting for the networks to settle");
                helper.setBlock(LEFT_BUS.north(), RouterRegistration.FEDERATION_CABLE.get());
                helper.setBlock(RIGHT_BUS.north(), RouterRegistration.FEDERATION_CABLE.get());
                scene.placeCable(otherBus);
                helper.setBlock(otherBus.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
                helper.setBlock(otherBus.above(), RouterRegistration.FEDERATION_CABLE.get());
                placed[0] = true;
            }
            pair.advance();
            if (second[0] == null) {
                second[0] = scene.attunedTunnel(LEFT_BUS, Direction.NORTH);
                second[1] = scene.attunedTunnel(RIGHT_BUS, Direction.NORTH);
                second[2] = scene.attunedTunnel(otherBus, Direction.UP);
            }
            if (second[0].getFrequency() == 0) {
                helper.assertTrue(FederationP2PScene.online(second), "Waiting for the second tunnels' channels");
                // The card still holds the first pair's frequency: the other carrier's tunnel takes it first.
                scene.pair(pair.left, List.of(second[2]));
                scene.pair(second[0], List.of(second[1]));
            }
            helper.assertValueEqual(scene.consumerIron(), 9L, "Waiting for the consumer to see the iron");
            var first = scene.domainOf(pair.left).orElseThrow().federationDomainId();
            var other = scene.domainOf(second[0]);
            helper.assertTrue(other.isPresent() && other.get().nodes().size() == 4,
                    "Waiting for the second pair's domain: its two tunnels and the cable in front of each");
            helper.assertTrue(!other.get().federationDomainId().equals(first) && other.get().memberships().isEmpty(),
                    "The second pair on the same buses must be its own domain");
            helper.assertTrue(second[2].getFrequency() == pair.left.getFrequency() && second[2].getInput() == null,
                    "The other carrier's tunnel has the frequency but no input");
            helper.assertTrue(scene.domainOf(second[2]).map(domain -> domain.nodes().size() == 2).orElse(false),
                    "The other carrier's tunnel must reach only the cable in front of it");
            PolicyEvidence.write("p2ptunnelsstayapart", 9, Map.of("consumerIron", "9", "secondDomainNodes", "4",
                    "otherCarrierNodes", "2"));
            scene.close();
            helper.succeed();
        });
    }

    /**
     * The link holds only while the carrier has power, and a tunnel saved and loaded again keeps its frequency and its
     * role, so the link returns.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 500, required = true, manualOnly = true)
    public static void p2pLinkFollowsPowerAndReload(GameTestHelper helper) {
        var scene = new FederationP2PScene(helper);
        var pair = new Pair(helper, scene);
        var step = new int[1];
        var frequency = new short[1];
        helper.succeedWhen(() -> {
            pair.advance();
            if (step[0] == 0) {
                helper.assertValueEqual(scene.consumerIron(), 9L, "Waiting for the consumer to see the iron");
                scene.remove(CARRIER_POWER);
                step[0] = 1;
            }
            if (step[0] == 1) {
                helper.assertValueEqual(scene.consumerIron(), 0L, "Waiting for the unpowered tunnels to drop the link");
                helper.setBlock(CARRIER_POWER, AEBlocks.CREATIVE_ENERGY_CELL.block());
                step[0] = 2;
            }
            if (step[0] == 2) {
                helper.assertValueEqual(scene.consumerIron(), 9L, "Waiting for the powered tunnels to link again");
                frequency[0] = pair.left.getFrequency();
                BlockEntityReload.reload(helper, LEFT_BUS.below(), RIGHT_BUS);
                step[0] = 3;
            }
            var left = scene.tunnel(LEFT_BUS, Direction.WEST);
            var right = scene.tunnel(RIGHT_BUS, Direction.EAST);
            helper.assertTrue(left != pair.left, "The bus must have been loaded anew");
            helper.assertTrue(!left.isOutput() && right.isOutput() && left.getFrequency() == frequency[0]
                    && right.getFrequency() == frequency[0], "A loaded tunnel keeps its frequency and role");
            helper.assertValueEqual(scene.consumerIron(), 9L, "Waiting for the loaded tunnels to link again");
            PolicyEvidence.write("p2plinkfollowspowerandreload", 8, Map.of("unpowered", "0", "repowered", "9",
                    "reloaded", "9"));
            scene.close();
            helper.succeed();
        });
    }

    /**
     * Over a real Quantum Bridge, tunnels join an overworld consumer and a nether provider: the consumer sees the
     * provider's storage, runs on its power and gets its pattern. When the nether side unloads its nodes leave at once,
     * and when it loads again the link returns.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 600, required = true, manualOnly = true)
    public static void p2pAcrossDimensionsSharesNetworks(GameTestHelper helper) {
        var scene = new QuantumP2PRouterScene(helper);
        var reloaded = new boolean[1];
        var staleAfterReload = new boolean[1];
        helper.succeedWhen(() -> {
            scene.advance();
            helper.assertTrue(scene.domainReachesNether(), "Waiting for the tunnels to reach the nether");
            helper.assertValueEqual(scene.consumerIron(), 9L, "Waiting for the consumer to see the nether iron");
            helper.assertTrue(scene.consumerPowered(), "Waiting for the consumer to run on the provider's power");
            helper.assertTrue(scene.consumerCraftsStone(), "Waiting for the nether pattern on the consumer");
            if (!reloaded[0]) {
                // The nether side unloads and loads again in one tick, as a chunk does.
                scene.site().reloadBlockEntities();
                staleAfterReload[0] = scene.domainReachesNether();
                reloaded[0] = true;
                helper.fail("Reloaded the nether side");
            }
            helper.assertFalse(staleAfterReload[0], "The unloaded nether nodes must have left the domain at once");
            PolicyEvidence.write("p2pacrossdimensionssharesnetworks", 9, Map.of("consumerIron", "9",
                    "consumerPowered", "true", "projectedPattern", "stone", "reloaded", "true"));
            scene.close();
            helper.succeed();
        });
    }

    /**
     * Over a real Quantum Bridge, a Provider in the overworld maps, claims and drives an Endpoint in the nether: the
     * products come back, and FE sent into the Provider's front reaches the machine beside the Endpoint.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 800, required = true, manualOnly = true)
    public static void p2pAcrossDimensionsDrivesEndpoint(GameTestHelper helper) {
        var scene = new QuantumP2PProviderScene(helper);
        var phase = new int[1];
        helper.succeedWhen(() -> {
            scene.runMachine();
            scene.advance();
            if (phase[0] == 0) {
                helper.assertValueEqual(scene.provider().laneCount(), 1, "Waiting for the nether Endpoint's Lane");
                helper.assertTrue(scene.endpoint().claimState() instanceof ClaimState.Owned owned
                        && owned.ownerIdentity().provider().equals(scene.provider().providerIdentity()),
                        "Waiting for the Provider to claim the nether Endpoint");
                scene.beginCraft(2);
                phase[0] = 1;
            }
            if (phase[0] == 1) {
                helper.assertTrue(scene.submitWhenPlanned(), "Waiting for the crafting plan");
                phase[0] = 2;
            }
            helper.assertValueEqual(scene.sourceAmount(QuantumP2PProviderScene.OUTPUT), 2L,
                    "Waiting for both products to come back from the nether");
            helper.assertFalse(scene.cpuBusy(), "Waiting for the crafting CPU to finish");
            helper.assertValueEqual(scene.returned(), 2L, "The nether machine made exactly two products");
            var relay = scene.relay();
            helper.assertTrue(relay != null, "The cable in front of the Provider must offer its FE relay");
            var before = scene.machinePower();
            helper.assertValueEqual(relay.receiveEnergy(1000, false), 1000,
                    "The relay must pass the FE to the machine beside the nether Endpoint");
            helper.assertValueEqual(scene.machinePower() - before, 500.0, "The nether machine must hold the 1000 FE");
            PolicyEvidence.write("p2pacrossdimensionsdrivesendpoint", 8, Map.of("returned", "2",
                    "relayedFe", "1000"));
            scene.close();
            helper.succeed();
        });
    }

    /**
     * The guide's "An Outpost in the Nether", built in a player's order: a carrier powered by the base through a quartz
     * fiber takes the tunnels over a Quantum Bridge whose nether half is built later, and the outpost there, with no
     * power of its own, uses the base's storage and runs on its power. Taking the singularity out of the base's link
     * chamber cuts the outpost off; put back, it shares again, and the base's identity stays settled throughout.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 800, required = true, manualOnly = true)
    public static void p2pNetherOutpost(GameTestHelper helper) {
        var scene = new QuantumP2POutpostScene(helper);
        var phase = new int[1];
        var singularity = new ItemStack[1];
        helper.succeedWhen(() -> {
            scene.advance();
            if (phase[0] == 0) {
                helper.assertValueEqual(scene.outpostIron(), 9L, "Waiting for the outpost to see the base's iron");
                helper.assertTrue(scene.outpostPowered(), "Waiting for the outpost to run on the base's power");
                helper.assertValueEqual(scene.baseIdentity(), IdentityStatus.SETTLED,
                        "The Quantum link must leave the base's identity settled");
                singularity[0] = scene.breakLink();
                phase[0] = 1;
            }
            if (phase[0] == 1) {
                helper.assertValueEqual(scene.outpostIron(), 0L, "Waiting for the outpost to lose the base's iron");
                helper.assertFalse(scene.outpostPowered(), "Waiting for the outpost to lose the base's power");
                scene.restoreLink(singularity[0]);
                phase[0] = 2;
            }
            helper.assertValueEqual(scene.outpostIron(), 9L, "Waiting for the outpost to see the base's iron again");
            helper.assertTrue(scene.outpostPowered(), "Waiting for the outpost to run on the base's power again");
            helper.assertValueEqual(scene.baseIdentity(), IdentityStatus.SETTLED,
                    "The base's identity must be settled again");
            PolicyEvidence.write("p2pnetheroutpost", 8, Map.of("outpostIron", "9", "outpostPowered", "true",
                    "linkBroken", "0", "restored", "9"));
            scene.close();
            helper.succeed();
        });
    }

    /**
     * The nether side's chunks really unload when nothing keeps them loaded, which takes the link with them, and the
     * link returns when they load again. Not required: how long the server takes to unload chunks is up to it.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1600, required = false, manualOnly = true)
    public static void p2pAcrossDimensionsChunkUnload(GameTestHelper helper) {
        var scene = new QuantumP2PRouterScene(helper);
        var phase = new int[1];
        helper.succeedWhen(() -> {
            scene.advance();
            if (phase[0] == 0) {
                helper.assertValueEqual(scene.consumerIron(), 9L, "Waiting for the consumer to see the nether iron");
                scene.site().releaseTickets();
                phase[0] = 1;
            }
            if (phase[0] == 1) {
                helper.assertFalse(scene.site().loaded(), "Waiting for the nether chunks to unload");
                helper.assertFalse(scene.domainReachesNether(), "The unloaded nether nodes must have left the domain");
                helper.assertValueEqual(scene.consumerIron(), 0L, "Waiting for the consumer to lose the nether iron");
                scene.site().forceAgain();
                phase[0] = 2;
            }
            helper.assertTrue(scene.site().ticking(), "Waiting for the nether chunks to tick again");
            helper.assertTrue(scene.domainReachesNether(), "Waiting for the tunnels to reach the nether again");
            helper.assertValueEqual(scene.consumerIron(), 9L, "Waiting for the consumer to see the nether iron again");
            scene.close();
            helper.succeed();
        });
    }

    private static boolean arm(GameTestHelper helper, BlockPos cable, Direction toward) {
        var bit = List.of(CableVisualConnections.DIRECTIONS).indexOf(toward);
        return (CableVisualConnections.mask(helper.getLevel(), helper.absolutePos(cable)) & 1 << bit) != 0;
    }

    /** The scene's input on the left bus and output on the right, placed, paired and ruled one step per tick. */
    private static final class Pair {
        private final GameTestHelper helper;
        private final FederationP2PScene scene;
        private FederationP2PTunnelPart left;
        private FederationP2PTunnelPart right;
        private int step;

        private Pair(GameTestHelper helper, FederationP2PScene scene) {
            this.helper = helper;
            this.scene = scene;
        }

        /** Fails until the two Routers share a domain through the pair and the storage rule is set. */
        private void advance() {
            if (step == 0) {
                helper.assertTrue(scene.networks().networksSettled(), "Waiting for the networks to settle");
                scene.placeCable(LEFT_BUS);
                scene.placeCable(RIGHT_BUS);
                left = scene.attunedTunnel(LEFT_BUS, Direction.WEST);
                right = scene.attunedTunnel(RIGHT_BUS, Direction.EAST);
                step = 1;
            }
            if (step == 1) {
                helper.assertTrue(FederationP2PScene.online(left, right), "Waiting for the tunnels' channels");
                scene.pair(left, List.of(right));
                scene.placeRouters();
                step = 2;
            }
            if (step == 2) {
                helper.assertTrue(scene.networks().connected(), "Waiting for the tunnels to join the two Routers");
                scene.enableStorage();
                step = 3;
            }
        }
    }
}
