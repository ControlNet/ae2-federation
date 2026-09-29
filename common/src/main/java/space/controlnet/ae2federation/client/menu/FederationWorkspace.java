package space.controlnet.ae2federation.client.menu;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AmountFormat;
import net.minecraft.network.chat.Component;

import net.minecraft.world.item.ItemStack;

/** Local navigation and presentation; every business selection still goes through the authorized menu request. */
final class FederationWorkspace {
    private static final List<String> PAGES = List.of("overview", "mapping", "diagnostics");
    /** The server's choice groups; only the Endpoint diagnostics keep a selector, the wires view is the rest. */
    private static final List<String> CHOICE_GROUPS = List.of("mapping_provider", "slot", "target", "endpoint");
    private static final Map<String, String> SELECTORS = Map.of("endpoint", "endpoint_next");
    private final Map<JsonObject, List<GenericStack>> resourceCache = new java.util.IdentityHashMap<>();
    private final UI ui;
    private final FederationEndpointBrowser endpointBrowser;
    private final Consumer<String> select;
    private final Map<String, Selector<String>> selectors = new HashMap<>();
    private final Map<String, String> confirmedSelections = new HashMap<>();
    private final Map<String, List<JsonObject>> choices = new HashMap<>();
    private final Map<String, String> choiceFilters = new HashMap<>();
    private final Map<String, Label> choiceEmptyLabels = new HashMap<>();
    private boolean entranceApplied;
    private String navigationGroup;
    private String navigationId;
    private String endpointNavigationReceipt;
    private FederationTopologyView topology;
    private FederationProcessingGraph processing;
    private boolean authorityAllowsNavigation;
    private String page = "overview";

    @SuppressWarnings("unchecked")
    FederationWorkspace(UI ui, Consumer<String> select) {
        this.ui = ui;
        this.select = select;
        endpointBrowser = new FederationEndpointBrowser(ui, select);
        element("endpoint_mapping", Button.class).setOnClick(event -> navigateEndpoint());
        element("endpoint_browse", Button.class).setOnClick(event -> endpointBrowser.open());
        // The design's title is bold; the tab name after it is not.
        element("domain_title", Label.class).setText(Component.translatable("ae2federation.ui.domain.title")
                .withStyle(net.minecraft.ChatFormatting.BOLD));
        var icons = Map.of("overview", FederationIcons.TOPOLOGY, "mapping", FederationIcons.PROCESSING,
                "diagnostics", FederationIcons.DIAGNOSTICS);
        for (var page : PAGES) {
            var tab = element("tab_" + page, Button.class);
            tab.noText();
            tab.addChild(new UIElement().layout(style -> style.widthPercent(100).heightPercent(100))
                    .style(style -> style.backgroundTexture(icons.get(page))));
            tab.style(style -> style.tooltips(tr(page)));
            tab.setOnClick(event -> show(page));
        }
        show("overview");
        SELECTORS.forEach((group, id) -> {
            var selector = (Selector<String>) element(id, Selector.class);
            selector.buttonIcon.style(style -> style.backgroundTexture(
                    com.lowdragmc.lowdraglib2.gui.texture.Icons.DOWN_ARROW_NO_BAR.copy().setColor(FederationTheme.TEXT)));
            selector.setCandidateUIProvider(value -> {
                var label = new Label();
                label.addClass("choice-label");
                if (value != null) label.addClass("choice-" + group + "-" + value.replaceAll("[^a-zA-Z0-9_-]", "_"));
                label.setText(choiceText(group, value));
                label.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = choiceTooltip(group, value));
                return label;
            });
            selector.selectorStyle(style -> style.maxItemCount(5).scrollerViewHeight(90));
            selector.setOnValueChanged(value -> {
                if (value != null) select.accept(group + ":" + value);
                selector.setValue(confirmedSelections.get(group), false);
            });
            selectors.put(group, selector);
            {
                var search = new TextField();
                search.setId(id + "_search");
                search.layout(style -> style.height(18).widthPercent(100).flexShrink(0));
                search.textFieldStyle(style -> style.placeholder(tr("search_devices").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)));
                var empty = new Label();
                empty.setId(id + "_empty");
                empty.setText(tr("no_choices"));
                empty.layout(style -> style.height(18).paddingAll(3));
                empty.setDisplay(false);
                choiceEmptyLabels.put(group, empty);
                selector.dialog.addChildAt(search, 0).addChild(empty);
                search.setTextResponder(value -> {
                    choiceFilters.put(group, value.strip().toLowerCase(Locale.ROOT));
                    refreshChoices(group);
                });
            }
        });
    }

    void updateNavigationAuthority(boolean allowed, boolean rejected) {
        authorityAllowsNavigation = allowed;
        if (rejected) endpointNavigationReceipt = null;
        updateEndpointNavigation();
    }

    private void updateEndpointNavigation() {
        var id = confirmedSelections.get("endpoint");
        var endpoint = choices.getOrDefault("endpoint", List.of()).stream()
                .filter(choice -> choice.get("id").getAsString().equals(id)).findFirst().orElse(null);
        boolean available = endpoint != null && endpoint.has("mappingNavigation")
                && endpoint.get("mappingNavigation").getAsBoolean();
        var button = element("endpoint_mapping", Button.class);
        button.setActive(authorityAllowsNavigation && available);
        button.style(style -> style.tooltips(tr(available ? "endpoint_navigation_help" : "endpoint_navigation_unavailable")));
    }

    private void navigateEndpoint() {
        var endpoint = confirmedSelections.get("endpoint");
        if (!authorityAllowsNavigation || endpoint == null) return;
        endpointNavigationReceipt = "endpoint_mapping:" + endpoint + "/" + java.util.UUID.randomUUID();
        select.accept(endpointNavigationReceipt);
    }

    void bindProcessing(Consumer<String> setMapping, Runnable release) {
        processing = new FederationProcessingGraph(ui, setMapping, select, release, this::patternName, this::outputStack,
                this::patternFacts);
        if (topology != null) topology.onSearch(processing::filter);
    }

    void setProcessingEditable(boolean editable) {
        if (processing != null) processing.setEditable(editable);
    }

    void bindGraph(FederationTopologyView graph) {
        topology = graph;
        // One header search: it dims networks in the topology and filters Providers, patterns and Endpoints here.
        if (processing != null) graph.onSearch(processing::filter);
        element("endpoint_locate", Button.class).setOnClick(event -> {
            var id = confirmedSelections.get("endpoint");
            if (id != null && graph.focusObject(id)) show("overview");
        });
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
        }
        element("graph_search", UIElement.class).setDisplay("overview".equals(page) || mapping);
        // Close floating selectors when navigating away from their anchors.
        selectors.values().forEach(Selector::hide);
    }

    /** The footer repeats processing feedback on the processing page only, and only when there is something to say. */
    void updateFeedback() {
        var feedback = element("processing_status", UIElement.class);
        feedback.setDisplay("mapping".equals(page) && !feedback.hasClass("feedback-neutral"));
    }

    void acceptChoices(String encoded) {
        if (encoded.isEmpty()) return;
        resourceCache.clear();
        var root = JsonParser.parseString(encoded).getAsJsonObject();
        endpointBrowser.accept(root);
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
        boolean hasDomain = selected.has("consumer");
        boolean localEndpoint = !hasDomain && root.has("localEndpointPosition");
        element("endpoint_next", Selector.class).setDisplay(!localEndpoint);
        var localLabel = element("endpoint_local", Label.class);
        localLabel.setDisplay(localEndpoint);
        if (localEndpoint) localLabel.setText(tr("local_endpoint", root.get("localEndpointPosition").getAsString()));
        element("diagnostics_description", Label.class).setText(tr(localEndpoint ? "local_diagnostics_help" : "diagnostics_help"));
        for (var id : List.of("endpoint_browse")) {
            var button = element(id, Button.class);
            button.setActive(hasDomain);
            button.style(style -> style.tooltips(tr(hasDomain ? "domain_browse_help" : "domain_browse_unavailable")));
        }
        for (var group : CHOICE_GROUPS) {
            var values = new ArrayList<JsonObject>();
            root.getAsJsonArray(group).forEach(value -> values.add(value.getAsJsonObject()));
            var previous = choices.put(group, List.copyOf(values));
            var selector = selectors.get(group);
            if (!values.equals(previous) && selector != null) refreshChoices(group);
            var confirmed = selected.has(group) ? selected.get(group).getAsString() : null;
            confirmedSelections.put(group, confirmed);
            if (selector != null) selector.setValue(confirmed, false);
        }
        element("endpoint_locate", Button.class).setActive(confirmedSelections.get("endpoint") != null);
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
            show(navigationGroup.equals("mapping_provider") ? "mapping" : "diagnostics");
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

    private void refreshChoices(String group) {
        var query = choiceFilters.getOrDefault(group, "");
        var matches = choices.getOrDefault(group, List.of()).stream()
                .map(value -> value.get("id").getAsString())
                .filter(id -> query.isEmpty() || (id + " " + choiceText(group, id).getString())
                        .toLowerCase(Locale.ROOT).contains(query)).toList();
        selectors.get(group).setCandidates(matches);
        var empty = choiceEmptyLabels.get(group);
        if (empty != null) empty.setDisplay(matches.isEmpty());
    }

    void openObject(String group, String id) {
        navigationGroup = group;
        navigationId = id;
        if (id.equals(confirmedSelections.get(group))) {
            show(group.equals("mapping_provider") ? "mapping" : "diagnostics");
            navigationGroup = null;
        } else {
            select.accept(group + ":" + id);
        }
    }

    private HoverTooltips choiceTooltip(String group, String id) {
        var tooltip = HoverTooltips.empty().append(choiceText(group, id));
        var choice = choices.getOrDefault(group, List.of()).stream()
                .filter(value -> value.get("id").getAsString().equals(id)).findFirst().orElse(null);
        if (group.equals("target") && choice != null && choice.has("state")) {
            tooltip = tooltip.append(tr("target_state." + choice.get("state").getAsString() + ".detail"));
            if (choice.has("owner")) tooltip = tooltip.append(tr("target_owner", choice.get("owner").getAsString(),
                    choice.get("ownerInstance").getAsLong()));
        }
        // Choice ids are internal hashes: the position and state above already say which device this is.
        return tooltip;
    }

    private Component choiceText(String group, String id) {
        if (id == null) return tr("none");
        var choice = choices.getOrDefault(group, List.of()).stream()
                .filter(value -> value.get("id").getAsString().equals(id)).findFirst().orElse(null);
        if (choice == null) return Component.literal(id);
        if (group.equals("slot")) return Component.literal("#" + id + " ").append(patternName(choice));
        if (group.equals("target") && choice.has("state")) return tr("target_choice",
                tr("target_state." + choice.get("state").getAsString()),
                choice.has("position") ? choice.get("position").getAsString() : choice.get("label").getAsString());
        if (choice.has("position")) return tr(group.equals("mapping_provider") ? "provider_at" : "endpoint_at",
                choice.get("position").getAsString());
        return Component.literal(choice.get("label").getAsString());
    }

    /** A pattern's outputs and inputs with exact amounts, for the wires view's pattern detail and search. */
    private List<Component> patternFacts(JsonObject value) {
        var lines = new ArrayList<Component>();
        for (var output : resources(value, "outputs")) lines.add(tr("output_resource", output.what().getDisplayName(), amount(output)));
        for (var input : resources(value, "inputs")) lines.add(tr("input_resource", input.what().getDisplayName(), amount(input)));
        return lines;
    }

    private List<GenericStack> resources(JsonObject choice, String field) {
        var array = choice.getAsJsonArray(field);
        if (array == null || array.isEmpty()) return List.of();
        // Cache each immutable resource object for the lifetime of this synchronized snapshot.
        return array.asList().stream().map(value -> resourceCache.computeIfAbsent(value.getAsJsonObject(), resource -> {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            if (level == null) return List.of();
            var ops = level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
            return GenericStack.CODEC.parse(ops, resource).result().map(List::of).orElseGet(List::of);
        })).flatMap(List::stream).toList();
    }

    private static String amount(GenericStack stack) {
        return stack.what().formatAmount(stack.amount(), AmountFormat.FULL);
    }

    /** The objects of one root array, or none when an older payload leaves it out. */
    private static List<JsonObject> objects(JsonObject root, String field) {
        if (!root.has(field)) return List.of();
        var values = new java.util.ArrayList<JsonObject>();
        root.getAsJsonArray(field).forEach(value -> values.add(value.getAsJsonObject()));
        return values;
    }

    private ItemStack outputStack(JsonObject choice) {
        var outputs = resources(choice, "outputs");
        if (outputs.isEmpty()) return ItemStack.EMPTY;
        var output = outputs.getFirst();
        return output.what() instanceof AEItemKey item ? item.toStack() : GenericStack.wrapInItemStack(output);
    }

    private Component patternName(JsonObject choice) {
        if (choice.get("empty").getAsBoolean()) return tr("empty_slot");
        var outputs = resources(choice, "outputs");
        return outputs.isEmpty() ? Component.literal(choice.get("label").getAsString()) : outputs.getFirst().what().getDisplayName();
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
