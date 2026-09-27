package space.controlnet.ae2federation.test.ui;

import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import space.controlnet.ae2federation.processing.claim.ClaimRejection;

@LDLRegisterClient(name = "ui.reject-claim-conflict", group = "ae2federation", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class TaskThirtyThreeClaimConflictScenario implements UIScenario {
    @Override
    public void configure(ScenarioOptions options) {
        TaskThirtyThreeScenarioSupport.configure(options, 3);
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        TaskThirtyThreeScenarioSupport.open(scenario, TaskThirtyThreeScenarioSupport.Entrance.ROUTER)
                .click("#tab_diagnostics").frames(3)
                .server("submit competing Endpoint Claim", context ->
                        context.put("task33.claimConflict", TaskThirtyThreeWorldFixture.claimConflict(context)))
                .checkServer("competing Claim is rejected", context ->
                        "Rejected".equals(context.get("task33.claimConflict")))
                .waitForTextContains("#endpoint_detail", "Another Provider owns this endpoint")
                .checkTextContains("#endpoint_identity", ClaimRejection.OWNER_CONFLICT.name())
                .step("record Claim conflict evidence", context -> {
                    TaskThirtyThreeScenarioSupport.attach(context, "ui.reject-claim-conflict");
                    context.attach("claimConflict", context.get("task33.claimConflict"));
                    context.attach("claimConflictResult", context.el("#endpoint_detail").text());
                    context.put("task33.claimConflictResult", context.el("#endpoint_detail").text());
                    context.put("task33.claimConflictIdentity", context.el("#endpoint_identity").text());
                })
                .check("all font-cache accesses stayed on the render thread", context -> FontThreadEvidence.violations() == 0)
                .screenshot("ui-claim-conflict-rejected")
                .click("#tab_mapping")
                .click("#mapping_view_list")
                .click("#pattern_slot_1")
                .waitForTextContains("#mapping_lane_next .__selector_preview__ .choice-label", "Mapped")
                .server("transfer idle claim to a competing identity", TaskThirtyThreeWorldFixture::transferIdleClaimToCompetitor)
                .waitForTextContains("#mapping_lane_next .__selector_preview__ .choice-label", "Occupied")
                .check("target tooltip identifies the real competing claim", context -> {
                    var label = context.el("#mapping_lane_next .__selector_preview__ .choice-label")
                            .as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class);
                    var tooltip = label.collectHoverTooltips();
                    return tooltip != null && tooltip.tooltipTexts().stream().anyMatch(line ->
                            line.getString().contains(context.<String>get("target.competitor")))
                            && tooltip.tooltipTexts().stream().anyMatch(line ->
                                    line.getString().contains("Another Provider owns this endpoint"));
                })
                .click("#mapping_lane_next")
                .typeInto("#mapping_lane_next_search", "Occupied")
                .check("occupied target can be found before attempting mapping", context -> !context.el("#mapping_lane_next_empty").isVisible())
                .screenshot("ui-target-occupied-before-mapping")
                .click("#tab_mapping")
                .click("#mapping_view_graph")
                .waitUntil("wires view marks the Endpoint as owned elsewhere", context ->
                        context.el(".processing-endpoint-state").text().equals("Owned by another Provider"))
                .serverGet("record lanes before the refused drop", "task33.lanesBeforeDrop",
                        TaskThirtyThreeWorldFixture::slotZeroLanes)
                .drag("#processing_port_0", ".processing-endpoint")
                .waitForTextContains("#processing_detail_text", "Release it there first.")
                .checkServer("the refused drop changes no mapping", context ->
                        TaskThirtyThreeWorldFixture.slotZeroLanes(context).equals(context.get("task33.lanesBeforeDrop")))
                .check("an Endpoint owned elsewhere cannot be released here", context -> !context.el("#processing_release").isActive())
                .screenshot("ui-processing-drop-occupied")
                .step("record Claim conflict and refused drop evidence", context -> {
                    // Attachments are grouped per step; this record supersedes the earlier one for this case.
                    TaskThirtyThreeScenarioSupport.attach(context, "ui.reject-claim-conflict");
                    context.attach("claimConflict", context.get("task33.claimConflict"));
                    context.attach("claimConflictResult", context.get("task33.claimConflictResult"));
                    context.attach("endpointIdentity", context.get("task33.claimConflictIdentity"));
                    context.attach("processingOccupiedDrop", "refused");
                })
                .closeScreen();
    }
}
