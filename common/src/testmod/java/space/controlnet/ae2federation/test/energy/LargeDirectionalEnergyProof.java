package space.controlnet.ae2federation.test.energy;

import java.util.Map;
import net.minecraft.gametest.framework.GameTestHelper;

public final class LargeDirectionalEnergyProof {
    private static final double THRESHOLD = 1_000_000_000.0;
    private static final double REQUESTED = 1_000_000_250.0;

    private LargeDirectionalEnergyProof() {
    }

    public static Map<String, String> capture(GameTestHelper helper, DirectionalEnergyFixture fixture) {
        var capacity = fixture.providerCapacity();
        var before = fixture.providerStored();
        var available = fixture.consumerAvailable();
        var simulated = fixture.simulate(REQUESTED);
        var afterSimulation = fixture.providerStored();
        var accepted = fixture.extract(REQUESTED);
        var after = fixture.providerStored();
        EnergyAuthorityReceipt.captureOperation("energydirectionalpolicy", "large-extract", fixture.consumerGrid(),
                fixture.providerGrid(), REQUESTED, accepted, before, after);
        helper.assertTrue(capacity > THRESHOLD, "Native provider capacity must exceed one billion AE");
        helper.assertTrue(before > THRESHOLD, "Native provider must hold more than one billion AE");
        helper.assertTrue(available > THRESHOLD,
                "Consumer native service must expose more than one billion AE");
        helper.assertValueEqual(simulated, REQUESTED, "Large native simulation must preserve the full request");
        helper.assertValueEqual(afterSimulation, before, "Large native simulation must not mutate provider energy");
        helper.assertValueEqual(accepted, REQUESTED,
                "Large consumer demand must accept more than one billion AE exactly");
        helper.assertValueEqual(before - after, accepted,
                "Large provider debit must equal large consumer acceptance");
        return Map.ofEntries(Map.entry("largeProviderCapacity", number(capacity)),
                Map.entry("largeProviderBefore", number(before)), Map.entry("largeConsumerAvailable", number(available)),
                Map.entry("largeRequested", number(REQUESTED)), Map.entry("largeSimulated", number(simulated)),
                Map.entry("largeProviderAfterSimulation", number(afterSimulation)),
                Map.entry("largeAccepted", number(accepted)), Map.entry("largeProviderAfter", number(after)),
                Map.entry("largeProviderDebit", number(before - after)));
    }

    private static String number(double value) {
        return Double.toString(value);
    }
}
