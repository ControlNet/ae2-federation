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

final class NativeAutomationContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void manifestRegistersExactTaskTwentyEightCases() throws IOException {
        var manifest = source("tests/scenarios/manifest.json");
        var entries = Pattern.compile("\\{[^{}]*\"id\":\"(automation\\.[^\"]+)\"[^{}]*}").matcher(manifest);
        var cases = new TreeSet<String>();
        while (entries.find()) {
            assertTrue(cases.add(entries.group(1)));
            assertTrue(entries.group().contains("\"backend\":\"gametest\""));
            assertTrue(Pattern.compile("\"testId\":\"automation[a-z]+\"").matcher(entries.group()).find());
        }
        assertEquals(Set.of("automation.interface-stock", "automation.crafting-card", "automation.native-buses",
                "automation.reject-duplicate-demand", "automation.contention"), cases);
    }

    @Test
    void nativeFixturesOwnDemandAndTransferState() throws IOException {
        var fixture = source(
                "common/src/testmod/java/space/controlnet/ae2federation/test/automation/NativeAutomationFixture.java");
        var tests = source(
                "common/src/testmod/java/space/controlnet/ae2federation/test/NativeAutomationGameTests.java")
                + source("common/src/testmod/java/space/controlnet/ae2federation/test/NativeAutomationDemandGameTests.java");

        assertTrue(fixture.contains("InterfaceBlockEntity"));
        assertTrue(fixture.contains("AEItems.CRAFTING_CARD"));
        assertTrue(fixture.contains("ImportBusPart"));
        assertTrue(fixture.contains("ExportBusPart"));
        assertTrue(fixture.contains("StorageMountService"));
        assertTrue(tests.contains("getRequestedJobs()"));
        assertTrue(tests.contains("getCraftingID()"));
        assertTrue(tests.contains("removeCraftingCard"));
        assertFalse(fixture.contains("NativeTerminalAdapter"));
        assertFalse(fixture.contains("NativeTerminalRequest"));
    }

    @Test
    void taskTwentyEightEvidenceRejectsAutomationSubstitutes() throws IOException {
        var script = source("gradle/federation-qa.gradle");

        assertTrue(script.contains("verifyTaskTwentyEightEvidence(attempt"));
        assertTrue(script.contains("AE2F_AUTOMATION_NATIVE_ENTRY"));
        assertTrue(script.contains("AE2F_AUTOMATION_AUTHORITY"));
        assertTrue(script.contains("federationTaskTwentyEightEvidenceConsumer"));
        assertTrue(script.contains("federationTaskTwentyEightEvidenceSelfTest"));
        assertTrue(script.contains("Task 28 fixture-only or manual demand was substituted"));
        assertTrue(script.contains("Task 28 duplicate native demand or job detected"));
        assertTrue(script.contains("Task 28 contention exceeded physical stock"));
        assertTrue(script.contains("Task 28 optional addon isolation is incomplete"));
        assertTrue(script.contains("Task 28 reciprocal stocking authority was inferred"));
    }

    @Test
    void taskTwentyEightBindsNativeObservationsToIndependentPreOperationAuthority() throws IOException {
        var authority = source(
                "common/src/testmod/java/space/controlnet/ae2federation/test/automation/AutomationAuthorityObservation.java");
        var nativeObservation = source(
                "common/src/testmod/java/space/controlnet/ae2federation/test/automation/AutomationNativeObservation.java");
        var script = source("gradle/federation-qa.gradle");

        assertTrue(authority.contains("AE2F_AUTOMATION_PREOP_AUTHORITY"));
        assertTrue(authority.contains("authorizeInterface"));
        assertTrue(authority.contains("authorizeProjectionOperation"));
        assertTrue(authority.contains("StorageMountService"));
        assertTrue(authority.contains("mountGeneration"));
        assertTrue(authority.contains("sourceDomain"));
        assertTrue(nativeObservation.contains("acceptsInterfaceOwner"));
        assertTrue(nativeObservation.contains("acceptsProjectionOperation"));
        assertTrue(script.contains("Task 28 pre-operation Interface owner mismatch"));
        assertTrue(script.contains("Task 28 pre-operation projection mismatch"));
        assertTrue(script.contains("coordinated-interface-owner"));
        assertTrue(script.contains("coordinated-projection-identity"));
    }

    private static String source(String path) throws IOException {
        return Files.readString(ROOT.resolve(path));
    }
}
