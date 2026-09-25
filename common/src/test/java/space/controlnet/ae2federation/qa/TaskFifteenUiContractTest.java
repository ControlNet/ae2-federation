package space.controlnet.ae2federation.qa;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class TaskFifteenUiContractTest {
    private static final Path REPOSITORY_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void unifiedPolicyUiOwnsServerValidationAndProductionResources() throws IOException {
        var menu = REPOSITORY_ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/client/menu/FederationDomainPolicyMenu.java");
        var session = REPOSITORY_ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/client/policy/FederationDomainPolicySession.java");
        var selection = REPOSITORY_ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/client/policy/PolicyEditorSelection.java");
        var xml = REPOSITORY_ROOT.resolve("common/src/main/resources/assets/ae2federation/ui/domain.xml");
        var lss = REPOSITORY_ROOT.resolve("common/src/main/resources/assets/ae2federation/lss/domain.lss");

        assertTrue(Files.isRegularFile(menu), "Task 15 must provide one production Router/Bridge menu");
        assertTrue(Files.isRegularFile(session), "Task 15 must provide a server-owned policy session");
        assertTrue(Files.isRegularFile(xml), "Task 15 must provide the production LDLib2 XML resource");
        assertTrue(Files.isRegularFile(lss), "Task 15 must provide one shared production LSS resource");

        var sessionSource = Files.readString(session);
        assertTrue(sessionSource.contains("isCurrent"), "Mutations must reject stale Federation Domain generations");
        assertTrue(sessionSource.contains("distanceToSqr"), "The server must validate menu distance");
        assertTrue(sessionSource.contains("expectedRevision"), "Policy mutation must use authoritative CAS revisions");
        assertTrue(Files.readString(selection).contains("PolicyCapability.values()"),
                "Capability selection must be bounds checked");
    }

    @Test
    void actualClientHarnessRegistersExactlyTheFiveTaskFifteenCases() throws IOException {
        var manifest = Files.readString(REPOSITORY_ROOT.resolve("tests/scenarios/manifest.json"));
        for (var caseId : new String[] { "ui.router", "ui.bridge", "ui.shared-policy", "ui.stale-context",
                "ui.close-unsubscribe" }) {
            assertTrue(manifest.contains("\"id\":\"" + caseId + "\",\"backend\":\"ldlib2\""),
                    "Task 15 case must use the actual LDLib2 client: " + caseId);
        }

        var harness = Files.readString(REPOSITORY_ROOT.resolve("gradle/federation-ui.gradle"));
        assertTrue(harness.contains("verifyTaskFifteenReport"),
                "Fresh and persisted Task 15 evidence must share a semantic verifier");
        assertTrue(harness.contains("forged-task15-ack"),
                "Task 15 must reject a fully rebound locally forged acknowledgment");
        assertTrue(harness.contains("stale-context-success"),
                "Task 15 must reject stale topology reported as success");

        var fixture = REPOSITORY_ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/ui/TaskFifteenWorldFixture.java");
        var support = REPOSITORY_ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/ui/TaskFifteenScenarioSupport.java");
        assertTrue(Files.readString(fixture).contains("PolicyService.get"),
                "Task 15 scenarios must observe the real production PolicyService");
        assertTrue(Files.readString(support).contains("TaskFifteenWorldFixture.openRouter")
                        && Files.readString(support).contains("TaskFifteenWorldFixture.openBridge"),
                "Task 15 scenarios must open both production menu entrances");
        assertTrue(Files.notExists(REPOSITORY_ROOT.resolve(
                        "common/src/testmod/java/space/controlnet/ae2federation/test/ui/TaskFifteenUiFixture.java")),
                "Task 15 must not retain a shadow policy/menu implementation");
    }
}
