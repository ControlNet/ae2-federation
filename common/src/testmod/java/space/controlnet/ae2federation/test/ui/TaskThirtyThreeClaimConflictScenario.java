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
                })
                .check("all font-cache accesses stayed on the render thread", context -> FontThreadEvidence.violations() == 0)
                .screenshot("ui-claim-conflict-rejected")
                .click("#tab_mapping")
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
                .closeScreen();
    }
}
