package space.controlnet.ae2federation.test;

import java.util.Map;
import java.util.TreeMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.test.energy.AdjacentRouterEnergyFixture;
import space.controlnet.ae2federation.test.energy.LargeSharedEnergyProof;
import space.controlnet.ae2federation.test.energy.RingEnergyFixture;
import space.controlnet.ae2federation.test.energy.SharedEnergyEvidence;
import space.controlnet.ae2federation.test.energy.SharedEnergyFixture;

/**
 * Federation energy sharing: an enabled ME power rule between two networks, in either direction, puts their Grids in
 * one AE2 energy pool, the way a Quartz Fiber does, and turning the rule off or cutting the topology splits it at once.
 */
@PrefixGameTestTemplate(false)
public final class SharedEnergyGameTests {
    private SharedEnergyGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void energySharedMutual(GameTestHelper helper) {
        runReady(helper, fixture -> {
            fixture.charge(1_500_000_500.0);
            fixture.enable(fixture.key());
            var consumerChannels = fixture.consumerChannels();
            var providerChannels = fixture.providerChannels();
            var before = fixture.providerStored();
            var simulated = fixture.simulate(250);
            var afterSimulation = fixture.providerStored();
            var accepted = fixture.extract(250);
            var after = fixture.providerStored();
            var consumerAvailable = fixture.consumerAvailable();
            var providerAvailable = fixture.providerAvailable();
            helper.assertValueEqual(simulated, 250.0, "A shared pool must expose the provider's energy");
            helper.assertValueEqual(afterSimulation, before, "Simulation must not mutate provider energy");
            helper.assertValueEqual(accepted, 250.0, "Consumer native demand must accept exact energy");
            helper.assertValueEqual(before - after, accepted, "Provider debit must equal consumer acceptance");
            helper.assertValueEqual(consumerAvailable, providerAvailable,
                    "Both Grids of a shared pool must see the same energy");
            helper.assertTrue(fixture.shares(fixture.key()), "The rule's pair must share energy");
            helper.assertTrue(fixture.shares(fixture.reverseKey()), "Sharing must be mutual");
            helper.assertTrue(!fixture.configured(fixture.reverseKey()), "Mutual sharing must need only one rule");
            helper.assertTrue(fixture.consumerGrid() != fixture.providerGrid(), "Native Grids must remain distinct");
            helper.assertValueEqual(fixture.consumerChannels(), consumerChannels,
                    "Consumer native channel count must remain unchanged");
            helper.assertValueEqual(fixture.providerChannels(), providerChannels,
                    "Provider native channel count must remain unchanged");
            var evidence = new TreeMap<String, String>();
            evidence.putAll(Map.ofEntries(
                    Map.entry("providerBefore", number(before)), Map.entry("providerAfter", number(after)),
                    Map.entry("providerAfterSimulation", number(afterSimulation)),
                    Map.entry("simulated", number(simulated)), Map.entry("accepted", number(accepted)),
                    Map.entry("providerDebit", number(before - after)),
                    Map.entry("consumerAvailable", number(consumerAvailable)),
                    Map.entry("providerAvailable", number(providerAvailable)),
                    Map.entry("sharesForward", "true"), Map.entry("sharesReverse", "true"),
                    Map.entry("reverseConfigured", "false"), Map.entry("distinctGrids", "true"),
                    Map.entry("consumerChannels", Integer.toString(consumerChannels)),
                    Map.entry("providerChannels", Integer.toString(providerChannels))));
            evidence.putAll(LargeSharedEnergyProof.capture(helper, fixture));
            SharedEnergyEvidence.write("energysharedmutual", 18, evidence);
        });
    }

    /** A Grid without any energy of its own powers up from a rule written the other way round. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 500, required = true, manualOnly = true)
    public static void energyColdStart(GameTestHelper helper) {
        var fixture = new SharedEnergyFixture(helper);
        var phase = new int[1];
        var providerBefore = new double[1];
        var consumerStoredBefore = new double[1];
        var consumerAvailableBefore = new double[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for settled cold energy topology");
            if (phase[0] == 0) {
                helper.assertTrue(!fixture.consumerPowered(), "Consumer must begin natively unpowered");
                consumerStoredBefore[0] = fixture.consumerStored();
                consumerAvailableBefore[0] = fixture.consumerAvailable();
                helper.assertValueEqual(consumerStoredBefore[0], 0.0,
                        "Cold consumer must have zero native stored reserve");
                helper.assertValueEqual(consumerAvailableBefore[0], 0.0,
                        "Cold consumer service must expose zero native availability");
                fixture.charge(1_000);
                providerBefore[0] = fixture.providerStored();
                fixture.enable(fixture.reverseKey());
                helper.assertTrue(fixture.shares(fixture.key()), "A rule in the other direction must share too");
                helper.assertTrue(!fixture.configured(fixture.key()), "The cold Grid must hold no rule of its own");
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for native powered-state stabilization");
            }
            helper.assertTrue(fixture.consumerPowered(), "Consumer Grid must recover native powered state");
            var providerAfter = fixture.providerStored();
            helper.assertTrue(providerAfter < providerBefore[0], "Cold recovery must consume real provider energy");
            SharedEnergyEvidence.write("energycoldstart", 8, Map.of(
                    "initiallyPowered", "false", "ruleDirection", "reverse", "poweredAfter", "true",
                    "providerBefore", number(providerBefore[0]), "providerAfter", number(providerAfter),
                    "providerDebit", number(providerBefore[0] - providerAfter),
                    "consumerStoredBefore", number(consumerStoredBefore[0]),
                    "consumerAvailableBefore", number(consumerAvailableBefore[0]),
                    "distinctGrids", "true"));
            fixture.close();
        });
    }

    /** Rules first-second and second-third pool all three Grids, so the first draws on the third's energy. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 450, required = true, manualOnly = true)
    public static void energySharedTransitive(GameTestHelper helper) {
        var fixture = new RingEnergyFixture(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for settled three-Grid energy topology");
            fixture.chargeThird(300);
            fixture.enableChain();
            var before = fixture.totalStored();
            var accepted = fixture.extractFirst(400);
            var after = fixture.totalStored();
            helper.assertValueEqual(fixture.grids().stream().distinct().count(), 3L,
                    "The pool must keep three independent native Grids");
            helper.assertValueEqual(fixture.sharedPairCount(), 2, "Exactly the two ruled pairs must share");
            helper.assertTrue(!fixture.shares(fixture.keys().get(2)), "First and third must hold no rule");
            helper.assertValueEqual(accepted, 300.0, "Demand must stop at the pool's real energy");
            helper.assertValueEqual(before - after, accepted, "The pool's debit must equal the acceptance");
            SharedEnergyEvidence.write("energysharedtransitive", 7, Map.of(
                    "distinctGridCount", "3", "ruledPairs", "2", "firstThirdRule", "false",
                    "totalBefore", number(before), "totalAfter", number(after), "requested", "400.0",
                    "accepted", number(accepted), "totalDebit", number(before - after), "createdEnergy", "0"));
            fixture.close();
        });
    }

    /** Turning the rule off splits the pool before the next energy operation; turning it on joins it again. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void energyRuleOffSplits(GameTestHelper helper) {
        runReady(helper, fixture -> {
            fixture.charge(1_000);
            fixture.enable(fixture.key());
            var initial = fixture.extract(200);
            var sharedWhileEnabled = fixture.shares(fixture.key());
            fixture.disable(fixture.key());
            var providerBefore = fixture.providerStored();
            var afterOff = fixture.extract(100);
            var providerAfter = fixture.providerStored();
            var availableAfterOff = fixture.consumerAvailable();
            var sharedAfterOff = fixture.shares(fixture.key());
            fixture.enable(fixture.key());
            var reenabled = fixture.extract(100);
            helper.assertValueEqual(initial, 200.0, "The enabled rule must share native energy");
            helper.assertTrue(sharedWhileEnabled, "The enabled rule's pair must share energy");
            helper.assertValueEqual(afterOff, 0.0, "A disabled rule must leave nothing to draw at once");
            helper.assertValueEqual(providerAfter, providerBefore, "A disabled rule must not debit the provider");
            helper.assertValueEqual(availableAfterOff, 0.0, "The split consumer must see no provider energy");
            helper.assertTrue(!sharedAfterOff, "A disabled rule's pair must not share energy");
            helper.assertValueEqual(reenabled, 100.0, "Enabling the rule again must share again");
            SharedEnergyEvidence.write("energyruleoffsplits", 10, Map.of(
                    "initialTransfer", number(initial), "sharedWhileEnabled", "true",
                    "extractedAfterOff", number(afterOff), "providerMutationAfterOff",
                    number(providerBefore - providerAfter), "availableAfterOff", number(availableAfterOff),
                    "sharedAfterOff", "false", "reenabledExtracted", number(reenabled)));
        });
    }

    /** Removing the only bridge between the two networks splits their pool at once. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 500, required = true, manualOnly = true)
    public static void energyDisconnectSplits(GameTestHelper helper) {
        var fixture = new SharedEnergyFixture(helper);
        var phase = new int[1];
        var facts = new TreeMap<String, String>();
        helper.succeedWhen(() -> {
            if (phase[0] == 0) {
                helper.assertTrue(fixture.ready(), "Waiting for settled disconnect topology");
                fixture.charge(1_000);
                fixture.enable(fixture.key());
                var initial = fixture.extract(200);
                helper.assertValueEqual(initial, 200.0, "The connected pair must share native energy");
                helper.assertTrue(fixture.shares(fixture.key()), "The connected pair must share energy");
                fixture.disconnect();
                var providerBefore = fixture.providerStored();
                var afterDisconnect = fixture.extract(100);
                var providerAfter = fixture.providerStored();
                var availableAfterDisconnect = fixture.consumerAvailable();
                helper.assertValueEqual(afterDisconnect, 0.0, "A removed bridge must leave nothing to draw at once");
                helper.assertValueEqual(providerAfter, providerBefore, "A removed bridge must not debit the provider");
                helper.assertValueEqual(availableAfterDisconnect, 0.0,
                        "The disconnected consumer must see no provider energy");
                facts.putAll(Map.of("initialTransfer", number(initial), "sharedWhileConnected", "true",
                        "extractedAfterDisconnect", number(afterDisconnect),
                        "providerMutationAfterDisconnect", number(providerBefore - providerAfter),
                        "availableAfterDisconnect", number(availableAfterDisconnect), "distinctGrids", "true"));
                phase[0] = 1;
            }
            helper.assertTrue(!fixture.shares(fixture.key()), "The disconnected pair must stop sharing");
            facts.put("sharedAfterDisconnect", "false");
            SharedEnergyEvidence.write("energydisconnectsplits", 7, facts);
            fixture.close();
        });
    }

    /**
     * Sharing is one switch per pair: switching it off from either direction turns off every ME power rule of the pair,
     * including one an older world holds the other way round; switching it on enables the one rule it names.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void energySwitchPairLevel(GameTestHelper helper) {
        runReady(helper, fixture -> {
            fixture.charge(1_000);
            fixture.enable(fixture.key());
            fixture.enable(fixture.reverseKey());
            var session = fixture.session();
            helper.assertTrue(fixture.switchRule(session, fixture.reverseKey(), false), "The switch must be handled");
            helper.assertTrue(!fixture.enabled(fixture.key()) && !fixture.enabled(fixture.reverseKey()),
                    "Switching sharing off must turn off both directions");
            helper.assertTrue(!fixture.shares(fixture.key()), "The pair must stop sharing");
            helper.assertValueEqual(fixture.extract(100), 0.0, "The split consumer must draw nothing");
            helper.assertTrue(fixture.switchRule(session, fixture.key(), true), "The switch must be handled");
            helper.assertTrue(fixture.enabled(fixture.key()) && !fixture.enabled(fixture.reverseKey()),
                    "Switching sharing on must enable only the named rule");
            helper.assertValueEqual(fixture.extract(100), 100.0, "The pair must share again");
            helper.succeed();
        });
    }

    /**
     * Two Routers placed face to face carry shared ME power with no Federation Cable between them: the consumer draws on
     * the provider's cell, and removing one Router splits the pool at once.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void energyAdjacentRouters(GameTestHelper helper) {
        var fixture = new AdjacentRouterEnergyFixture(helper);
        var phase = new int[1];
        var facts = new TreeMap<String, String>();
        helper.succeedWhen(() -> {
            if (phase[0] == 0) {
                helper.assertTrue(fixture.ready(), "Waiting for the adjacent Routers to link");
                fixture.chargeProvider(1_000);
                fixture.enable();
                var before = fixture.providerStored();
                var accepted = fixture.extractFromConsumer(250);
                var after = fixture.providerStored();
                helper.assertTrue(fixture.routersLinked(), "The touching Router faces must link with no native edge");
                helper.assertTrue(fixture.shares(), "The ME power rule must share across the adjacent Routers");
                helper.assertValueEqual(accepted, 250.0, "The consumer must draw on the provider's cell");
                helper.assertValueEqual(before - after, accepted, "The provider's debit must equal the draw");
                helper.assertTrue(fixture.consumerGrid() != fixture.providerGrid(), "The native Grids must stay distinct");
                facts.put("routersLinked", "true");
                facts.put("sharedWhileLinked", "true");
                facts.put("initialTransfer", number(accepted));
                facts.put("providerDebit", number(before - after));
                facts.put("distinctGrids", "true");
                fixture.removeProviderRouter();
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for the pool to split");
            }
            helper.assertTrue(!fixture.shares(), "Removing a Router must stop the sharing");
            // Measured around the draw itself: the provider's own devices keep using their idle power.
            var providerBefore = fixture.providerStored();
            var extracted = fixture.extractFromConsumer(100);
            helper.assertValueEqual(extracted, 0.0, "The split consumer must draw nothing");
            helper.assertValueEqual(fixture.providerStored(), providerBefore,
                    "The split consumer's draw must not reach the provider's cell");
            facts.put("sharedAfterRemoval", "false");
            facts.put("extractedAfterRemoval", number(extracted));
            SharedEnergyEvidence.write("energyadjacentrouters", 8, facts);
            fixture.close();
        });
    }

    private static void runReady(GameTestHelper helper, java.util.function.Consumer<SharedEnergyFixture> assertions) {
        var fixture = new SharedEnergyFixture(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for shared energy fixture");
            assertions.accept(fixture);
            fixture.close();
        });
    }

    private static String number(double value) {
        return Double.toString(value);
    }
}
