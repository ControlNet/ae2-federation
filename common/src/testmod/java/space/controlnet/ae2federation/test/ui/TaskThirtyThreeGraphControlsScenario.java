package space.controlnet.ae2federation.test.ui;

import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;

@LDLRegisterClient(name = "ui.graph-controls", group = "ae2federation", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class TaskThirtyThreeGraphControlsScenario implements UIScenario {
    @Override
    public void configure(ScenarioOptions options) {
        TaskThirtyThreeScenarioSupport.configure(options, 3);
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        TaskThirtyThreeScenarioSupport.open(scenario, TaskThirtyThreeScenarioSupport.Entrance.ROUTER)
                .checkBounds("#domain_graph", bounds -> bounds.width() > 180 && bounds.height() > 150)
                .click("#graph_zoom_in").click("#graph_zoom_out").click("#graph_fit")
                .click("#physical_layer_toggle").click("#physical_layer_toggle")
                .click("#capability_layer_toggle").click("#capability_layer_toggle")
                .check("graph remains visible after controls", context -> context.el("#domain_graph").isVisible())
                .step("record zoom before object search", context -> context.put("graph.searchScale",
                        context.el("#domain_graph").as(com.lowdragmc.lowdraglib2.gui.ui.elements.GraphView.class).getScale()))
                .typeInto("#graph_search", "not an existing object")
                .check("graph search shows no-result message", context -> context.el("#graph_search_empty").isVisible())
                .check("search does not hide graph nodes", context -> context.el(".graph-node-provider").isVisible())
                .typeInto("#graph_search", "10, -57, 10")
                .click(".graph-object-row")
                .checkTextContains("#graph_selection", "10, -57, 10")
                .checkTextContains("#graph_selection", "Last target check: Policy denied access")
                .check("object navigation preserves zoom", context -> Math.abs(
                        context.el("#domain_graph").as(com.lowdragmc.lowdraglib2.gui.ui.elements.GraphView.class).getScale()
                                - context.<Float>get("graph.searchScale")) < 0.0001f)
                .hover("#domain_title").frames(3).screenshot("ui-graph-object-search")
                .typeInto("#graph_search", "").blur()
                .click("#graph_fit")
                .click(".graph-node-provider").hover("#graph_open")
                .step("activate graph object navigation", context -> {
                    var bounds = context.el("#graph_open").bounds();
                    context.input().mouseDown(bounds.centerX(), bounds.centerY(), 0);
                    context.input().mouseUp(bounds.centerX(), bounds.centerY(), 0);
                })
                .waitUntil("graph provider opens mapping", context -> context.el("#page_mapping").isVisible())
                .click("#tab_overview")
                .click(".graph-node-endpoint").hover("#graph_open")
                .step("activate graph object navigation", context -> {
                    var bounds = context.el("#graph_open").bounds();
                    context.input().mouseDown(bounds.centerX(), bounds.centerY(), 0);
                    context.input().mouseUp(bounds.centerX(), bounds.centerY(), 0);
                })
                .waitUntil("graph endpoint opens diagnostics", context -> context.el("#page_diagnostics").isVisible())
                .waitForTextContains("#endpoint_detail", "Configured mode: Federated")
                .click("#tab_overview")
                .serverGet("record current policy revision", "task33.policyBefore",
                        TaskFifteenWorldFixture::policyRevision)
                .click("#tab_policy").frames(2)
                .checkTextContains("#policy_direction", " uses ")
                .checkTextContains("#policy_direction", " from ")
                .screenshot("ui-policy-direction")
                .click("#policy_toggle")
                .waitUntilServer("real policy revision advances", context ->
                        TaskFifteenWorldFixture.policyRevision(context)
                                > context.<Long>get("task33.policyBefore"))
                .waitForTextContains("#ack_status", "Server accepted revision")
                .server("record authoritative policy result", context -> {
                    context.put("task33.policyRevision", Long.toString(TaskFifteenWorldFixture.policyRevision(context)));
                    context.put("task33.policyEnabled", Boolean.toString(TaskFifteenWorldFixture.policyEnabled(context)));
                })
                .step("record graph control evidence", context -> {
                    TaskThirtyThreeScenarioSupport.attach(context, "ui.graph-controls");
                    context.attach("policyRevision", context.get("task33.policyRevision"));
                    context.attach("policyEnabled", context.get("task33.policyEnabled"));
                    context.attach("worldCaptures", "router-overview,bridge-north,provider-host,endpoint-faces");
                })
                .server("policy detail reads preserve backend counters", TaskThirtyThreeWorldFixture::verifyPolicySnapshotReads)
                .server("configure another real rule for the browser", TaskThirtyThreeWorldFixture::installBrowserRule)
                .hover("#policy_browse")
                .step("open configured rules", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#policy_browse"))
                .awaitElement("#policy_rule_search")
                .typeInto("#policy_rule_search", "no such rule")
                .check("rule search has an explicit empty result", context -> context.el("#policy_rule_empty").isVisible())
                .typeInto("#policy_rule_search", "enabled")
                .check("enabled search excludes disabled rule", context -> context.all(".policy-rule-row").size() == 1
                        && context.el(".policy-rule-row").text().contains("Storage"))
                .typeInto("#policy_rule_search", "disabled")
                .check("disabled search excludes enabled rule", context -> context.all(".policy-rule-row").size() == 1
                        && context.el(".policy-rule-row").text().contains("Crafting"))
                .typeInto("#policy_rule_search", "Crafting")
                .waitUntil("rule search finds the configured capability", context -> context.all(".policy-rule-row").size() == 1)
                .screenshot("ui-policy-rule-browser")
                .hover(".policy-rule-row")
                .step("select the complete rule key", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, ".policy-rule-row"))
                .waitForTextContains("#rule_value", "Crafting: disabled")
                .checkTextContains("#rule_value", "Runtime: disabled by configuration.")
                .check("policy detail has a bounded scroll area", context -> TaskThirtyThreeScenarioSupport.withinWorkspace(
                        context, "#policy_detail_scroll", "#policy_toggle", "#policy_browse"))
                .screenshot("ui-policy-runtime-disabled")
                .check("server confirms both rule endpoints together", context ->
                        context.el("#consumer_next").value().equals(context.get("rule.consumer"))
                                && context.el("#provider_next").value().equals(context.get("rule.provider")))
                .hover("#policy_browse")
                .step("reopen current rule", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#policy_browse"))
                .awaitElement("#policy_browser_close")
                .check("browser highlights the authoritative rule", context -> context.all(".policy-rule-row.selected").size() == 1
                        && context.el(".policy-rule-row.selected").text().contains("Crafting"))
                .hover("#policy_browser_close")
                .step("close rule browser", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#policy_browser_close"))
                .server("enable rule without required request permission", TaskThirtyThreeWorldFixture::removeBrowserRuleOperation)
                .waitForTextContains("#rule_value", "required operation is not allowed: crafting requests")
                .screenshot("ui-policy-runtime-operation-denied")
                .server("observe the actual unavailable crafting backend", TaskThirtyThreeWorldFixture::observeUnavailableCraftingBackend)
                .waitForTextContains("#rule_value", "Last backend check: No active native crafting provider.")
                .screenshot("ui-policy-runtime-backend-missing")
                .server("place a real native crafting provider", TaskThirtyThreeWorldFixture::placeNativeCraftingProvider)
                .waitUntilServer("native provider is active but its grid has no CPU", TaskThirtyThreeWorldFixture::nativeProviderHasNoCpu)
                .waitForTextContains("#rule_value", "Last backend check: No native crafting CPU.")
                .check("new backend observation replaces the missing-provider explanation", context ->
                        !context.el("#rule_value").text().contains("No active native crafting provider"))
                .screenshot("ui-policy-runtime-cpu-missing")
                .server("enable a real reverse crafting rule", context -> TaskThirtyThreeWorldFixture.setReverseCraftingRule(context, true))
                .waitForTextContains("#rule_value", "Last backend check: Crafting dependencies form a cycle.")
                .check("cycle observation replaces CPU explanation", context -> !context.el("#rule_value").text().contains("No native crafting CPU"))
                .screenshot("ui-policy-runtime-cycle")
                .server("break the reverse crafting relationship", context -> TaskThirtyThreeWorldFixture.setReverseCraftingRule(context, false))
                .waitForTextContains("#rule_value", "Last backend check: No native crafting CPU.")
                .check("breaking the cycle removes its stale explanation", context -> !context.el("#rule_value").text().contains("form a cycle"))
                .server("disable the observed rule and reject its old diagnostic", TaskThirtyThreeWorldFixture::disableObservedCraftingRule)
                .waitForTextContains("#rule_value", "Runtime: disabled by configuration.")
                .check("disabled revision has no leftover backend reason", context -> !context.el("#rule_value").text().contains("Last backend check"))
                .check("displayed rule revision matches the actual disabled record", context -> context.el("#rule_value").text()
                        .contains("(revision " + context.get("runtime.disabledRevision") + ")"))
                .screenshot("ui-policy-runtime-revision-invalidated")
                .checkServer("displaying a new revision does not authorize an outdated edit", TaskThirtyThreeWorldFixture::displayedRevisionDoesNotGrantAuthority)
                .closeScreen()
                .server("remove the fixture's native energy source", TaskThirtyThreeWorldFixture::removeEndpointEnergySource)
                .waitUntilServer("real backend reports no extractable native energy source", TaskThirtyThreeWorldFixture::energySourceUnavailable)
                .server("open fresh policy context after source removal", TaskThirtyThreeWorldFixture::openRouter)
                .awaitScreen(com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen.class)
                .awaitModularUI().click("#tab_policy")
                .hover("#policy_browse")
                .step("find the configured energy rule", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#policy_browse"))
                .awaitElement("#policy_rule_search")
                .typeInto("#policy_rule_search", "ME power")
                .waitUntil("energy search selects one actual rule", context -> context.all(".policy-rule-row").size() == 1)
                .hover(".policy-rule-row")
                .step("inspect the actual energy relationship", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, ".policy-rule-row"))
                .waitForTextContains("#rule_value", "Last backend check: No local public energy source allows extraction.")
                .screenshot("ui-policy-runtime-energy-source-missing")
                .click("#tab_overview").frames(2)
                .screenshot("ui-graph-controls").closeScreen()
                .server("position Router overview camera", TaskThirtyThreeWorldFixture::positionRouterOverviewCamera)
                .serverTicks(2).frames(2).screenshot("world-router-overview")
                .server("position ME Federation Bridge camera", TaskThirtyThreeWorldFixture::positionBridgeCamera)
                .serverTicks(2).frames(2).screenshot("world-multipart-bridge-north")
                .server("position Provider host camera", TaskThirtyThreeWorldFixture::positionProviderCamera)
                .serverTicks(2).frames(2).screenshot("world-provider-host")
                .server("position Endpoint face camera", TaskThirtyThreeWorldFixture::positionEndpointCamera)
                .serverTicks(2).frames(2).screenshot("world-endpoint-faces");
    }
}
