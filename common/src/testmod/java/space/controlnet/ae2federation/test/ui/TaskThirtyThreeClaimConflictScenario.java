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
                .waitUntil("the Endpoint is used by this Provider", context ->
                        context.el(".processing-endpoint-state").text().equals("Patterns mapped: 1"))
                .server("transfer idle claim to a competing identity", TaskThirtyThreeWorldFixture::transferIdleClaimToCompetitor)
                .waitUntil("wires view marks the Endpoint as owned elsewhere", context ->
                        context.el(".processing-endpoint-state").text().equals("Owned by another Provider"))
                .click("#processing_pattern_1")
                .click(".processing-endpoint")
                .waitUntil("the Endpoint detail names the real competing owner", context -> context.el("#processing_fact_value_owner")
                        .text().contains(context.<String>get("target.competitor").substring(0, 8)))
                .check("mapping the chosen pattern here is refused before anything is sent", context ->
                        !context.el("#mapping_toggle").isActive()
                                && context.el("#processing_detail_text").text().contains("Release it there first."))
                .screenshot("ui-target-occupied-before-mapping")
                .serverGet("record lanes before the refused drop", "task33.lanesBeforeDrop",
                        TaskThirtyThreeWorldFixture::slotZeroLanes)
                .step("start dragging pattern 0 without dropping it", context -> {
                    var port = context.el("#processing_port_0").bounds();
                    var card = context.el(".processing-endpoint").bounds();
                    context.input().moveTo(port.centerX(), port.centerY());
                    context.input().mouseDown(port.centerX(), port.centerY(), 0);
                    context.input().dragTo((port.x() + port.width() + card.x()) / 2, card.centerY(), 0);
                })
                .waitUntil("the Endpoint card shows why it refuses the drop", context ->
                        context.el(".processing-endpoint-state").text().startsWith("Taken by Provider ")
                                && context.el(".processing-endpoint").as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class)
                                .hasClass("drop-refused"))
                .screenshot("ui-processing-drop-hint")
                .step("let go in the gap between the columns", context -> {
                    var port = context.el("#processing_port_0").bounds();
                    var card = context.el(".processing-endpoint").bounds();
                    context.input().mouseUp((port.x() + port.width() + card.x()) / 2, card.centerY(), 0);
                })
                .waitUntil("the card returns to its claim once the drag ends", context ->
                        context.el(".processing-endpoint-state").text().equals("Owned by another Provider"))
                .drag("#processing_port_0", ".processing-endpoint")
                .waitForTextContains("#processing_detail_text", "Release it there first.")
                .checkServer("the refused drop changes no mapping", context ->
                        TaskThirtyThreeWorldFixture.slotZeroLanes(context).equals(context.get("task33.lanesBeforeDrop")))
                .check("an Endpoint owned elsewhere cannot be released here", context -> !context.el("#processing_release").isActive()
                        && !context.el("#processing_release").isVisible())
                .screenshot("ui-processing-drop-occupied")
                .step("record Claim conflict and refused drop evidence", context -> {
                    // Attachments are grouped per step; this record supersedes the earlier one for this case.
                    TaskThirtyThreeScenarioSupport.attach(context, "ui.reject-claim-conflict");
                    context.attach("claimConflict", context.get("task33.claimConflict"));
                    context.attach("claimConflictResult", context.get("task33.claimConflictResult"));
                    context.attach("endpointIdentity", context.get("task33.claimConflictIdentity"));
                    context.attach("processingOccupiedDrop", "refused");
                })
                // A Federation Pattern Provider is a domain node: placing one renews the domain, so the open
                // workspace goes stale as designed and is opened again.
                .server("place a second Federation Pattern Provider", TaskThirtyThreeWorldFixture::placeSecondProvider)
                .waitUntilServer("second Provider joins the main network", TaskThirtyThreeWorldFixture::secondProviderReady)
                .serverGet("record second Provider position", "task33.secondProviderAt",
                        TaskThirtyThreeWorldFixture::secondProviderPosition)
                .closeScreen()
                .server("open the renewed domain from the Router", TaskThirtyThreeWorldFixture::openRouter)
                .awaitScreen(com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen.class)
                .awaitModularUI()
                .click("#tab_mapping")
                .waitUntil("the wires view stacks both Providers", context -> context.all(".processing-provider-text").size() == 2
                        && context.all(".processing-provider-text").stream().anyMatch(header -> header.text().contains(
                                context.<String>get("task33.secondProviderAt")) && header.text().contains("1/9 slots")))
                .check("exactly one Provider is being edited", context -> context.all(".processing-provider").stream()
                        .filter(header -> header.hasClass("selected")).count() == 1
                        && context.all(".processing-provider.selected .processing-provider-text").size() == 1)
                .screenshot("ui-processing-two-providers")
                .step("select the other Provider from its header", context -> {
                    var header = context.all(".processing-provider").stream()
                            .filter(candidate -> !candidate.hasClass("selected")).findFirst().orElseThrow();
                    var editing = context.el(".processing-provider.selected .processing-provider-text").text();
                    context.put("task33.otherProviderHeader", context.all(".processing-provider-text").stream()
                            .map(text -> text.text()).filter(text -> !text.equals(editing)).findFirst().orElseThrow());
                    var bounds = header.bounds();
                    context.input().mouseDown(bounds.centerX(), bounds.centerY(), 0);
                    context.input().mouseUp(bounds.centerX(), bounds.centerY(), 0);
                })
                .waitUntil("the other Provider becomes the one being edited", context ->
                        context.all(".processing-provider.selected .processing-provider-text").stream()
                                .anyMatch(header -> header.text().equals(context.get("task33.otherProviderHeader"))))
                .check("its patterns now have editable ports", context -> context.elOpt("#processing_port_0").isPresent())
                .closeScreen()
                .server("remove the second Provider", TaskThirtyThreeWorldFixture::removeSecondProvider);
    }
}
