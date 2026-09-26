package space.controlnet.ae2federation.qa;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class TaskThirtyThreeUiContractTest {
    private static final Path REPOSITORY_ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final String[] CASES = {
            "ui.graph-controls", "ui.mapping", "ui.endpoint", "ui.multipart-attachments",
            "ui.chinese-scales", "ui.reject-claim-conflict"
    };

    @Test
    void productionResourcesExposeTheScopedTabbedWorkspace() throws IOException {
        var xml = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/main/resources/assets/ae2federation/ui/domain.xml"));
        var lss = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/main/resources/assets/ae2federation/lss/domain.lss"));
        var holder = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/client/menu/FederationDomainPolicyMenuHolder.java"));

        for (var id : new String[] { "domain_graph", "graph_zoom_in", "graph_zoom_out", "graph_fit",
                "physical_layer_toggle", "capability_layer_toggle", "member_list", "pattern_search",
                "pattern_list", "mapping_provider_next", "mapping_slot_next", "mapping_lane_next",
                "mapping_toggle", "mapping_status", "endpoint_next", "endpoint_detail", "entrance_value",
                "members_value", "consumer_value", "provider_value", "rule_value", "ack_status" }) {
            assertTrue(xml.contains("id=\"" + id + "\""), "Missing stable Task 33 control #" + id);
        }
        assertTrue(xml.contains("<graph-view"), "Production UI must use LDLib2's pan/zoom graph canvas");
        assertTrue(xml.contains("<virtual-scroller-view"), "Large member and Pattern lists must be virtualized");
        assertTrue(lss.contains("allow-zoom: true") && lss.contains("allow-pan: true"),
                "Graph interaction must be enabled in shared LSS");
        assertTrue(lss.contains("width: 396;") && lss.contains("height: 236;"),
                "The production workspace must fit a 400x240 logical scale-4 viewport");
        for (var page : new String[] {"overview", "policy", "mapping", "diagnostics"}) {
            assertTrue(xml.contains("id=\"page_" + page + "\""), "Missing task page " + page);
        }
        assertTrue(lss.contains(".__button_text__") && lss.contains("adaptive-width: false"),
                "Button child text must have an explicit bounded style");
        assertTrue(holder.contains("FederationGraphPresenter") && holder.contains("stringS2C"),
                "The rendered graph must consume a server-owned scoped projection");
    }

    @Test
    void authoritativeMappingAndEndpointDiagnosticsUseProductionOwners() throws IOException {
        var session = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/client/policy/FederationDomainPolicySession.java"));
        assertTrue(session.contains("PatternSlotHandle"), "Mapping mutations must carry the slot generation");
        assertTrue(session.contains("replaceMapping"), "Mapping mutations must reach the real MappedPatternProvider");
        assertTrue(session.contains("ProviderTargetState"), "Unavailable Policy and Claim reasons must be surfaced");
        assertTrue(session.contains("EndpointTargetBinding"), "Endpoint detail must read the live production binding");
        assertTrue(session.contains("lastClaimResultCode"),
                "Endpoint detail must expose the production Claim result through the session binding");

        var projection = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/client/policy/FederationDomainGraphProjection.java"));
        assertTrue(projection.contains("PatternDetailsHelper.decodePattern") && projection.contains("amount()"),
                "Pattern rows must project native input quantities from decoded production patterns");
        assertTrue(projection.contains("FederationDomainPatternRow.format(slot, name, quantities, provider.lanesForSlot(slot))")
                        && !projection.contains("rows.add(slot + \"\\t\""),
                "Displayed Pattern rows must use a visible separator rather than tab control characters");

        var claimScenario = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/ui/TaskThirtyThreeClaimConflictScenario.java"));
        assertTrue(!claimScenario.contains("setText(") && !claimScenario.contains("\"OWNER_CONFLICT\""),
                "Claim evidence must come from the production session binding");
        var mappingScenario = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/ui/TaskThirtyThreeMappingScenario.java"));
        assertTrue(!mappingScenario.contains("context.put(\"task33.mappingAck\", \"accepted-0-0\")"),
                "Mapping evidence must read the server session acknowledgment rather than a fixture literal");
        var chineseScenario = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/ui/TaskThirtyThreeChineseScalesScenario.java"));
        assertTrue(!chineseScenario.contains("context.attach(\"largeQuantity\", \"4000000000\")"),
                "Quantity evidence must be read from the rendered Pattern row");

        var entrance = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/client/policy/FederationDomainPolicyEntrance.java"));
        assertTrue(entrance.contains("label(ServerLevel level)")
                        && entrance.contains("getCableConnectionLength(AECableType.GLASS)"),
                "Visible multipart diagnostics must be derived from the live server-side part");

        assertTrue(Files.isRegularFile(REPOSITORY_ROOT.resolve(
                "common/src/main/resources/assets/ae2federation/lang/zh_cn.json")),
                "Task 33 must ship Simplified Chinese UI translations");
    }

    @Test
    void actualClientHarnessRegistersExactlyTheSixTaskThirtyThreeCases() throws IOException {
        var manifest = Files.readString(REPOSITORY_ROOT.resolve("tests/scenarios/manifest.json"));
        var harness = Files.readString(REPOSITORY_ROOT.resolve("gradle/federation-ui.gradle"));
        for (var caseId : CASES) {
            assertTrue(manifest.contains("\"id\":\"" + caseId + "\",\"backend\":\"ldlib2\""),
                    "Task 33 case must use the actual LDLib2 client: " + caseId);
        }
        assertTrue(harness.contains("verifyTaskThirtyThreeReport"),
                "Fresh and persisted Task 33 evidence must share a semantic verifier");
        assertTrue(harness.contains("forged-task33-mapping-lanes"),
                "Task 33 evidence must reject a rebound forged mapping acknowledgment");
        assertTrue(harness.contains("claim-conflict-success"),
                "Task 33 evidence must reject a Claim conflict reported as success");
        assertTrue(harness.contains("fabricated-task33-multipart") && harness.contains("exercisedScales"),
                "Task 33 evidence must reject fabricated multipart state and derive actual scale coverage");
    }
}
