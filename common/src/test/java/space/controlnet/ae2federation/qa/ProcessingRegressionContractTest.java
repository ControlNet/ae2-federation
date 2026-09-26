package space.controlnet.ae2federation.qa;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProcessingRegressionContractTest {
    private static final Path REPOSITORY_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void taskNineteenRegistersExactNativeCasesAndEvidenceGates() throws IOException {
        var manifest = Files.readString(REPOSITORY_ROOT.resolve("tests/scenarios/manifest.json"));
        var qa = Files.readString(REPOSITORY_ROOT.resolve("gradle/federation-qa.gradle"));

        for (var caseId : new String[] { "processing.native-differential", "processing.shared-capacity",
                "processing.lock-isolation", "processing.disconnect-restart", "processing.dismantle",
                "processing.reject-false-replay" }) {
            assertTrue(manifest.contains(caseId), () -> "missing Task 19 case " + caseId);
        }
        assertTrue(qa.contains("verifyTaskNineteenEvidence"));
        assertTrue(qa.contains("federationTaskNineteenEvidenceSelfTest"));
        assertTrue(qa.contains("AE2F_PROCESSING_NATIVE_TRACE"));
        assertTrue(qa.contains("AE2F_PROCESSING_NATIVE_STATE"));
        assertTrue(qa.contains("AE2F_PROCESSING_NATIVE_TARGET"));
        assertTrue(qa.contains("ae2DependencyJarSha256"));
        assertTrue(qa.contains("ae2PatternProviderLogicSourceSha256"));
        assertTrue(qa.contains("restart native lifecycle receipts are incomplete"));
        assertTrue(qa.contains("dismantle native ownership receipts are incomplete"));
        assertTrue(qa.contains("task19-rebound-restart-owner"));
        assertTrue(qa.contains("task19-rebound-dismantle-drop"));
    }

    @Test
    void taskNineteenUsesOnlyTestmodNativeObservers() throws IOException {
        var registration = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/FederationTestMod.java"));
        var testmod = REPOSITORY_ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/ProcessingRegressionGameTests.java");
        var productionProbe = REPOSITORY_ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/processing/ProcessingRegressionProbe.java");

        assertTrue(Files.isRegularFile(testmod));
        assertTrue(registration.contains("ProcessingRegressionGameTests.class"));
        assertFalse(Files.exists(productionProbe));
    }
}
