package space.controlnet.ae2federation.qa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

final class ProviderClaimContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void manifestRegistersExactlySixNativeProviderClaimCases() throws IOException {
        var manifest = Files.readString(ROOT.resolve("tests/scenarios/manifest.json"));
        var entries = Pattern.compile(
                        "\\{[^{}]*\"id\":\"((?:provider\\.(?:orientation|rotate-pending|reject-same-grid)|claim\\.(?:compete|offline-owner|overlap)))\"[^{}]*}")
                .matcher(manifest);
        var cases = new TreeSet<String>();
        while (entries.find()) {
            assertTrue(cases.add(entries.group(1)));
            assertTrue(entries.group().contains("\"backend\":\"gametest\""));
            assertTrue(Pattern.compile("\"testId\":\"(?:provider|claim)[a-z]+\"").matcher(entries.group()).find());
        }
        assertEquals(Set.of("provider.orientation", "claim.compete", "claim.offline-owner", "claim.overlap",
                "provider.rotate-pending", "provider.reject-same-grid"), cases);
    }

    @Test
    void runtimeAndEvidenceBindTaskSeventeenSemantics() throws IOException {
        var tests = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/ProviderClaimGameTests.java"));
        var testMod = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/FederationTestMod.java"));
        var script = Files.readString(ROOT.resolve("gradle/federation-qa.gradle"));

        assertTrue(tests.contains("providerOrientation"));
        assertTrue(tests.contains("claimCompete"));
        assertTrue(tests.contains("claimOfflineOwner"));
        assertTrue(tests.contains("claimOverlap"));
        assertTrue(tests.contains("providerRotatePending"));
        assertTrue(tests.contains("providerRejectSameGrid"));
        assertTrue(testMod.contains("ProviderClaimGameTests.class"));
        assertTrue(script.contains("verifyTaskSeventeenEvidence"));
        assertTrue(script.contains("AE2F_PROVIDER_CLAIM_TRACE"));
        assertTrue(script.contains("federationTaskSeventeenEvidenceSelfTest"));
    }

    @Test
    void productionRuntimeOwnsProviderAndEndpointBinding() throws IOException {
        var providerRuntimePath = ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/processing/provider/ProviderRuntime.java");
        assertTrue(Files.isRegularFile(providerRuntimePath),
                "Task 17 requires a production owner for Provider wiring and native Lane target binding");
        var providerRuntime = Files.readString(providerRuntimePath);
        var registration = Files.readString(ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/processing/ProcessingRegistration.java"));

        assertTrue(providerRuntime.contains("new ProviderNodeWiring"));
        assertTrue(providerRuntime.contains("provider.bindTarget"));
        assertTrue(providerRuntime.contains("ProviderTargetAuthorization.resolve"));
        assertTrue(registration.contains("EndpointTargetCapability.BLOCK"));
        assertTrue(registration.contains("EndpointTargetBinding.find"));
    }
}
