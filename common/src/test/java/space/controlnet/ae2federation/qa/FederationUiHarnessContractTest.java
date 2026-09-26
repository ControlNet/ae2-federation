package space.controlnet.ae2federation.qa;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class FederationUiHarnessContractTest {
    private static final Path REPOSITORY_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void actualClientHarnessRequiresTaskAndFreshReportContract() throws IOException {
        var harness = REPOSITORY_ROOT.resolve("gradle/federation-ui.gradle");
        var neoForgeBuild = REPOSITORY_ROOT.resolve("neoforge-1.21.1/build.gradle");
        var manifest = REPOSITORY_ROOT.resolve("tests/scenarios/manifest.json");

        assertTrue(Files.isRegularFile(harness), "federationUiTest harness must exist");
        var content = Files.readString(harness);
        assertTrue(content.contains("federationUiTest"), "actual-client task must be registered");
        assertTrue(content.contains("report.json"), "actual-client report must be required");
        assertTrue(content.contains(":(exclude).omo/**"),
                "source identity must exclude dynamic orchestration state");
        assertTrue(content.contains("renderer.guiScale"),
                "published GUI scale must come from the rendered scenario state");
        assertTrue(content.contains("federationUiVerifierSelfTest"),
                "persisted verifier must expose rebound upstream-report regression probes");
        assertTrue(content.contains("verifyLdlibReport(upstreamReport"),
                "persisted verifier must reuse producer-time LDLib2 semantic validation");

        var manifestContent = Files.readString(manifest);
        assertTrue(manifestContent.contains(
                "{\"id\":\"ui-harness.shared-resource\",\"backend\":\"ldlib2\",\"testId\":\"ui-harness.shared-resource\",\"assertions\":2}"));
        assertTrue(manifestContent.contains(
                "{\"id\":\"ui-harness.server-ack\",\"backend\":\"ldlib2\",\"testId\":\"ui-harness.server-ack\",\"assertions\":4}"));
        assertTrue(manifestContent.contains(
                "{\"id\":\"ui-harness.reject-stale\",\"backend\":\"self-test\",\"assertions\":1}"));

        var buildContent = Files.readString(neoForgeBuild);
        assertTrue(buildContent.contains("prepareUiTestClientOptions"),
                "actual-client run must prepare deterministic options");
        assertTrue(buildContent.contains("onboardAccessibility:false"),
                "actual-client run must bypass Minecraft accessibility onboarding");
        assertTrue(buildContent.contains("taskBefore(prepareUiTestClientOptions)"),
                "uiTestClient must run options preparation before launch");
    }
}
