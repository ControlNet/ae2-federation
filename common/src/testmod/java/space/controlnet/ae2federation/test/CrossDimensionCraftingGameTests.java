package space.controlnet.ae2federation.test;

import static space.controlnet.ae2federation.test.crafting.NetherProviderProjectionScene.INPUT;
import static space.controlnet.ae2federation.test.crafting.NetherProviderProjectionScene.OUTPUT;
import static space.controlnet.ae2federation.test.crafting.PatternProjectionFixture.cobblestone;
import static space.controlnet.ae2federation.test.crafting.PatternProjectionFixture.stone;

import java.util.Map;
import net.minecraft.world.item.ItemStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.world.item.Items;
import space.controlnet.ae2federation.crafting.projection.CraftingProjectionService;
import space.controlnet.ae2federation.domain.FederationBindingRefresh;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.energy.EnergySharingService;
import space.controlnet.ae2federation.observability.LevelObservabilityService;
import space.controlnet.ae2federation.storage.mount.StorageLevelLifecycle;
import space.controlnet.ae2federation.test.crafting.PatternProjectionFixture;
import space.controlnet.ae2federation.test.storage.OtherDimensionRouterPair;
import space.controlnet.ae2federation.test.world.OtherDimensionSite;
import space.controlnet.ae2federation.processing.claim.ClaimState;
import space.controlnet.ae2federation.test.crafting.NetherProviderProjectionScene;
import space.controlnet.ae2federation.test.p2p.QuantumP2PProviderScene;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;

/**
 * Crafting across dimensions with Federation's own blocks: a Federation Provider and its Endpoint in another dimension
 * than the networks that use them, joined by real Quantum Network Bridges.
 */
@PrefixGameTestTemplate(false)
public final class CrossDimensionCraftingGameTests {
    private CrossDimensionCraftingGameTests() {
    }

    /**
     * The provider network reaches the nether over a Quantum Bridge, where its Federation Provider drives an Endpoint.
     * The overworld consumer gets the Provider's lane projected once, orders two diamonds from its own terminal's
     * crafting service, and its CPU gets both back through the projection; nothing stays owed or on the provider
     * network.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 1000)
    public static void projectionNetherFederationProvider(GameTestHelper helper) {
        var scene = new NetherProviderProjectionScene(helper);
        var stage = new int[1];
        helper.succeedWhen(() -> {
            scene.runMachine();
            if (stage[0] == 0) {
                helper.assertTrue(scene.ready(), "Waiting for the nether Provider's lane to its Endpoint");
                helper.assertFalse(scene.consumerCrafts(), "Without a crafting rule nothing is projected");
                scene.enableRules();
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                helper.assertTrue(scene.consumerCrafts(), "Waiting for the nether Provider's pattern on the consumer");
                helper.assertValueEqual(scene.projections(), 1, "The nether Provider's lane is projected once");
                helper.assertValueEqual(scene.consumerProviders(), 1, "The consumer lists one provider for it");
                scene.putInConsumer(INPUT, 2);
                scene.begin(2);
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                helper.assertTrue(scene.submitWhenPlanned(), "Waiting for the consumer's plan");
                stage[0] = 3;
            }
            helper.assertValueEqual(scene.returned(), 2L, "Waiting for the nether machine to make both diamonds");
            helper.assertFalse(scene.consumerBusy(), "Waiting for the consumer's job to finish");
            helper.assertValueEqual(scene.inConsumerChest(OUTPUT), 2L, "The consumer stores its two diamonds");
            helper.assertValueEqual(scene.onProviderNetwork(OUTPUT), 0L, "The provider network keeps none");
            helper.assertValueEqual(scene.owed(), 0L, "Nothing is owed afterwards");
            helper.assertValueEqual(scene.inConsumerChest(INPUT), 0L, "The cobblestone was used");
            helper.assertTrue(scene.endpoint().claimState() instanceof ClaimState.Owned owned
                    && owned.ownerIdentity().provider().equals(scene.provider().providerIdentity()),
                    "The nether Provider owns its Endpoint");
            PolicyEvidence.write("projectionnetherfederationprovider", 11, Map.of("projectedLanes", "1",
                    "consumerProviders", "1", "returned", "2", "owedAfter", "0"));
            scene.close();
        });
    }

    /**
     * An overworld Provider drives a nether Endpoint over P2P tunnels across a real Quantum Bridge, while an overworld
     * twin Endpoint sits at the nether Endpoint's very coordinates. The Provider claims and drives only the nether one;
     * diamonds offered to the twin never reach the Provider's network. After a simulated restart of both sides (every
     * block entity saved and loaded again in one tick; the server keeps running), the nether Endpoint keeps its
     * identity, Claim epoch and owner, the Provider its one lane to it, and a second order returns through it.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 1600)
    public static void endpointAcrossDimensionsKeepsIdentity(GameTestHelper helper) {
        var scene = new QuantumP2PProviderScene(helper);
        var stage = new int[1];
        var claim = new ClaimState[1];
        var twinTook = new long[1];
        helper.succeedWhen(() -> {
            scene.runMachine();
            scene.advance();
            if (stage[0] == 0) {
                helper.assertValueEqual(scene.provider().laneCount(), 1, "Waiting for the nether Endpoint's lane");
                helper.assertTrue(owned(scene), "Waiting for the Provider to claim the nether Endpoint");
                scene.placeTwin();
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                helper.assertTrue(scene.twin() != null, "Waiting for the overworld twin");
                helper.assertFalse(scene.twin().claimState() instanceof ClaimState.Owned,
                        "The twin at the same coordinates is not claimed");
                helper.assertFalse(scene.twin().claimState().key().equals(scene.endpoint().claimState().key()),
                        "The twin is another Endpoint");
                claim[0] = scene.endpoint().claimState();
                twinTook[0] = scene.offerTwin(1);
                scene.beginCraft(2);
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                helper.assertTrue(scene.submitWhenPlanned(), "Waiting for the crafting plan");
                stage[0] = 3;
            }
            if (stage[0] == 3) {
                helper.assertValueEqual(scene.sourceAmount(QuantumP2PProviderScene.OUTPUT), 2L,
                        "Waiting for both diamonds to come back from the nether");
                helper.assertFalse(scene.cpuBusy(), "Waiting for the crafting CPU to finish");
                helper.assertValueEqual(scene.returned(), 2L, "The nether machine made exactly two diamonds");
                scene.reloadBoth();
                stage[0] = 4;
            }
            if (stage[0] == 4) {
                helper.assertTrue(scene.sourceReady(), "Waiting for the reloaded source network");
                helper.assertValueEqual(scene.provider().laneCount(), 1, "Waiting for the reloaded Provider's lane");
                helper.assertTrue(owned(scene), "Waiting for the reloaded Provider to own the nether Endpoint again");
                helper.assertTrue(scene.endpoint().claimState().equals(claim[0]),
                        "The nether Endpoint keeps its identity, Claim epoch and owner: " + claim[0] + " -> "
                                + scene.endpoint().claimState());
                helper.assertFalse(scene.twin().claimState() instanceof ClaimState.Owned, "The twin stays unclaimed");
                scene.beginCraft(2);
                stage[0] = 5;
            }
            if (stage[0] == 5) {
                helper.assertTrue(scene.submitWhenPlanned(), "Waiting for the second crafting plan");
                stage[0] = 6;
            }
            helper.assertValueEqual(scene.sourceAmount(QuantumP2PProviderScene.OUTPUT), 4L,
                    "Waiting for the second order's diamonds");
            helper.assertFalse(scene.cpuBusy(), "Waiting for the crafting CPU to finish again");
            helper.assertValueEqual(scene.returned(), 4L, "The nether machine made exactly four diamonds");
            PolicyEvidence.write("endpointacrossdimensionskeepsidentity", 13, Map.of("twinClaimed", "false",
                    "twinTook", Long.toString(twinTook[0]), "returnedBeforeRestart", "2", "returnedAfterRestart", "2",
                    "claimKept", "true"));
            scene.close();
        });
    }

    /**
     * The Quantum link under an overworld Provider and its nether Endpoint breaks mid-processing, with both inputs at
     * the Endpoint. The Endpoint keeps its Claim and its return path to the Lane that owns it (the retained binding), so
     * the diamonds the machine makes while the link is down return to that Provider, once, and the job finishes; FE
     * through the Provider's relay stops. A job submitted while the link is still down sends no input over it; when the
     * link returns, it runs, and FE flows again. Every diamond comes back once.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 2000)
    public static void endpointAcrossDimensionsLinkBreaks(GameTestHelper helper) {
        var scene = new QuantumP2PProviderScene(helper);
        var stage = new int[1];
        var since = new long[1];
        var singularity = new ItemStack[1];
        var relayedWhileDown = new long[1];
        helper.succeedWhen(() -> {
            long now = helper.getLevel().getGameTime();
            scene.runMachine();
            scene.advance();
            if (stage[0] == 0) {
                helper.assertValueEqual(scene.provider().laneCount(), 1, "Waiting for the nether Endpoint's lane");
                helper.assertTrue(owned(scene), "Waiting for the Provider to claim the nether Endpoint");
                scene.machineRuns(false);
                scene.beginCraft(2);
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                helper.assertTrue(scene.submitWhenPlanned(), "Waiting for the crafting plan");
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                helper.assertValueEqual(scene.subnetAmount(QuantumP2PProviderScene.INPUT), 2L,
                        "Waiting for both inputs to reach the nether Endpoint's subnet");
                singularity[0] = scene.carrier().breakLink();
                stage[0] = 3;
            }
            if (stage[0] == 3) {
                helper.assertFalse(scene.endpointLinked(), "Waiting for the broken link to part the Endpoint");
                helper.assertTrue(owned(scene), "The Endpoint keeps its Claim while the link is down");
                relayedWhileDown[0] = relay(scene);
                scene.machineRuns(true);
                stage[0] = 4;
            }
            if (stage[0] == 4) {
                helper.assertValueEqual(scene.sourceAmount(QuantumP2PProviderScene.OUTPUT), 2L,
                        "Waiting for the diamonds made while the link is down to return to the owning Provider");
                helper.assertFalse(scene.cpuBusy(), "Waiting for the first job to finish");
                helper.assertValueEqual(scene.returned(), 2L, "The machine made exactly two diamonds");
                helper.assertFalse(scene.endpointLinked(), "The link is still down");
                helper.assertValueEqual(relayedWhileDown[0], 0L, "No FE crosses the broken link");
                scene.beginCraft(2);
                stage[0] = 5;
            }
            if (stage[0] == 5) {
                helper.assertTrue(scene.submitWhenPlanned(), "Waiting for the plan made while the link is down");
                since[0] = now;
                stage[0] = 6;
            }
            if (stage[0] == 6) {
                helper.assertTrue(now - since[0] >= 60, "Letting the job try while the link is down");
                helper.assertValueEqual(scene.subnetAmount(QuantumP2PProviderScene.INPUT) + scene.returned(), 2L,
                        "No input is sent over the broken link");
                helper.assertTrue(scene.cpuBusy(), "The job waits for the link");
                scene.carrier().restoreLink(singularity[0]);
                stage[0] = 7;
            }
            helper.assertValueEqual(scene.sourceAmount(QuantumP2PProviderScene.OUTPUT), 4L,
                    "Waiting for the second order's diamonds");
            helper.assertFalse(scene.cpuBusy(), "Waiting for the second job to finish");
            helper.assertValueEqual(scene.returned(), 4L, "Exactly four diamonds were made");
            helper.assertValueEqual(relay(scene), 1000L, "FE flows through the relay again");
            PolicyEvidence.write("endpointacrossdimensionslinkbreaks", 15, Map.of("returnedWhileDown", "2",
                    "inputsWhileDown", "0", "relayedWhileDown", "0", "returned", "4"));
            scene.close();
        });
    }

    /**
     * The nether Endpoint is replaced by a fresh one at the same place after a finished order. The fresh Endpoint is
     * another Endpoint: the Provider does not own it, sends a new job no input through it, and what a machine puts into
     * it never reaches the Provider's network.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 1600)
    public static void endpointAcrossDimensionsReplaced(GameTestHelper helper) {
        var scene = new QuantumP2PProviderScene(helper);
        var stage = new int[1];
        var since = new long[1];
        var oldKey = new Object[1];
        var took = new long[1];
        helper.succeedWhen(() -> {
            long now = helper.getLevel().getGameTime();
            scene.runMachine();
            scene.advance();
            if (stage[0] == 0) {
                helper.assertValueEqual(scene.provider().laneCount(), 1, "Waiting for the nether Endpoint's lane");
                helper.assertTrue(owned(scene), "Waiting for the Provider to claim the nether Endpoint");
                scene.beginCraft(1);
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                helper.assertTrue(scene.submitWhenPlanned(), "Waiting for the crafting plan");
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                helper.assertValueEqual(scene.sourceAmount(QuantumP2PProviderScene.OUTPUT), 1L,
                        "Waiting for the first diamond");
                helper.assertFalse(scene.cpuBusy(), "Waiting for the first job to finish");
                oldKey[0] = scene.endpoint().claimState().key();
                scene.machineRuns(false);
                scene.replaceEndpoint();
                stage[0] = 3;
            }
            if (stage[0] == 3) {
                helper.assertTrue(scene.endpoint() != null, "Waiting for the fresh Endpoint");
                helper.assertFalse(scene.endpoint().claimState().key().equals(oldKey[0]),
                        "The fresh Endpoint is another Endpoint");
                helper.assertFalse(owned(scene), "The Provider does not own the fresh Endpoint");
                took[0] = scene.offerEndpoint(1);
                scene.beginCraft(1);
                stage[0] = 4;
            }
            if (stage[0] == 4) {
                helper.assertTrue(scene.submitWhenPlanned(), "Waiting for the second plan");
                since[0] = now;
                stage[0] = 5;
            }
            helper.assertTrue(now - since[0] >= 60, "Letting the second job try");
            helper.assertValueEqual(scene.subnetAmount(QuantumP2PProviderScene.INPUT), 0L,
                    "No input is sent through the fresh Endpoint");
            helper.assertValueEqual(scene.sourceAmount(QuantumP2PProviderScene.OUTPUT), 1L,
                    "Nothing put into the fresh Endpoint reaches the Provider's network");
            helper.assertFalse(owned(scene), "The Provider still does not own the fresh Endpoint");
            scene.cancelJobs();
            PolicyEvidence.write("endpointacrossdimensionsreplaced", 8, Map.of("freshClaimed", "false",
                    "inputsToFresh", "0", "freshTook", Long.toString(took[0]), "leakedToProvider", "0"));
            scene.close();
        });
    }

    /**
     * The nether chunks of an overworld Provider's Endpoint really unload (the site's tickets are released) while its
     * inputs wait at the Endpoint, and nothing keeps them loaded for four seconds. The job keeps waiting and nothing comes
     * back meanwhile; when the chunks load again the machine runs and both diamonds return once.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 2000)
    public static void endpointAcrossDimensionsUnloaded(GameTestHelper helper) {
        var scene = new QuantumP2PProviderScene(helper);
        var stage = new int[1];
        var since = new long[1];
        helper.succeedWhen(() -> {
            long now = helper.getLevel().getGameTime();
            scene.runMachine();
            if (stage[0] < 3) scene.advance();
            if (stage[0] == 0) {
                helper.assertValueEqual(scene.provider().laneCount(), 1, "Waiting for the nether Endpoint's lane");
                helper.assertTrue(owned(scene), "Waiting for the Provider to claim the nether Endpoint");
                scene.machineRuns(false);
                scene.beginCraft(2);
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                helper.assertTrue(scene.submitWhenPlanned(), "Waiting for the crafting plan");
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                helper.assertValueEqual(scene.subnetAmount(QuantumP2PProviderScene.INPUT), 2L,
                        "Waiting for both inputs to reach the nether Endpoint's subnet");
                scene.site().releaseTickets();
                stage[0] = 3;
            }
            if (stage[0] == 3) {
                helper.assertFalse(scene.site().loaded(), "Waiting for the nether chunks to unload");
                since[0] = now;
                stage[0] = 4;
            }
            if (stage[0] == 4) {
                helper.assertTrue(now - since[0] >= 80, "Letting four seconds pass with the Endpoint unloaded");
                helper.assertFalse(scene.site().loaded(), "The nether chunks stay unloaded throughout");
                helper.assertTrue(scene.cpuBusy(), "The job keeps waiting for the unloaded Endpoint");
                helper.assertValueEqual(scene.sourceAmount(QuantumP2PProviderScene.OUTPUT), 0L, "Nothing came back");
                scene.site().forceAgain();
                scene.machineRuns(true);
                stage[0] = 5;
            }
            helper.assertTrue(scene.site().ticking(), "Waiting for the nether chunks to tick again");
            helper.assertValueEqual(scene.sourceAmount(QuantumP2PProviderScene.OUTPUT), 2L,
                    "Waiting for both diamonds after the reload");
            helper.assertFalse(scene.cpuBusy(), "Waiting for the job to finish");
            helper.assertValueEqual(scene.returned(), 2L, "The machine made exactly two diamonds");
            helper.assertTrue(owned(scene), "The Provider still owns the reloaded Endpoint");
            PolicyEvidence.write("endpointacrossdimensionsunloaded", 10, Map.of("unloadedTicks", "80",
                    "returnedWhileUnloaded", "0", "returned", "2"));
            scene.close();
        });
    }

    /**
     * The nether chunks holding the Federation Provider and its Endpoint really unload while the overworld consumer's
     * job waits for the diamonds: the consumer's CPU stays loaded, the provider network loses its nether half. The debt
     * stays through several ledger sweeps; when the chunks load again the machine runs and the consumer's job gets both
     * diamonds, once.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 2000)
    public static void projectionNetherProviderUnloaded(GameTestHelper helper) {
        var scene = new NetherProviderProjectionScene(helper);
        var stage = new int[1];
        var sweeps = new long[1];
        helper.succeedWhen(() -> {
            scene.runMachine();
            if (stage[0] == 0) {
                helper.assertTrue(scene.ready(), "Waiting for the nether Provider's lane to its Endpoint");
                scene.enableRules();
                scene.machineRuns(false);
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                helper.assertTrue(scene.consumerCrafts(), "Waiting for the nether Provider's pattern on the consumer");
                scene.putInConsumer(INPUT, 2);
                scene.begin(2);
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                helper.assertTrue(scene.submitWhenPlanned(), "Waiting for the consumer's plan");
                stage[0] = 3;
            }
            if (stage[0] == 3) {
                helper.assertValueEqual(scene.subnetAmount(INPUT), 2L, "Waiting for both inputs at the Endpoint");
                helper.assertValueEqual(scene.owed(), 2L, "The provider network owes the consumer two diamonds");
                scene.site().releaseTickets();
                stage[0] = 4;
            }
            if (stage[0] == 4) {
                helper.assertFalse(scene.site().loaded(), "Waiting for the nether chunks to unload");
                sweeps[0] = scene.ledgerSweeps();
                stage[0] = 5;
            }
            if (stage[0] == 5) {
                helper.assertTrue(scene.ledgerSweeps() - sweeps[0] >= 4, "Letting four ledger sweeps pass");
                helper.assertFalse(scene.site().loaded(), "The nether chunks stay unloaded throughout");
                helper.assertTrue(scene.consumerBusy(), "The consumer's job keeps waiting");
                helper.assertValueEqual(scene.owed(), 2L, "The debt stays while the provider's half is unloaded");
                scene.site().forceAgain();
                scene.machineRuns(true);
                stage[0] = 6;
            }
            helper.assertTrue(scene.site().ticking(), "Waiting for the nether chunks to tick again");
            helper.assertValueEqual(scene.returned(), 2L, "Waiting for the machine to make both diamonds");
            helper.assertFalse(scene.consumerBusy(), "Waiting for the consumer's job to finish");
            helper.assertValueEqual(scene.inConsumerChest(OUTPUT), 2L, "The consumer stores its two diamonds");
            helper.assertValueEqual(scene.onProviderNetwork(OUTPUT), 0L, "The provider network keeps none");
            helper.assertValueEqual(scene.owed(), 0L, "Nothing is owed afterwards");
            PolicyEvidence.write("projectionnetherproviderunloaded", 13, Map.of("sweepsUnloaded", "4",
                    "owedWhileUnloaded", "2", "returned", "2"));
            scene.close();
        });
    }

    /**
     * The crafting service is the server's, not a level's: with the overworld and a nether site (a Router pair in a
     * Federation Domain) loaded, its ledger is swept once per 20 server ticks, not once per level. When the nether's
     * level closes (the entrypoint's level-unload callbacks, run here directly while the server keeps running), the
     * nether's nodes leave the registry and the overworld's projection stays and serves a full order.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 1400)
    public static void levelCloseKeepsOverworldProjection(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper);
        var site = OtherDimensionSite.nether(helper, OtherDimensionRouterPair.SIZE);
        var nether = new OtherDimensionRouterPair(site);
        var stage = new int[1];
        var start = new long[2];
        helper.succeedWhen(() -> {
            long tick = helper.getLevel().getServer().getTickCount();
            if (stage[0] == 0) {
                helper.assertTrue(site.ready(), "Waiting for the nether site to tick: " + site.tickDiagnostics());
                nether.place();
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                helper.assertTrue(fixture.ready() && nether.networksSettled(), "Waiting for every network");
                nether.connectRouters();
                fixture.enableRules();
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                helper.assertTrue(nether.connected(), "Waiting for the nether's Domain");
                helper.assertTrue(fixture.consumerService().isCraftable(stone()), "Waiting for the projection");
                start[0] = tick;
                start[1] = fixture.ledgerSweeps();
                stage[0] = 3;
            }
            if (stage[0] == 3) {
                helper.assertTrue(tick - start[0] >= 200, "Counting ledger sweeps over 200 server ticks");
                long sweeps = fixture.ledgerSweeps() - start[1];
                helper.assertTrue(sweeps >= 9 && sweeps <= 11,
                        "The server's crafting service sweeps once per 20 server ticks, not per level: " + sweeps);
                var level = site.level();
                FederationBindingRefresh.closeLevel(level);
                var receipt = StorageLevelLifecycle.close(level);
                CraftingProjectionService.levelClosed(level);
                EnergySharingService.levelClosed(level);
                LevelObservabilityService.levelClosed(level);
                helper.assertTrue(receipt.dimensionNodesRemoved() > 0 && receipt.dimensionNodesLeft() == 0,
                        "The close takes every nether node out of the registry: " + receipt);
                helper.assertTrue(FederationDomainRegistryAccess.get(helper.getLevel())
                        .federationDomainOf(nether.routerNode()).isEmpty(), "No Domain holds a nether node");
                stage[0] = 4;
            }
            if (stage[0] == 4) {
                helper.assertTrue(fixture.projections() > 0, "The overworld projection stays");
                fixture.putInConsumer(cobblestone(), 2);
                fixture.begin(stone(), 2);
                stage[0] = 5;
            }
            if (stage[0] == 5) {
                helper.assertTrue(fixture.planReady(), "Waiting for the consumer's plan");
                helper.assertTrue(fixture.submit(), "The consumer's CPU must take the job");
                stage[0] = 6;
            }
            if (stage[0] == 6) {
                helper.assertValueEqual(fixture.inMachine(Items.COBBLESTONE), 2L, "Waiting for both pushes");
                helper.assertValueEqual(fixture.runMachine(2), 2L, "The provider network takes the stone");
                stage[0] = 7;
            }
            helper.assertValueEqual(fixture.busyConsumerCpus(), 0L, "Waiting for the job to finish");
            helper.assertValueEqual(fixture.held(fixture.consumerChest(), stone()), 2L, "The consumer stores its stone");
            helper.assertValueEqual(fixture.owed(stone()), 0L, "Nothing is owed afterwards");
            PolicyEvidence.write("levelclosekeepsoverworldprojection", 12, Map.of("sweepsPer200Ticks", "10",
                    "netherNodesLeft", "0", "overworldOrder", "2"));
            fixture.close();
            site.close();
        });
    }

    /** FE the Provider's relay passes on now, out of 1000 offered; nothing when there is no relay. */
    private static long relay(QuantumP2PProviderScene scene) {
        var relay = scene.relay();
        return relay == null ? 0 : relay.receiveEnergy(1000, false);
    }

    private static boolean owned(QuantumP2PProviderScene scene) {
        return scene.endpoint().claimState() instanceof ClaimState.Owned owned
                && owned.ownerIdentity().provider().equals(scene.provider().providerIdentity());
    }
}
