package space.controlnet.ae2federation.client.menu;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.FlexDirection;
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
    private static final List<String> PAGES = List.of("overview", "policy", "mapping", "diagnostics");
    private static final Map<String, String> SELECTORS = Map.of(
            "consumer", "consumer_next", "provider", "provider_next", "capability", "capability_next",
            "mapping_provider", "mapping_provider_next", "slot", "mapping_slot_next",
            "target", "mapping_lane_next", "endpoint", "endpoint_next");
    private final Map<JsonObject, List<GenericStack>> resourceCache = new java.util.IdentityHashMap<>();
    private final UI ui;
    private final FederationPolicyBrowser policyBrowser;
    private final FederationEndpointBrowser endpointBrowser;
    private final Consumer<String> select;
    private final Map<String, Selector<String>> selectors = new HashMap<>();
    private final Map<String, String> confirmedSelections = new HashMap<>();
    private final Map<String, List<JsonObject>> choices = new HashMap<>();
    private final Map<String, String> choiceFilters = new HashMap<>();
    private final Map<String, Label> choiceEmptyLabels = new HashMap<>();
    private final VirtualScrollerView<JsonObject> patterns;
    private boolean entranceApplied;
    private boolean showEmpty;
    private String navigationGroup;
    private String navigationId;
    private String endpointNavigationReceipt;
    private String endpointNavigationPage;
    private boolean authorityAllowsNavigation;
    private String filter = "";
    private String selectedSlot = "";
    private String patternSignature = "";

    @SuppressWarnings("unchecked")
    FederationWorkspace(UI ui, Consumer<String> select) {
        this.ui = ui;
        this.select = select;
        policyBrowser = new FederationPolicyBrowser(ui, select);
        endpointBrowser = new FederationEndpointBrowser(ui, select);
        element("endpoint_mapping", Button.class).setOnClick(event -> navigateEndpoint("mapping"));
        element("endpoint_policy", Button.class).setOnClick(event -> navigateEndpoint("policy"));
        element("endpoint_browse", Button.class).setOnClick(event -> endpointBrowser.open());
        element("policy_browse", Button.class).setOnClick(event -> policyBrowser.open());
        for (var page : PAGES) element("tab_" + page, Button.class).setOnClick(event -> show(page));
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
            if (!group.equals("capability")) {
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
        patterns = (VirtualScrollerView<JsonObject>) element("pattern_list", VirtualScrollerView.class);
        patterns.setItemUIProvider(this::patternRow);
        element("pattern_show_empty", Button.class).setOnClick(event -> {
            showEmpty = !showEmpty;
            FederationGraphPresenter.mark(element("pattern_show_empty", Button.class), showEmpty);
            refreshPatterns();
        });
        element("pattern_search", TextField.class).textFieldStyle(style -> style.placeholder(tr("search_patterns").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)));
        element("pattern_search", TextField.class).setTextResponder(value -> {
            filter = value.toLowerCase(Locale.ROOT);
            refreshPatterns();
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
        for (var destination : List.of("mapping", "policy")) {
            boolean available = endpoint != null && endpoint.has(destination + "Navigation")
                    && endpoint.get(destination + "Navigation").getAsBoolean();
            var button = element("endpoint_" + destination, Button.class);
            button.setActive(authorityAllowsNavigation && available);
            button.style(style -> style.tooltips(tr(available ? "endpoint_navigation_help" : "endpoint_navigation_unavailable")));
        }
    }

    private void navigateEndpoint(String destination) {
        var endpoint = confirmedSelections.get("endpoint");
        if (!authorityAllowsNavigation || endpoint == null) return;
        endpointNavigationReceipt = "endpoint_" + destination + ":" + endpoint + "/" + java.util.UUID.randomUUID();
        endpointNavigationPage = destination;
        select.accept(endpointNavigationReceipt);
    }

    void bindGraph(FederationGraphPresenter graph) {
        element("endpoint_locate", Button.class).setOnClick(event -> {
            var id = confirmedSelections.get("endpoint");
            if (id != null && graph.focusObject(id)) show("overview");
        });
    }

    void show(String page) {
        navigationGroup = null;
        for (var candidate : PAGES) {
            element("page_" + candidate, UIElement.class).setDisplay(candidate.equals(page));
            var button = element("tab_" + candidate, Button.class);
            button.removeClass("selected");
            if (candidate.equals(page)) button.addClass("selected");
        }
        // Close floating selectors when navigating away from their anchors.
        selectors.values().forEach(Selector::hide);
    }

    void acceptChoices(String encoded) {
        if (encoded.isEmpty()) return;
        resourceCache.clear();
        var root = JsonParser.parseString(encoded).getAsJsonObject();
        policyBrowser.accept(root);
        endpointBrowser.accept(root);
        if (!entranceApplied) {
            if (root.has("initialPage")) show(root.get("initialPage").getAsString());
            element("return_provider", Button.class).setDisplay(root.has("returnProvider") && root.get("returnProvider").getAsBoolean());
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
        for (var id : List.of("endpoint_browse", "policy_browse")) {
            var button = element(id, Button.class);
            button.setActive(hasDomain);
            button.style(style -> style.tooltips(tr(hasDomain ? "domain_browse_help" : "domain_browse_unavailable")));
        }
        for (var group : SELECTORS.keySet()) {
            var values = new ArrayList<JsonObject>();
            root.getAsJsonArray(group).forEach(value -> values.add(value.getAsJsonObject()));
            var previous = choices.put(group, List.copyOf(values));
            var selector = selectors.get(group);
            if (!values.equals(previous)) refreshChoices(group);
            var confirmed = selected.has(group) ? selected.get(group).getAsString() : null;
            confirmedSelections.put(group, confirmed);
            selector.setValue(confirmed, false);
        }
        element("endpoint_locate", Button.class).setActive(confirmedSelections.get("endpoint") != null);
        updateEndpointNavigation();
        if (endpointNavigationReceipt != null && root.has("navigationReceipt")
                && endpointNavigationReceipt.equals(root.get("navigationReceipt").getAsString())) {
            show(endpointNavigationPage);
            endpointNavigationReceipt = null;
        }
        boolean hasPolicyPair = confirmedSelections.get("consumer") != null && confirmedSelections.get("provider") != null;
        element("policy_direction", Label.class).setText(hasPolicyPair ? tr("policy_direction",
                choiceText("consumer", confirmedSelections.get("consumer")),
                choiceText("provider", confirmedSelections.get("provider")),
                choiceText("capability", confirmedSelections.get("capability"))) : tr("policy_pair_unavailable"));
        if (navigationGroup != null && navigationId.equals(confirmedSelections.get(navigationGroup))) {
            show(navigationGroup.equals("mapping_provider") ? "mapping" : "diagnostics");
            navigationGroup = null;
        }
        selectedSlot = selected.has("slot") ? selected.get("slot").getAsString() : "";
        var signature = root.getAsJsonArray("slot").toString() + selectedSlot;
        if (!patternSignature.equals(signature)) {
            patternSignature = signature;
            refreshPatterns();
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
        if (id != null) tooltip = tooltip.append(Component.literal(id));
        return tooltip;
    }

    private Component choiceText(String group, String id) {
        if (id == null) return tr("none");
        if (group.equals("capability")) return tr("capability." + id.toLowerCase(Locale.ROOT));
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

    private void refreshPatterns() {
        var all = choices.getOrDefault("slot", List.of());
        var visible = all.stream().filter(value -> showEmpty || !value.get("empty").getAsBoolean())
                .filter(value -> filter.isEmpty() || (value.get("id").getAsString() + " " + searchablePattern(value))
                        .toLowerCase(Locale.ROOT).contains(filter)).toList();
        patterns.setItems(visible);
        element("pattern_summary", Label.class).setText(tr("pattern_summary", visible.size(),
                all.stream().filter(value -> !value.get("empty").getAsBoolean()).count()));
        var empty = element("pattern_empty", Label.class);
        empty.setDisplay(visible.isEmpty());
        empty.setText(tr(all.isEmpty() ? "no_provider" : !filter.isEmpty() ? "no_matches" : "no_patterns"));
        patterns.setDisplay(!visible.isEmpty());
    }

    private UIElement patternRow(JsonObject value) {
        var row = new Button();
        row.noText();
        row.addClass("pattern-row");
        row.setId("pattern_slot_" + value.get("id").getAsString());
        row.layout(style -> style.height(36).paddingAll(3).gapAll(4).flexDirection(FlexDirection.ROW));
        if (value.get("id").getAsString().equals(selectedSlot)) row.addClass("selected");
        var stack = outputStack(value);
        if (!stack.isEmpty()) {
            var icon = new UIElement();
            icon.layout(style -> style.width(18).height(18));
            icon.style(style -> style.backgroundTexture(new ItemStackTexture(stack)));
            row.addChild(icon);
        }
        var text = new Label();
        text.addClass("virtual-row");
        var summary = Component.literal("#" + value.get("id").getAsString() + " ").append(patternName(value));
        var outputs = resources(value, "outputs");
        if (!outputs.isEmpty()) {
            summary.append(" × " + amount(outputs.getFirst()));
            if (outputs.size() > 1) summary.append(" +" + (outputs.size() - 1));
        }
        var inputs = resources(value, "inputs");
        if (!inputs.isEmpty()) {
            summary.append("\n").append(tr("inputs", inputs.stream().map(FederationWorkspace::amount)
                    .collect(java.util.stream.Collectors.joining(", "))));
        }
        summary.append("\n").append(tr("mapped_count", value.get("mapped").getAsInt()));
        text.setText(summary);
        text.layout(style -> style.flex(1).height(30));
        text.textStyle(style -> style.fontSize(7).textShadow(false));
        row.addChild(text);
        row.setOnClick(event -> select.accept("slot:" + value.get("id").getAsString()));
        var tooltip = new ArrayList<Component>();
        tooltip.add(Component.literal("#" + value.get("id").getAsString() + " ").append(patternName(value)));
        for (var output : outputs) tooltip.add(tr("output_resource", output.what().getDisplayName(), amount(output)));
        for (var input : inputs) tooltip.add(tr("input_resource", input.what().getDisplayName(), amount(input)));
        tooltip.add(tr("mapped_count", value.get("mapped").getAsInt()));
        row.style(style -> style.tooltips(tooltip.toArray(Component[]::new)));
        return row;
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

    private String searchablePattern(JsonObject choice) {
        return patternName(choice).getString() + " " + resources(choice, "outputs").stream()
                .map(output -> output.what().getDisplayName().getString()).collect(java.util.stream.Collectors.joining(" "));
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

    static net.minecraft.network.chat.MutableComponent tr(String key, Object... arguments) {
        return Component.translatable("ae2federation.ui.workspace." + key, arguments);
    }

    private <T> T element(String id, Class<T> type) {
        return ui.selectId(id, type).findFirst().orElseThrow();
    }
}
