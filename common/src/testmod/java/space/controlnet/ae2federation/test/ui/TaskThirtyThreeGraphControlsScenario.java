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
                // The tab rail's background wraps its tabs and ends under the last one, not at the window's bottom.
                .check("the tab rail ends under its last tab", context -> {
                    var rail = context.el("#workspace_rail").bounds();
                    var last = context.el("#tab_mapping").bounds();
                    var root = context.el("#domain_root").bounds();
                    float bottom = last.y() + last.height();
                    return rail.y() + rail.height() >= bottom && rail.y() + rail.height() <= bottom + 8
                            && rail.y() + rail.height() < root.y() + root.height() - 20;
                })
                .click("#graph_zoom_in").click("#graph_zoom_out").click("#graph_fit")
                .check("graph remains visible after controls", context -> context.el("#domain_graph").isVisible())
                .check("both member networks are drawn", context -> context.all(".graph-node-member").size() == 2)
                .check("two-member domain opens the pair editor", context -> context.el("#pair_editor").isVisible())
                // Empty canvas between the cards lies under the link layer; a left drag there must still pan.
                .step("press the left button between the two cards", context -> {
                    var cards = context.all(".graph-node-member").stream().map(card -> card.bounds())
                            .sorted(java.util.Comparator.comparingDouble(bounds -> bounds.x())).toList();
                    var point = new float[] {(cards.get(0).x() + cards.get(0).width() + cards.get(1).x()) / 2f,
                            cards.get(0).centerY()};
                    var graph = context.el("#domain_graph").as(com.lowdragmc.lowdraglib2.gui.ui.elements.GraphView.class);
                    context.put("graph.panPoint", point);
                    context.put("graph.panStart", new float[] {graph.getOffsetX(), graph.getOffsetY()});
                    context.input().moveTo(point[0], point[1]);
                    context.input().mouseDown(point[0], point[1], 0);
                })
                .repeat(6, steps -> steps.step("drag the canvas", context -> {
                    var point = context.<float[]>get("graph.panPoint");
                    point[0] += 10;
                    context.input().dragTo(point[0], point[1], 0);
                }).frames(1))
                .step("release the canvas", context -> {
                    var point = context.<float[]>get("graph.panPoint");
                    context.input().mouseUp(point[0], point[1], 0);
                })
                .check("a left drag on empty canvas pans the graph", context -> {
                    var graph = context.el("#domain_graph").as(com.lowdragmc.lowdraglib2.gui.ui.elements.GraphView.class);
                    return graph.getOffsetX() < context.<float[]>get("graph.panStart")[0] - 1f;
                })
                // A card covers much of the canvas: a drag that starts on one pans as well, and a wheel turn over one zooms.
                .step("press the left button on a card", context -> {
                    var card = context.all(".graph-node-member").getFirst().bounds();
                    var point = new float[] {card.centerX(), card.centerY()};
                    var graph = context.el("#domain_graph").as(com.lowdragmc.lowdraglib2.gui.ui.elements.GraphView.class);
                    context.put("graph.panPoint", point);
                    context.put("graph.panStart", new float[] {graph.getOffsetX(), graph.getOffsetY()});
                    context.input().moveTo(point[0], point[1]);
                    context.input().mouseDown(point[0], point[1], 0);
                })
                .repeat(6, steps -> steps.step("drag from the card", context -> {
                    var point = context.<float[]>get("graph.panPoint");
                    point[0] += 10;
                    context.input().dragTo(point[0], point[1], 0);
                }).frames(1))
                .step("release over the canvas", context -> {
                    var point = context.<float[]>get("graph.panPoint");
                    context.input().mouseUp(point[0], point[1], 0);
                })
                .check("a left drag that starts on a card pans the graph", context -> {
                    var graph = context.el("#domain_graph").as(com.lowdragmc.lowdraglib2.gui.ui.elements.GraphView.class);
                    return graph.getOffsetX() < context.<float[]>get("graph.panStart")[0] - 1f;
                })
                .step("turn the wheel over a card", context -> {
                    var graph = context.el("#domain_graph").as(com.lowdragmc.lowdraglib2.gui.ui.elements.GraphView.class);
                    context.put("graph.wheelScale", graph.getScale());
                    var card = context.all(".graph-node-member").getFirst().bounds();
                    context.input().moveTo(card.centerX(), card.centerY());
                    context.input().scroll(card.centerX(), card.centerY(), 1);
                })
                .check("a wheel turn over a card zooms the graph", context ->
                        context.el("#domain_graph").as(com.lowdragmc.lowdraglib2.gui.ui.elements.GraphView.class).getScale()
                                > context.<Float>get("graph.wheelScale") + 0.01f)
                // The link itself is a target too: with a network selected, a press on the line to the other network (dashed
                // while the pair has no rule) opens that pair, away from the label in the middle.
                .click("#graph_fit")
                .step("select the first network", TaskThirtyThreeScenarioSupport::selectFirstNetworkCard)
                .waitUntil("the network panel replaces the pair editor", context -> !context.el("#pair_editor").isVisible())
                .step("press on the link a third of the way along", context -> {
                    var cards = context.all(".graph-node-member").stream().map(card -> card.bounds()).toList();
                    var a = cards.get(0);
                    var b = cards.get(1);
                    var point = space.controlnet.ae2federation.client.policy.TopologyLink
                            .between(a.x(), a.y(), b.x(), b.y(), a.width(), a.height()).curve().at(0.3f);
                    context.input().moveTo(point[0], point[1]);
                    context.input().mouseDown(point[0], point[1], 0);
                    context.input().mouseUp(point[0], point[1], 0);
                })
                .waitUntil("a press on the link opens its pair", context -> context.el("#pair_editor").isVisible())
                .frames(2).screenshot("ui-graph-link-pair-selected")
                .click("#graph_fit")
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
                .serverGet("record the Provider's position", "search.providerPosition",
                        TaskThirtyThreeWorldFixture::providerPositionQuery)
                .step("search by the Provider's coordinates", context -> context.el("#graph_search")
                        .as(com.lowdragmc.lowdraglib2.gui.ui.elements.TextField.class).setText(context.get("search.providerPosition"), true))
                .waitUntil("only the network holding that Provider stays highlighted", context ->
                        opacity(context, context.get("net.providerHost")) == 1f && opacity(context, context.get("net.endpoint")) < 1f)
                .typeInto("#graph_search", "endpoint")
                .waitUntil("a device kind finds the Endpoint's network", context ->
                        opacity(context, context.get("net.endpoint")) == 1f && opacity(context, context.get("net.providerHost")) < 1f)
                .typeInto("#graph_search", "").blur()
                .click("#graph_fit")
                .step("select the Provider host network", context -> TaskThirtyThreeScenarioSupport.selectNetworkCard(
                        context, context.get("net.providerHost")))
                .waitUntil("network detail replaces the pair editor", context -> context.el("#network_detail").isVisible()
                        && context.el("#network_title").text().equals(TaskThirtyThreeScenarioSupport.networkName(
                                context.get("net.providerHost"))))
                .check("the devices button counts its Provider and the Endpoint that Provider maps", context ->
                        context.el("#graph_open").text().equals("Devices (2)")
                                && TaskThirtyThreeScenarioSupport.tooltipContains(context, "#graph_open", "Pattern providers: 1")
                                && TaskThirtyThreeScenarioSupport.tooltipContains(context, "#graph_open", "Processing endpoints: 1"))
                .waitForTextContains("#network_stat_energy", " AE")
                .check("figures are the design's five rows", context -> context.all(".stat-row").size() == 5
                        && context.el("#network_stat_cpus").text().matches("\\d+/\\d+ busy")
                        && context.el("#network_stat_channels").text().matches("\\d+ · \\d+ nodes")
                        && context.el("#network_stat_io").text().matches("\\+.* / −.* AE/t"))
                .check("a confirmed member explains nothing more", context -> !context.el("#network_explain").isVisible())
                .waitUntil("an online, confirmed network's card has no state line", context -> {
                    var texts = TaskThirtyThreeScenarioSupport.cardTexts(context, context.get("net.providerHost"));
                    return !texts.isEmpty() && texts.stream().noneMatch(text -> text.contains("Online")
                            || text.contains("Identity") || text.contains("Waiting"));
                })
                .check("identity location is shown", context -> context.el("#network_identity").text().startsWith("Overworld · "))
                .check("card shows live figures", context -> context.all(".graph-node-member").stream()
                        .allMatch(card -> card.as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class).allChildrenStream()
                                .filter(com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement.class::isInstance)
                                .map(child -> ((com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement) child).getText().getString())
                                .toList().containsAll(java.util.List.of("Energy", "Storage")))
                        && context.all(".graph-node-member").stream()
                        .allMatch(card -> card.as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class).allChildrenStream()
                                .filter(com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement.class::isInstance)
                                .map(child -> ((com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement) child).getText().getString())
                                .anyMatch(text -> text.matches("\\d+(\\.\\d+)?[kMGT]? types")))
                        && context.all(".graph-node-member").stream()
                        .allMatch(card -> card.as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class).allChildrenStream()
                                .filter(com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement.class::isInstance)
                                .anyMatch(child -> ((com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement) child).getText()
                                        .getString().matches("CPU \\d+/\\d+"))))
                .check("every card is named by its identity tag while unnamed", context -> context.all(".card-name").size()
                        == context.all(".graph-node-member").size()
                        && context.all(".card-name").stream().allMatch(name -> name.text().matches("Network [0-9A-F]{4}")))
                .check("cards say where their network is", context -> context.all(".graph-node-member").stream()
                        .allMatch(card -> card.as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class).allChildrenStream()
                                .filter(com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement.class::isInstance)
                                .anyMatch(child -> ((com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement) child).getText()
                                        .getString().matches("Overworld -?\\d+, -?\\d+, -?\\d+"))))
                .waitUntil("card thumbnails map the loaded terrain", context -> !context.all("#domain_graph .map-thumbnail").isEmpty()
                        && context.all("#domain_graph .map-thumbnail").stream().allMatch(tile -> tile.as(
                                space.controlnet.ae2federation.client.menu.FederationMapPreview.class).sampledCells() > 0))
                .waitUntil("settled network can be renamed", context -> context.el("#network_rename").isActive())
                .screenshot("ui-graph-network-detail")
                .waitUntil("the location map samples loaded terrain", context -> context.el("#network_preview .map-preview-tile")
                        .as(space.controlnet.ae2federation.client.menu.FederationMapPreview.class).sampledCells() > 0)
                .checkTextContains("#network_location_legend", "This network's blocks")
                .check("the legend counts the tinted blocks", context ->
                        TaskThirtyThreeScenarioSupport.tooltipContains(context, "#network_location_legend", "Tinted: "))
                .check("the map has no caption and no dimmed-surroundings entry", context ->
                        context.all("#network_location_caption").isEmpty()
                                && !context.el("#network_location_legend").text().contains("Surroundings"))
                .step("reveal the location map", context -> TaskThirtyThreeScenarioSupport.revealInAside(context, "#network_location"))
                .frames(2)
                .hover("#network_highlight")
                .step("highlight the network in the world", context ->
                        TaskThirtyThreeScenarioSupport.activateNavigation(context, "#network_highlight"))
                .waitUntil("the network's blocks are outlined", context ->
                        space.controlnet.ae2federation.client.WorldHighlight.activeBlocks() > 1)
                .waitUntil("the highlight button reads as pressed instead of a note saying so", context ->
                        context.all("#network_highlight.selected").size() == 1
                                && !context.el("#network_location_note").isVisible())
                .checkTextContains("#network_highlight", "Highlight network")
                .click("#network_view_3d")
                .waitUntil("the 3D preview draws the network's loaded blocks", context -> {
                    var preview = context.el("#network_preview .map-preview-tile")
                            .as(space.controlnet.ae2federation.client.menu.FederationMapPreview.class);
                    return space.controlnet.ae2federation.client.menu.FederationMapPreview.threeDimensional()
                            && preview.sceneView() != null && preview.sceneView().isDisplayed()
                            && preview.sceneView().renderedBlocks() > 0;
                })
                .check("the segmented switch marks 3D", context -> context.all("#network_view_3d.selected").size() == 1
                        && context.all("#network_view_map.selected").isEmpty())
                .frames(10)
                .screenshot("ui-network-preview-3d")
                .click("#network_view_map")
                .check("the map is back", context -> !space.controlnet.ae2federation.client.menu.FederationMapPreview.threeDimensional()
                        && !context.el("#network_preview .map-preview-scene").isVisible())
                .step("record highlight evidence", context -> context.put("task33.highlightBlocks",
                        Integer.toString(space.controlnet.ae2federation.client.WorldHighlight.activeBlocks())))
                .screenshot("ui-network-location")
                .step("return the aside to the top", context -> TaskThirtyThreeScenarioSupport.revealInAside(context, "#network_title"))
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
                .check("the Endpoint is a node beside the network, with no tooltip", context ->
                        context.all(".graph-node-endpoint").size() == 1
                                && TaskThirtyThreeScenarioSupport.noTooltip(context, ".graph-node-endpoint"))
                .hover(".graph-node-endpoint")
                .step("select the Endpoint node", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, ".graph-node-endpoint"))
                .waitUntil("the Endpoint node is selected in place, its panel in the aside", context ->
                        context.el("#page_overview").isVisible() && context.el("#endpoint_detail").isVisible())
                .waitForTextContains("#endpoint_fact_mode", "Federated")
                .check("the owner is a fact row, not repeated above it", context -> !context.el("#network_explain").isVisible())
                .step("select the Provider host network again", context -> TaskThirtyThreeScenarioSupport.selectNetworkCard(
                        context, context.get("net.providerHost")))
                .waitUntil("the network panel is back", context -> !context.el("#endpoint_detail").isVisible())
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
                .check("the pair names the Routers that link it, with their positions", context -> context.el("#pair_title").text()
                        .matches("(?s).*Via the Router at -?\\d+, -?\\d+, -?\\d+ · this domain.*"))
                .screenshot("ui-policy-direction")
                .waitUntil("with nothing to report the footer takes no room", context -> !context.el("#domain_footer")
                        .as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class).isDisplayed())
                .check("a rule not yet written reads as off", context -> context.el(TaskFifteenScenarioSupport.STORAGE_STATE)
                        .text().equals("Off"))
                .click(TaskFifteenScenarioSupport.STORAGE_SWITCH)
                .waitUntilServer("real policy revision advances", context ->
                        TaskFifteenWorldFixture.policyRevision(context)
                                > context.<Long>get("task33.policyBefore"))
                .waitUntil("the switch turns on", context -> storageSwitch(context).hasClass("on"))
                .click(TaskFifteenScenarioSupport.STORAGE_SWITCH)
                .waitUntil("the switch moves to re-export", context -> storageSwitch(context).hasClass("reexport"))
                .check("an accepted edit leaves the footer hidden", context -> !context.el("#domain_footer")
                        .as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class).isDisplayed())
                .waitForText(TaskFifteenScenarioSupport.STORAGE_STATE, "On with re-export")
                .check("the third state is the re-export switch", context -> context.el(
                        TaskFifteenScenarioSupport.STORAGE_SWITCH).as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class)
                        .hasClass("reexport"))
                .waitUntil("the graph shows the re-export at once, no longer in active green", context -> {
                    var chips = context.all(".pill-chip.reexport");
                    return !chips.isEmpty() && chips.stream().noneMatch(chip -> chip.as(
                            com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement.class).getTextStyle().textColor()
                            == space.controlnet.ae2federation.client.menu.FederationTheme.OK);
                })
                .frames(2).screenshot("ui-policy-reexport")
                .step("right click steps back to enabled", context -> {
                    var bounds = context.el(TaskFifteenScenarioSupport.STORAGE_SWITCH).bounds();
                    context.input().mouseDown(bounds.centerX(), bounds.centerY(), 1);
                    context.input().mouseUp(bounds.centerX(), bounds.centerY(), 1);
                })
                .waitUntil("the switch steps back to plain on", context -> storageSwitch(context).hasClass("on")
                        && !storageSwitch(context).hasClass("reexport"))
                .waitUntil("the graph drops the re-export with it", context -> context.all(".pill-chip.reexport").isEmpty())
                // The fixture's provider side may or may not have storage: a working rule has no tooltip, one waiting for
                // storage only its reason. The server reports the runtime a moment after the switch, so wait for it.
                .waitUntil("the switch has no tooltip, and the rule's state only says what needs attention", context -> {
                    var state = TaskThirtyThreeScenarioSupport.tooltipLines(context, TaskFifteenScenarioSupport.STORAGE_STATE);
                    var label = context.el(TaskFifteenScenarioSupport.STORAGE_STATE);
                    return TaskThirtyThreeScenarioSupport.noTooltip(context, TaskFifteenScenarioSupport.STORAGE_SWITCH)
                            && label.text().equals("On")
                            && (label.as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class).hasClass("health-active") ? state.isEmpty()
                                    : state.equals(java.util.List.of("The other network has no storage it can share.")));
                })
                .check("a storage rule lists no operations", context -> context.all(".policy-terms-row").isEmpty())
                .server("record authoritative policy result", context -> {
                    context.put("task33.policyRevision", Long.toString(TaskFifteenWorldFixture.policyRevision(context)));
                    context.put("task33.policyEnabled", Boolean.toString(TaskFifteenWorldFixture.policyEnabled(context)));
                })
                .step("record graph control evidence", context -> {
                    TaskThirtyThreeScenarioSupport.attach(context, "ui.graph-controls");
                    context.attach("policyRevision", context.get("task33.policyRevision"));
                    context.attach("policyEnabled", context.get("task33.policyEnabled"));
                    context.attach("networkRename", context.get("task33.renamed"));
                    context.attach("highlightBlocks", context.get("task33.highlightBlocks"));
                    context.attach("worldCaptures", "router-overview,bridge-north,provider-host,endpoint-faces");
                })
                .server("policy detail reads preserve backend counters", TaskThirtyThreeWorldFixture::verifyPolicySnapshotReads)
                .server("configure a disabled crafting rule", TaskThirtyThreeWorldFixture::installBrowserRule)
                .waitUntil("pair editor shows the configured rule off", context ->
                        TaskThirtyThreeScenarioSupport.ruleState(context, "crafting").equals("Off"))
                .check("the rule's switch is off", context -> !context.el(TaskThirtyThreeScenarioSupport.ruleControl(context,
                        "switch", context.get("net.providerHost"), "crafting"))
                        .as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class).hasClass("on"))
                .check("pair editor stays inside the workspace", context -> TaskThirtyThreeScenarioSupport.withinWorkspace(
                        context, "#topology_aside", TaskFifteenScenarioSupport.STORAGE_SWITCH))
                .step("reveal the crafting rule", context -> TaskThirtyThreeScenarioSupport.revealRule(context, "crafting"))
                .frames(2).screenshot("ui-policy-runtime-disabled")
                .server("enable rule without required request permission", TaskThirtyThreeWorldFixture::removeBrowserRuleOperation)
                .waitUntil("the blocked rule's tooltip is only its reason", context -> TaskThirtyThreeScenarioSupport.tooltipLines(context,
                        TaskThirtyThreeScenarioSupport.ruleControl(context, "state", context.get("net.providerHost"), "crafting"))
                        .equals(java.util.List.of("Required operation is not allowed: crafting requests.")))
                .check("the state line does not repeat the reason", context -> !TaskThirtyThreeScenarioSupport
                        .ruleState(context, "crafting").contains("required operation"))
                .step("reveal the crafting rule", context -> TaskThirtyThreeScenarioSupport.revealRule(context, "crafting"))
                .frames(2).screenshot("ui-policy-runtime-operation-denied")
                .server("enable the crafting rule", TaskThirtyThreeWorldFixture::enableCraftingRule)
                .waitUntil("crafting is active", context -> TaskThirtyThreeScenarioSupport.ruleActive(context, "crafting"))
                .frames(2).screenshot("ui-policy-runtime-crafting-active")
                .server("switch the storage rule off through the API", context ->
                        TaskThirtyThreeWorldFixture.setCraftingStorage(context, false))
                .waitUntil("missing storage is explained", context -> TaskThirtyThreeScenarioSupport.tooltipLines(context,
                        TaskThirtyThreeScenarioSupport.ruleControl(context, "state", context.get("net.providerHost"), "crafting"))
                        .stream().anyMatch(line -> line.contains("This direction's Storage rule is off")))
                .check("the state line does not repeat the reason", context -> !TaskThirtyThreeScenarioSupport
                        .ruleState(context, "crafting").contains("Storage rule is off"))
                .step("reveal the crafting rule", context -> TaskThirtyThreeScenarioSupport.revealRule(context, "crafting"))
                .frames(2).screenshot("ui-policy-runtime-storage-required")
                .server("switch the storage rule back on", context -> TaskThirtyThreeWorldFixture.setCraftingStorage(context, true))
                .waitUntil("crafting is active again", context -> TaskThirtyThreeScenarioSupport.ruleActive(context, "crafting"))
                .server("disable the observed rule and reject its old diagnostic", TaskThirtyThreeWorldFixture::disableObservedCraftingRule)
                .waitUntil("the disabled rule reads off, with no leftover reason", context -> TaskThirtyThreeScenarioSupport
                        .ruleState(context, "crafting").equals("Off"))
                .frames(2).screenshot("ui-policy-runtime-revision-invalidated")
                .checkServer("displaying a new revision does not authorize an outdated edit", TaskThirtyThreeWorldFixture::displayedRevisionDoesNotGrantAuthority)
                .waitForTextContains("#ack_status", "Rule changed elsewhere")
                .check("refused switch keeps the newer rule off", context ->
                        TaskThirtyThreeScenarioSupport.ruleState(context, "crafting").equals("Off"))
                .screenshot("ui-policy-switch-conflict")
                // A hovered rail tab drops a pixel on AE2's lighter sprite, as AE2's toolbar buttons do.
                .hover("#tab_mapping").frames(2).screenshot("ui-rail-tab-hover")
                .closeScreen()
                .server("share energy with the Endpoint and remove its own energy cell", TaskThirtyThreeWorldFixture::removeEndpointEnergySource)
                .waitUntilServer("the Endpoint runs on the shared energy pool", TaskThirtyThreeWorldFixture::endpointRunsOnSharedEnergy)
                .server("open fresh policy context after source removal", TaskThirtyThreeWorldFixture::openRouter)
                .awaitScreen(com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen.class)
                .awaitModularUI()
                .awaitElement("#policy_section_title_0")
                .waitUntil("the sharing energy rule is active", context -> TaskThirtyThreeScenarioSupport.ruleActive(context, "me_power"))
                .step("reveal the energy rule", context -> TaskThirtyThreeScenarioSupport.revealRule(context, "me_power"))
                .frames(2).screenshot("ui-policy-runtime-energy-shared")
                .step("select a network card so the shared link is drawn unselected", TaskThirtyThreeScenarioSupport::selectFirstNetworkCard)
                .frames(3)
                .check("the Endpoint's network runs on the shared pool, not a low-energy warning", context ->
                        TaskThirtyThreeScenarioSupport.cardTexts(context, context.get("net.endpoint")).stream()
                                .anyMatch(text -> text.equals("Shared energy")))
                .check("both networks of the pool read its one energy percentage, not their own cells", context -> {
                    var host = TaskThirtyThreeScenarioSupport.cardPercent(context, context.get("net.providerHost"));
                    return host != null && host.equals(TaskThirtyThreeScenarioSupport.cardPercent(context, context.get("net.endpoint")));
                })
                .screenshot("ui-graph-energy-shared-link")
                .click("#tab_overview").frames(2)
                .screenshot("ui-graph-controls")
                .server("add a real Bridge domain that shares the outer network, and one more beyond it",
                        TaskThirtyThreeWorldFixture::installRelatedDomain)
                .waitUntilServer("the related domains settle", TaskThirtyThreeWorldFixture::relatedDomainReady)
                .server("configure a rule inside each related domain", TaskThirtyThreeWorldFixture::installRelatedRule)
                .serverGet("record the related network", "related.id", TaskThirtyThreeWorldFixture::relatedNetwork)
                .serverGet("record the far related network", "related.far.id", TaskThirtyThreeWorldFixture::relatedFarNetwork)
                .waitForTextContains("#scope_caption", "this domain")
                .check("the domain segment is selected", context -> context.all("#graph_scope_domain.selected").size() == 1)
                .check("the domain scope shows only members", context -> context.all(".graph-node-member").size() == 2
                        && context.all(".related-network").isEmpty())
                .click("#graph_scope")
                .waitUntil("all related shows both related networks read-only, however far", context ->
                        context.all(".related-network").size() == 2 && context.all(".graph-node-member").size() == 4)
                .waitUntil("the graph refits so the related cards are in view", context -> {
                    var viewport = context.el("#domain_graph").bounds();
                    return context.all(".related-network").stream().map(element -> element.bounds()).allMatch(card ->
                            card.x() >= viewport.x() && card.y() >= viewport.y()
                                    && card.x() + card.width() <= viewport.x() + viewport.width()
                                    && card.y() + card.height() <= viewport.y() + viewport.height());
                })
                .step("select the related network", context -> TaskThirtyThreeScenarioSupport.selectNetworkCard(
                        context, context.get("related.id")))
                .waitForTextContains("#graph_selection", ", a related domain")
                .check("the related domain has a readable name, not its internal identity", context -> {
                    var text = context.el("#graph_selection").text();
                    return text.matches("(?s)Member of Bridge domain [0-9A-F]{4}, a related domain\\..*") && !text.contains("direct:");
                })
                .check("a related network cannot be renamed here", context -> !context.el("#network_rename").isActive())
                .step("open the related pair", context -> {
                    var tag = TaskThirtyThreeScenarioSupport.networkTag(context.get("net.endpoint"));
                    var link = context.all(".network-link").stream().filter(candidate -> candidate.text().contains(tag))
                            .findFirst().orElseThrow();
                    var bounds = link.bounds();
                    context.input().mouseDown(bounds.centerX(), bounds.centerY(), 0);
                    context.input().mouseUp(bounds.centerX(), bounds.centerY(), 0);
                })
                .waitForTextContains("#pair_note", "Read-only: belongs to Bridge domain ")
                .check("related rules are shown but cannot be switched", context -> context.all(".policy-switch").stream()
                        .noneMatch(toggle -> toggle.isActive()) && context.all(".policy-switch.on").size() == 1)
                .check("only the related pair's configured rule is listed", context -> context.all(".policy-row").size() == 1)
                .checkTextContains("#scope_caption", "with connected domains (read-only)")
                .check("all shown related networks fit under the cap", context -> !context.el("#scope_caption").text().contains("showing"))
                .check("both related domains' links are drawn as read-only, the far one too", context ->
                        context.all(".related-pair").size() == 2)
                .check("a related domain's network shows its own status, read-only", context -> {
                    var texts = TaskThirtyThreeScenarioSupport.cardTexts(context, context.get("related.id"));
                    return context.all(".related-network").size() > 0
                            && TaskThirtyThreeScenarioSupport.cardPercent(context, context.get("related.id")) != null
                            && texts.stream().anyMatch(text -> text.matches("(No power · )?Related: .+"));
                })
                .screenshot("ui-scope-related")
                .step("record scope evidence", context -> {
                    // A separate record: the case record above already holds the accepted-edit status.
                    context.attach("evidenceFor", "ui.graph-controls");
                    context.attach("scope", "related-read-only");
                    context.attach("relatedNetwork", context.get("related.id"));
                    context.attach("farRelatedNetwork", context.get("related.far.id"));
                })
                .click("#graph_scope_domain")
                .waitUntil("the domain scope hides the related network again", context -> context.all(".related-network").isEmpty())
                .server("remove the related domain", TaskThirtyThreeWorldFixture::removeRelatedDomain)
                .checkServer("the related domains' rules are gone with them", context -> {
                    var policies = space.controlnet.ae2federation.policy.PolicyService.get(context.level());
                    return policies.configured(context.get("related.rule")).isEmpty()
                            && policies.configured(context.get("related.far.rule")).isEmpty();
                })
                .check("the legend is shown in the canvas corner", context -> context.el("#graph_legend").isVisible()
                        && context.el("#graph_legend").text().contains("▸ points to the network that uses it"))
                .click("#graph_legend_toggle")
                .waitUntil("the legend folds away", context -> !context.el("#graph_legend").isVisible())
                .click("#graph_legend_toggle")
                .waitUntil("the legend is back", context -> context.el("#graph_legend").isVisible())
                .server("join the two member networks with a cable", TaskThirtyThreeWorldFixture::joinMemberNetworks)
                .waitUntilServer("the joined Grid reports a merge", TaskThirtyThreeWorldFixture::memberNetworksMerged)
                .step("select the Provider host network", context -> TaskThirtyThreeScenarioSupport.selectNetworkCard(
                        context, context.get("net.providerHost")))
                .waitForTextContains("#graph_selection", "Merge pending")
                .check("the merge names both histories and how to recover", context -> {
                    var text = context.el("#graph_selection").text();
                    return text.contains("Contains: ") && text.contains(" · ") && text.contains("Disconnect them to recover.");
                })
                .waitForTextContains("#network_highlight", "Highlight both parts")
                .step("end earlier highlights", context -> space.controlnet.ae2federation.client.WorldHighlight.clear())
                .step("highlight both parts", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#network_highlight"))
                .check("both parts are outlined in their own colours", context ->
                        space.controlnet.ae2federation.client.WorldHighlight.activeGroups() == 2)
                .step("reveal the identity explanation", context -> TaskThirtyThreeScenarioSupport.revealInAside(context, "#graph_selection"))
                .frames(2).screenshot("ui-identity-merge")
                .step("record merge evidence", context -> {
                    context.attach("evidenceFor", "ui.graph-controls");
                    context.attach("identityMerge", context.el("#graph_selection").text());
                    context.attach("identityMergeGroups", Integer.toString(
                            space.controlnet.ae2federation.client.WorldHighlight.activeGroups()));
                })
                .server("disconnect the two networks again", TaskThirtyThreeWorldFixture::separateMemberNetworks)
                .waitUntilServer("both networks recover their own identity", TaskThirtyThreeWorldFixture::memberNetworksRecovered)
                .waitUntil("the workspace shows the recovered identity", context ->
                        !context.el("#graph_selection").text().contains("Merge pending"))
                .step("select the Provider host network", context -> TaskThirtyThreeScenarioSupport.selectNetworkCard(
                        context, context.get("net.providerHost")))
                .waitUntil("its location is shown", context -> context.el("#network_location").isVisible()
                        && context.el("#network_highlight").isActive())
                .step("reveal the location map", context -> TaskThirtyThreeScenarioSupport.revealInAside(context, "#network_location"))
                .frames(2)
                .hover("#network_highlight")
                .step("highlight the network before looking at it", context ->
                        TaskThirtyThreeScenarioSupport.activateNavigation(context, "#network_highlight"))
                .closeScreen()
                .server("look at the highlighted Provider host", TaskThirtyThreeWorldFixture::positionProviderCamera)
                .serverTicks(2).frames(2)
                .check("the highlight is still running", context ->
                        space.controlnet.ae2federation.client.WorldHighlight.activeBlocks() > 1)
                .screenshot("world-network-highlight")
                .server("position Router overview camera", TaskThirtyThreeWorldFixture::positionRouterOverviewCamera)
                .serverTicks(2).frames(2).screenshot("world-router-overview")
                .server("position ME Federation Bridge camera", TaskThirtyThreeWorldFixture::positionBridgeCamera)
                .serverTicks(2).frames(2).screenshot("world-multipart-bridge-north")
                .server("position Provider host camera", TaskThirtyThreeWorldFixture::positionProviderCamera)
                .serverTicks(2).frames(2).screenshot("world-provider-host")
                .server("position Endpoint face camera", TaskThirtyThreeWorldFixture::positionEndpointCamera)
                .serverTicks(2).frames(2).screenshot("world-endpoint-faces")
                // A block hidden behind stained glass, water and stone, in the rain, keeps its outline's own colour
                // under Fancy and Fabulous graphics alike; Fabulous composites the translucent layers after the level.
                .teardown("restore Fancy graphics, the HUD and clear weather", context -> {
                    space.controlnet.ae2federation.client.WorldHighlight.clear();
                    context.mc().options.hideGui = false;
                    if (context.mc().options.graphicsMode().get() != net.minecraft.client.GraphicsStatus.FANCY) {
                        context.mc().options.graphicsMode().set(net.minecraft.client.GraphicsStatus.FANCY);
                        context.mc().levelRenderer.allChanged();
                    }
                })
                .teardownServer("remove the occluded highlight scene", TaskThirtyThreeGraphControlsScenario::removeOccludedScene)
                .server("build the occluded highlight scene", TaskThirtyThreeGraphControlsScenario::buildOccludedScene)
                .awaitClientChunk(new net.minecraft.core.BlockPos(OCCLUDED_X, 64, OCCLUDED_TARGET_Z))
                .runCommand("weather rain")
                .waitUntil("the client sees rain", context -> context.level().getRainLevel(1f) > 0.9f)
                .step("hide the HUD", context -> context.mc().options.hideGui = true)
                .server("look at the hidden block", TaskThirtyThreeGraphControlsScenario::positionOccludedCamera)
                .serverTicks(2);
        occludedHighlight(scenario, net.minecraft.client.GraphicsStatus.FANCY, "fancy");
        occludedHighlight(scenario, net.minecraft.client.GraphicsStatus.FABULOUS, "fabulous");
    }

    private static final String OVERWORLD = "minecraft:overworld";
    /** Opaque magenta: nothing in the scene (stone, green glass, water, grass, sky, rain) is near it. */
    private static final int OCCLUDED_COLOR = 0xFF3CFF;
    private static final String OCCLUDED_GROUND = "highlight.occludedGround";
    // One chunk west of the fixture's chunk, so the scene stays out of the other world captures.
    private static final int OCCLUDED_X = -10;
    private static final int OCCLUDED_TARGET_Z = 5;
    private static final int OCCLUDED_CAMERA_Z = 12;

    private static void occludedHighlight(ScenarioBuilder scenario, net.minecraft.client.GraphicsStatus mode, String name) {
        scenario.step("switch graphics to " + name, context -> {
                    space.controlnet.ae2federation.client.WorldHighlight.clear();
                    context.mc().options.graphicsMode().set(mode);
                    context.mc().levelRenderer.allChanged();
                })
                .check(name + " graphics is in effect", context -> context.mc().options.graphicsMode().get() == mode
                        && (context.mc().levelRenderer.getTranslucentTarget() != null)
                                == (mode == net.minecraft.client.GraphicsStatus.FABULOUS))
                .waitUntil("the sections are rebuilt", context -> context.mc().levelRenderer.hasRenderedAllSections())
                .frames(5)
                .check("no outline colour before the highlight (" + name + ")", context ->
                        recordOutlinePixels(context, name + ".before") < 20
                                && recordFaintOutlinePixels(context, name + ".before") < 100)
                .step("highlight the hidden block", context -> {
                    int ground = context.<Integer>get(OCCLUDED_GROUND);
                    space.controlnet.ae2federation.client.WorldHighlight.show(OVERWORLD, java.util.List.of(
                            new space.controlnet.ae2federation.client.policy.BlockMarks.Mark(OCCLUDED_X, ground + 1,
                                    OCCLUDED_TARGET_Z)), OCCLUDED_COLOR);
                })
                .frames(3)
                .screenshot("world-highlight-occluded-" + name)
                // The outline blinks, so a frame grabbed while it is dark shows nothing: wait for a lit one.
                .waitUntil("the outline keeps its colour over glass, water, stone and rain (" + name + ")", context ->
                        recordOutlinePixels(context, name + ".after") > 400)
                // Between flashes the outline dims rather than vanishing: no full colour, but a faint tint remains.
                .waitUntil("the outline blinks dim while the highlight runs (" + name + ")", context ->
                        space.controlnet.ae2federation.client.WorldHighlight.activeBlocks() == 1
                                && recordOutlinePixels(context, name + ".dim") < 20
                                && recordFaintOutlinePixels(context, name + ".dim") > 400);
    }

    /**
     * Counts pixels near the screen's centre that still read as the outline's magenta. Glass, water or rain drawn
     * over the line would tint it (green, blue or grey) and the count would drop.
     */
    private static int recordOutlinePixels(com.lowdragmc.lowdraglib2.uitest.TestContext context, String label) {
        return recordPixels(context, "outlinePixels." + label, (red, green, blue) -> red > 150 && blue > 150 && green < 100);
    }

    /** Pixels tinted toward the outline's magenta without its full colour: the outline drawn at its dim brightness. */
    private static int recordFaintOutlinePixels(com.lowdragmc.lowdraglib2.uitest.TestContext context, String label) {
        return recordPixels(context, "faintOutlinePixels." + label, (red, green, blue) ->
                !(red > 150 && blue > 150 && green < 100) && red - green > 25 && blue - green > 25);
    }

    private interface PixelTest {
        boolean matches(int red, int green, int blue);
    }

    private static int recordPixels(com.lowdragmc.lowdraglib2.uitest.TestContext context, String key, PixelTest test) {
        var frame = com.lowdragmc.lowdraglib2.uitest.capture.FrameCapture.grab();
        try {
            int width = frame.getWidth();
            int height = frame.getHeight();
            int count = 0;
            for (int y = height / 4; y < height * 3 / 4; y++) {
                for (int x = width / 4; x < width * 3 / 4; x++) {
                    int abgr = frame.getPixelRGBA(x, y);
                    int red = abgr & 0xff;
                    int green = abgr >> 8 & 0xff;
                    int blue = abgr >> 16 & 0xff;
                    if (test.matches(red, green, blue)) count++;
                }
            }
            context.attach("evidenceFor", "ui.graph-controls");
            context.attach(key, Integer.toString(count));
            return count;
        } finally {
            com.lowdragmc.lowdraglib2.uitest.capture.FrameCapture.closeQuietly(frame);
        }
    }

    /**
     * Seen from the camera: green stained glass, then water held in a stone frame, then a stone wall, then the
     * highlighted block.
     */
    private static void buildOccludedScene(com.lowdragmc.lowdraglib2.uitest.ServerContext context) {
        var level = context.level();
        level.getChunk(OCCLUDED_X >> 4, 0);
        int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                OCCLUDED_X, OCCLUDED_TARGET_Z);
        context.put(OCCLUDED_GROUND, ground);
        var stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        for (int x = OCCLUDED_X - 2; x <= OCCLUDED_X + 2; x++) {
            for (int y = ground; y <= ground + 3; y++) {
                boolean frame = x == OCCLUDED_X - 2 || x == OCCLUDED_X + 2 || y == ground + 3;
                level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x, y, 6), stone);
                level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x, y, 7),
                        frame ? stone : net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
                level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x, y, 8), frame ? stone
                        : net.minecraft.world.level.block.Blocks.GREEN_STAINED_GLASS.defaultBlockState());
            }
        }
        level.setBlockAndUpdate(new net.minecraft.core.BlockPos(OCCLUDED_X, ground + 1, OCCLUDED_TARGET_Z),
                net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState());
    }

    private static void positionOccludedCamera(com.lowdragmc.lowdraglib2.uitest.ServerContext context) {
        int ground = context.<Integer>get(OCCLUDED_GROUND);
        var player = context.player();
        // Standing on the ground, the eye is about level with the hidden block's centre, which lies mid-frame.
        player.connection.teleport(OCCLUDED_X + 0.5, ground, OCCLUDED_CAMERA_Z + 0.5,
                player.getYRot(), player.getXRot());
        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                net.minecraft.world.phys.Vec3.atCenterOf(new net.minecraft.core.BlockPos(OCCLUDED_X, ground + 1,
                        OCCLUDED_TARGET_Z)));
    }

    private static void removeOccludedScene(com.lowdragmc.lowdraglib2.uitest.ServerContext context) {
        context.server().getCommands().performPrefixedCommand(
                context.server().createCommandSourceStack().withSuppressedOutput(), "weather clear");
        Integer ground = (Integer) context.state().get(OCCLUDED_GROUND);
        if (ground == null) return;
        var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        for (int x = OCCLUDED_X - 2; x <= OCCLUDED_X + 2; x++) {
            for (int y = ground; y <= ground + 3; y++) {
                for (int z = OCCLUDED_TARGET_Z; z <= 8; z++) {
                    context.level().setBlockAndUpdate(new net.minecraft.core.BlockPos(x, y, z), air);
                }
            }
        }
    }


    private static com.lowdragmc.lowdraglib2.gui.ui.UIElement storageSwitch(com.lowdragmc.lowdraglib2.uitest.TestContext context) {
        return context.el(TaskFifteenScenarioSupport.STORAGE_SWITCH).as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class);
    }

    private static float opacity(com.lowdragmc.lowdraglib2.uitest.TestContext context, String networkUuid) {
        return TaskThirtyThreeScenarioSupport.networkCard(context, networkUuid)
                .as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class).getStyle().opacity();
    }
}
