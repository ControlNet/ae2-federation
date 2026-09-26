package space.controlnet.ae2federation.qa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

final class NativeEnergyBindingContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void manifestRegistersExactlyFiveNativeEnergyCases() throws IOException {
        var manifest = Files.readString(ROOT.resolve("tests/scenarios/manifest.json"));
        var entries = Pattern.compile("\\{[^{}]*\"id\":\"(energy-proof\\.[^\"]+)\"[^{}]*}").matcher(manifest);
        var cases = new TreeSet<String>();
        while (entries.find()) {
            assertTrue(cases.add(entries.group(1)));
            assertTrue(entries.group().contains("\"backend\":\"gametest\""));
            assertTrue(Pattern.compile("\"testId\":\"energyproof[a-z]+\"").matcher(entries.group()).find());
            assertTrue(Pattern.compile("\"assertions\":[1-9][0-9]*").matcher(entries.group()).find());
        }
        assertEquals(Set.of("energy-proof.directional", "energy-proof.cold-start", "energy-proof.reject-reverse",
                "energy-proof.no-source", "energy-proof.ring"), cases);
    }

    @Test
    void testmodUsesNativeEnergyServicesAndPinnedOverlayConnection() throws IOException {
        var fixture = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/energy/NativeEnergyFixtures.java"));
        var tests = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/NativeEnergyGameTests.java"));
        var testMod = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/FederationTestMod.java"));
        assertTrue(fixture.contains("IEnergyOverlayGridConnection.class"));
        assertTrue(fixture.contains("getEnergyService()"));
        assertTrue(fixture.contains("IAEPowerStorage"));
        assertTrue(fixture.contains("EnergyService"));
        assertTrue(fixture.contains("GridFlags.CANNOT_CARRY"));
        assertTrue(tests.contains("energyProofDirectional"));
        assertTrue(tests.contains("energyProofColdStart"));
        assertTrue(tests.contains("energyProofRejectReverse"));
        assertTrue(tests.contains("energyProofNoSource"));
        assertTrue(tests.contains("energyProofRing"));
        assertTrue(testMod.contains("NativeEnergyGameTests.class"));
        assertFalse(fixture.contains("ForgeEnergy"));
    }

    @Test
    void taskThirtyOneRegistersPolicyBoundDirectionalEnergyCases() throws IOException {
        var fixture = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/energy/DirectionalEnergyFixture.java"));
        var tests = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/DirectionalEnergyGameTests.java"));
        var testMod = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/FederationTestMod.java"));
        assertTrue(fixture.contains("EnergyBindingService"));
        assertTrue(fixture.contains("PolicyOperation.SUPPLY"));
        assertTrue(fixture.contains("DirectionalEnergySource"));
        assertTrue(tests.contains("energyDirectionalPolicy"));
        assertTrue(tests.contains("energyColdStart"));
        assertTrue(tests.contains("energyRingConservation"));
        assertTrue(tests.contains("energyRejectReverse"));
        assertTrue(tests.contains("energyDisconnectNoSource"));
        assertTrue(testMod.contains("DirectionalEnergyGameTests.class"));
    }

    @Test
    void taskTenGatePersistsHonestBlockedDiagnosticsAndRejectsCompletionClaims() throws IOException {
        var script = Files.readString(ROOT.resolve("gradle/federation-qa.gradle"));
        assertTrue(script.contains("verifyTaskTenEvidence"));
        assertTrue(script.contains("AE2F_ENERGY_NATIVE_ENTRY"));
        assertTrue(script.contains("shared EnergyOverlayGrid has no caller/edge direction, route identity, or authorization boundary"));
        assertTrue(script.contains("def taskTenBlocked = kind == 'verify' && requestedCases == taskTenCases"));
        assertTrue(script.contains("def reportBlocked = taskTenBlocked || taskThirtySevenBlocked"));
        assertTrue(script.contains("def reportStatus = reportBlocked ? 'BLOCKED' : 'complete'"));
        assertTrue(script.contains("status: reportStatus"));
        assertTrue(script.contains("parentExit: reportBlocked ? 1 : 0"));
        assertTrue(script.contains("report.status != 'complete' || report.parentExit != 0"));
        assertTrue(script.contains("report.reason = 'native-energy-directionality-unavailable'"));
        assertTrue(script.contains("federationTaskTenEvidenceSelfTest"));
        assertTrue(script.contains("federationTaskTenBlockedConsumerSelfTest"));
        assertTrue(script.contains("Task 10 completed-looking evidence is invalid"));
        assertTrue(script.contains("Task 10 BLOCKED report must not be consumed as complete evidence"));
    }
}
