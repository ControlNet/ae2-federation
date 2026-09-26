package space.controlnet.ae2federation.qa;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;

final class TaskThirtySixCompatibilityContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final String[] CASES = {
            "compat.native-differential", "compat.provider-hooks", "compat.real-tech-line",
            "compat.optional-absent", "compat.reject-unsupported"
    };

    @Test
    void compatibilityStatusesCannotPassWithoutExecutedOptionalModChildren() throws IOException {
        var matrix = new Properties();
        try (var input = Files.newInputStream(ROOT.resolve("tests/compatibility/matrix.properties"))) {
            matrix.load(input);
        }
        assertFalse(matrix.getProperty("storageStatus").equals("BLOCKED_NO_RUNTIME_SCENE"));
        assertFalse(matrix.getProperty("logisticsStatus").equals("BLOCKED_NO_RUNTIME_SCENE"));
        assertFalse(matrix.getProperty("technologyStatus").equals("BLOCKED_RUNTIME_NOT_AUTHENTICATED"));
        var harness = Files.readString(ROOT.resolve("gradle/federation-qa.gradle"));
        assertTrue(harness.contains("positive-compatfunctionalstorage"));
        assertTrue(harness.contains("positive-compatprettyitems"));
        assertTrue(harness.contains("positive-compatprettyfluids"));
        assertTrue(harness.contains("positive-compatgtceu"));
    }

    @Test
    void manifestAndHarnessOwnTheExactCompatibilityCases() throws IOException {
        var manifest = Files.readString(ROOT.resolve("tests/scenarios/manifest.json"));
        var harness = Files.readString(ROOT.resolve("gradle/federation-qa.gradle"));
        for (var caseId : CASES) {
            assertTrue(manifest.contains("\"id\":\"" + caseId + "\""), "Missing Task 36 case " + caseId);
        }
        assertTrue(harness.contains("verifyTaskThirtySixEvidence"));
        assertTrue(harness.contains("federationTaskThirtySixEvidenceConsumer"));
        assertTrue(harness.contains("federationTaskThirtySixEvidenceSelfTest"));
    }

    @Test
    void profilesAreIsolatedAndOptionalModsCannotLeakIntoReleaseArchives() throws IOException {
        var build = Files.readString(ROOT.resolve("neoforge-1.21.1/build.gradle"));
        assertTrue(build.contains("functionalStorageCompatibility"));
        assertTrue(build.contains("prettyPipesCompatibility"));
        assertTrue(build.contains("gtceuCompatibility"));
        assertTrue(build.contains("enableAppfluxCompatibility"));
        assertTrue(build.contains("com.glodblock.github.appflux"));
        assertTrue(build.contains("com/buuz135/functionalstorage"));
        assertTrue(build.contains("de/maxhenkel/pipez"));
        assertTrue(build.contains("com/gregtechceu/gtceu"));
        assertFalse(build.contains("implementation(\"maven.modrinth:functional-storage"));
        assertTrue(build.contains("maven.modrinth:cO40ZIg3:qyocTQUb"));
    }

    @Test
    void matrixRecordsPinnedArtifactsLicensesAndBlockedBoundaries() throws IOException {
        var matrix = Files.readString(ROOT.resolve("docs/compatibility/matrix.md"));
        assertTrue(matrix.contains("974a1b0e45e98a9769e84fdfe4d7e3eac3de3936ed7a9baf009026ebee98fc12"));
        assertTrue(matrix.contains("2d5c0dfbf1853e28d515b4224ca39a1de4520a1ac2e0fe987e6400965bf1f555"));
        assertTrue(matrix.contains("c09a550523e931342a575193cbaad2e4b643e5de2cefe280cd1de540022c6388"));
        assertTrue(matrix.contains("BLOCKED"));
        assertTrue(matrix.contains("All Rights Reserved"));
        assertTrue(matrix.contains("No optional mod is bundled"));
    }
}
