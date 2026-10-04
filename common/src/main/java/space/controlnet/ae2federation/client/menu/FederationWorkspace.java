package space.controlnet.ae2federation.client.menu;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;


/** Local navigation and presentation; every business selection still goes through the authorized menu request. */
final class FederationWorkspace {
    /** The topology (with each Endpoint's panel) and the wires view; an Endpoint's details are on the topology. */
    private static final List<String> PAGES = List.of("overview", "mapping");
    /** The server's choice groups; the wires view and the topology present them. */
    private static final List<String> CHOICE_GROUPS = List.of("mapping_provider", "slot", "target", "endpoint");
    private final PatternChoices patterns = new PatternChoices();
    private final UI ui;
    private final Consumer<String> select;
    private final Map<String, String> confirmedSelections = new HashMap<>();
    private final Map<String, com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture> icons =
            Map.of("overview", FederationIcons.TOPOLOGY, "mapping", FederationIcons.PROCESSING);
    private final Map<String, List<JsonObject>> choices = new HashMap<>();
    private boolean entranceApplied;
    private String navigationGroup;
    private String navigationId;
    private String endpointNavigationReceipt;
    private FederationTopologyView topology;
    private FederationProcessingGraph processing;
    private boolean authorityAllowsNavigation;
    private String page = "overview";

    FederationWorkspace(UI ui, Consumer<String> select) {
        this.ui = ui;
        this.select = select;
        // The design's title is bold; the tab name after it is not.
        element("domain_title", Label.class).setText(Component.translatable("ae2federation.ui.domain.title")
                .withStyle(net.minecraft.ChatFormatting.BOLD));
        for (var page : PAGES) {
            var tab = element("tab_" + page, Button.class);
            tab.noText();
            tab.style(style -> style.tooltips(tr(page)));
            tab.setOnClick(event -> show(page));
        }
        show("overview");
    }

    void updateNavigationAuthority(boolean allowed, boolean rejected) {
        authorityAllowsNavigation = allowed;
        if (rejected) endpointNavigationReceipt = null;
        updateEndpointNavigation();
    }

    private void updateEndpointNavigation() {
        if (topology != null) topology.setEndpointNavigation(authorityAllowsNavigation);
    }

    /** Follows an Endpoint to its owner's mappings once the server has selected the owner, its slot and the Endpoint. */
    private void navigateEndpoint(String endpoint) {
        if (!authorityAllowsNavigation || endpoint == null) return;
        endpointNavigationReceipt = "endpoint_mapping:" + endpoint + "/" + java.util.UUID.randomUUID();
        select.accept(endpointNavigationReceipt);
    }

    void bindProcessing(Consumer<String> setMapping, Runnable release) {
        processing = new FederationProcessingGraph(ui, setMapping, select, release, patterns::name, patterns::outputStack,
                patterns::facts);
        if (topology != null) topology.onSearch(processing::filter);
    }

    void setProcessingEditable(boolean editable) {
        if (processing != null) processing.setEditable(editable);
    }

    void bindGraph(FederationTopologyView graph) {
        topology = graph;
        // One header search: it dims networks in the topology and filters Providers, patterns and Endpoints here.
        if (processing != null) graph.onSearch(processing::filter);
        updateEndpointNavigation();
    }

    void show(String page) {
        this.page = page;
        navigationGroup = null;
        element("domain_tab", Label.class).setText(PAGES.contains(page) ? Component.literal(" · ").append(tr("tab_title." + page)) : Component.empty());
        boolean mapping = "mapping".equals(page);
        element("header_graph_tools", UIElement.class).setDisplay("overview".equals(page));
        // Classes, not setDisplay: the narrow layout's LSS also folds the summary away.
        for (var summary : Map.of("members_value", !mapping, "processing_summary", mapping, "scope_caption", "overview".equals(page)).entrySet()) {
            var label = element(summary.getKey(), UIElement.class);
            label.removeClass("off-page");
            if (!summary.getValue()) label.addClass("off-page");
        }
        updateFeedback();
        for (var candidate : PAGES) {
            element("page_" + candidate, UIElement.class).setDisplay(candidate.equals(page));
            var button = element("tab_" + candidate, Button.class);
            button.removeClass("selected");
            if (candidate.equals(page)) button.addClass("selected");
            // AE2's toolbar button: hovering or pressing drops it a pixel; the open page's tab is on the focused sprite.
            var icon = icons.get(candidate);
            var base = candidate.equals(page) ? FederationTheme.toolbarSelected(icon) : FederationTheme.toolbar(icon);
            var hover = FederationTheme.toolbarHover(icon);
            button.buttonStyle(style -> style.baseTexture(base).hoverTexture(hover).pressedTexture(hover));
        }
        element("graph_search", UIElement.class).setDisplay("overview".equals(page) || mapping);
    }

    /** The footer repeats processing feedback on the processing page only, and only when there is something to say. */
    void updateFeedback() {
        var feedback = element("processing_status", UIElement.class);
        feedback.setDisplay("mapping".equals(page) && !feedback.hasClass("feedback-neutral"));
    }

    void acceptChoices(String encoded) {
        if (encoded.isEmpty()) return;
        patterns.reset();
        var root = JsonParser.parseString(encoded).getAsJsonObject();
        if (!entranceApplied) {
            boolean fromProvider = root.has("returnProvider") && root.get("returnProvider").getAsBoolean();
            if (root.has("initialPage")) show(root.get("initialPage").getAsString());
            element("return_provider", Button.class).setDisplay(fromProvider);
            entranceApplied = true;
        }
        boolean bridgeUnavailable = root.has("bridgeUnavailable") && root.get("bridgeUnavailable").getAsBoolean();
        element("bridge_unavailable", UIElement.class).setDisplay(bridgeUnavailable);
        element("workspace_tabs", UIElement.class).setDisplay(!bridgeUnavailable);
        if (bridgeUnavailable) show("unavailable");
        var selected = root.getAsJsonObject("selected");
        for (var group : CHOICE_GROUPS) {
            var values = new ArrayList<JsonObject>();
            root.getAsJsonArray(group).forEach(value -> values.add(value.getAsJsonObject()));
            choices.put(group, List.copyOf(values));
            confirmedSelections.put(group, selected.has(group) ? selected.get(group).getAsString() : null);
        }
        updateEndpointNavigation();
        if (endpointNavigationReceipt != null && root.has("navigationReceipt")
                && endpointNavigationReceipt.equals(root.get("navigationReceipt").getAsString())) {
            // The server selected the owner, its mapped slot and this Endpoint; the wires view selects them too.
            if (processing != null) processing.focus(
                    confirmedSelections.get("target"), selected.has("slot") ? selected.get("slot").getAsString() : "");
            show("mapping");
            endpointNavigationReceipt = null;
        }
        if (navigationGroup != null && navigationId.equals(confirmedSelections.get(navigationGroup))) {
            show("mapping");
            navigationGroup = null;
        }
        if (processing != null) {
            var providerId = confirmedSelections.get("mapping_provider");
            var providerAt = choices.getOrDefault("mapping_provider", List.of()).stream()
                    .filter(choice -> choice.get("id").getAsString().equals(providerId) && choice.has("position"))
                    .map(choice -> choice.get("position").getAsString()).findFirst().orElse("");
            var names = objects(root, "networks").stream().map(network -> FederationTopologyView.displayName(
                    network.get("id").getAsString(), network.has("name") ? network.get("name").getAsString() : "").getString()).toList();
            var networkIds = objects(root, "networks").stream().map(network -> network.get("id").getAsString()).toList();
            processing.setNetworkBlocks((index, dimension) -> topology == null || index < 0 || index >= networkIds.size()
                    ? List.of() : topology.networkBlocks(networkIds.get(index), dimension));
            processing.accept(choices.getOrDefault("slot", List.of()), choices.getOrDefault("target", List.of()),
                    confirmedSelections.get("target"), providerAt, objects(root, "processingProviders"), names);
            element("processing_summary", Label.class).setText(processing.summary());
        }
    }

    /** Opens a Provider's mappings, or follows an Endpoint to its owner's: the two places the topology links to. */
    void openObject(String group, String id) {
        if (group.equals("endpoint_mapping")) {
            navigateEndpoint(id);
            return;
        }
        navigationGroup = group;
        navigationId = id;
        if (id.equals(confirmedSelections.get(group))) {
            show("mapping");
            navigationGroup = null;
        } else {
            select.accept(group + ":" + id);
        }
    }

    /** The objects of one root array, or none when an older payload leaves it out. */
    private static List<JsonObject> objects(JsonObject root, String field) {
        if (!root.has(field)) return List.of();
        var values = new java.util.ArrayList<JsonObject>();
        root.getAsJsonArray(field).forEach(value -> values.add(value.getAsJsonObject()));
        return values;
    }

    static net.minecraft.network.chat.MutableComponent trLocation(String key, Object... arguments) {
        return Component.translatable("ae2federation.ui.location." + key, arguments);
    }

    static net.minecraft.network.chat.MutableComponent tr(String key, Object... arguments) {
        return Component.translatable("ae2federation.ui.workspace." + key, arguments);
    }

    private <T> T element(String id, Class<T> type) {
        return ui.selectId(id, type).findFirst().orElseThrow();
    }
}
