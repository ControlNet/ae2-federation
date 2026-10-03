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
                // An Endpoint is selected on the topology like a network, and its details fill the aside.
                .waitUntil("the Endpoint is a node on the graph", context -> context.all(".graph-node-endpoint").size() == 1)
                .step("select the Endpoint node", context -> TaskThirtyThreeScenarioSupport.selectEndpointNode(context, "10, -57, 13"))
                .waitUntil("the Endpoint panel replaces the network's", context -> context.el("#endpoint_detail").isVisible()
                        && context.el("#network_title").text().contains("10, -57, 13"))
                .check("the topology stays open", context -> context.el("#page_overview").isVisible())
                .hover("#endpoint_detail")
                .waitForTextContains("#endpoint_fact_mode", "Federated")
                .server("record live Endpoint identity", context -> {
                    context.put("task33.endpointId", TaskThirtyThreeWorldFixture.endpointId(context));
                    context.put("task33.nativeNetwork", TaskThirtyThreeWorldFixture.endpointNativeNetwork(context));
                })
                .check("the caption's tooltip names the full live endpoint identity", context ->
                        TaskThirtyThreeScenarioSupport.endpointIdentity(context).contains(context.<String>get("task33.endpointId")))
                .check("the panel keeps no identity and ownership diagnostics", context ->
                        context.all("#endpoint_identity").isEmpty() && context.all("#endpoint_fact_return").isEmpty()
                                && context.all("#endpoint_fact_claim").isEmpty())
                .checkTextContains("#endpoint_fact_face", "Up")
                .checkTextContains("#endpoint_fact_mode", "Federated")
                .check("the panel names the actual native network", context -> context.el("#endpoint_fact_native").text()
                        .contains(TaskThirtyThreeScenarioSupport.networkTag(context.get("task33.nativeNetwork"))))
                .check("the panel lists the patterns in effect by their output, with their Provider", context ->
                        !context.all(".endpoint-pattern").isEmpty()
                                && context.el("#network_links_heading").text().startsWith("Active patterns (")
                                && context.el(".endpoint-pattern-name").text().startsWith("#")
                                && !context.el(".endpoint-pattern-name").text().contains("Processing Pattern")
                                && context.el(".endpoint-pattern-provider").text().startsWith("Provider ")
                                && !context.all(".endpoint-pattern-icon").isEmpty())
                .check("the panel maps the Endpoint's place", context -> context.el("#network_location").isVisible()
                        && context.el("#network_highlight").isActive())
                .step("record Endpoint evidence", context -> {
                    TaskThirtyThreeScenarioSupport.attach(context, "ui.endpoint");
                    TaskThirtyThreeScenarioSupport.attachEndpoint(context);
                    context.attach("endpointId", context.get("task33.endpointId"));
                    context.attach("endpointMode", "FEDERATED");
                })
                .step("reveal the Endpoint panel", context -> TaskThirtyThreeScenarioSupport.revealInAside(context, "#network_location"))
                .frames(2)
                .screenshot("ui-endpoint-detail")
                .hover("#network_highlight")
                .step("another highlight is already running", context -> {
                    space.controlnet.ae2federation.client.WorldHighlight.clear();
                    space.controlnet.ae2federation.client.WorldHighlight.show("minecraft:overworld", java.util.List.of(
                            new space.controlnet.ae2federation.client.policy.BlockMarks.Mark(0, -60, 0)), 0xFFFFFF);
                })
                .step("highlight the Endpoint in the world", context ->
                        TaskThirtyThreeScenarioSupport.activateNavigation(context, "#network_highlight"))
                .waitUntil("the Endpoint's block is outlined beside the earlier highlight", context ->
                        space.controlnet.ae2federation.client.WorldHighlight.activeGroups() == 2
                                && space.controlnet.ae2federation.client.WorldHighlight.activeBlocks() == 2)
                .waitUntil("the button reads as pressed while its outline runs", context ->
                        context.all("#network_highlight.selected").size() == 1)
                .checkTextContains("#network_highlight", "Highlight Endpoint")
                .step("press the highlight again", context ->
                        TaskThirtyThreeScenarioSupport.activateNavigation(context, "#network_highlight"))
                .waitUntil("pressing it again ends only its own outline and releases the button", context ->
                        space.controlnet.ae2federation.client.WorldHighlight.activeGroups() == 1
                                && space.controlnet.ae2federation.client.WorldHighlight.activeBlocks() == 1
                                && context.all("#network_highlight.selected").isEmpty())
                .server("record actual contextual navigation endpoints", TaskThirtyThreeWorldFixture::recordEndpointNavigation)
                .step("reveal the owner navigation", context -> TaskThirtyThreeScenarioSupport.revealInAside(context, "#graph_open"))
                .frames(2)
                .checkTextContains("#graph_open", "Owner mappings")
                .hover("#graph_open")
                .step("open owner mapping relationship", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#graph_open"))
                .waitUntil("server-confirmed navigation opens mappings", context -> context.el("#page_mapping").isVisible())
                .waitUntil("mapping selects the actual endpoint with its mapped slot", context -> context.el(
                        "#processing_endpoint_" + context.<String>get("navigation.target").replaceAll("[^a-zA-Z0-9_-]", "_"))
                        .as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class).hasClass("selected")
                        && context.el("#mapping_toggle").text().equals("Unmap #1"))
                .screenshot("ui-endpoint-owner-mapping")
                .checkServer("contextual navigation does not mutate mappings or claim", TaskThirtyThreeWorldFixture::endpointNavigationReadOnly)
                .closeScreen()
                .checkServer("direct Endpoint opens the domain its Federation face joins", TaskThirtyThreeWorldFixture::endpointOpensItsFaceDomain)
                .server("open direct Endpoint inspection", TaskThirtyThreeWorldFixture::openEndpoint)
                .awaitScreen(com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen.class)
                .awaitModularUI().frames(5)
                .waitUntil("device entrance opens the topology with its Endpoint selected", context ->
                        context.el("#page_overview").isVisible() && context.el("#endpoint_detail").isVisible()
                                && context.el("#network_title").text().contains("10, -57, 13"))
                .check("direct Endpoint opens at the full workspace size, centered", context -> {
                    var bounds = context.el("#domain_root").bounds();
                    var window = net.minecraft.client.Minecraft.getInstance().getWindow();
                    var size = space.controlnet.ae2federation.client.policy.WorkspaceSize.fit(
                            window.getGuiScaledWidth(), window.getGuiScaledHeight());
                    return Math.abs(bounds.width() - size.width()) <= 1 && Math.abs(bounds.height() - size.height()) <= 1
                            && Math.abs(bounds.centerX() - window.getGuiScaledWidth() / 2f) <= 1
                            && Math.abs(bounds.centerY() - window.getGuiScaledHeight() / 2f) <= 1;
                })
                .waitForTextContains("#endpoint_fact_mode", "Federated")
                .check("direct inspection preserves native network identity", context -> context.el("#endpoint_fact_native").text()
                        .contains(TaskThirtyThreeScenarioSupport.networkTag(context.get("task33.nativeNetwork"))))
                .check("the Endpoint panel keeps its controls in the workspace", context ->
                        TaskThirtyThreeScenarioSupport.withinWorkspace(context, "#graph_open", "#network_highlight", "#ack_status"))
                .check("the domain's Endpoint offers its owner's mappings", context -> context.el("#graph_open").isActive())
                .screenshot("ui-endpoint-direct").closeScreen()
                .server("place another production Endpoint", TaskThirtyThreeWorldFixture::placeSecondObservedEndpoint)
                .serverTicks(4)
                .waitUntilServer("both real endpoints are observed in the same domain", TaskThirtyThreeWorldFixture::secondEndpointObserved)
                .server("open fresh domain context", TaskThirtyThreeWorldFixture::openRouter)
                .awaitScreen(com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen.class)
                .awaitModularUI()
                .waitUntil("each endpoint of the domain is a node on the graph", context -> context.all(".graph-node-endpoint").size() == 2)
                .step("select the second Endpoint", context -> TaskThirtyThreeScenarioSupport.selectEndpointNode(context, "12, -57, 13"))
                .waitUntil("the panel shows the second Endpoint's identity", context -> TaskThirtyThreeScenarioSupport
                        .endpointIdentity(context).contains(context.<String>get("endpoint.secondId")))
                .checkTextContains("#network_title", "12, -57, 13")
                .checkTextContains("#endpoint_fact_owner", "Unclaimed")
                .check("an unclaimed Endpoint has no patterns", context -> context.all(".endpoint-pattern").isEmpty())
                .check("unclaimed endpoint has no invented owner navigation", context -> !context.el("#graph_open").isActive())
                .screenshot("ui-endpoint-second-selected")
                .step("return to the original Endpoint", context -> TaskThirtyThreeScenarioSupport.selectEndpointNode(context, "10, -57, 13"))
                .waitUntil("the original identity is shown again", context -> TaskThirtyThreeScenarioSupport
                        .endpointIdentity(context).contains(context.<String>get("task33.endpointId")))
                .step("select the network whose Provider maps the endpoint", context -> TaskThirtyThreeScenarioSupport.selectNetworkCard(
                        context, context.get("navigation.consumer")))
                .waitUntil("a network selection replaces the Endpoint panel", context -> !context.el("#endpoint_detail").isVisible()
                        && context.el("#network_title").text().contains(TaskThirtyThreeScenarioSupport.networkTag(
                                context.get("navigation.consumer"))))
                .check("the network's devices name its endpoints", context ->
                        TaskThirtyThreeScenarioSupport.tooltipContains(context, "#graph_open", "Processing endpoints: 1"))
                .screenshot("ui-endpoint-network-nodes")
                .closeScreen()
                .server("open a real Endpoint before its first network tick", TaskThirtyThreeWorldFixture::openNewEndpointBeforeIdentitySettles)
                .awaitScreen(com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen.class)
                .awaitModularUI().frames(5)
                .waitForTextContains("#ack_status", "Opened before network identity was confirmed")
                .waitUntil("the local Endpoint is shown alone, selected", context -> context.el("#endpoint_detail").isVisible()
                        && context.el("#network_title").text().contains("14, -57, 16"))
                .check("unconfirmed entry is explicitly local and read-only", context ->
                        !context.el("#graph_open").isVisible() && context.el("#graph_selection").text().contains("Read-only")
                                && context.all(".policy-switch").isEmpty())
                .screenshot("ui-endpoint-unconfirmed")
                .closeScreen()
                .waitUntilServer("isolated network confirms without an editable domain", TaskThirtyThreeWorldFixture::isolatedEndpointReady)
                .server("inspect the isolated Endpoint", TaskThirtyThreeWorldFixture::openIsolatedEndpoint)
                .awaitScreen(com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen.class)
                .awaitModularUI().frames(5)
                .waitForTextContains("#ack_status", "No domain with two networks")
                .waitUntil("the isolated Endpoint is shown alone, selected", context -> context.el("#endpoint_detail").isVisible()
                        && context.el("#network_title").text().contains("14, -57, 16"))
                .check("the local panel preserves actual endpoint and network identities", context ->
                        TaskThirtyThreeScenarioSupport.endpointIdentity(context).contains(context.<String>get("endpoint.isolatedId"))
                                && context.el("#endpoint_fact_native").text().contains(
                                        TaskThirtyThreeScenarioSupport.networkTag(context.get("endpoint.isolatedNetwork"))))
                .check("domain-only actions are unavailable without a unique domain", context ->
                        !context.el("#graph_open").isVisible() && context.all(".endpoint-pattern").isEmpty())
                .screenshot("ui-endpoint-no-domain")
                .check("isolated device cannot edit a policy", context -> context.all(".policy-switch").isEmpty()
                        && !context.el("#pair_editor").isVisible())
                .closeScreen();
    }
}
