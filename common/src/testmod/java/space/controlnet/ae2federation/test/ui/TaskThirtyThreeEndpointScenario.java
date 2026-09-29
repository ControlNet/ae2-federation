package space.controlnet.ae2federation.test.ui;

import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;

@LDLRegisterClient(name = "ui.endpoint", group = "ae2federation", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class TaskThirtyThreeEndpointScenario implements UIScenario {
    @Override
    public void configure(ScenarioOptions options) {
        TaskThirtyThreeScenarioSupport.configure(options, 2);
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        TaskThirtyThreeScenarioSupport.open(scenario, TaskThirtyThreeScenarioSupport.Entrance.ROUTER)
                .click("#tab_diagnostics").frames(3)
                .hover("#endpoint_detail")
                .waitForTextContains("#endpoint_detail", "Configured mode: Federated")
                .checkTextContains("#endpoint_identity", "Claim epoch: 1")
                .server("record live Endpoint identity", context -> {
                    context.put("task33.endpointId", TaskThirtyThreeWorldFixture.endpointId(context));
                    context.put("task33.nativeNetwork", TaskThirtyThreeWorldFixture.endpointNativeNetwork(context));
                    context.put("task33.ownerInstance", TaskThirtyThreeWorldFixture.providerInstanceEpoch(context));
                })
                .check("diagnostics display the full live endpoint identity", context ->
                        context.el("#endpoint_identity").text().contains(context.<String>get("task33.endpointId")))
                .checkTextContains("#endpoint_detail", "Federation face: Up")
                .checkTextContains("#endpoint_detail", "Runtime mode:")
                .check("diagnostics display the actual native network and owner instance", context ->
                        context.el("#endpoint_detail").text().contains(context.<String>get("task33.nativeNetwork"))
                                && context.el("#endpoint_identity").text().contains("Owner instance epoch: " + context.get("task33.ownerInstance")))
                .step("record Endpoint evidence", context -> {
                    TaskThirtyThreeScenarioSupport.attach(context, "ui.endpoint");
                    context.attach("endpointId", context.get("task33.endpointId"));
                    context.attach("endpointMode", "FEDERATED");
                })
                .screenshot("ui-endpoint-detail")
                .server("record actual contextual navigation endpoints", TaskThirtyThreeWorldFixture::recordEndpointNavigation)
                .hover("#endpoint_mapping")
                .step("open owner mapping relationship", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#endpoint_mapping"))
                .waitUntil("server-confirmed navigation opens mappings", context -> context.el("#page_mapping").isVisible())
                .waitUntil("mapping selects the actual endpoint with its mapped slot", context -> context.el(
                        "#processing_endpoint_" + context.<String>get("navigation.target").replaceAll("[^a-zA-Z0-9_-]", "_"))
                        .as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class).hasClass("selected")
                        && context.el("#mapping_toggle").text().equals("Unmap #1"))
                .screenshot("ui-diagnostic-owner-mapping")
                .checkServer("contextual navigation does not mutate mappings or claim", TaskThirtyThreeWorldFixture::endpointNavigationReadOnly)
                .click("#tab_diagnostics")
                .hover("#endpoint_browse")
                .step("open endpoint observations", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#endpoint_browse"))
                .awaitElement("#endpoint_table_search")
                .typeInto("#endpoint_table_search", "no such endpoint")
                .check("endpoint table has an explicit no-result state", context -> context.el("#endpoint_table_empty").isVisible())
                .typeInto("#endpoint_table_search", "Ownership acquired")
                .check("table search matches the actual last claim result", context -> context.all(".endpoint-table-row").size() == 1)
                .typeInto("#endpoint_table_search", "10, -57, 13")
                .check("endpoint table shows the live observation", context -> context.all(".endpoint-table-row").size() == 1)
                .checkTextContains(".endpoint-table-reason", "Last claim request:")
                .check("endpoint table retains full identity in tooltips", context -> context.el(".endpoint-table-row")
                        .as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class).getStyle().tooltips().asList().stream()
                        .anyMatch(line -> line.getString().contains(context.<String>get("task33.endpointId"))))
                .checkTextContains(".endpoint-table-row .endpoint-table-position", "ME node ready")
                .check("table columns and reason fit", context -> TaskThirtyThreeScenarioSupport.wrappedTextFits(context,
                        ".endpoint-table-row .endpoint-table-position", ".endpoint-table-row .endpoint-table-modes",
                        ".endpoint-table-row .endpoint-table-owner", ".endpoint-table-reason"))
                .screenshot("ui-endpoint-observations")
                .hover(".endpoint-table-row")
                .step("inspect observed endpoint", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, ".endpoint-table-row"))
                .waitUntil("endpoint browser returns to inspector", context -> context.all("#endpoint_browser_close").isEmpty())
                .waitForTextContains("#endpoint_detail", "Configured mode: Federated")
                .closeScreen()
                .checkServer("direct Endpoint opens the domain its Federation face joins", TaskThirtyThreeWorldFixture::endpointOpensItsFaceDomain)
                .server("open direct Endpoint inspection", TaskThirtyThreeWorldFixture::openEndpoint)
                .awaitScreen(com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen.class)
                .awaitModularUI().frames(5)
                .check("device entrance selects diagnostics", context -> context.el("#page_diagnostics").isVisible())
                .check("direct Endpoint opens at the full workspace size, centered", context -> {
                    var bounds = context.el("#domain_root").bounds();
                    var window = net.minecraft.client.Minecraft.getInstance().getWindow();
                    var size = space.controlnet.ae2federation.client.policy.WorkspaceSize.fit(
                            window.getGuiScaledWidth(), window.getGuiScaledHeight());
                    return Math.abs(bounds.width() - size.width()) <= 1 && Math.abs(bounds.height() - size.height()) <= 1
                            && Math.abs(bounds.centerX() - window.getGuiScaledWidth() / 2f) <= 1
                            && Math.abs(bounds.centerY() - window.getGuiScaledHeight() / 2f) <= 1;
                })
                .waitForTextContains("#endpoint_detail", "Configured mode: Federated")
                .check("direct inspection preserves native network identity", context ->
                        context.el("#endpoint_detail").text().contains(context.<String>get("task33.nativeNetwork")))
                .check("the Endpoint workspace retains its controls and explanation", context ->
                        TaskThirtyThreeScenarioSupport.withinWorkspace(context, "#endpoint_next", "#endpoint_mapping",
                                "#ack_status", "#diagnostics_description"))
                .check("the domain's Endpoint offers its domain actions", context ->
                        context.el("#endpoint_next").isVisible() && !context.el("#endpoint_local").isVisible()
                                && context.el("#endpoint_browse").isActive() && context.el("#endpoint_mapping").isActive())
                .screenshot("ui-endpoint-direct").closeScreen()
                .server("place another production Endpoint", TaskThirtyThreeWorldFixture::placeSecondObservedEndpoint)
                .serverTicks(4)
                .waitUntilServer("both real endpoints are observed in the same domain", TaskThirtyThreeWorldFixture::secondEndpointObserved)
                .server("open fresh domain context", TaskThirtyThreeWorldFixture::openRouter)
                .awaitScreen(com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen.class)
                .awaitModularUI().click("#tab_diagnostics")
                .hover("#endpoint_browse")
                .step("compare observed endpoints", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#endpoint_browse"))
                .awaitElement("#endpoint_table_search")
                .waitUntil("table displays both real endpoint rows", context -> context.all(".endpoint-table-row").size() == 2)
                .check("table exposes both distinct endpoint identities", context -> {
                    var identities = context.all(".endpoint-table-row").stream()
                            .flatMap(row -> row.as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class).getStyle().tooltips().asList().stream())
                            .map(net.minecraft.network.chat.Component::getString).toList();
                    return identities.contains(context.<String>get("task33.endpointId"))
                            && identities.contains(context.<String>get("endpoint.secondId"));
                })
                .screenshot("ui-endpoint-comparison")
                .typeInto("#endpoint_table_search", "Unclaimed")
                .check("ownership filter excludes the owned endpoint", context -> context.all(".endpoint-table-row").size() == 1)
                .checkTextContains(".endpoint-table-row .endpoint-table-position", "12, -57, 13")
                .hover(".endpoint-table-row")
                .step("select a different real endpoint", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, ".endpoint-table-row"))
                .waitUntil("server selection updates the inspector identity", context -> context.el("#endpoint_identity").text()
                        .contains(context.<String>get("endpoint.secondId")))
                .checkTextContains("#endpoint_detail", "Position: 12, -57, 13")
                .checkTextContains("#endpoint_identity", "Unclaimed")
                .check("unclaimed endpoint has no invented owner navigation", context -> !context.el("#endpoint_mapping").isActive())
                .screenshot("ui-endpoint-second-selected")
                .hover("#endpoint_browse")
                .step("reopen observations for the new selection", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#endpoint_browse"))
                .awaitElement("#endpoint_table_search")
                .check("the second endpoint is the only highlighted row", context -> context.all(".endpoint-table-row.selected").size() == 1
                        && context.el(".endpoint-table-row.selected").as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class)
                                .getStyle().tooltips().asList().stream().anyMatch(line -> line.getString().equals(context.<String>get("endpoint.secondId"))))
                .typeInto("#endpoint_table_search", "10, -57, 13")
                .hover(".endpoint-table-row")
                .step("return to the original endpoint", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, ".endpoint-table-row"))
                .waitUntil("original identity is restored by server selection", context -> context.el("#endpoint_identity").text()
                        .contains(context.<String>get("task33.endpointId")))
                .hover("#endpoint_locate")
                .step("locate inspected endpoint on graph", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#endpoint_locate"))
                .waitUntil("diagnostic navigation opens graph", context -> context.el("#page_overview").isVisible())
                .waitUntil("graph selects the network whose Provider maps the endpoint", context -> context.el("#network_detail").isVisible()
                        && context.el("#network_title").text().contains(TaskThirtyThreeScenarioSupport.networkTag(
                                context.get("navigation.consumer"))))
                .check("the network's devices name its endpoints", context ->
                        TaskThirtyThreeScenarioSupport.tooltipContains(context, "#graph_open", "Processing endpoints: 1"))
                .check("each endpoint of the domain is a node on the graph", context -> context.all(".graph-node-endpoint").size() == 2)
                .waitUntil("located network is centered in screen space", context -> TaskThirtyThreeScenarioSupport.networkCardCentered(
                        context, context.get("navigation.consumer")))
                .screenshot("ui-diagnostics-locate-endpoint")
                .click("#graph_zoom_in")
                .click("#tab_diagnostics")
                .hover("#endpoint_locate")
                .step("locate after returning from a hidden graph", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#endpoint_locate"))
                .waitUntil("repeated locate centers the network after layout", context -> TaskThirtyThreeScenarioSupport.networkCardCentered(
                        context, context.get("navigation.consumer")))
                .closeScreen()
                .server("open a real Endpoint before its first network tick", TaskThirtyThreeWorldFixture::openNewEndpointBeforeIdentitySettles)
                .awaitScreen(com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen.class)
                .awaitModularUI().frames(5)
                .waitForTextContains("#ack_status", "Opened before network identity was confirmed")
                .checkTextContains("#endpoint_local", "Local endpoint • 14, -57, 16")
                .check("unconfirmed entry is explicitly local and read-only", context ->
                        !context.el("#endpoint_next").isVisible() && !context.el("#endpoint_browse").isActive()
                                && !context.el("#endpoint_mapping").isActive())
                .screenshot("ui-endpoint-unconfirmed")
                .closeScreen()
                .waitUntilServer("isolated network confirms without an editable domain", TaskThirtyThreeWorldFixture::isolatedEndpointReady)
                .server("inspect the isolated Endpoint", TaskThirtyThreeWorldFixture::openIsolatedEndpoint)
                .awaitScreen(com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen.class)
                .awaitModularUI().frames(5)
                .waitForTextContains("#ack_status", "No domain with two networks")
                .checkTextContains("#endpoint_local", "Local endpoint • 14, -57, 16")
                .check("isolated local device uses a read-only identity label", context ->
                        context.el("#endpoint_local").isVisible() && !context.el("#endpoint_next").isVisible())
                .check("local diagnostics preserve actual endpoint and network identities", context ->
                        context.el("#endpoint_identity").text().contains(context.<String>get("endpoint.isolatedId"))
                                && context.el("#endpoint_detail").text().contains(context.<String>get("endpoint.isolatedNetwork")))
                .check("domain-only actions are unavailable without a unique domain", context ->
                        !context.el("#endpoint_browse").isActive() && !context.el("#endpoint_mapping").isActive())
                .screenshot("ui-endpoint-no-domain")
                .click("#tab_overview").frames(3)
                .checkTextContains("#network_title", "No editable domain")
                .checkTextContains("#graph_selection", "No domain with two networks")
                .screenshot("ui-endpoint-no-domain-graph")
                .check("isolated device cannot edit a policy", context -> context.all(".policy-switch").isEmpty()
                        && !context.el("#pair_editor").isVisible())
                .closeScreen();
    }
}
