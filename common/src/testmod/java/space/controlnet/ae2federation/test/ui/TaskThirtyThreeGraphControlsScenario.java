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
                .server("record the fixture's networks", TaskThirtyThreeWorldFixture::recordNetworks)
                .checkBounds("#domain_graph", bounds -> bounds.width() > 180 && bounds.height() > 150)
                .click("#graph_zoom_in").click("#graph_zoom_out").click("#graph_fit")
                .check("graph remains visible after controls", context -> context.el("#domain_graph").isVisible())
                .check("both member networks are drawn", context -> context.all(".graph-node-member").size() == 2)
                .check("two-member domain opens the pair editor", context -> context.el("#pair_editor").isVisible())
                .step("record zoom before network search", context -> context.put("graph.searchScale",
                        context.el("#domain_graph").as(com.lowdragmc.lowdraglib2.gui.ui.elements.GraphView.class).getScale()))
                .typeInto("#graph_search", "not an existing network")
                .check("graph search shows no-result message", context -> context.el("#graph_search_empty").isVisible())
                .check("search does not hide graph nodes", context -> context.all(".graph-node-member").stream()
                        .allMatch(card -> card.isVisible()))
                .typeInto("#graph_search", "Network")
                .check("matching search clears the no-result message", context -> !context.el("#graph_search_empty").isVisible())
                .check("search preserves zoom", context -> Math.abs(
                        context.el("#domain_graph").as(com.lowdragmc.lowdraglib2.gui.ui.elements.GraphView.class).getScale()
                                - context.<Float>get("graph.searchScale")) < 0.0001f)
                .hover("#domain_title").frames(3).screenshot("ui-graph-network-search")
                .typeInto("#graph_search", "").blur()
                .click("#graph_fit")
                .step("select the Provider host network", context -> TaskThirtyThreeScenarioSupport.selectNetworkCard(
                        context, context.get("net.providerHost")))
                .waitUntil("network detail replaces the pair editor", context -> context.el("#network_detail").isVisible()
                        && context.el("#network_title").text().equals(TaskThirtyThreeScenarioSupport.networkName(
                                context.get("net.providerHost"))))
                .checkTextContains("#graph_selection", "Pattern providers: 1")
                .waitForTextContains("#network_stat_energy", " AE (")
                .checkTextContains("#network_stat_cpus", "Crafting CPUs ")
                .checkTextContains("#network_stat_channels", "Channels ")
                .waitForTextContains("#network_stat_identity", "Identity settled")
                .check("identity location is shown", context -> context.el("#network_stat_place").text().startsWith("Location Overworld "))
                .check("card shows live figures", context -> context.all(".graph-node-member").stream()
                        .allMatch(card -> card.as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class).allChildrenStream()
                                .filter(com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement.class::isInstance)
                                .anyMatch(child -> ((com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement) child).getText()
                                        .getString().contains(" types · CPU "))))
                .waitUntil("settled network can be renamed", context -> context.el("#network_rename").isActive())
                .screenshot("ui-graph-network-detail")
                .click("#network_rename")
                .waitUntil("rename field opens", context -> context.el("#network_rename_field").isVisible())
                .typeInto("#network_rename_field", "North Storage").blur()
                .hover("#network_rename_save")
                .step("save the name", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#network_rename_save"))
                .waitUntilServer("server stores the new name", context ->
                        TaskThirtyThreeWorldFixture.providerHostName(context).equals("North Storage"))
                .waitUntil("the renamed network shows its name", context ->
                        context.el("#network_title").text().equals("North Storage")
                                && !context.el("#network_rename_row").isVisible())
                .check("the card shows the new name", context -> context.all(".graph-node-member").stream()
                        .anyMatch(card -> card.as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class).allChildrenStream()
                                .filter(com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement.class::isInstance)
                                .anyMatch(child -> ((com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement) child).getText()
                                        .getString().equals("North Storage"))))
                .screenshot("ui-graph-network-renamed")
                .click("#network_rename")
                .typeInto("#network_rename_field", "").blur()
                .hover("#network_rename_save")
                .step("save the name", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#network_rename_save"))
                .waitUntilServer("an empty name restores the default", context ->
                        TaskThirtyThreeWorldFixture.providerHostName(context).isEmpty())
                .waitUntil("the default name returns", context -> context.el("#network_title").text()
                        .equals(TaskThirtyThreeScenarioSupport.networkName(context.get("net.providerHost"))))
                .step("record rename evidence", context -> context.put("task33.renamed", "North Storage"))
                .hover("#graph_open")
                .step("open the network's devices", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#graph_open"))
                .waitUntil("Provider network opens mapping", context -> context.el("#page_mapping").isVisible())
                .click("#tab_overview")
                .step("select the Endpoint network", context -> TaskThirtyThreeScenarioSupport.selectNetworkCard(
                        context, context.get("net.endpoint")))
                .waitUntil("Endpoint network detail is shown", context -> context.el("#network_title").text()
                        .equals(TaskThirtyThreeScenarioSupport.networkName(context.get("net.endpoint"))))
                .hover("#graph_open")
                .step("open the network's devices", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#graph_open"))
                .waitUntil("Endpoint network opens diagnostics", context -> context.el("#page_diagnostics").isVisible())
                .waitForTextContains("#endpoint_detail", "Configured mode: Federated")
                .click("#tab_overview")
                .hover(".network-link")
                .step("open the pair from the network's links", context ->
                        TaskThirtyThreeScenarioSupport.activateNavigation(context, ".network-link"))
                .waitUntil("pair editor returns", context -> context.el("#pair_editor").isVisible())
                .serverGet("record current policy revision", "task33.policyBefore",
                        TaskFifteenWorldFixture::policyRevision)
                .checkTextContains("#policy_section_title_0", " uses ")
                .checkTextContains("#policy_section_title_1", " uses ")
                .check("the two sections are the two directions", context -> !context.el("#policy_section_title_0").text()
                        .equals(context.el("#policy_section_title_1").text()))
                .screenshot("ui-policy-direction")
                .click(TaskFifteenScenarioSupport.STORAGE_SWITCH)
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
                    context.attach("networkRename", context.get("task33.renamed"));
                    context.attach("worldCaptures", "router-overview,bridge-north,provider-host,endpoint-faces");
                })
                .server("policy detail reads preserve backend counters", TaskThirtyThreeWorldFixture::verifyPolicySnapshotReads)
                .server("configure a disabled crafting rule", TaskThirtyThreeWorldFixture::installBrowserRule)
                .waitUntil("pair editor shows the configured rule off", context ->
                        TaskThirtyThreeScenarioSupport.ruleState(context, "crafting").startsWith("Off · revision"))
                .check("the rule's switch is off", context -> !context.el(TaskThirtyThreeScenarioSupport.ruleControl(context,
                        "switch", context.get("net.providerHost"), "crafting"))
                        .as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class).hasClass("on"))
                .check("pair editor stays inside the workspace", context -> TaskThirtyThreeScenarioSupport.withinWorkspace(
                        context, "#topology_aside", TaskFifteenScenarioSupport.STORAGE_SWITCH))
                .step("reveal the crafting rule", context -> TaskThirtyThreeScenarioSupport.revealRule(context, "crafting"))
                .frames(2).screenshot("ui-policy-runtime-disabled")
                .server("enable rule without required request permission", TaskThirtyThreeWorldFixture::removeBrowserRuleOperation)
                .waitUntil("missing operation is explained", context -> TaskThirtyThreeScenarioSupport.ruleState(context, "crafting")
                        .contains("required operation is not allowed: crafting requests"))
                .step("reveal the crafting rule", context -> TaskThirtyThreeScenarioSupport.revealRule(context, "crafting"))
                .frames(2).screenshot("ui-policy-runtime-operation-denied")
                .server("observe the actual unavailable crafting backend", TaskThirtyThreeWorldFixture::observeUnavailableCraftingBackend)
                .waitUntil("missing provider is explained", context -> TaskThirtyThreeScenarioSupport.ruleState(context, "crafting")
                        .contains("Last backend check: No active native crafting provider."))
                .frames(2).screenshot("ui-policy-runtime-backend-missing")
                .server("place a real native crafting provider", TaskThirtyThreeWorldFixture::placeNativeCraftingProvider)
                .waitUntilServer("native provider is active but its grid has no CPU", TaskThirtyThreeWorldFixture::nativeProviderHasNoCpu)
                .waitUntil("missing CPU is explained", context -> TaskThirtyThreeScenarioSupport.ruleState(context, "crafting")
                        .contains("Last backend check: No native crafting CPU."))
                .check("new backend observation replaces the missing-provider explanation", context ->
                        !TaskThirtyThreeScenarioSupport.ruleState(context, "crafting").contains("No active native crafting provider"))
                .frames(2).screenshot("ui-policy-runtime-cpu-missing")
                .server("enable a real reverse crafting rule", context -> TaskThirtyThreeWorldFixture.setReverseCraftingRule(context, true))
                .waitUntil("cycle is explained", context -> TaskThirtyThreeScenarioSupport.ruleState(context, "crafting")
                        .contains("Last backend check: Crafting dependencies form a cycle."))
                .check("cycle observation replaces CPU explanation", context ->
                        !TaskThirtyThreeScenarioSupport.ruleState(context, "crafting").contains("No native crafting CPU"))
                .frames(2).screenshot("ui-policy-runtime-cycle")
                .server("break the reverse crafting relationship", context -> TaskThirtyThreeWorldFixture.setReverseCraftingRule(context, false))
                .waitUntil("missing CPU is explained again", context -> TaskThirtyThreeScenarioSupport.ruleState(context, "crafting")
                        .contains("Last backend check: No native crafting CPU."))
                .check("breaking the cycle removes its stale explanation", context ->
                        !TaskThirtyThreeScenarioSupport.ruleState(context, "crafting").contains("form a cycle"))
                .server("disable the observed rule and reject its old diagnostic", TaskThirtyThreeWorldFixture::disableObservedCraftingRule)
                .waitUntil("disabled rule shows its new revision", context -> TaskThirtyThreeScenarioSupport.ruleState(context, "crafting")
                        .equals("Off · revision " + context.get("runtime.disabledRevision")))
                .check("disabled revision has no leftover backend reason", context ->
                        !TaskThirtyThreeScenarioSupport.ruleState(context, "crafting").contains("Last backend check"))
                .frames(2).screenshot("ui-policy-runtime-revision-invalidated")
                .checkServer("displaying a new revision does not authorize an outdated edit", TaskThirtyThreeWorldFixture::displayedRevisionDoesNotGrantAuthority)
                .waitForTextContains("#ack_status", "Rule changed elsewhere")
                .check("refused switch keeps the newer rule off", context ->
                        TaskThirtyThreeScenarioSupport.ruleState(context, "crafting").startsWith("Off · revision"))
                .screenshot("ui-policy-switch-conflict")
                .closeScreen()
                .server("remove the fixture's native energy source", TaskThirtyThreeWorldFixture::removeEndpointEnergySource)
                .waitUntilServer("real backend reports no extractable native energy source", TaskThirtyThreeWorldFixture::energySourceUnavailable)
                .server("open fresh policy context after source removal", TaskThirtyThreeWorldFixture::openRouter)
                .awaitScreen(com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen.class)
                .awaitModularUI()
                .awaitElement("#policy_section_title_0")
                .waitUntil("energy source absence is explained", context -> TaskThirtyThreeScenarioSupport.ruleState(context, "me_power")
                        .contains("Last backend check: No local public energy source allows extraction."))
                .step("reveal the energy rule", context -> TaskThirtyThreeScenarioSupport.revealRule(context, "me_power"))
                .frames(2).screenshot("ui-policy-runtime-energy-source-missing")
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
