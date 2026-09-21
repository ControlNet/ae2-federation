package space.controlnet.ae2federation.test;

import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.test.energy.DirectionalEnergyEvidence;
import space.controlnet.ae2federation.test.energy.DirectionalEnergyFixture;
import space.controlnet.ae2federation.test.energy.EnergyAuthorityReceipt;
import space.controlnet.ae2federation.test.energy.LargeDirectionalEnergyProof;
import space.controlnet.ae2federation.test.energy.RingEnergyFixture;

@PrefixGameTestTemplate(false)
public final class DirectionalEnergyGameTests {
    private DirectionalEnergyGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void energyDirectionalPolicy(GameTestHelper helper) {
        runReady(helper, fixture -> {
            fixture.charge(1_500_000_500.0);
            fixture.enable();
            var binding = fixture.binding();
            var source = binding.consumerSource();
            var consumerChannels = fixture.consumerChannels();
            var providerChannels = fixture.providerChannels();
            EnergyAuthorityReceipt.captureCurrent("energydirectionalpolicy", "pre-operation", binding);
            var before = fixture.providerStored();
            var simulated = fixture.simulate(250);
            var afterSimulation = fixture.providerStored();
            var accepted = fixture.extract(250);
            var after = fixture.providerStored();
            EnergyAuthorityReceipt.captureOperation("energydirectionalpolicy", "extract", fixture.consumerGrid(),
                    fixture.providerGrid(), 250, accepted, before, after);
            helper.assertValueEqual(simulated, 250.0, "Authorized native query must expose provider energy");
            helper.assertValueEqual(source.extractAEPower(0.125, appeng.api.config.Actionable.SIMULATE,
                    appeng.api.config.PowerMultiplier.CONFIG), 0.125,
                    "CONFIG multiplier must preserve fractional caller units");
            helper.assertValueEqual(source.extractAEPower(0.0, appeng.api.config.Actionable.MODULATE,
                    appeng.api.config.PowerMultiplier.ONE), 0.0, "Zero extraction must remain zero");
            assertInvalidAmount(helper, source, -0.125);
            assertInvalidAmount(helper, source, Double.NaN);
            assertInvalidAmount(helper, source, Double.POSITIVE_INFINITY);
            helper.assertValueEqual(afterSimulation, before, "Simulation must not mutate provider energy");
            helper.assertValueEqual(accepted, 250.0, "Consumer native demand must accept exact energy");
            helper.assertValueEqual(before - after, accepted, "Provider debit must equal consumer acceptance");
            helper.assertTrue(binding.providerService() == fixture.providerGrid().getEnergyService(),
                    "Binding must retain exact provider native service");
            helper.assertTrue(!fixture.bindings().capability(fixture.reverseKey()).isPresent(),
                    "Reverse Policy capability must remain absent");
            helper.assertTrue(fixture.consumerGrid() != fixture.providerGrid(), "Native Grids must remain distinct");
            helper.assertValueEqual(fixture.consumerChannels(), consumerChannels,
                    "Consumer native channel count must remain unchanged");
            helper.assertValueEqual(fixture.providerChannels(), providerChannels,
                    "Provider native channel count must remain unchanged");
            var evidence = new java.util.TreeMap<String, String>();
            evidence.putAll(Map.ofEntries(
                    Map.entry("providerBefore", number(before)), Map.entry("providerAfter", number(after)),
                    Map.entry("providerAfterSimulation", number(afterSimulation)),
                    Map.entry("simulated", number(simulated)), Map.entry("accepted", number(accepted)),
                    Map.entry("providerDebit", number(before - after)), Map.entry("simulationMutation", "false"),
                    Map.entry("reverseCapability", "false"), Map.entry("distinctGrids", "true"),
                    Map.entry("consumerChannels", Integer.toString(consumerChannels)),
                    Map.entry("providerChannels", Integer.toString(providerChannels)),
                    Map.entry("federationOverhead", "0"), Map.entry("providerServiceExact", "true")));
            evidence.putAll(LargeDirectionalEnergyProof.capture(helper, fixture));
            DirectionalEnergyEvidence.write("energydirectionalpolicy", 21, evidence);
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 500, required = true, manualOnly = true)
    public static void energyColdStart(GameTestHelper helper) {
        var fixture = new DirectionalEnergyFixture(helper);
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
                fixture.enable();
                var binding = fixture.binding();
                helper.assertTrue(binding.isCurrent(), "Policy route must exist while consumer is cold");
                EnergyAuthorityReceipt.captureCurrent("energycoldstart", "cold-route", binding);
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for native powered-state stabilization");
            }
            helper.assertTrue(fixture.consumerPowered(), "Consumer Grid must recover native powered state");
            var providerAfter = fixture.providerStored();
            EnergyAuthorityReceipt.captureOperation("energycoldstart", "native-recovery", fixture.consumerGrid(),
                    fixture.providerGrid(), providerBefore[0] - providerAfter, providerBefore[0] - providerAfter,
                    providerBefore[0], providerAfter);
            helper.assertTrue(providerAfter < providerBefore[0], "Cold recovery must consume real provider energy");
            DirectionalEnergyEvidence.write("energycoldstart", 7, Map.of(
                    "initiallyPowered", "false", "routeEstablishedCold", "true", "poweredAfter", "true",
                    "providerBefore", number(providerBefore[0]), "providerAfter", number(providerAfter),
                    "providerDebit", number(providerBefore[0] - providerAfter),
                    "consumerStoredBefore", number(consumerStoredBefore[0]),
                    "consumerAvailableBefore", number(consumerAvailableBefore[0]),
                    "distinctGrids", "true"));
            fixture.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 450, required = true, manualOnly = true)
    public static void energyRingConservation(GameTestHelper helper) {
        var fixture = new RingEnergyFixture(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for settled three-Grid energy ring");
            fixture.chargeSecond(300);
            fixture.enableCycle();
            for (var index = 0; index < fixture.keys().size(); index++) {
                EnergyAuthorityReceipt.captureCurrent("energyringconservation", "edge-" + index,
                        fixture.bindings().capability(fixture.keys().get(index)).orElseThrow());
            }
            var before = fixture.totalStored();
            var accepted = fixture.extractFirst(400);
            var after = fixture.totalStored();
            EnergyAuthorityReceipt.captureOperation("energyringconservation", "cycle-extract",
                    fixture.grids().getFirst(), fixture.grids().get(1), 400, accepted, before, after);
            helper.assertValueEqual(fixture.grids().stream().distinct().count(), 3L,
                    "Ring must retain three independent native Grids");
            helper.assertValueEqual(fixture.bindings().relationshipCount(), 3,
                    "Ring must publish exactly three directed Policy bindings");
            helper.assertValueEqual(accepted, 300.0, "Cyclic demand must terminate at real available energy");
            helper.assertValueEqual(before - after, accepted, "Three-Grid ring debit must occur exactly once");
            DirectionalEnergyEvidence.write("energyringconservation", 4, Map.of(
                    "distinctGridCount", "3", "directedEdges", "3", "totalBefore", number(before),
                    "totalAfter", number(after), "requested", "400.0", "accepted", number(accepted),
                    "totalDebit", number(before - after), "cycleTerminated", "true", "createdEnergy", "0"));
            fixture.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void energyRejectReverse(GameTestHelper helper) {
        runReady(helper, fixture -> {
            fixture.charge(600);
            fixture.enable();
            EnergyAuthorityReceipt.captureCurrent("energyrejectreverse", "forward-only", fixture.binding());
            var before = fixture.providerStored();
            var reverse = fixture.reverseRouteExtract(200);
            var after = fixture.providerStored();
            EnergyAuthorityReceipt.captureOperation("energyrejectreverse", "reverse-denied", fixture.providerGrid(),
                    fixture.consumerGrid(), 200, reverse, before, after);
            helper.assertValueEqual(reverse, 0.0, "Unconfigured reverse route must reject extraction");
            helper.assertValueEqual(after, before, "Reverse rejection must not mutate provider energy");
            helper.assertTrue(fixture.bindings().capability(fixture.reverseKey()).isEmpty(),
                    "Reverse capability must not be inferred");
            helper.assertTrue(fixture.consumerGrid() != fixture.providerGrid(), "Reverse rejection must preserve Grids");
            DirectionalEnergyEvidence.write("energyrejectreverse", 4, Map.of(
                    "forwardConfigured", "true", "reverseConfigured", "false", "reverseExtracted", "0",
                    "providerBefore", number(before), "providerAfter", number(after), "reverseMutation", "0",
                    "distinctGrids", "true"));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 500, required = true, manualOnly = true)
    public static void energyDisconnectNoSource(GameTestHelper helper) {
        var fixture = new DirectionalEnergyFixture(helper);
        var phase = new int[1];
        var held = new space.controlnet.ae2federation.energy.EnergyCapabilityBinding[1];
        var providerAfterTransfer = new double[1];
        var generationBefore = new long[1];
        helper.succeedWhen(() -> {
            if (phase[0] == 0) {
                helper.assertTrue(fixture.ready(), "Waiting for settled disconnect topology");
                fixture.charge(1_000);
                fixture.enable();
                held[0] = fixture.binding();
                EnergyAuthorityReceipt.captureCurrent("energydisconnectnosource", "initial", held[0]);
                generationBefore[0] = held[0].revision().providerGeneration().value();
                helper.assertValueEqual(fixture.extract(200), 200.0, "Initial route must transfer native energy");
                providerAfterTransfer[0] = fixture.providerStored();
                EnergyAuthorityReceipt.captureOperation("energydisconnectnosource", "initial-extract",
                        fixture.consumerGrid(), fixture.providerGrid(), 200, 200, providerAfterTransfer[0] + 200,
                        providerAfterTransfer[0]);
                fixture.removeProviderSources();
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for native provider-source removal");
            }
            if (phase[0] == 1) {
                var staleExtract = fixture.extract(100);
                helper.assertValueEqual(staleExtract, 0.0, "Removed native provider source must yield zero extraction");
                helper.assertTrue(!held[0].isCurrent(), "Held binding must retire after source removal");
                helper.assertValueEqual(fixture.bindings().relationshipCount(), 0,
                        "Source removal must release stale binding ownership");
                EnergyAuthorityReceipt.captureUnavailable("energydisconnectnosource", "source-removed",
                        fixture.bindings(), fixture.key(), fixture.consumerGrid(), fixture.providerGrid());
                EnergyAuthorityReceipt.captureOperation("energydisconnectnosource", "stale-denied",
                        fixture.consumerGrid(), fixture.providerGrid(), 100, staleExtract,
                        providerAfterTransfer[0], providerAfterTransfer[0]);
                fixture.replaceProviderSource();
                phase[0] = 2;
                helper.assertTrue(false, "Waiting for replacement native provider source");
            }
            helper.assertTrue(fixture.replacementSourceReady(), "Replacement source must join the original provider Grid");
            fixture.charge(400);
            var replacementBefore = fixture.providerStored();
            var recovered = fixture.extract(100);
            var replacementAfter = fixture.providerStored();
            var replacement = fixture.binding();
            helper.assertValueEqual(recovered, 100.0, "Replacement source must republish native supply");
            helper.assertTrue(replacement.revision().providerGeneration().value() > generationBefore[0],
                    "Replacement source must advance provider generation");
            helper.assertTrue(replacement.providerService() == fixture.providerGrid().getEnergyService(),
                    "Replacement binding must capture the current native service");
            EnergyAuthorityReceipt.captureCurrent("energydisconnectnosource", "replacement", replacement);
            EnergyAuthorityReceipt.captureOperation("energydisconnectnosource", "replacement-extract",
                    fixture.consumerGrid(), fixture.providerGrid(), 100, recovered, replacementBefore,
                    replacementAfter);
            DirectionalEnergyEvidence.write("energydisconnectnosource", 9, Map.of(
                    "initialTransfer", number(200), "heldBindingCurrent", "false", "staleExtracted", number(0),
                    "providerAfterTransfer", number(providerAfterTransfer[0]), "bindingCountWithoutSource", "0",
                    "generationBefore", Long.toString(generationBefore[0]),
                    "generationAfter", Long.toString(replacement.revision().providerGeneration().value()),
                    "replacementExtracted", number(recovered), "replacementServiceExact", "true"));
            fixture.close();
        });
    }

    private static void runReady(GameTestHelper helper, java.util.function.Consumer<DirectionalEnergyFixture> assertions) {
        var fixture = new DirectionalEnergyFixture(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for directional energy fixture");
            assertions.accept(fixture);
            fixture.close();
        });
    }

    private static String number(double value) {
        return Double.toString(value);
    }

    private static void assertInvalidAmount(GameTestHelper helper,
            space.controlnet.ae2federation.energy.DirectionalEnergySource source, double amount) {
        try {
            source.extractAEPower(amount, appeng.api.config.Actionable.MODULATE, appeng.api.config.PowerMultiplier.ONE);
            helper.assertTrue(false, "Invalid native energy amount must be rejected: " + amount);
        } catch (IllegalArgumentException expected) {
            helper.assertTrue(true, "Invalid native energy amount was rejected");
        }
    }
}
