package space.controlnet.ae2federation.client.menu;

import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.client.shader.LDLibRenderTypes;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.GraphView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.joml.Vector2f;
import space.controlnet.ae2federation.client.domain.FederationDomainGraphLayer;
import space.controlnet.ae2federation.client.domain.FederationDomainGraphNodeKind;
import space.controlnet.ae2federation.client.domain.FederationDomainGraphSnapshot;
import space.controlnet.ae2federation.client.policy.PolicySwitchTarget;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyRevision;

/**
 * The domain seen as networks: a card per ME network, one edge per network pair with configured rules, and a pair
 * editor whose switches each request one explicit rule state. Layout and colours are local presentation only.
 */
final class FederationTopologyView {
    static final float CARD_WIDTH = 160;
    static final float CARD_HEIGHT = 46;
    private static final PolicyCapability[] CAPABILITIES = PolicyCapability.values();

    private final GraphView graph;
    private final Consumer<String> setPolicy;
    private final BiConsumer<String, String> openObject;
    private final Label title;
    private final Label identity;
    private final UIElement accent;
    private final Label detail;
    private final Button devices;
    private final Label linksHeading;
    private final UIElement links;
    private final UIElement networkDetail;
    private final UIElement pairEditor;
    private final Label pairTitle;
    private final UIElement pairSections;
    private final Label searchEmpty;

    private final List<Network> networks = new ArrayList<>();
    private final Map<String, JsonObject> rules = new HashMap<>();
    private final Map<String, Long> revisions = new HashMap<>();
    private final Map<String, String> memberStatus = new HashMap<>();
    private final Map<String, List<String>> providersByMember = new HashMap<>();
    private final Map<String, List<String>> endpointsByMember = new HashMap<>();
    private final Map<String, Vector2f> positions = new LinkedHashMap<>();
    private final Map<String, Button> cards = new HashMap<>();
    private String focus = "";
    private boolean focusApplied;
    private String selectedNetwork = "";
    private String selectedPair = "";
    private String structure = "";
    private String asideSignature = "";
    private String search = "";
    private boolean editable;
    private boolean fitted;
    private String pendingCenter = "";
    private String scope = "domain";

    FederationTopologyView(UI ui, Consumer<String> setPolicy, BiConsumer<String, String> openObject) {
        this.setPolicy = setPolicy;
        this.openObject = openObject;
        graph = element(ui, "domain_graph", GraphView.class);
        title = element(ui, "network_title", Label.class);
        identity = element(ui, "network_identity", Label.class);
        accent = element(ui, "network_accent", UIElement.class);
        detail = element(ui, "graph_selection", Label.class);
        devices = element(ui, "graph_open", Button.class);
        linksHeading = element(ui, "network_links_heading", Label.class);
        links = element(ui, "network_links", UIElement.class);
        networkDetail = element(ui, "network_detail", UIElement.class);
        pairEditor = element(ui, "pair_editor", UIElement.class);
        pairTitle = element(ui, "pair_title", Label.class);
        pairSections = element(ui, "pair_sections", UIElement.class);
        searchEmpty = element(ui, "graph_search_empty", Label.class);
        searchEmpty.setText(tr("no_network_matches"));
        var searchField = element(ui, "graph_search", TextField.class);
        searchField.textFieldStyle(style -> style.placeholder(tr("search").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)));
        searchField.setTextResponder(value -> {
            search = value.strip().toLowerCase(Locale.ROOT);
            applySearch();
        });
        element(ui, "graph_zoom_in", Button.class).setOnClick(event -> graph.setScale(graph.getScale() * 1.25f));
        element(ui, "graph_zoom_out", Button.class).setOnClick(event -> graph.setScale(graph.getScale() / 1.25f));
        element(ui, "graph_fit", Button.class).setOnClick(event -> graph.fitToChildren(12, 0.25f));
        devices.setOnClick(event -> openDevices());
        pairEditor.setDisplay(false);
    }

    /** Device-to-network membership and live network status come from the scoped graph projection. */
    void accept(String encoded) {
        if (encoded.isEmpty()) return;
        var snapshot = FederationDomainGraphSnapshot.decode(encoded);
        memberStatus.clear();
        providersByMember.clear();
        endpointsByMember.clear();
        var kinds = new HashMap<String, FederationDomainGraphNodeKind>();
        for (var node : snapshot.nodes()) {
            kinds.put(node.id(), node.kind());
            if (node.kind() == FederationDomainGraphNodeKind.MEMBER) memberStatus.put(node.id(), node.status());
        }
        for (var edge : snapshot.edges()) {
            if (edge.layer() != FederationDomainGraphLayer.PHYSICAL) continue;
            var target = kinds.get(edge.to()) == FederationDomainGraphNodeKind.PROVIDER ? providersByMember : endpointsByMember;
            target.computeIfAbsent(edge.from(), key -> new ArrayList<>()).add(edge.to());
        }
        refresh();
    }

    void acceptChoices(JsonObject root) {
        // An unpublished domain sends no members; keep the last view until the server reports the context stale.
        if (!root.has("networks") && !networks.isEmpty()) return;
        scope = root.has("scope") ? root.get("scope").getAsString() : "domain";
        networks.clear();
        if (root.has("networks")) {
            var index = 0;
            for (var value : root.getAsJsonArray("networks")) {
                var json = value.getAsJsonObject();
                networks.add(new Network(json.get("id").getAsString(), json.get("member").getAsString(), index++));
            }
        }
        rules.clear();
        if (root.has("rules")) for (var value : root.getAsJsonArray("rules")) {
            var rule = value.getAsJsonObject();
            rules.put(key(rule.get("consumer").getAsString(), rule.get("provider").getAsString(),
                    rule.get("capability").getAsString()), rule);
        }
        revisions.clear();
        if (root.has("revisions")) for (var value : root.getAsJsonArray("revisions")) {
            var row = value.getAsJsonObject();
            revisions.put(key(row.get("consumer").getAsString(), row.get("provider").getAsString(),
                    row.get("capability").getAsString()), row.get("revision").getAsLong());
        }
        if (!focusApplied && root.has("initialGraphFocus")) focus = root.get("initialGraphFocus").getAsString();
        if (selectedNetwork.isEmpty() && selectedPair.isEmpty() && !networks.isEmpty()) {
            if (networks.size() == 2) {
                selectedPair = pair(networks.get(0).id(), networks.get(1).id());
            } else {
                selectedNetwork = networks.stream().filter(network -> network.member().equals(focus)).findFirst()
                        .orElse(networks.getFirst()).id();
            }
        }
        if (!selectedNetwork.isEmpty() && network(selectedNetwork) == null) selectedNetwork = "";
        refresh();
    }

    void setEditable(boolean value) {
        if (editable == value) return;
        editable = value;
        asideSignature = "";
        refresh();
    }

    /** Selects the network that hosts the given Provider or Endpoint and centres it. */
    boolean focusObject(String objectId) {
        for (var network : networks) {
            if (providersByMember.getOrDefault(network.member(), List.of()).contains(objectId)
                    || endpointsByMember.getOrDefault(network.member(), List.of()).contains(objectId)) {
                selectNetwork(network.id());
                pendingCenter = network.id();
                focusApplied = true;
                return true;
            }
        }
        return false;
    }

    /** Opens the pair editor for a relationship the server has just selected, for example from diagnostics. */
    void selectPair(String consumer, String provider) {
        if (network(consumer) == null || network(provider) == null || consumer.equals(provider)) return;
        selectedPair = pair(consumer, provider);
        selectedNetwork = "";
        focusApplied = true;
        refresh();
    }

    private void refresh() {
        var signature = new StringBuilder();
        networks.forEach(network -> signature.append(network.id()).append(','));
        signature.append('|').append(selectedNetwork).append('|').append(selectedPair).append('|');
        pairsWithRules().forEach(value -> signature.append(value).append(';'));
        rules.values().forEach(rule -> signature.append(rule.get("capability").getAsString())
                .append(rule.get("enabled").getAsBoolean()));
        memberStatus.forEach((member, status) -> signature.append(member).append('=').append(status));
        if (!signature.toString().equals(structure)) {
            structure = signature.toString();
            rebuildGraph();
        }
        renderAside();
        applySearch();
    }

    private void rebuildGraph() {
        graph.clearAllContentChildren();
        cards.clear();
        layout();
        graph.addContentChild(new Links());
        for (var pair : pairsWithRules()) graph.addContentChild(edgePill(pair));
        for (var network : networks) graph.addContentChild(card(network));
    }

    /** Networks on an ellipse, the entrance network first on the left; two networks sit side by side. */
    private void layout() {
        positions.clear();
        var ordered = new ArrayList<>(networks);
        ordered.stream().filter(network -> network.member().equals(focus)).findFirst().ifPresent(first -> {
            ordered.remove(first);
            ordered.addFirst(first);
        });
        int count = ordered.size();
        float radius = count <= 2 ? 120 : (float) Math.max(110, 80 / Math.sin(Math.PI / count));
        var raw = new ArrayList<Vector2f>();
        for (int i = 0; i < count; i++) {
            double angle = Math.PI + 2 * Math.PI * i / Math.max(1, count);
            raw.add(new Vector2f((float) (Math.cos(angle) * radius * 1.35f), (float) (Math.sin(angle) * radius * 0.8f)));
        }
        float minX = raw.stream().map(point -> point.x).min(Float::compare).orElse(0f);
        float minY = raw.stream().map(point -> point.y).min(Float::compare).orElse(0f);
        for (int i = 0; i < count; i++) {
            positions.put(ordered.get(i).id(), new Vector2f(raw.get(i).x - minX + 8, raw.get(i).y - minY + 8));
        }
    }

    private Button card(Network network) {
        var position = positions.get(network.id());
        var button = new Button();
        button.noText();
        button.addClass("graph-node-member");
        button.setId("graph_node_" + sanitize(network.member()));
        boolean selected = network.id().equals(selectedNetwork) || selectedPair.contains(network.id());
        var status = memberStatus.getOrDefault(network.member(), "pending");
        int statusColor = status.equals("online") ? FederationTheme.OK : FederationTheme.WARN;
        var face = GuiTextureGroup.of(selected ? FederationTheme.CARD_SELECTED : FederationTheme.CARD,
                FederationTheme.accentLine(statusColor));
        button.buttonStyle(style -> style.baseTexture(face).hoverTexture(GuiTextureGroup.of(FederationTheme.CARD_SELECTED,
                FederationTheme.accentLine(statusColor))).pressedTexture(face));
        button.layout(style -> style.positionType(TaffyPosition.ABSOLUTE).left(position.x).top(position.y)
                .width(CARD_WIDTH).height(CARD_HEIGHT).paddingAll(5).paddingBottom(6).gapAll(2)
                .flexDirection(FlexDirection.COLUMN).alignItems(AlignItems.FLEX_START));
        var head = new UIElement();
        head.layout(style -> style.widthPercent(100).height(10).flexDirection(FlexDirection.ROW).gapAll(3)
                .alignItems(AlignItems.CENTER));
        var swatch = new UIElement();
        swatch.layout(style -> style.width(6).height(6).flexShrink(0));
        swatch.style(style -> style.backgroundTexture(FederationTheme.solid(network.accent())));
        var heading = text(name(network), FederationTheme.DARK_TITLE);
        heading.layout(style -> style.flex(1).minWidth(0).widthAuto());
        head.addChildren(swatch, heading);
        button.addChildren(head, text(tr("network_status." + status).withStyle(Style.EMPTY.withColor(statusColor & 0xffffff)),
                FederationTheme.DARK_TEXT), text(tr("network_devices", providers(network).size(), endpoints(network).size()),
                FederationTheme.DARK_MUTED));
        button.setOnClick(event -> selectNetwork(network.id()));
        button.style(style -> style.tooltips(name(network), Component.literal(network.id())));
        cards.put(network.id(), button);
        return button;
    }

    private Button edgePill(String pair) {
        var ends = pair.split("\\|");
        var a = network(ends[0]);
        var b = network(ends[1]);
        var from = center(a.id());
        var to = center(b.id());
        var summary = Component.empty().append(direction(a, b));
        var reverse = direction(b, a);
        if (!reverse.getString().isEmpty()) {
            if (!summary.getString().isEmpty()) summary.append("\n");
            summary.append(reverse);
        }
        var lines = summary.getString().split("\n").length;
        var width = Math.max(60, net.minecraft.client.Minecraft.getInstance().font.width(summary.getString().lines()
                .max(java.util.Comparator.comparingInt(String::length)).orElse("")) + 10);
        var button = new Button();
        button.setId("graph_pair_" + sanitize(a.member()) + "_" + sanitize(b.member()));
        button.addClass("graph-pair");
        button.setText(summary);
        button.textStyle(style -> style.textWrap(TextWrap.NONE).textColor(FederationTheme.DARK_TEXT));
        boolean selected = pair.equals(selectedPair);
        var face = GuiTextureGroup.of(FederationTheme.WELL_RECT,
                new com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture(1, selected ? FederationTheme.SELECT : 0xff47434f));
        button.buttonStyle(style -> style.baseTexture(face).hoverTexture(GuiTextureGroup.of(FederationTheme.WELL_RECT,
                new com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture(1, FederationTheme.SELECT))).pressedTexture(face));
        float height = lines * 10 + 6;
        button.layout(style -> style.positionType(TaffyPosition.ABSOLUTE).left((from.x + to.x) / 2 - width / 2f)
                .top((from.y + to.y) / 2 - height / 2).width(width).height(height).paddingAll(2));
        button.setOnClick(event -> {
            selectedPair = pair;
            selectedNetwork = "";
            focusApplied = true;
            refresh();
        });
        return button;
    }

    /** "3F9A▸81D0 Storage Crafting", each capability coloured by its configured and observed state. */
    private MutableComponent direction(Network consumer, Network provider) {
        var line = Component.empty();
        boolean any = false;
        for (var capability : CAPABILITIES) {
            var rule = rules.get(key(consumer.id(), provider.id(), capability.name()));
            if (rule == null) continue;
            if (!any) line.append(Component.literal(tag(consumer) + "▸" + tag(provider) + " ")
                    .withStyle(Style.EMPTY.withColor(FederationTheme.DARK_MUTED & 0xffffff)));
            else line.append(" ");
            any = true;
            var state = ruleState(rule);
            var chip = capabilityName(capability).copy().withStyle(Style.EMPTY.withColor(state.color() & 0xffffff));
            if (!rule.get("enabled").getAsBoolean()) chip = chip.withStyle(net.minecraft.ChatFormatting.STRIKETHROUGH);
            line.append(chip);
        }
        return line;
    }

    private void selectNetwork(String id) {
        selectedNetwork = id;
        selectedPair = "";
        focusApplied = true;
        refresh();
    }

    private void renderAside() {
        var signature = scope + "|" + selectedNetwork + "|" + selectedPair + "|" + editable + "|" + rules + "|" + revisions + "|"
                + memberStatus + "|" + providersByMember + "|" + endpointsByMember + "|" + networks;
        if (signature.equals(asideSignature)) return;
        asideSignature = signature;
        var pairEnds = selectedPair.isEmpty() ? null : selectedPair.split("\\|");
        networkDetail.setDisplay(pairEnds == null);
        pairEditor.setDisplay(pairEnds != null);
        if (pairEnds != null) renderPair(network(pairEnds[0]), network(pairEnds[1]));
        else renderNetwork(network(selectedNetwork));
    }

    private void renderNetwork(Network network) {
        links.clearAllChildren();
        if (network == null) {
            var scoped = networks.isEmpty() && !scope.equals("domain") && !scope.equals("available");
            title.setText(scoped ? FederationWorkspace.tr("scope_summary." + scope) : tr("no_network"));
            identity.setText(Component.empty());
            detail.setText(FederationWorkspace.tr(scoped ? "device_scope." + scope
                    : networks.isEmpty() ? "empty_graph" : "select_node"));
            devices.setActive(false);
            linksHeading.setText(Component.empty());
            return;
        }
        accent.style(style -> style.backgroundTexture(FederationTheme.solid(network.accent())));
        title.setText(name(network));
        identity.setText(tr("network_identity", network.id()));
        var status = memberStatus.getOrDefault(network.member(), "pending");
        detail.setText(tr("network_detail", tr("network_status." + status), providers(network).size(),
                endpoints(network).size()));
        devices.setText(tr("devices", providers(network).size() + endpoints(network).size()));
        devices.setActive(!providers(network).isEmpty() || !endpoints(network).isEmpty());
        var others = networks.stream().filter(other -> !other.id().equals(network.id())).toList();
        linksHeading.setText(tr("connections", others.size()));
        for (var other : others) {
            var summary = Component.empty().append(direction(network, other));
            var reverse = direction(other, network);
            if (!reverse.getString().isEmpty()) {
                if (!summary.getString().isEmpty()) summary.append(" ");
                summary.append(reverse);
            }
            var link = new Button();
            link.addClass("network-link");
            link.setId("network_link_" + sanitize(other.member()));
            link.setText(summary.getString().isEmpty() ? name(other).copy().append(" · ").append(tr("no_rules"))
                    : name(other).copy().append("\n").append(summary));
            link.layout(style -> style.widthPercent(100).height(summary.getString().isEmpty() ? 16 : 26));
            link.textStyle(style -> style.textWrap(TextWrap.NONE).textAlignHorizontal(
                    com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal.LEFT));
            link.setOnClick(event -> {
                selectedPair = pair(network.id(), other.id());
                selectedNetwork = "";
                refresh();
            });
            links.addChild(link);
        }
    }

    private void renderPair(Network a, Network b) {
        pairSections.clearAllChildren();
        if (a == null || b == null) return;
        pairTitle.setText(name(a).copy().append(" ⇄ ").append(name(b)));
        int section = 0;
        for (var direction : List.of(new Network[] {a, b}, new Network[] {b, a})) {
            var consumer = direction[0];
            var provider = direction[1];
            var panel = new UIElement();
            panel.addClass("dark-panel");
            panel.setId("policy_section_" + section);
            var heading = new Label();
            heading.addClass("pair-section-title");
            heading.setId("policy_section_title_" + section);
            heading.setText(tr("uses", name(consumer), name(provider)));
            panel.addChild(heading);
            for (var capability : CAPABILITIES) panel.addChild(row(section, consumer, provider, capability));
            pairSections.addChild(panel);
            section++;
        }
    }

    private UIElement row(int section, Network consumer, Network provider, PolicyCapability capability) {
        var ruleKey = key(consumer.id(), provider.id(), capability.name());
        var rule = rules.get(ruleKey);
        var suffix = section + "_" + capability.name().toLowerCase(Locale.ROOT);
        var row = new UIElement();
        row.addClass("policy-row");
        row.setId("policy_row_" + suffix);
        var head = new UIElement();
        head.addClass("policy-row-head");
        var name = new Label();
        name.addClass("policy-capability");
        name.setText(capabilityName(capability));
        head.addChild(name);
        boolean on = rule != null && rule.get("enabled").getAsBoolean();
        if (capability == PolicyCapability.PROCESSING && on) {
            var mapping = new Button();
            mapping.addClass("policy-link");
            mapping.setId("policy_mapping_" + suffix);
            mapping.setText(tr("mapping_link"));
            mapping.setOnClick(event -> openProviders(consumer));
            mapping.setActive(!providers(consumer).isEmpty());
            head.addChild(mapping);
        }
        var toggle = new Button();
        toggle.noText();
        toggle.addClass("policy-switch");
        toggle.setId("policy_switch_" + suffix);
        if (on) toggle.addClass("on");
        toggle.buttonStyle(style -> style.baseTexture(on ? FederationTheme.SWITCH_ON : FederationTheme.SWITCH_OFF)
                .hoverTexture(on ? FederationTheme.SWITCH_ON_HOVER : FederationTheme.SWITCH_OFF_HOVER)
                .pressedTexture(on ? FederationTheme.SWITCH_ON_HOVER : FederationTheme.SWITCH_OFF_HOVER));
        toggle.setActive(editable);
        toggle.style(style -> style.opacity(editable ? 1f : 0.55f).tooltips(ruleSummary(capability, rule), runtimeText(rule)));
        var observed = rule != null ? rule.get("revision").getAsLong() : revisions.getOrDefault(ruleKey, 0L);
        toggle.setOnClick(event -> {
            if (!editable) return;
            setPolicy.accept(new PolicySwitchTarget(new PolicyKey(NetworkId.parse(consumer.id()),
                    NetworkId.parse(provider.id()), capability), !on, new PolicyRevision(observed)).encode());
        });
        head.addChild(toggle);
        var stateLabel = new Label();
        stateLabel.addClass("policy-state");
        stateLabel.setId("policy_state_" + suffix);
        var state = ruleState(rule);
        var text = tr("rule_state." + state.code(), observed).withStyle(Style.EMPTY.withColor(state.color() & 0xffffff));
        if (state.explain()) text.append("\n").append(runtimeText(rule).copy().withStyle(
                Style.EMPTY.withColor(FederationTheme.DARK_MUTED & 0xffffff)));
        stateLabel.setText(text);
        stateLabel.style(style -> style.tooltips(ruleSummary(capability, rule), runtimeText(rule)));
        row.addChildren(head, stateLabel);
        return row;
    }

    private static RuleState ruleState(JsonObject rule) {
        if (rule == null) return new RuleState("unconfigured", FederationTheme.DARK_MUTED, false);
        if (!rule.get("enabled").getAsBoolean()) return new RuleState("off", FederationTheme.DARK_MUTED, false);
        var runtime = rule.getAsJsonObject("runtime");
        var code = runtime == null ? "unobserved" : runtime.get("code").getAsString();
        return switch (code) {
            case "published" -> new RuleState("active", FederationTheme.OK, false);
            case "on_dispatch" -> new RuleState("dispatch", FederationTheme.OK, false);
            default -> new RuleState("waiting", FederationTheme.WARN, true);
        };
    }

    private static Component ruleSummary(PolicyCapability capability, JsonObject rule) {
        return Component.translatable("ae2federation.ui.domain.rule", capabilityName(capability),
                rule == null ? Component.translatable("ae2federation.ui.domain.rule.unconfigured")
                        : Component.translatable("ae2federation.ui.domain.rule." + (rule.get("enabled").getAsBoolean() ? "on" : "off")),
                rule == null ? 0 : rule.get("revision").getAsLong());
    }

    /** Rebuilds the server's runtime observation from its translation keys. */
    private static Component runtimeText(JsonObject rule) {
        var prefix = "ae2federation.ui.domain.runtime.";
        if (rule == null) return Component.translatable(prefix + "unconfigured");
        var runtime = rule.getAsJsonObject("runtime");
        if (runtime == null) return Component.translatable(prefix + (rule.get("enabled").getAsBoolean() ? "unobserved" : "off"));
        var code = runtime.get("code").getAsString();
        if (code.equals("operation_missing")) return Component.translatable(prefix + code,
                Component.translatable(prefix + "operation." + runtime.get("operation").getAsString()));
        var text = Component.translatable(prefix + code);
        if (runtime.has("backend")) text.append("\n").append(Component.translatable(prefix + "backend_reason",
                Component.translatable(prefix + "backend." + runtime.get("backend").getAsString())));
        if (runtime.has("storage")) text.append("\n").append(Component.translatable(prefix + "storage_reason",
                Component.translatable(prefix + "provenance." + runtime.get("storage").getAsString())));
        return text;
    }

    private void applySearch() {
        boolean any = search.isEmpty();
        for (var network : networks) {
            var card = cards.get(network.id());
            if (card == null) continue;
            boolean match = search.isEmpty() || (name(network).getString() + " " + network.id()).toLowerCase(Locale.ROOT).contains(search);
            any |= match;
            card.style(style -> style.opacity(match ? 1f : 0.3f));
        }
        searchEmpty.setDisplay(!any);
    }

    private void openDevices() {
        var network = network(selectedNetwork);
        if (network == null) return;
        if (!providers(network).isEmpty()) openObject.accept("mapping_provider", providers(network).getFirst());
        else if (!endpoints(network).isEmpty()) openObject.accept("endpoint", endpoints(network).getFirst());
    }

    private void openProviders(Network network) {
        if (!providers(network).isEmpty()) openObject.accept("mapping_provider", providers(network).getFirst());
    }

    private List<String> pairsWithRules() {
        var pairs = new java.util.TreeSet<String>();
        for (var rule : rules.values()) {
            var consumer = rule.get("consumer").getAsString();
            var provider = rule.get("provider").getAsString();
            if (network(consumer) != null && network(provider) != null) pairs.add(pair(consumer, provider));
        }
        return List.copyOf(pairs);
    }

    /** Pair keys list the lower member index first, which is also the first pair-editor section. */
    private String pair(String first, String second) {
        var a = network(first);
        var b = network(second);
        return a.index() <= b.index() ? a.id() + "|" + b.id() : b.id() + "|" + a.id();
    }

    private Network network(String id) {
        return networks.stream().filter(network -> network.id().equals(id)).findFirst().orElse(null);
    }

    private List<String> providers(Network network) {
        return providersByMember.getOrDefault(network.member(), List.of());
    }

    private List<String> endpoints(Network network) {
        return endpointsByMember.getOrDefault(network.member(), List.of());
    }

    private Vector2f center(String id) {
        var position = positions.get(id);
        return new Vector2f(position.x + CARD_WIDTH / 2, position.y + CARD_HEIGHT / 2);
    }

    private static Label text(Component value, int color) {
        var label = new Label();
        label.setText(value);
        label.textStyle(style -> style.textColor(color).textWrap(TextWrap.NONE));
        label.layout(style -> style.height(10).widthPercent(100).flexShrink(0));
        return label;
    }

    static Component capabilityName(PolicyCapability capability) {
        return FederationWorkspace.tr("capability." + capability.name().toLowerCase(Locale.ROOT));
    }

    static Component name(Network network) {
        return tr("network_name", tag(network) + network.id().substring(4, 8).toUpperCase(Locale.ROOT));
    }

    private static String tag(Network network) {
        return network.id().substring(0, 4).toUpperCase(Locale.ROOT);
    }

    private static String key(String consumer, String provider, String capability) {
        return consumer + "/" + provider + "/" + capability;
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    static MutableComponent tr(String key, Object... arguments) {
        return Component.translatable("ae2federation.ui.topology." + key, arguments);
    }

    private static <T> T element(UI ui, String id, Class<T> type) {
        return ui.selectId(id, type).findFirst().orElseThrow(() -> new IllegalStateException("Missing UI element #" + id));
    }

    record Network(String id, String member, int index) {
        int accent() {
            return FederationTheme.networkAccent(index);
        }
    }

    private record RuleState(String code, int color, boolean explain) {
    }

    /** Relationship lines behind the cards: configured pairs solid, unconfigured pairs of the selection dashed. */
    private final class Links extends UIElement {
        Links() {
            float width = positions.values().stream().map(point -> point.x + CARD_WIDTH).max(Float::compare).orElse(1f);
            float height = positions.values().stream().map(point -> point.y + CARD_HEIGHT).max(Float::compare).orElse(1f);
            layout(style -> style.positionType(TaffyPosition.ABSOLUTE).left(0).top(0).width(width + 8).height(height + 8));
        }

        @Override
        public void screenTick() {
            super.screenTick();
            if (graph.getContentWidth() <= 0 || graph.getContentHeight() <= 0 || positions.isEmpty()) return;
            if (!fitted) {
                graph.fitToChildren(16, 0.25f);
                fitted = true;
            }
            if (!pendingCenter.isEmpty() && positions.containsKey(pendingCenter)) {
                var point = center(pendingCenter);
                float halfWidth = graph.getContentWidth() / graph.getScale() / 2;
                float halfHeight = graph.getContentHeight() / graph.getScale() / 2;
                graph.fit(point.x - halfWidth, point.y - halfHeight, point.x + halfWidth, point.y + halfHeight, graph.getScale());
                pendingCenter = "";
            }
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            var pose = context.graphics.pose();
            pose.pushPose();
            pose.translate(getPositionX(), getPositionY(), 0);
            var configured = pairsWithRules();
            for (var pair : configured) {
                var ends = pair.split("\\|");
                boolean selected = pair.equals(selectedPair);
                line(context, center(ends[0]), center(ends[1]), selected ? FederationTheme.SELECT : FederationTheme.EDGE,
                        selected ? 3f : 2f, false);
            }
            if (!selectedNetwork.isEmpty() && positions.containsKey(selectedNetwork)) {
                for (var other : networks) {
                    if (other.id().equals(selectedNetwork) || configured.contains(pair(selectedNetwork, other.id()))) continue;
                    line(context, center(selectedNetwork), center(other.id()), 0x668b83a0, 1.5f, true);
                }
            }
            pose.popPose();
        }

        private void line(GUIContext context, Vector2f from, Vector2f to, int color, float width, boolean dashed) {
            if (!dashed) {
                DrawerHelper.drawTexLines(context.graphics, LDLibRenderTypes.graphWire(), List.of(from, to), color, color, width);
                return;
            }
            float length = from.distance(to);
            int segments = Math.max(1, (int) (length / 8));
            for (int i = 0; i < segments; i += 2) {
                var start = new Vector2f(from).lerp(to, i / (float) segments);
                var end = new Vector2f(from).lerp(to, Math.min(1f, (i + 1) / (float) segments));
                DrawerHelper.drawTexLines(context.graphics, LDLibRenderTypes.graphWire(), List.of(start, end), color, color, width);
            }
        }
    }
}
