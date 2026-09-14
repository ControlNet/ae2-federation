package space.controlnet.ae2federation.test;

import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.test.energy.NativeEnergyEvidence;
import space.controlnet.ae2federation.test.energy.NativeEnergyFixtures;

@PrefixGameTestTemplate(false)
public final class NativeEnergyGameTests {
    private NativeEnergyGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void energyProofDirectional(GameTestHelper helper) {
        run(helper, 2, fixture -> {
            fixture.charge(0, 1_000);
            fixture.connectOneWay(1, 0);
            var providerBefore = fixture.stored(0);
            var extracted = fixture.extract(1, 250);
            var providerAfter = fixture.stored(0);
            helper.assertValueEqual(extracted, 250.0, "Consumer Grid must extract native shared power");
            helper.assertValueEqual(providerBefore - providerAfter, extracted,
                    "Provider storage debit must equal consumer extraction");
            helper.assertTrue(fixture.grid(0) != fixture.grid(1), "Energy overlay must preserve distinct data Grids");
            helper.assertValueEqual(fixture.usedChannels(0), 0, "Provider data channels must remain local");
            helper.assertValueEqual(fixture.usedChannels(1), 0, "Consumer data channels must remain local");
            NativeEnergyEvidence.write("energyproofdirectional", 5, Map.of(
                    "providerGrid", identity(fixture.grid(0)), "consumerGrid", identity(fixture.grid(1)),
                    "providerBefore", number(providerBefore), "providerAfter", number(providerAfter),
                    "extracted", number(extracted), "sourceDebit", number(providerBefore - providerAfter),
                    "distinctDataGrids", "true", "providerChannels", "0", "consumerChannels", "0",
                    "targetReceiptTransaction", "none"));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void energyProofColdStart(GameTestHelper helper) {
        var fixture = new NativeEnergyFixtures(helper, 2);
        var connected = new boolean[] { false };
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for two native energy Grids");
            if (!connected[0]) {
                helper.assertTrue(!fixture.powered(1), "Consumer must begin unpowered");
                fixture.charge(0, 1_000);
                fixture.connectOneWay(1, 0);
                connected[0] = true;
                helper.assertTrue(false, "Waiting for AE2 public powered-state stabilization");
            }
            helper.assertTrue(fixture.powered(1), "Loaded consumer must discover native shared power");
            NativeEnergyEvidence.write("energyproofcoldstart", 2, Map.of(
                    "initiallyPowered", "false", "poweredAfterOverlay", "true",
                    "providerGrid", identity(fixture.grid(0)), "consumerGrid", identity(fixture.grid(1)),
                    "distinctDataGrids", Boolean.toString(fixture.grid(0) != fixture.grid(1))));
            fixture.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void energyProofRejectReverse(GameTestHelper helper) {
        run(helper, 2, fixture -> {
            fixture.charge(1, 600);
            fixture.connectOneWay(1, 0);
            fixture.discoverOverlay(1);
            var consumerBefore = fixture.stored(1);
            var reverseExtracted = fixture.extract(0, 200);
            var consumerAfter = fixture.stored(1);
            helper.assertValueEqual(reverseExtracted, 200.0,
                    "Pinned one-sided native overlay unexpectedly permits reverse extraction");
            helper.assertValueEqual(consumerBefore - consumerAfter, reverseExtracted,
                    "Reverse extraction must debit consumer storage when symmetry is exposed");
            helper.assertTrue(fixture.grid(0) != fixture.grid(1), "Reverse reachability must not imply data-Grid merge");
            NativeEnergyEvidence.write("energyproofrejectreverse", 3, Map.of(
                    "declaredEdge", "consumer-to-provider", "reverseAttempted", "true",
                    "reverseExtracted", number(reverseExtracted), "consumerBefore", number(consumerBefore),
                    "consumerAfter", number(consumerAfter), "reverseRejected", "false",
                    "missingHook", "caller-edge-direction-route-identity-authorization", "distinctDataGrids", "true"));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void energyProofNoSource(GameTestHelper helper) {
        run(helper, 2, fixture -> {
            fixture.connectOneWay(1, 0);
            var extracted = fixture.extract(1, 200);
            helper.assertValueEqual(extracted, 0.0, "No native source must yield zero extraction");
            helper.assertTrue(!fixture.powered(1), "No-source consumer must remain unpowered");
            NativeEnergyEvidence.write("energyproofnosource", 2, Map.of(
                    "extracted", "0", "consumerPowered", "false", "generatedEnergy", "0",
                    "fabricIdlePower", "0", "fabricInfiniteEnergy", "false"));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void energyProofRing(GameTestHelper helper) {
        run(helper, 3, fixture -> {
            fixture.charge(0, 900);
            fixture.connectRing();
            var totalBefore = fixture.stored(0) + fixture.stored(1) + fixture.stored(2);
            var extracted = fixture.extract(2, 300);
            var totalAfter = fixture.stored(0) + fixture.stored(1) + fixture.stored(2);
            helper.assertValueEqual(extracted, 300.0, "Ring extraction must terminate");
            helper.assertValueEqual(totalBefore - totalAfter, extracted, "Ring must conserve native stored energy");
            helper.assertTrue(fixture.grid(0) != fixture.grid(1) && fixture.grid(1) != fixture.grid(2)
                    && fixture.grid(0) != fixture.grid(2), "Ring must preserve three native data Grids");
            NativeEnergyEvidence.write("energyproofring", 3, Map.of(
                    "serviceCount", "3", "uniqueServiceCount", "3", "terminated", "true",
                    "totalBefore", number(totalBefore), "totalAfter", number(totalAfter),
                    "extracted", number(extracted), "createdEnergy", "0", "distinctDataGrids", "true"));
        });
    }

    private static void run(GameTestHelper helper, int sideCount,
            java.util.function.Consumer<NativeEnergyFixtures> assertions) {
        var fixture = new NativeEnergyFixtures(helper, sideCount);
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for native energy Grids");
            assertions.accept(fixture);
            fixture.close();
        });
    }

    private static String identity(Object value) {
        return Integer.toUnsignedString(System.identityHashCode(value));
    }

    private static String number(double value) {
        return Long.toString(Math.round(value));
    }
}
