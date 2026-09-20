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

final class NativeTerminalFlowContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void terminalBoundaryDelegatesToCapturedNativeEntryPoints() throws IOException {
        var adapter = source("common/src/main/java/space/controlnet/ae2federation/crafting/terminal/NativeTerminalAdapter.java");
        var request = source("common/src/main/java/space/controlnet/ae2federation/crafting/terminal/NativeTerminalRequest.java");
        var requester = source(
                "common/src/main/java/space/controlnet/ae2federation/crafting/terminal/CapturedTerminalRequester.java");

        assertTrue(adapter.contains("CraftingBindingService.get(level).capability(key)"));
        assertTrue(adapter.contains("getCraftables"));
        assertTrue(adapter.contains("PolicyOperation.REQUEST"));
        assertFalse(adapter.contains("addGlobalCraftingProvider"));
        assertFalse(adapter.contains("GridHelper.createConnection"));
        assertTrue(request.contains("beginCraftingCalculation"));
        assertTrue(request.contains("submitJob"));
        assertTrue(request.contains("submissionAuthorityCurrent"));
        assertTrue(request.contains("future.isDone()"));
        assertTrue(requester.contains("return actionSource;"));
        assertTrue(requester.contains("return gridNode;"));
        assertFalse(requester.contains("ServerLevel"));
        assertFalse(requester.contains("PolicyService"));
        assertFalse(requester.contains("FabricRegistry"));
    }

    @Test
    void manifestRegistersExactTaskTwentySevenCases() throws IOException {
        var manifest = source("tests/scenarios/manifest.json");
        var entries = Pattern.compile("\\{[^{}]*\"id\":\"(terminal\\.[^\"]+)\"[^{}]*}").matcher(manifest);
        var cases = new TreeSet<String>();
        while (entries.find()) {
            assertTrue(cases.add(entries.group(1)));
            assertTrue(entries.group().contains("\"backend\":\"gametest\""));
            assertTrue(Pattern.compile("\"testId\":\"terminal[a-z]+\"").matcher(entries.group()).find());
        }
        assertEquals(Set.of("terminal.native-crafting", "terminal.native-result", "terminal.missing-material",
                "terminal.no-cpu", "terminal.reject-async-world-access"), cases);
    }

    @Test
    void taskTwentySevenEvidenceRequiresNativeAndPlannerAuthority() throws IOException {
        var script = source("gradle/federation-qa.gradle");

        assertTrue(script.contains("verifyTaskTwentySevenEvidence(attempt"));
        assertTrue(script.contains("AE2F_TERMINAL_NATIVE_ENTRY"));
        assertTrue(script.contains("AE2F_TERMINAL_PLANNER"));
        assertTrue(script.contains("federationTaskTwentySevenEvidenceConsumer"));
        assertTrue(script.contains("federationTaskTwentySevenEvidenceSelfTest"));
        assertTrue(script.contains("Task 27 native child is missing, extra, or substituted"));
        assertTrue(script.contains("Task 27 stale binding was submitted"));
        assertTrue(script.contains("Task 27 planner thread touched mutable server authority"));
        assertTrue(script.contains("Task 27 result ownership correlation is incomplete"));
    }

    @Test
    void resultEvidenceBindsPreestablishedPhysicalDestination() throws IOException {
        var fixture = source(
                "common/src/testmod/java/space/controlnet/ae2federation/test/crafting/TerminalCraftingFixture.java");
        var script = source("gradle/federation-qa.gradle");

        assertTrue(fixture.contains("captureResultAuthority"));
        assertTrue(script.contains("AE2F_TERMINAL_RESULT_AUTHORITY"));
        assertTrue(script.contains("fabricated-result-link"));
        assertTrue(script.contains("fabricated-result-logic"));
        assertTrue(script.contains("fabricated-result-destination"));
    }

    @Test
    void discoveryEvidenceRequiresAllowedAndForbiddenNativePatterns() throws IOException {
        var sourceFixture = source(
                "common/src/testmod/java/space/controlnet/ae2federation/test/crafting/CraftingNativeSourceFixture.java");
        var terminalFixture = source(
                "common/src/testmod/java/space/controlnet/ae2federation/test/crafting/TerminalCraftingFixture.java");
        var script = source("gradle/federation-qa.gradle");

        assertTrue(sourceFixture.contains("craftingTablePattern"));
        assertTrue(terminalFixture.contains("PolicyFilterMode.ALLOW_LIST"));
        assertTrue(script.contains("AE2F_TERMINAL_DISCOVERY_AUTHORITY"));
        assertTrue(script.contains("forbidden-pattern-discovery"));
        assertTrue(script.contains("fake-single-pattern-source"));
        assertTrue(script.contains("copied-allowed-pattern"));
        assertTrue(script.contains("projection-recursion"));
    }

    private static String source(String path) throws IOException {
        return Files.readString(ROOT.resolve(path));
    }
}
