package space.controlnet.ae2federation.test.ui;

import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;

@LDLRegisterClient(name = "ui.mapping", group = "ae2federation", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class TaskThirtyThreeMappingScenario implements UIScenario {
    /** TEST-ONLY: frames timed once the showcase's network detail (graph and map preview) is on screen. */
    private static final int PERF_FRAMES = 120;
    private static final long[] FRAME_TIMING = new long[1];

    @Override
    public void configure(ScenarioOptions options) {
        TaskThirtyThreeScenarioSupport.configure(options, 3);
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        scenario.teardown("restore English viewport", context ->
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(context.mc().getWindow().getWindow(), 1600, 960));
        TaskThirtyThreeScenarioSupport.open(scenario, TaskThirtyThreeScenarioSupport.Entrance.ROUTER)
                .click("#tab_mapping").frames(3)
                .waitUntil("wires view lists patterns and the Endpoint", context -> context.el("#processing_graph").isVisible()
                        && context.all(".processing-endpoint").size() == 1
                        && !context.all("#processing_port_1").isEmpty())
                .check("empty slots have no port", context -> context.all("#processing_port_2").isEmpty())
                .check("the mapped Endpoint is shown as used by this Provider", context ->
                        context.el(".processing-endpoint-state").text().equals("Patterns mapped: 1"))
                .screenshot("ui-processing-wires")
                .drag("#processing_port_0", ".processing-endpoint")
                .waitUntilServer("dropping a port maps the pattern", TaskThirtyThreeWorldFixture::mappingAccepted)
                .waitForTextContains("#processing_status", "Mapping updated for pattern slot 0.")
                .step("select the new wire", context -> TaskThirtyThreeScenarioSupport.clickWire(context, "0"))
                .waitForTextContains("#processing_detail_title", "#0 ")
                .check("a mapped wire can be unlinked", context -> context.el("#processing_unlink").isActive())
                .screenshot("ui-processing-wire-selected")
                .hover("#processing_unlink")
                .step("unlink the wire", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#processing_unlink"))
                .waitUntilServer("unlinking removes only that wire", TaskThirtyThreeWorldFixture::slotZeroUnmapped)
                .checkServer("the Endpoint stays claimed by the remaining pattern", TaskThirtyThreeWorldFixture::endpointOwnedByProvider)
                .click(".processing-endpoint")
                .waitForText("#processing_fact_value_mapped", "#1")
                .waitForTextContains("#processing_fact_value_network", "Subnet ready")
                .check("the Endpoint detail names its claim epoch", context -> context.el("#processing_fact_value_claim").text()
                        .matches("(?s).*Claim epoch \\d+.*"))
                .check("an Endpoint still in use offers no release", context -> !context.el("#processing_release").isActive()
                        && !context.el("#processing_release").isVisible())
                .waitUntil("the Endpoint thumbnail samples loaded terrain", context -> context.el("#processing_preview_to .map-thumbnail")
                        .as(space.controlnet.ae2federation.client.menu.FederationMapPreview.class).sampledCells() > 0)
                .click("#processing_highlight")
                .check("the selected Endpoint is outlined in the world", context ->
                        space.controlnet.ae2federation.client.WorldHighlight.activeBlocks() == 1)
                .screenshot("ui-processing-endpoint-detail")
                .step("record processing wire evidence", context -> context.put("task33.processingWire", "mapped-unlinked"))
                .step("remember target identity", context -> context.put("target.initialId",
                        context.el(".processing-endpoint").as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class).getId()))
                // The header search filters Providers, patterns and Endpoints on this page.
                .typeInto("#graph_search", "no such device")
                .waitUntil("the search exposes its empty state", context -> context.el("#processing_search_empty").isVisible())
                .typeInto("#graph_search", "10, -57, 10")
                .waitUntil("coordinate search finds the Provider", context -> !context.el("#processing_search_empty").isVisible()
                        && context.el(".processing-provider-card").isVisible())
                .screenshot("ui-provider-search")
                .typeInto("#graph_search", "Gold")
                .waitUntil("search shows only the matching pattern", context ->
                        !context.el("#processing_pattern_0").isVisible() && context.el("#processing_pattern_1").isVisible())
                .typeInto("#graph_search", "").blur()
                .waitUntil("clearing the search shows every pattern again", context -> context.el("#processing_pattern_0").isVisible())
                .check("empty slots have no row", context -> context.all("#processing_pattern_2").isEmpty())
                // The click way of mapping: a pattern, then an Endpoint, then "Map".
                .click("#processing_pattern_0")
                .waitForTextContains("#processing_detail_title", "#0 ")
                .click(".processing-endpoint")
                .waitForText("#mapping_toggle", "Map #0 here")
                .waitUntil("the map action is ready", context -> context.el("#mapping_toggle").isActive())
                .hover("#mapping_toggle")
                .step("rapid mapping clicks show waiting and submit once", context -> {
                    var bounds = context.el("#mapping_toggle").bounds();
                    context.input().mouseDown(bounds.centerX(), bounds.centerY(), 0);
                    context.input().mouseUp(bounds.centerX(), bounds.centerY(), 0);
                    var waiting = context.el("#request_status").text();
                    if (!waiting.contains("Waiting for server confirmation") || context.el("#mapping_toggle").isActive()) {
                        throw new IllegalStateException("Missing request waiting text or duplicate-click guard");
                    }
                    context.attach("requestWaitingText", waiting);
                    context.input().mouseDown(bounds.centerX(), bounds.centerY(), 0);
                    context.input().mouseUp(bounds.centerX(), bounds.centerY(), 0);
                })
                .waitUntilServer("real Provider mapping accepted", TaskThirtyThreeWorldFixture::mappingAccepted)
                .waitForTextContains("#processing_status", "Mapping updated for pattern slot 0.")
                .server("record authoritative mapping identity", context -> {
                    context.put("task33.providerId", TaskThirtyThreeWorldFixture.providerId(context));
                    context.put("task33.mappingLanes", TaskThirtyThreeWorldFixture.mappingLanes(context));
                })
                .step("record mapping evidence", context -> {
                    TaskThirtyThreeScenarioSupport.attach(context, "ui.mapping");
                    context.attach("providerId", context.get("task33.providerId"));
                    context.attach("authoritativeMappingLanes", context.get("task33.mappingLanes"));
                    context.attach("processingWire", context.get("task33.processingWire"));
                })
                .screenshot("ui-mapping-accepted")
                .server("install component-rich and fluid processing patterns", TaskThirtyThreeWorldFixture::installRichPatterns)
                .typeInto("#graph_search", "Calibrated Diamond")
                .awaitElement("#processing_pattern_2")
                .click("#processing_pattern_2")
                .check("component name and long output amount survive projection", context -> {
                    var lines = java.util.List.of(context.el("#processing_detail_text").text().split("\n"));
                    return lines.stream().anyMatch(line -> line.contains("Calibrated Diamond") && line.contains("4,000,000,000"))
                            && lines.stream().anyMatch(line -> line.contains("Water") && line.contains("1.5 B"));
                })
                .typeInto("#graph_search", "Water")
                .waitUntil("search includes secondary and primary fluid outputs", context ->
                        context.el("#processing_pattern_2").isVisible() && context.el("#processing_pattern_3").isVisible()
                                && !context.el("#processing_pattern_0").isVisible())
                .check("fluid icon retains its AE resource and exact amount", context -> {
                    var row = context.el("#processing_pattern_3").as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class);
                    var texture = row.getChildren().stream().map(child -> child.getStyle().backgroundTexture())
                            .filter(com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture.class::isInstance)
                            .map(com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture.class::cast).findFirst().orElseThrow();
                    var resource = appeng.api.stacks.GenericStack.unwrapItemStack(texture.items[0]);
                    return resource != null && resource.amount() == 2500
                            && resource.what().equals(appeng.api.stacks.AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER));
                })
                .hover("#domain_title").frames(3)
                .screenshot("ui-generic-patterns")
                .typeInto("#graph_search", "").blur()
                .closeScreen()
                .server("sneak-right-click the Provider block", TaskThirtyThreeWorldFixture::sneakRightClickProvider)
                .awaitScreen(com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen.class)
                .awaitModularUI().frames(5)
                .check("a sneak-use opens the Provider screen too", context -> context.el("#provider_window").isVisible())
                .closeScreen()
                .serverGet("read the Provider's position", "task33.providerAt", TaskThirtyThreeWorldFixture::providerPositionQuery)
                .server("right-click the Provider block", TaskThirtyThreeWorldFixture::openProviderMapping)
                .awaitScreen(com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen.class)
                .awaitModularUI().frames(5)
                .waitUntil("right-click opens the Provider's own screen on this Provider", context ->
                        context.el("#provider_window").isVisible() && context.all("#page_overview").isEmpty()
                        && context.all(".processing-provider.selected .processing-provider-text").stream()
                                .anyMatch(header -> header.text().startsWith(context.<String>get("task33.providerAt") + " ")))
                .waitUntil("every pattern slot has a row holding its real slot", context -> java.util.stream.IntStream.range(0, 9)
                        .allMatch(slot -> !context.all("#processing_pattern_" + slot + " #pattern_slot_" + slot).isEmpty()))
                .check("only patterns have a port to wire", context -> !context.all("#processing_port_1").isEmpty()
                        && context.all("#processing_port_8").isEmpty())
                .check("the domain's Endpoint is on the canvas", context -> context.all(".processing-endpoint").size() == 1)
                .check("a pattern slot shows what the pattern makes, as AE2's does", context -> {
                    var slot = context.el("#pattern_slot_0").as(space.controlnet.ae2federation.client.menu.FederationPatternSlot.class);
                    return appeng.api.crafting.PatternDetailsHelper.isEncodedPattern(slot.getValue())
                            && slot.displayStack().is(net.minecraft.world.item.Items.DIAMOND);
                })
                .check("a fluid pattern shows its fluid", context -> {
                    var shown = appeng.api.stacks.GenericStack.unwrapItemStack(context.el("#pattern_slot_3")
                            .as(space.controlnet.ae2federation.client.menu.FederationPatternSlot.class).displayStack());
                    return shown != null && shown.what() instanceof appeng.api.stacks.AEFluidKey fluid
                            && fluid.getFluid() == net.minecraft.world.level.material.Fluids.WATER;
                })
                .check("the screen is the workspace's size", context -> {
                    var window = context.mc().getWindow();
                    var size = space.controlnet.ae2federation.client.policy.WorkspaceSize.fit(window.getGuiScaledWidth(),
                            window.getGuiScaledHeight());
                    var bounds = context.el("#provider_root").bounds();
                    return Math.round(bounds.width()) == size.width() && Math.round(bounds.height()) == size.height();
                })
                .waitUntil("the Lane's return buffer is listed as empty", context -> !context.all("#provider_return_state_0").isEmpty()
                        && context.el("#provider_return_state_0").text().equals("Empty"))
                .waitUntil("AE2 settings arrive from the server", context -> context.el("#setting_blocking")
                        .as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class).isActive())
                .screenshot("ui-provider-screen")
                // AE2's settings live on the screen: Blocking reaches the Provider and each of its Lanes.
                .click("#setting_blocking")
                .waitUntilServer("Blocking mode is on", TaskThirtyThreeWorldFixture::providerBlocking)
                .checkServer("every Lane blocks too", TaskThirtyThreeWorldFixture::providerLanesBlocking)
                .waitUntil("the Blocking button shows it is on", context -> context.el("#setting_blocking")
                        .as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class).hasClass("selected"))
                .click("#setting_blocking")
                .waitUntilServer("Blocking mode is off again", TaskThirtyThreeWorldFixture::providerNotBlocking)
                // Requests and the server's value travel separately, so a quick double click and a two-digit
                // priority settle instead of bouncing between the values in flight.
                .click("#setting_blocking").click("#setting_blocking")
                .serverTicks(40)
                .checkServer("a quick double click settles on off", TaskThirtyThreeWorldFixture::providerNotBlocking)
                .typeInto("#provider_priority", "12")
                .waitUntilServer("a two-digit priority reaches the Provider", TaskThirtyThreeWorldFixture::providerPriorityTwelve)
                .serverTicks(40)
                .checkServer("the priority stays at 12", TaskThirtyThreeWorldFixture::providerPriorityTwelve)
                .typeInto("#provider_priority", "7")
                .waitUntilServer("the typed priority reaches the Provider", TaskThirtyThreeWorldFixture::providerPrioritySeven)
                .typeInto("#provider_priority", "0").blur()
                .waitUntilServer("the priority is back to 0", TaskThirtyThreeWorldFixture::providerPriorityZero)
                // The slots are real menu slots: a pattern goes in with a click and comes back out with a shift-click.
                .server("give the player a pattern", TaskThirtyThreeWorldFixture::givePlayerPattern)
                .waitUntil("the pattern is in the player's inventory on the screen", context -> !context.el("#inventory_9")
                        .as(com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot.class).getSlot().getItem().isEmpty())
                .click("#inventory_9")
                .click("#pattern_slot_8")
                .waitUntilServer("a click puts the pattern into Provider slot 8", TaskThirtyThreeWorldFixture::providerSlotEightFilled)
                .waitUntil("slot 8's row gets a port once it holds a pattern", context -> !context.all("#processing_port_8").isEmpty())
                .screenshot("ui-provider-slot-filled")
                .click("#pattern_slot_8")
                .click("#inventory_9")
                .waitUntilServer("two clicks take the pattern back out", TaskThirtyThreeWorldFixture::providerSlotEightTakenBack)
                .waitUntil("slot 8's row loses its port again", context -> context.all("#processing_port_8").isEmpty())
                .server("shift-click the pattern in", TaskThirtyThreeWorldFixture::quickMovePlayerPattern)
                .waitUntilServer("a shift-click fills the first empty pattern slot",
                        TaskThirtyThreeWorldFixture::providerSlotFourQuickMoved)
                .waitUntil("the screen shows it in slot 4", context -> !context.all("#processing_port_4").isEmpty())
                .server("shift-click the pattern out", TaskThirtyThreeWorldFixture::quickMoveProviderPattern)
                .waitUntilServer("a shift-click moves it back to the player", TaskThirtyThreeWorldFixture::providerSlotFourReturned)
                .server("clear the copied pattern", TaskThirtyThreeWorldFixture::takePlayerPattern)
                .step("record Provider screen evidence", context -> {
                    context.attach("evidenceFor", "ui.mapping");
                    context.attach("providerScreen", "slots-9;blocking-toggled;priority-7;slot-click;quick-move");
                })
                .closeScreen()
                // The workspace still opens on this Provider; its Edit patterns button leads to the Provider's screen.
                .server("open the Provider's workspace", TaskThirtyThreeWorldFixture::openProviderWorkspace)
                .awaitScreen(com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen.class)
                .awaitModularUI().frames(5)
                .check("device entrance selects mapping", context -> context.el("#page_mapping").isVisible())
                .hover("#return_provider")
                .step("press Provider return navigation", context -> {
                    var bounds = context.el("#return_provider").bounds();
                    context.put("task33.returnPoint", new float[] { bounds.centerX(), bounds.centerY() });
                    context.input().mouseDown(bounds.centerX(), bounds.centerY(), 0);
                })
                .step("release after navigation replaces the screen", context -> {
                    var point = context.<float[]>get("task33.returnPoint");
                    context.input().mouseUp(point[0], point[1], 0);
                })
                .awaitModularUI()
                .waitUntil("Edit patterns opens the Provider's own screen", context -> !context.all("#provider_window").isEmpty()
                        && context.el("#provider_window").isVisible())
                .screenshot("ui-provider-return")
                .closeScreen()
                .server("open the Provider's workspace again", TaskThirtyThreeWorldFixture::openProviderWorkspace)
                .awaitScreen(com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen.class)
                .awaitModularUI().frames(5)
                .check("the workspace opens on the Provider's wires", context -> context.el("#page_mapping").isVisible())
                .screenshot("ui-provider-mapping")
                .waitUntilServer("native lane sends a real processing input", TaskThirtyThreeWorldFixture::dispatchRealWork)
                .checkServer("the send was recorded against the Endpoint's lane", TaskThirtyThreeWorldFixture::processingFlowObserved)
                .waitUntil("dots travel along the busy wire", context ->
                        space.controlnet.ae2federation.client.menu.FederationProcessingGraph.drawnWireDots() > 0)
                .step("select the busy wire", context -> TaskThirtyThreeScenarioSupport.clickWire(context, "1"))
                .waitUntil("the wire reports what its lane really sent", context -> context.el("#processing_fact_value_lane").text()
                        .matches("last 5 s: sent [1-9]\\d* · returned \\d+"))
                .screenshot("ui-processing-lane-flow")
                .check("the wire detail shows both ends", context -> context.el("#processing_end_from").isVisible()
                        && context.el("#processing_end_to").isVisible()
                        && context.el("#processing_from_label").text().startsWith("Provider @ ")
                        && context.el("#processing_to_label").text().startsWith("Endpoint @ "))
                .server("record the fixture's networks", TaskThirtyThreeWorldFixture::recordNetworks)
                .click("#tab_overview")
                .waitUntil("the Endpoint is a node on the graph", context -> context.all(".graph-node-endpoint").size() == 1)
                .waitUntil("the Endpoint's node shows the real delivery", context ->
                        TaskThirtyThreeScenarioSupport.tooltipContains(context, ".graph-node-endpoint", "Delivered 1× in the last 5 s"))
                .waitUntil("teal dots travel along the Endpoint's link", context -> context.el("#graph_flow_pulses")
                        .as(space.controlnet.ae2federation.client.menu.FederationFlowPulses.class).drawnDots() > 0)
                .waitForTextContains("#graph_throughput", "Flow · last 5 s: delivered 1×")
                .hover("#domain_title")
                .frames(2).screenshot("ui-flow-processing")
                // Dots move and skip the link's label, so a single frame may draw none; record one that draws them.
                .waitUntil("dots are drawn when the evidence is recorded", context -> {
                    int dots = context.el("#graph_flow_pulses")
                            .as(space.controlnet.ae2federation.client.menu.FederationFlowPulses.class).drawnDots();
                    if (dots > 0) context.put("task33.flowDots", Integer.toString(dots));
                    return dots > 0;
                })
                .step("record flow evidence", context -> {
                    context.attach("evidenceFor", "ui.mapping");
                    context.attach("processingFlow", context.el(".graph-node-endpoint").as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class)
                            .getStyle().tooltips().asList().stream().map(net.minecraft.network.chat.Component::getString)
                            .collect(java.util.stream.Collectors.joining("\n")));
                    context.attach("flowDots", context.get("task33.flowDots"));
                })
                .click("#graph_flow_toggle")
                .waitForText("#graph_throughput", "Live flow off")
                .waitUntil("no dots are drawn while live flow is off", context -> context.el("#graph_flow_pulses")
                        .as(space.controlnet.ae2federation.client.menu.FederationFlowPulses.class).drawnDots() == 0)
                .click("#graph_flow_toggle")
                .waitUntil("live flow is back on", context -> !context.el("#graph_throughput").text().equals("Live flow off"))
                .step("scroll the pair editor back to its top", context ->
                        TaskThirtyThreeScenarioSupport.revealInAside(context, "#pair_title"))
                .click("#tab_mapping")
                .click("#processing_pattern_1")
                .click(".processing-endpoint")
                .waitForText("#mapping_toggle", "Unmap #1")
                .waitUntil("the unmap action is ready", context -> context.el("#mapping_toggle").isActive())
                .click("#mapping_toggle")
                .waitUntilServer("slot one is unmapped", TaskThirtyThreeWorldFixture::slotOneUnmapped)
                .click("#processing_pattern_0")
                .click(".processing-endpoint")
                .waitForText("#mapping_toggle", "Unmap #0")
                .waitUntil("the unmap action is ready again", context -> context.el("#mapping_toggle").isActive())
                .click("#mapping_toggle")
                .waitUntilServer("last unmap retains the claim", TaskThirtyThreeWorldFixture::endpointRetained)
                .waitForText(".processing-endpoint-state", "Retained · return lane still open")
                .check("target identity survives mapping state changes", context -> context.el(".processing-endpoint")
                        .as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class).getId().equals(context.get("target.initialId")))
                .step("resize English workspace", context ->
                        org.lwjgl.glfw.GLFW.glfwSetWindowSize(context.mc().getWindow().getWindow(), 320, 240))
                .waitUntil("narrow English viewport", context -> context.mc().getWindow().getGuiScaledWidth() == 320
                        && context.mc().getWindow().getGuiScaledHeight() == 240)
                .frames(5)
                // A narrow aside scrolls its details down to the actions, as a player would.
                .repeat(30, steps -> steps.scroll("#processing_detail", -1)).frames(3)
                .check("narrow English mapping buttons fit", context -> TaskThirtyThreeScenarioSupport.singleLineButtonTextFits(
                        context, "#mapping_toggle", "#processing_release", "#return_provider"))
                .check("narrow English mapping text fits", context -> TaskThirtyThreeScenarioSupport.wrappedTextFits(
                        context, "#processing_detail_title", "#processing_detail_text"))
                .check("narrow English mapping controls stay in workspace", context -> TaskThirtyThreeScenarioSupport.withinWorkspace(
                        context, "#mapping_toggle", "#processing_release", "#processing_detail"))
                .screenshot("ui-english-narrow-mapping")
                .click("#tab_overview").frames(3)
                .check("narrow English pair editor text fits", context -> TaskThirtyThreeScenarioSupport.wrappedTextFits(
                        context, "#pair_title", "#pair_note", "#policy_section_title_0", "#policy_state_0_storage"))
                .check("narrow English pair editor stays in workspace", context -> TaskThirtyThreeScenarioSupport.withinWorkspace(
                        context, "#topology_aside", "#policy_switch_0_storage"))
                .screenshot("ui-english-narrow-policy")
                .click("#tab_diagnostics").frames(3)
                .check("narrow English diagnostic text fits", context -> TaskThirtyThreeScenarioSupport.wrappedTextFits(context, "#endpoint_detail", "#endpoint_identity"))
                .screenshot("ui-english-narrow-diagnostics")
                .click("#tab_overview").step("select a network card", TaskThirtyThreeScenarioSupport::selectFirstNetworkCard).frames(3)
                .check("narrow English graph action fits", context -> TaskThirtyThreeScenarioSupport.singleLineButtonTextFits(context, "#graph_open"))
                .screenshot("ui-english-narrow-overview")
                .click("#tab_mapping").frames(3)
                .repeat(30, steps -> steps.scroll("#processing_detail", -1)).frames(3)
                .hover("#processing_release")
                .step("prepare release", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#processing_release"))
                .awaitElement("#release_confirm")
                .checkTextContains("#release_consequence", "closes the return path")
                .checkServer("opening confirmation does not release ownership", TaskThirtyThreeWorldFixture::endpointRetained)
                .check("narrow English release text fits", context -> TaskThirtyThreeScenarioSupport.wrappedTextFits(context, "#release_consequence"))
                .check("narrow English release controls stay on screen", context -> TaskThirtyThreeScenarioSupport.withinWorkspace(
                        context, "#release_consequence", "#release_cancel", "#release_confirm"))
                .screenshot("ui-release-confirmation")
                .hover("#release_cancel")
                .step("cancel release", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#release_cancel"))
                .waitForTextContains("#processing_status", "Choose a pattern")
                .checkServer("cancellation preserves ownership and return lane", TaskThirtyThreeWorldFixture::endpointRetained)
                .hover("#processing_release")
                .step("prepare release again", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#processing_release"))
                .awaitElement("#release_confirm")
                .server("external mapping edit invalidates prepared release", TaskThirtyThreeWorldFixture::toggleExternalMapping)
                .waitUntil("stale confirmation disappears", context -> context.all("#release_confirm").isEmpty())
                .checkServer("external mapping remains installed", TaskThirtyThreeWorldFixture::mappingAccepted)
                .server("remove external mapping", TaskThirtyThreeWorldFixture::toggleExternalMapping)
                .waitUntilServer("external unmap preserves dispatched work", TaskThirtyThreeWorldFixture::endpointRetained)
                .frames(5)
                .check("old confirmation does not reopen", context -> context.all("#release_confirm").isEmpty())
                .hover("#processing_release")
                .step("prepare fresh release after external change", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#processing_release"))
                .awaitElement("#release_confirm")
                .hover("#release_confirm")
                .step("confirm release", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#release_confirm"))
                .waitUntilServer("confirmed release removes retained ownership", TaskThirtyThreeWorldFixture::endpointReleased)
                .waitForTextContains("#processing_status", "Endpoint released. Its return path is closed.")
                .waitForText(".processing-endpoint-state", "Available · not claimed")
                .repeat(30, steps -> steps.scroll("#processing_detail", -1)).frames(3)
                .check("narrow English detail can expose its last control", context -> {
                    var scroller = context.el("#processing_detail").as(com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView.class);
                    var text = context.el("#processing_highlight").as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class);
                    return text.getPositionY() + text.getSizeHeight()
                            <= scroller.viewPort.getContentY() + scroller.viewPort.getContentHeight() + 0.01f;
                })
                .screenshot("ui-release-complete")
                // Mapping needs no rule: a rule changed elsewhere does not make the mapping request stale.
                .server("change a rule outside this menu", TaskThirtyThreeWorldFixture::changeStoragePolicyExternally)
                .hover("#mapping_toggle")
                .step("map after a rule changed elsewhere", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#mapping_toggle"))
                .waitUntilServer("the mapping claims the Endpoint for the host", TaskThirtyThreeWorldFixture::endpointClaimedByHost)
                .waitUntil("the request completes without waiting", context -> !context.el("#request_status").isVisible())
                .waitForTextContains("#processing_status", "Mapping updated")
                .screenshot("ui-request-after-external-rule")
                .step("restore English window", context ->
                        org.lwjgl.glfw.GLFW.glfwSetWindowSize(context.mc().getWindow().getWindow(), 1600, 960))
                .waitUntil("English window restored", context -> context.mc().getWindow().getWidth() == 1600)
                // A fuller domain for design review: eight named networks on two Routers, rules of every kind, three
                // Providers mapped many-to-many onto five Endpoints. The names, rules and patterns are test-world data.
                .server("showcase: six more networks on two Routers", TaskThirtyThreeShowcaseFixture::placeNetworks)
                .serverTicks(4)
                .waitUntilServer("showcase: eight networks join the Router's domain", TaskThirtyThreeShowcaseFixture::networksReady)
                .server("showcase: name the networks and link them", TaskThirtyThreeShowcaseFixture::installNamesAndRules)
                .server("showcase: place Providers and Endpoints", TaskThirtyThreeShowcaseFixture::placeDevices)
                .serverTicks(4)
                .waitUntilServer("showcase: devices join their networks", TaskThirtyThreeShowcaseFixture::devicesReady)
                .server("showcase: map patterns many-to-many", TaskThirtyThreeShowcaseFixture::mapDevices)
                .waitUntilServer("showcase: mappings installed", TaskThirtyThreeShowcaseFixture::mapped)
                // The domain gained members, so the open workspace is out of date: reopen it, as a player would.
                .closeScreen()
                .server("showcase: open the Router workspace", TaskThirtyThreeWorldFixture::openRouter)
                .awaitScreen(com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen.class)
                .awaitModularUI().awaitElement("#domain_graph")
                .waitUntil("showcase: eight named network cards", context -> context.all(".graph-node-member").size() == 8
                        && context.all(".card-name").stream().anyMatch(name -> name.text().equals("Storage Hall"))
                        && context.all(".card-name").stream().anyMatch(name -> name.text().equals("Sky Lab")))
                .click("#graph_fit").frames(3)
                .step("showcase: select Main Base", context -> showcaseSelect(context, "Main Base"))
                .waitUntil("showcase: Main Base details", context -> context.el("#network_detail").isVisible())
                // TEST-ONLY performance evidence: the average frame with the domain graph and the map preview shown.
                .step("perf: start frame timing", context -> FRAME_TIMING[0] = System.nanoTime())
                .frames(PERF_FRAMES)
                .step("perf: report frame timing", context -> org.slf4j.LoggerFactory.getLogger("ae2federation-perf")
                        .info("AE2F_PERF test=uishowcase metric=frameTime value={} unit=ns/frame",
                                (System.nanoTime() - FRAME_TIMING[0]) / PERF_FRAMES))
                .check("showcase: five Endpoint nodes, four beside the network that maps them", context ->
                        context.all(".graph-node-endpoint").size() == 5)
                .hover("#domain_title").frames(5)
                .screenshot("ui-showcase-topology")
                .step("showcase: select a network on the second Router", context -> showcaseSelect(context, "Sky Lab"))
                .waitForTextContains("#network_title", "Sky Lab")
                .hover("#domain_title").frames(5)
                .screenshot("ui-showcase-topology-remote")
                .typeInto("#graph_search", "Smeltery")
                .hover("#domain_title").frames(5)
                .screenshot("ui-showcase-search")
                .typeInto("#graph_search", "").blur()
                .click("#graph_zoom_in").click("#graph_zoom_in").frames(3)
                .hover("#domain_title").frames(5)
                .screenshot("ui-showcase-zoom")
                .click("#graph_fit").frames(3)
                .step("showcase: open the busiest link", TaskThirtyThreeMappingScenario::showcaseBusiestPair)
                .waitUntil("showcase: pair editor", context -> context.el("#pair_editor").isVisible())
                .hover("#domain_title").frames(5)
                .screenshot("ui-showcase-pair")
                .click("#tab_mapping").frames(2)
                .waitUntil("showcase: three Providers and five Endpoints", context ->
                        context.all(".processing-provider-card").size() == 3 && context.all(".processing-endpoint").size() == 5)
                .hover("#domain_title").frames(5)
                .screenshot("ui-showcase-processing")
                .step("showcase: edit the Mine's Provider", context -> {
                    var headers = context.all(".processing-provider");
                    var bounds = headers.get(headers.size() - 1).bounds();
                    context.input().mouseDown(bounds.centerX(), bounds.centerY(), 0);
                    context.input().mouseUp(bounds.centerX(), bounds.centerY(), 0);
                })
                .hover("#domain_title").frames(5)
                .screenshot("ui-showcase-processing-other")
                .closeScreen()
                // The host's own Provider screen in the fuller domain: its two Endpoints, two that other Providers own
                // (read-only) and one still free.
                .server("showcase: right-click the host Provider", TaskThirtyThreeWorldFixture::openProviderMapping)
                .awaitScreen(com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen.class)
                .awaitModularUI().frames(5)
                .waitUntil("showcase: the Provider screen shows the domain's five Endpoints", context ->
                        context.el("#provider_window").isVisible() && showcaseClaims(context).equals("free-1;in_use-2;occupied-2"))
                .check("showcase: another Provider's Endpoint names its owner and its patterns", context ->
                        context.all(".processing-endpoint.claim-occupied .processing-endpoint-state").stream()
                                .allMatch(state -> state.text().startsWith("Provider @ ") && state.text().contains(" · #")))
                .step("record showcase Provider evidence", context -> {
                    context.attach("evidenceFor", "ui.mapping");
                    context.attach("showcaseProviderClaims", showcaseClaims(context));
                })
                .hover("#provider_title").frames(5)
                .screenshot("ui-showcase-provider")
                .step("showcase: select one of its own Endpoints", context -> showcaseEndpoint(context, "in_use"))
                .waitUntil("showcase: its own Endpoint's details", context ->
                        !context.all(".processing-endpoint.claim-in_use.selected").isEmpty())
                .hover("#provider_title").frames(5)
                .screenshot("ui-showcase-provider-own")
                .step("showcase: select an Endpoint another Provider owns", context ->
                        showcaseEndpoint(context, "occupied"))
                .waitUntil("showcase: another Provider's Endpoint is shown with its owner", context ->
                        !context.all(".processing-endpoint.claim-occupied.selected").isEmpty()
                        && !context.all("#processing_fact_value_owner").isEmpty()
                        && !context.all("#processing_detail_text.read-only-banner").isEmpty()
                        && context.el("#processing_detail_text").text().startsWith("Read-only: this Endpoint belongs to Provider @ "))
                .check("showcase: only the owner edits it, so this screen offers no toggle", context ->
                        !context.el("#mapping_toggle").isVisible() && !context.el("#processing_unlink").isVisible())
                .hover("#provider_title").frames(5)
                .screenshot("ui-showcase-provider-other")
                .closeScreen();
    }

    /** The Provider screen's Endpoint cards counted by claim, as {@code claim-count} in claim order. */
    private static String showcaseClaims(com.lowdragmc.lowdraglib2.uitest.TestContext context) {
        return java.util.stream.Stream.of("free", "in_use", "occupied", "retained", "local", "unobserved")
                .map(code -> code + "-" + context.all(".processing-endpoint.claim-" + code).size())
                .filter(entry -> !entry.endsWith("-0"))
                .collect(java.util.stream.Collectors.joining(";"));
    }

    /** Clicks the first Endpoint card with this claim. */
    private static void showcaseEndpoint(com.lowdragmc.lowdraglib2.uitest.TestContext context, String claim) {
        var bounds = context.all(".processing-endpoint.claim-" + claim).get(0).bounds();
        context.input().mouseDown(bounds.centerX(), bounds.centerY(), 0);
        context.input().mouseUp(bounds.centerX(), bounds.centerY(), 0);
    }

    private static void showcaseSelect(com.lowdragmc.lowdraglib2.uitest.TestContext context, String name) {
        var card = context.all(".graph-node-member").stream()
                .filter(candidate -> candidate.as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class).allChildrenStream()
                        .filter(com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement.class::isInstance)
                        .anyMatch(text -> ((com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement) text).getText().getString().equals(name)))
                .findFirst().orElseThrow(() -> new IllegalStateException("No card named " + name));
        var bounds = card.bounds();
        context.input().mouseDown(bounds.centerX(), bounds.centerY(), 0);
        context.input().mouseUp(bounds.centerX(), bounds.centerY(), 0);
    }

    /** The link label with the most rule chips, the one whose editor has the most to show. */
    private static void showcaseBusiestPair(com.lowdragmc.lowdraglib2.uitest.TestContext context) {
        var pill = context.all(".graph-pair").stream()
                .max(java.util.Comparator.comparingLong(candidate -> candidate.as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class)
                        .allChildrenStream().filter(com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement.class::isInstance).count()))
                .orElseThrow(() -> new IllegalStateException("No link labels"));
        var bounds = pill.bounds();
        context.input().mouseDown(bounds.centerX(), bounds.centerY(), 0);
        context.input().mouseUp(bounds.centerX(), bounds.centerY(), 0);
    }
}
