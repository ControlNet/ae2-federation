package space.controlnet.ae2federation.client.menu;

import com.google.gson.JsonArray;
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
import space.controlnet.ae2federation.client.policy.TopologyLink;
import space.controlnet.ae2federation.client.policy.NetworkIdentityState;
import space.controlnet.ae2federation.client.policy.NetworkRenameTarget;
import space.controlnet.ae2federation.client.policy.PolicySwitchTarget;
import space.controlnet.ae2federation.client.policy.RelatedDomainLabel;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.persistence.NetworkNameBook;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyRevision;

/**
 * The domain seen as networks: a card per ME network, one edge per network pair with configured rules, and a pair
 * editor whose switches each request one explicit rule state. Layout and colours are local presentation only.
 */
final class FederationTopologyView {
    static final float CARD_WIDTH = 200;
    static final float CARD_HEIGHT = 88;
    private static final float THUMBNAIL_WIDTH = 44;
    private static final float THUMBNAIL_HEIGHT = 30;
    private static final PolicyCapability[] CAPABILITIES = PolicyCapability.values();
    private static final int LINK_SEGMENTS = 24;
    /** Fitting may zoom out this far, so a narrow canvas still shows every card. */
    private static final float MIN_FIT_SCALE = 0.1f;

    private final GraphView graph;
    private final Consumer<String> setPolicy;
    private final Consumer<String> rename;
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
    private final Label pairNote;
    private final UIElement pairSections;
    private final Label searchEmpty;
    private final Button renameButton;
    private final UIElement renameRow;
    private final TextField renameField;
    private final Button renameSave;
    private final UIElement stats;
    private final FederationMapPreview preview = new FederationMapPreview();
    private final UIElement location;
    private final Label locationNote;
    private final Button highlight;
    private List<space.controlnet.ae2federation.client.policy.BlockMarks.Mark> highlightBlocks = List.of();
    /** The parts of a network whose identity is in doubt, each outlined in its own colour. */
    private List<space.controlnet.ae2federation.client.WorldHighlight.Group> highlightParts = List.of();
    private String highlightDimension = "";
    private int highlightColor;
    private String highlightedNetwork = "";
    private long highlightedUntil;

    private final List<Network> networks = new ArrayList<>();
    /** Networks of related domains (sharing a member with this one); shown read-only in the "all related" scope. */
    private final List<Network> related = new ArrayList<>();
    private final Map<String, JsonObject> relatedRules = new HashMap<>();
    /** Accepted deliveries per rule key over the last five seconds, from real transfers only. */
    private final Map<String, JsonObject> flows = new HashMap<>();
    private Label throughput;
    /** The domain's Router group or Bridge, as the server reports it. */
    private JsonObject via;
    private boolean showRelated;
    private final Button scopeButton;
    private final Map<String, JsonObject> rules = new HashMap<>();
    private final Map<String, Long> revisions = new HashMap<>();
    private final Map<String, String> memberStatus = new HashMap<>();
    private final Map<String, List<String>> providersByMember = new HashMap<>();
    private final Map<String, List<String>> endpointsByMember = new HashMap<>();
    private final Map<String, Vector2f> positions = new LinkedHashMap<>();
    private final Map<String, Button> cards = new HashMap<>();
    private final Map<String, Label[]> cardLines = new HashMap<>();
    /** Stored energy as a fraction of capacity per card, read by its bar every frame; negative when unknown. */
    private final Map<String, float[]> cardEnergy = new HashMap<>();
    /** The colour of each card's bottom state line, read every frame. */
    private final Map<String, int[]> cardState = new HashMap<>();
    private final Map<String, FederationMapPreview> cardThumbnails = new HashMap<>();
    private final Map<String, JsonObject> overview = new HashMap<>();
    private String renaming = "";
    /** The name sent for {@link #renaming}; the editor closes once the server's choices carry it. */
    private String pendingName;
    private String focus = "";
    private boolean focusApplied;
    private String selectedNetwork = "";
    private String selectedPair = "";
    private String structure = "";
    private String asideSignature = "";
    private String search = "";
    private boolean editable;
    /**
     * The legend is shown, as the design keeps it in the canvas corner; "?" folds it away. The viewer's choice is kept
     * for the client session, like the processing view choice.
     */
    private static boolean legendHidden = false;
    /** Viewer preference only: the server keeps recording deliveries either way. */
    private static boolean liveFlowHidden = false;
    private boolean fitted;
    /** Half sizes of the link labels, which hide the middle of their link. */
    private final Map<String, Vector2f> pillHalfSizes = new HashMap<>();
    private int fitDelay;
    /** The graph viewport's size at the last fit; a resized window fits again so the cards stay in view. */
    private float fittedWidth;
    private float fittedHeight;
    private String pendingCenter = "";
    private String scope = "domain";

    FederationTopologyView(UI ui, Consumer<String> setPolicy, Consumer<String> rename, BiConsumer<String, String> openObject) {
        this.setPolicy = setPolicy;
        this.rename = rename;
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
        pairNote = element(ui, "pair_note", Label.class);
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
        element(ui, "graph_fit", Button.class).setOnClick(event -> graph.fitToChildren(12, MIN_FIT_SCALE));
        var legend = element(ui, "graph_legend", Label.class);
        legend.setText(legendText());
        var legendToggle = element(ui, "graph_legend_toggle", Button.class);
        // Reference text only: clicks reach the cards under it.
        legend.setAllowHitTest(false);
        Runnable showLegend = () -> {
            // A class, not setDisplay: the narrow layout's LSS keeps the legend off the few cards it has room for.
            legend.removeClass("folded");
            if (legendHidden) legend.addClass("folded");
            legendToggle.style(style -> style.tooltips(tr(legendHidden ? "legend.show" : "legend.hide")));
            legendToggle.removeClass("selected");
            if (!legendHidden) legendToggle.addClass("selected");
        };
        legendToggle.setOnClick(event -> {
            legendHidden = !legendHidden;
            showLegend.run();
        });
        showLegend.run();
        throughput = element(ui, "graph_throughput", Label.class);
        throughput.setAllowHitTest(false);
        var flowToggle = element(ui, "graph_flow_toggle", Button.class);
        Runnable showFlow = () -> {
            flowToggle.style(style -> style.tooltips(tr(liveFlowHidden ? "live_flow.show" : "live_flow.hide")));
            flowToggle.removeClass("selected");
            if (!liveFlowHidden) flowToggle.addClass("selected");
            updateThroughput();
        };
        flowToggle.setOnClick(event -> {
            liveFlowHidden = !liveFlowHidden;
            showFlow.run();
        });
        showFlow.run();
        devices.setOnClick(event -> openDevices());
        pairEditor.setDisplay(false);
        scopeButton = element(ui, "graph_scope", Button.class);
        scopeButton.setOnClick(event -> {
            showRelated = !showRelated;
            if (!showRelated && network(selectedNetwork) == null) selectedNetwork = "";
            if (!showRelated && !selectedPair.isEmpty() && java.util.Arrays.stream(selectedPair.split("\\|"))
                    .anyMatch(id -> network(id) == null)) selectedPair = "";
            // Fit once the rebuilt cards have been laid out; fitting in the same tick measures the old graph.
            fitted = false;
            fitDelay = 2;
            updateScopeButton();
            refresh();
        });
        stats = element(ui, "network_stats", UIElement.class);
        renameButton = element(ui, "network_rename", Button.class);
        renameRow = element(ui, "network_rename_row", UIElement.class);
        renameField = element(ui, "network_rename_field", TextField.class);
        renameSave = element(ui, "network_rename_save", Button.class);
        renameField.textFieldStyle(style -> style.placeholder(tr("rename_placeholder").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)));
        renameField.setTextResponder(value -> renameSave.setActive(NetworkNameBook.sanitize(value).isPresent()));
        renameButton.setOnClick(event -> {
            var network = network(selectedNetwork);
            if (network == null || !renamable(network)) return;
            renaming = network.id();
            pendingName = null;
            renameField.setText(network.name(), false);
            renameSave.setActive(true);
            asideSignature = "";
            refresh();
        });
        renameSave.setOnClick(event -> {
            var network = network(renaming);
            var value = NetworkNameBook.sanitize(renameField.getValue());
            if (network == null || value.isEmpty() || !renamable(network)) return;
            this.rename.accept(new NetworkRenameTarget(NetworkId.parse(network.id()), value.get()).encode());
            pendingName = value.get();
            renameSave.setActive(false);
        });
        element(ui, "network_rename_cancel", Button.class).setOnClick(event -> {
            renaming = "";
            pendingName = null;
            asideSignature = "";
            refresh();
        });
        renameRow.setDisplay(false);
        location = element(ui, "network_location", UIElement.class);
        locationNote = element(ui, "network_location_note", Label.class);
        highlight = element(ui, "network_highlight", Button.class);
        element(ui, "network_preview", UIElement.class).addChild(preview);
        preview.setCaption(element(ui, "network_location_caption", Label.class));
        highlight.style(style -> style.tooltips(FederationWorkspace.trLocation("highlight_help")));
        highlight.setOnClick(event -> {
            if (highlightBlocks.isEmpty()) return;
            if (highlightParts.size() > 1) {
                space.controlnet.ae2federation.client.WorldHighlight.show(highlightDimension, highlightParts);
            } else {
                space.controlnet.ae2federation.client.WorldHighlight.show(highlightDimension, highlightBlocks, highlightColor);
            }
            highlightedNetwork = selectedNetwork;
            highlightedUntil = System.currentTimeMillis() + space.controlnet.ae2federation.client.WorldHighlight.DURATION_MILLIS;
            locationNote.setText(highlightedText());
        });
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
        via = root.has("via") ? root.getAsJsonObject("via") : null;
        networks.clear();
        if (root.has("networks")) {
            var index = 0;
            for (var value : root.getAsJsonArray("networks")) {
                var json = value.getAsJsonObject();
                networks.add(new Network(json.get("id").getAsString(), json.get("member").getAsString(), index++,
                        json.has("name") ? json.get("name").getAsString() : "", ""));
            }
        }
        related.clear();
        if (root.has("relatedNetworks")) {
            var index = networks.size();
            for (var value : root.getAsJsonArray("relatedNetworks")) {
                var json = value.getAsJsonObject();
                var id = json.get("id").getAsString();
                related.add(new Network(id, "related_" + id, index++, json.has("name") ? json.get("name").getAsString() : "",
                        json.get("domain").getAsString()));
            }
        }
        relatedRules.clear();
        if (root.has("relatedRules")) for (var value : root.getAsJsonArray("relatedRules")) {
            var rule = value.getAsJsonObject();
            relatedRules.put(key(rule.get("consumer").getAsString(), rule.get("provider").getAsString(),
                    rule.get("capability").getAsString()), rule);
        }
        updateScopeButton();
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
        var renamed = network(renaming);
        if (pendingName != null && renamed != null && renamed.name().equals(pendingName)) {
            renaming = "";
            pendingName = null;
        }
        refresh();
    }

    /** Identity state, location and AE2 service figures per network, refreshed by the server about once a second. */
    void acceptOverview(JsonArray values) {
        overview.clear();
        for (var value : values) {
            var json = value.getAsJsonObject();
            overview.put(json.get("id").getAsString(), json);
        }
        refresh();
    }

    /** Recent accepted deliveries per rule; an edge animates only while its rule actually moved something. */
    void acceptFlows(JsonArray values) {
        flows.clear();
        for (var value : values) {
            var flow = value.getAsJsonObject();
            flows.put(key(flow.get("consumer").getAsString(), flow.get("provider").getAsString(),
                    flow.get("capability").getAsString()), flow);
        }
        asideSignature = "";
        renderAside();
        updateThroughput();
    }

    /** Deliveries of the shown networks' rules over the server's flow window. */
    private void updateThroughput() {
        if (throughput == null) return;
        if (liveFlowHidden) {
            throughput.setText(tr("throughput.off"));
            return;
        }
        var shownIds = new java.util.HashSet<String>();
        shown().forEach(network -> shownIds.add(network.id()));
        long events = 0;
        for (var flow : flows.values()) {
            if (shownIds.contains(flow.get("consumer").getAsString()) && shownIds.contains(flow.get("provider").getAsString())) {
                events += flow.get("events").getAsLong();
            }
        }
        throughput.setText(events == 0 ? tr("throughput.idle")
                : tr("throughput", events).withStyle(Style.EMPTY.withColor(FederationTheme.TEAL & 0xffffff)));
    }

    private void updateScopeButton() {
        scopeButton.setText(tr(showRelated ? "scope.related" : "scope.domain", related.size()));
        scopeButton.style(style -> style.tooltips(tr("scope.help", related.size())));
        scopeButton.removeClass("selected");
        if (showRelated) scopeButton.addClass("selected");
    }

    /** Member networks, plus the related domains' networks in the "all related" scope. */
    private List<Network> shown() {
        if (!showRelated || related.isEmpty()) return networks;
        var all = new ArrayList<>(networks);
        all.addAll(related);
        return all;
    }

    private JsonObject rule(String ruleKey) {
        var rule = rules.get(ruleKey);
        return rule != null || !showRelated ? rule : relatedRules.get(ruleKey);
    }

    private List<JsonObject> shownRules() {
        var all = new ArrayList<>(rules.values());
        if (showRelated) all.addAll(relatedRules.values());
        return all;
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
        shown().forEach(network -> signature.append(network.id()).append('=').append(network.name()).append(','));
        signature.append('|').append(selectedNetwork).append('|').append(selectedPair).append('|');
        pairsWithRules().forEach(value -> signature.append(value).append(';'));
        shownRules().forEach(rule -> signature.append(rule.get("capability").getAsString())
                .append(rule.get("enabled").getAsBoolean()).append(ruleState(rule).code()));
        memberStatus.forEach((member, status) -> signature.append(member).append('=').append(status));
        if (!signature.toString().equals(structure)) {
            structure = signature.toString();
            rebuildGraph();
        }
        updateCards();
        renderAside();
        applySearch();
    }

    private void rebuildGraph() {
        graph.clearAllContentChildren();
        cards.clear();
        cardLines.clear();
        cardState.clear();
        cardThumbnails.clear();
        pillHalfSizes.clear();
        layout();
        graph.addContentChild(new Links());
        // Above the lines but below the link labels and cards, so dots never cover text.
        var pulses = new FederationFlowPulses(this::flows);
        float width = positions.values().stream().map(point -> point.x + CARD_WIDTH).max(Float::compare).orElse(1f);
        float height = positions.values().stream().map(point -> point.y + CARD_HEIGHT).max(Float::compare).orElse(1f);
        pulses.layout(style -> style.positionType(TaffyPosition.ABSOLUTE).left(0).top(0).width(width + 8).height(height + 8));
        graph.addContentChild(pulses);
        for (var pair : pairsWithRules()) graph.addContentChild(edgePill(pair));
        for (var network : shown()) graph.addContentChild(card(network));
    }

    /** Links, provider to consumer, of every rule that delivered something in the flow window. */
    private List<FederationFlowPulses.Flow> flows() {
        var flows = new ArrayList<FederationFlowPulses.Flow>();
        if (liveFlowHidden) return flows;
        for (var pair : pairsWithRules()) {
            var ends = pair.split("\\|");
            if (!positions.containsKey(ends[0]) || !positions.containsKey(ends[1])) continue;
            var link = link(ends[0], ends[1]);
            var label = pillHalfSizes.getOrDefault(pair, new Vector2f());
            // Resources travel from the providing network to the consumer: from the link's end when the first network consumes.
            if (flowing(ends[0], ends[1])) flows.add(new FederationFlowPulses.Flow(link, true, label.x, label.y));
            if (flowing(ends[1], ends[0])) flows.add(new FederationFlowPulses.Flow(link, false, label.x, label.y));
        }
        return flows;
    }

    /** The curve from the first network's card to the second's. */
    private TopologyLink link(String from, String to) {
        var a = positions.get(from);
        var b = positions.get(to);
        return TopologyLink.between(a.x, a.y, b.x, b.y, CARD_WIDTH, CARD_HEIGHT);
    }

    /** Networks on an ellipse, the entrance network first on the left; two networks sit side by side. */
    private void layout() {
        positions.clear();
        var ordered = new ArrayList<>(shown());
        ordered.stream().filter(network -> network.member().equals(focus)).findFirst().ifPresent(first -> {
            ordered.remove(first);
            ordered.addFirst(first);
        });
        int count = ordered.size();
        float radius = count <= 2 ? 140 : (float) Math.max(150, 100 / Math.sin(Math.PI / count));
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

    /**
     * A network card as the design draws it: the map tile at the top left beside the name, position and state; then
     * labelled energy and storage rows and the crafting CPU and channel figures. The bottom line takes the state colour.
     */
    private Button card(Network network) {
        var position = positions.get(network.id());
        var button = new Button();
        button.noText();
        button.addClass("graph-node-member");
        button.setId("graph_node_" + sanitize(network.member()));
        boolean selected = network.id().equals(selectedNetwork) || selectedPair.contains(network.id());
        if (network.foreign()) button.addClass("related-network");
        var state = cardState.computeIfAbsent(network.id(), ignored -> new int[] {FederationTheme.DARK_MUTED});
        var inset = FederationTheme.painted((pen, x, y, width, height) -> pen.rect(x + 2, y + height - 4, width - 4, 2, state[0]));
        var face = GuiTextureGroup.of(selected ? FederationTheme.CARD_SELECTED : FederationTheme.CARD, inset);
        button.buttonStyle(style -> style.baseTexture(face).hoverTexture(GuiTextureGroup.of(FederationTheme.CARD_SELECTED, inset))
                .pressedTexture(face));
        button.layout(style -> style.positionType(TaffyPosition.ABSOLUTE).left(position.x).top(position.y)
                .width(CARD_WIDTH).height(CARD_HEIGHT).paddingAll(6).paddingBottom(7).gapAll(4)
                .flexDirection(FlexDirection.COLUMN).alignItems(AlignItems.FLEX_START));
        var top = new UIElement();
        top.layout(style -> style.widthPercent(100).height(THUMBNAIL_HEIGHT).flexDirection(FlexDirection.ROW).gapAll(5)
                .flexShrink(0));
        // Where the network is, as a map tile of its blocks; related networks carry no location facts.
        var thumbnail = new FederationMapPreview(true);
        thumbnail.setId("graph_node_map_" + sanitize(network.member()));
        thumbnail.layout(style -> style.width(THUMBNAIL_WIDTH).height(THUMBNAIL_HEIGHT).flexShrink(0));
        cardThumbnails.put(network.id(), thumbnail);
        var info = new UIElement();
        info.layout(style -> style.flex(1).minWidth(0).gapAll(1).flexDirection(FlexDirection.COLUMN));
        var head = new UIElement();
        head.layout(style -> style.widthPercent(100).height(10).flexDirection(FlexDirection.ROW).gapAll(3)
                .alignItems(AlignItems.CENTER));
        var swatch = new UIElement();
        swatch.layout(style -> style.width(6).height(6).flexShrink(0));
        swatch.style(style -> style.backgroundTexture(FederationTheme.solid(network.accent())));
        var heading = text(cardName(network), FederationTheme.DARK_TITLE);
        heading.layout(style -> style.flex(1).minWidth(0).widthAuto());
        // The identity tag, coloured by how settled the identity is; the full id is in the tooltip.
        var badge = text(Component.literal(tag(network)), FederationTheme.DARK_MUTED);
        badge.setId("graph_node_badge_" + sanitize(network.member()));
        badge.addClass("identity-badge");
        float badgeWidth = net.minecraft.client.Minecraft.getInstance().font.width(tag(network)) + 5;
        badge.layout(style -> style.flexShrink(0).width(badgeWidth).height(9).paddingLeft(2));
        badge.style(style -> style.backgroundTexture(FederationTheme.solid(0xff17141e)));
        head.addChildren(swatch, heading, badge);
        var positionLine = text(Component.empty(), FederationTheme.DARK_MUTED);
        var stateLine = text(Component.empty(), FederationTheme.DARK_TEXT);
        positionLine.setId("graph_node_position_" + sanitize(network.member()));
        stateLine.setId("graph_node_state_" + sanitize(network.member()));
        info.addChildren(head, positionLine, stateLine);
        top.addChildren(thumbnail, info);
        var energy = cardEnergy.computeIfAbsent(network.id(), ignored -> new float[] {-1});
        var energyValue = text(Component.empty(), FederationTheme.DARK_TEXT);
        var energyRow = figureRow(tr("card.energy"), energyBar(energy), energyValue);
        var storageValue = text(Component.empty(), FederationTheme.DARK_TEXT);
        storageValue.setId("graph_node_stats_" + sanitize(network.member()));
        var storageRow = figureRow(tr("card.storage"), null, storageValue);
        var figures = new UIElement();
        figures.layout(style -> style.widthPercent(100).height(10).flexDirection(FlexDirection.ROW).flexShrink(0));
        var cpus = text(Component.empty(), FederationTheme.DARK_MUTED);
        cpus.layout(style -> style.flex(1).minWidth(0).height(10));
        var channels = text(Component.empty(), FederationTheme.DARK_MUTED);
        channels.textStyle(style -> style.textAlignHorizontal(com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal.RIGHT));
        channels.layout(style -> style.flex(1).minWidth(0).height(10));
        figures.addChildren(cpus, channels);
        button.addChildren(top, energyRow, storageRow, figures);
        button.setOnClick(event -> selectNetwork(network.id()));
        button.style(style -> style.tooltips(name(network), Component.literal(network.id())));
        cards.put(network.id(), button);
        cardLines.put(network.id(), new Label[] {stateLine, storageValue, positionLine, badge, energyValue, cpus, channels});
        return button;
    }

    /** "Energy [bar] 98%": a muted label, an optional bar and the value on the right. */
    private static UIElement figureRow(Component name, com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture bar, Label value) {
        var row = new UIElement();
        row.layout(style -> style.widthPercent(100).height(10).flexDirection(FlexDirection.ROW).gapAll(4)
                .alignItems(AlignItems.CENTER).flexShrink(0));
        var label = text(name, FederationTheme.DARK_MUTED);
        label.layout(style -> style.width(40).flexShrink(0));
        row.addChild(label);
        if (bar != null) {
            var track = new UIElement();
            track.layout(style -> style.flex(1).minWidth(0).height(5));
            track.style(style -> style.backgroundTexture(bar));
            row.addChild(track);
        }
        value.textStyle(style -> style.textAlignHorizontal(com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal.RIGHT));
        value.layout(style -> {
            if (bar != null) style.width(30).flexShrink(0);
            else style.flex(1).minWidth(0);
        });
        row.addChild(value);
        return row;
    }

    /** Card state and figures change every second; they are updated in place instead of rebuilding the graph. */
    private void updateCards() {
        for (var network : shown()) {
            var lines = cardLines.get(network.id());
            if (lines == null) continue;
            var energy = cardEnergy.get(network.id());
            var state = cardState.get(network.id());
            var thumbnail = cardThumbnails.get(network.id());
            if (thumbnail != null) showThumbnail(thumbnail, network);
            if (network.foreign()) {
                lines[0].setText(tr("related_card", domainName(network.domain())).withStyle(Style.EMPTY.withColor(FederationTheme.DARK_MUTED & 0xffffff)));
                lines[1].setText(tr("read_only"));
                if (energy != null) energy[0] = -1;
                if (state != null) state[0] = FederationTheme.DARK_MUTED;
                continue;
            }
            var facts = overview.get(network.id());
            var identityState = identityState(network);
            lines[3].textStyle(style -> style.textColor(toneColor(identityState)));
            lines[3].style(style -> style.tooltips(identityState.isEmpty() ? Component.literal(network.id())
                    : tr("identity." + identityState).append(" · " + network.id())));
            lines[2].setText(facts == null || !facts.has("x") ? Component.empty() : tr("card_position",
                    dimension(facts.get("dimension").getAsString()), facts.get("x").getAsInt() + ", " + facts.get("y").getAsInt()
                            + ", " + facts.get("z").getAsInt()));
            if (energy != null) energy[0] = facts == null || !facts.has("energyMax") || facts.get("energyMax").getAsLong() <= 0 ? -1
                    : (float) Math.min(1, facts.get("energy").getAsDouble() / facts.get("energyMax").getAsDouble());
            int stateColor;
            if (!identityState.equals("settled") && !identityState.isEmpty()) {
                stateColor = toneColor(identityState);
                lines[0].setText(tr("identity." + identityState).withStyle(Style.EMPTY.withColor(stateColor & 0xffffff)));
            } else {
                var status = memberStatus.getOrDefault(network.member(), "pending");
                stateColor = status.equals("online") ? FederationTheme.OK : FederationTheme.WARN;
                lines[0].setText(tr("network_status." + status).withStyle(Style.EMPTY.withColor(stateColor & 0xffffff)));
            }
            if (state != null) state[0] = stateColor;
            if (facts == null || !facts.has("energyMax")) {
                lines[1].setText(tr("stats_unavailable"));
                lines[4].setText(Component.empty());
                lines[5].setText(Component.empty());
                lines[6].setText(Component.empty());
                continue;
            }
            int percent = percent(facts);
            lines[4].setText(Component.literal(percent + "%").withStyle(Style.EMPTY.withColor(energyColor(energy == null ? -1 : energy[0]) & 0xffffff)));
            lines[1].setText(tr("card.types", compact(facts.get("types").getAsLong())));
            lines[5].setText(tr("card.cpus", Component.literal(facts.get("cpusBusy").getAsInt() + "/" + facts.get("cpus").getAsInt())
                    .withStyle(Style.EMPTY.withColor(FederationTheme.VALUE & 0xffffff))));
            lines[6].setText(tr("card.channels", Component.literal(Integer.toString(facts.get("channels").getAsInt()))
                    .withStyle(Style.EMPTY.withColor(FederationTheme.DARK_TEXT & 0xffffff))));
        }
    }

    /**
     * The label of a link: one row per direction that has rules, "Main▸Mine" followed by a chip per capability in the
     * colour of its state, struck through when off and marked "!" on error. A related domain's link is dashed and locked.
     */
    private Button edgePill(String pair) {
        var ends = pair.split("\\|");
        var a = network(ends[0]);
        var b = network(ends[1]);
        var middle = link(a.id(), b.id()).middle();
        var button = new Button();
        button.noText();
        button.setId("graph_pair_" + sanitize(a.member()) + "_" + sanitize(b.member()));
        button.addClass("graph-pair");
        boolean selected = pair.equals(selectedPair);
        // A related domain's link is shown, not edited here: dashed and locked, as its cards are read-only.
        boolean related = a.foreign() || b.foreign();
        if (related) button.addClass("related-pair");
        var font = net.minecraft.client.Minecraft.getInstance().font;
        float width = 0;
        int rows = 0;
        var content = new ArrayList<UIElement>();
        for (var direction : List.of(new Network[] {a, b}, new Network[] {b, a})) {
            var row = pillRow(direction[0], direction[1], font);
            if (row == null) continue;
            content.add(row);
            width = Math.max(width, rowWidth(direction[0], direction[1], font));
            rows++;
        }
        if (related) {
            var lock = text(tr("related_lock"), FederationTheme.DARK_MUTED);
            lock.layout(style -> style.height(9).width(font.width(tr("related_lock")) + 1));
            content.add(lock);
            width = Math.max(width, font.width(tr("related_lock")) + 10);
            rows++;
        }
        var border = selected ? FederationTheme.SELECT : 0xff47434f;
        var face = related
                ? GuiTextureGroup.of(FederationTheme.solid(FederationTheme.WELL), FederationTheme.dashedBorder(selected ? FederationTheme.SELECT : 0xff8b83a0),
                        FederationTheme.lockMark(FederationTheme.DARK_MUTED))
                : GuiTextureGroup.of(FederationTheme.WELL_RECT, new com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture(1, border));
        var hover = related
                ? GuiTextureGroup.of(FederationTheme.solid(FederationTheme.WELL), FederationTheme.dashedBorder(FederationTheme.SELECT),
                        FederationTheme.lockMark(FederationTheme.DARK_MUTED))
                : GuiTextureGroup.of(FederationTheme.WELL_RECT, new com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture(1, FederationTheme.SELECT));
        button.buttonStyle(style -> style.baseTexture(face).hoverTexture(hover).pressedTexture(face));
        float pillWidth = Math.max(48, width + 10 + (related ? 8 : 0));
        float height = rows * 11 + Math.max(0, rows - 1) * 2 + 6;
        pillHalfSizes.put(pair, new Vector2f(pillWidth / 2f, height / 2));
        button.layout(style -> style.positionType(TaffyPosition.ABSOLUTE).left(middle[0] - pillWidth / 2f)
                .top(middle[1] - height / 2).width(pillWidth).height(height).paddingAll(3).gapAll(2)
                .flexDirection(FlexDirection.COLUMN).alignItems(AlignItems.FLEX_START));
        content.forEach(button::addChild);
        button.setOnClick(event -> {
            selectedPair = pair;
            selectedNetwork = "";
            focusApplied = true;
            refresh();
        });
        return button;
    }

    /** One direction of a link label, or null when that direction has no rules. */
    private UIElement pillRow(Network consumer, Network provider, net.minecraft.client.gui.Font font) {
        var chips = chips(consumer, provider);
        if (chips.isEmpty()) return null;
        var row = new UIElement();
        row.addClass("pill-row");
        row.layout(style -> style.height(11).flexDirection(FlexDirection.ROW).gapAll(3).alignItems(AlignItems.CENTER));
        var prefix = text(Component.literal(pillPrefix(consumer, provider)), FederationTheme.DARK_MUTED);
        // Slack for the "▸" glyph, which the font draws wider than it measures.
        prefix.layout(style -> style.width(font.width(pillPrefix(consumer, provider)) + 6).height(9).flexShrink(0));
        row.addChild(prefix);
        for (var chip : chips) {
            var label = text(chip.text(), chip.color());
            label.addClass("pill-chip");
            label.layout(style -> style.width(font.width(chip.text()) + 5).height(11).paddingLeft(2).paddingTop(1).flexShrink(0));
            label.style(style -> style.backgroundTexture(new com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture(1, chip.color())));
            row.addChild(label);
        }
        return row;
    }

    private float rowWidth(Network consumer, Network provider, net.minecraft.client.gui.Font font) {
        float width = font.width(pillPrefix(consumer, provider)) + 6;
        for (var chip : chips(consumer, provider)) width += 3 + font.width(chip.text()) + 5;
        return width;
    }

    private static String pillPrefix(Network consumer, Network provider) {
        var names = space.controlnet.ae2federation.client.policy.PillName.pair(consumer.name(), consumer.id(),
                provider.name(), provider.id());
        return names.consumer() + "▸" + names.provider();
    }

    /** The capabilities {@code consumer} uses from {@code provider}, in the colour of their configured and observed state. */
    private List<Chip> chips(Network consumer, Network provider) {
        var chips = new ArrayList<Chip>();
        for (var capability : CAPABILITIES) {
            var rule = rule(key(consumer.id(), provider.id(), capability.name()));
            if (rule == null) continue;
            var state = ruleState(rule);
            var text = capabilityName(capability).copy();
            if (!rule.get("enabled").getAsBoolean()) text = text.withStyle(net.minecraft.ChatFormatting.STRIKETHROUGH);
            if (state.code().equals("error")) text.append("!");
            chips.add(new Chip(text, state.color()));
        }
        return chips;
    }

    private record Chip(MutableComponent text, int color) {
    }

    private void selectNetwork(String id) {
        selectedNetwork = id;
        selectedPair = "";
        focusApplied = true;
        refresh();
    }

    private void renderAside() {
        var signature = scope + "|" + selectedNetwork + "|" + selectedPair + "|" + editable + "|" + rules + "|" + revisions + "|"
                + memberStatus + "|" + providersByMember + "|" + endpointsByMember + "|" + networks + "|" + overview + "|" + renaming + "|" + showRelated + related + relatedRules + "|" + flows;
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
        stats.clearAllChildren();
        if (network == null || !network.id().equals(renaming)) renaming = "";
        renameRow.setDisplay(!renaming.isEmpty());
        renameButton.setDisplay(network != null);
        stats.setDisplay(network != null);
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
        boolean canRename = renamable(network) && renaming.isEmpty();
        renameButton.setActive(canRename);
        renameButton.style(style -> style.opacity(canRename ? 1f : 0.55f).tooltips(tr(canRename ? "rename_help"
                : editable ? "rename_locked" : "rename_read_only")));
        var facts = overview.get(network.id());
        var shortId = network.id().length() > 8
                ? network.id().substring(0, 4) + "…" + network.id().substring(network.id().length() - 3) : network.id();
        identity.setText(facts != null && facts.has("x")
                ? tr("network_identity_at", facts.get("x").getAsInt() + ", " + facts.get("y").getAsInt() + ", "
                        + facts.get("z").getAsInt(), shortId)
                : tr("network_identity", shortId));
        identity.style(style -> style.tooltips(Component.literal(network.id())));
        var status = memberStatus.getOrDefault(network.member(), "pending");
        var identityState = identityState(network);
        var explanation = Component.empty();
        if (!identityState.isEmpty() && !identityState.equals("settled")) {
            var state = NetworkIdentityState.valueOf(identityState.toUpperCase(java.util.Locale.ROOT));
            var tone = Style.EMPTY.withColor(toneColor(identityState) & 0xffffff);
            explanation.append(tr("identity." + identityState).withStyle(tone));
            var parts = facts != null && facts.has("identityParts") ? facts.getAsJsonArray("identityParts") : null;
            if (state == NetworkIdentityState.MERGE && parts != null) {
                var names = new ArrayList<String>();
                parts.forEach(part -> {
                    var id = part.getAsJsonObject().has("network") ? part.getAsJsonObject().get("network").getAsString() : "";
                    var known = network(id);
                    names.add(known != null ? name(known).getString() : id.length() >= 8
                            ? tr("network_name", id.substring(0, 8).toUpperCase(Locale.ROOT)).getString() : id);
                });
                explanation.append("\n").append(tr("identity.merge.contains", String.join(" · ", names)));
            } else if (state == NetworkIdentityState.SPLIT && parts != null) {
                explanation.append("\n").append(tr("identity.split.parts", parts.size()));
            }
            explanation.append("\n").append(tr("identity." + identityState + ".help"));
            if (state.hasFix()) explanation.append("\n").append(tr("identity." + identityState + ".fix").withStyle(tone));
            explanation.append("\n");
        }
        detail.setText(network.foreign() ? tr("related_detail", domainName(network.domain()))
                : explanation.append(tr("network_detail", tr("network_status." + status), providers(network).size(),
                endpoints(network).size())));
        renderStats(facts, identityState);
        renderLocation(network, facts);
        devices.setText(tr("devices", providers(network).size() + endpoints(network).size()));
        devices.setActive(!providers(network).isEmpty() || !endpoints(network).isEmpty());
        // Linked networks first, as the design's "Connections (N)"; the others follow so rules can still be created.
        var others = shown().stream().filter(other -> !other.id().equals(network.id())).toList();
        var linked = others.stream().filter(other -> !linkSummary(network, other).getString().isEmpty()).toList();
        var unlinked = others.stream().filter(other -> !linked.contains(other)).toList();
        linksHeading.setText(tr("connections", linked.size()));
        if (linked.isEmpty()) links.addChild(sectionNote(tr("connections_none")));
        var ordered = new ArrayList<>(linked);
        ordered.addAll(unlinked);
        for (var other : ordered) {
            if (other == (unlinked.isEmpty() ? null : unlinked.getFirst())) {
                links.addChild(sectionNote(tr("unconnected", unlinked.size())));
            }
            var summary = linkSummary(network, other);
            var link = new Button();
            link.addClass("network-link");
            link.setId("network_link_" + sanitize(other.member()));
            link.setText(summary.getString().isEmpty() ? name(other).copy().append(" · ").append(tr("no_rules"))
                    : name(other).copy().append("\n").append(summary));
            link.layout(style -> style.widthPercent(100).height(summary.getString().isEmpty() ? 16 : 26));
            link.textStyle(style -> style.textWrap(TextWrap.NONE).textAlignHorizontal(
                    com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal.LEFT));
            var open = text(Component.literal("›"), FederationTheme.TEXT_MUTED);
            open.setAllowHitTest(false);
            open.layout(style -> style.positionType(TaffyPosition.ABSOLUTE).right(4).top(3).width(6));
            link.addChild(open);
            link.setOnClick(event -> {
                selectedPair = pair(network.id(), other.id());
                selectedNetwork = "";
                refresh();
            });
            links.addChild(link);
        }
    }

    /** Both directions of a pair as chips: "▸ Storage · Crafting ◂ ME power", what this network uses first. */
    private MutableComponent linkSummary(Network network, Network other) {
        var summary = Component.empty();
        for (var direction : List.of(new Network[] {network, other}, new Network[] {other, network})) {
            var chips = chips(direction[0], direction[1]);
            if (chips.isEmpty()) continue;
            if (!summary.getString().isEmpty()) summary.append(" ");
            summary.append(Component.literal(direction[0] == network ? "▸ " : "◂ ")
                    .withStyle(Style.EMPTY.withColor(FederationTheme.DARK_MUTED & 0xffffff)));
            for (int index = 0; index < chips.size(); index++) {
                if (index > 0) summary.append(Component.literal(" · ").withStyle(Style.EMPTY.withColor(FederationTheme.DARK_MUTED & 0xffffff)));
                summary.append(chips.get(index).text().withStyle(Style.EMPTY.withColor(chips.get(index).color() & 0xffffff)));
            }
        }
        return summary;
    }

    private static Label sectionNote(Component text) {
        var note = new Label();
        note.addClass("links-note");
        note.setText(text);
        return note;
    }

    /** The map tile of the network's blocks around its controller, and the matching in-world highlight. */
    private void renderLocation(Network network, JsonObject facts) {
        if (facts == null || !facts.has("x")) {
            location.setDisplay(false);
            preview.clear();
            highlightBlocks = List.of();
            highlightParts = List.of();
            return;
        }
        location.setDisplay(true);
        var blocks = new ArrayList<Integer>();
        if (facts.has("blocks")) facts.getAsJsonArray("blocks").forEach(value -> blocks.add(value.getAsInt()));
        var mask = space.controlnet.ae2federation.client.policy.BlockMarks.fromFlat(blocks);
        var anchor = new space.controlnet.ae2federation.client.policy.BlockMarks.Mark(facts.get("x").getAsInt(),
                facts.get("y").getAsInt(), facts.get("z").getAsInt());
        var dimension = facts.get("dimension").getAsString();
        preview.show(dimension, mask, network.accent(), List.of(anchor), FederationTheme.DARK_TITLE);
        highlightDimension = dimension;
        highlightColor = network.accent();
        highlightBlocks = mask.isEmpty() ? List.of(anchor) : mask;
        highlightParts = identityParts(facts, dimension, network.accent());
        highlight.setText(FederationWorkspace.trLocation(highlightParts.size() > 1 ? "highlight_parts" : "highlight"));
        boolean here = preview.inPlayerDimension();
        highlight.setActive(here);
        boolean outlined = network.id().equals(highlightedNetwork) && System.currentTimeMillis() < highlightedUntil;
        locationNote.setText(outlined ? highlightedText()
                : here ? FederationWorkspace.trLocation("network_blocks", mask.size())
                : FederationWorkspace.trLocation("other_dimension", dimension(dimension)));
    }

    /** Parts in the location's dimension with blocks, the first in the network's accent and the other in warning. */
    private static List<space.controlnet.ae2federation.client.WorldHighlight.Group> identityParts(JsonObject facts,
            String dimension, int accent) {
        if (!facts.has("identityParts")) return List.of();
        var groups = new ArrayList<space.controlnet.ae2federation.client.WorldHighlight.Group>();
        for (var element : facts.getAsJsonArray("identityParts")) {
            var part = element.getAsJsonObject();
            if (!part.has("blocks") || !dimension.equals(part.get("dimension").getAsString())) continue;
            var flat = new ArrayList<Integer>();
            part.getAsJsonArray("blocks").forEach(value -> flat.add(value.getAsInt()));
            groups.add(new space.controlnet.ae2federation.client.WorldHighlight.Group(
                    space.controlnet.ae2federation.client.policy.BlockMarks.fromFlat(flat),
                    groups.isEmpty() ? accent : FederationTheme.WARN));
        }
        return groups;
    }

    private Component highlightedText() {
        if (highlightParts.size() > 1) return FederationWorkspace.trLocation("highlighted_parts", highlightParts.size(),
                highlightParts.stream().mapToInt(group -> group.blocks().size()).sum());
        return FederationWorkspace.trLocation("highlighted", highlightBlocks.size());
    }

    private void renderStats(JsonObject facts, String identityState) {
        if (identityState.equals("settled")) stats.addChild(statLine("network_stat_identity",
                tr("identity.settled").withStyle(Style.EMPTY.withColor(FederationTheme.OK & 0xffffff))));
        if (facts != null && facts.has("x")) stats.addChild(statLine("network_stat_place", tr("network_place",
                dimension(facts.get("dimension").getAsString()), facts.get("x").getAsInt(), facts.get("y").getAsInt(),
                facts.get("z").getAsInt())));
        if (facts == null || !facts.has("energyMax")) {
            stats.addChild(statLine("network_stat_unavailable", tr("stats_unavailable")));
            return;
        }
        long stored = facts.get("energy").getAsLong();
        long max = facts.get("energyMax").getAsLong();
        stats.addChild(statLine("network_stat_energy", tr("stat.energy", compact(stored), compact(max), percent(facts))));
        float fill = max <= 0 ? 0 : Math.min(1f, stored / (float) max);
        stats.addChild(statBar("network_stat_energy_bar", fill, energyColor(fill)));
        stats.addChild(statLine("network_stat_io", tr("stat.io", decimal(facts.get("energyIn").getAsDouble()),
                decimal(facts.get("energyOut").getAsDouble()))));
        stats.addChild(statLine("network_stat_types", tr("stat.types", compact(facts.get("types").getAsLong()))));
        int cpus = facts.get("cpus").getAsInt();
        stats.addChild(statLine("network_stat_cpus", tr("stat.cpus", facts.get("cpusBusy").getAsInt(), cpus)));
        if (cpus > 0) stats.addChild(statBar("network_stat_cpus_bar", facts.get("cpusBusy").getAsInt() / (float) cpus, FederationTheme.VALUE));
        stats.addChild(statLine("network_stat_channels", tr("stat.channels", facts.get("channels").getAsInt(),
                facts.get("nodes").getAsInt(), tr("controller." + facts.get("controller").getAsString()))));
    }

    private static UIElement statBar(String id, float fill, int color) {
        var bar = new UIElement();
        bar.addClass("stat-bar");
        bar.setId(id);
        bar.style(style -> style.backgroundTexture(FederationTheme.painted((pen, x, y, width, height) -> {
            pen.rect(x, y, width, height, 0xff0d0d11);
            pen.rect(x + 1, y + 1, width - 2, height - 2, FederationTheme.WELL);
            if (fill > 0) pen.rect(x + 1, y + 1, Math.max(1, (width - 2) * Math.min(1, fill)), height - 2, color);
        })));
        return bar;
    }

    private static Label statLine(String id, Component value) {
        var label = new Label();
        label.addClass("stat-line");
        label.setId(id);
        label.setText(value);
        return label;
    }

    /** The colour of an identity state, as the design gives it: doubt yellow, copy conflict red, loading blue. */
    private static int toneColor(String identityState) {
        if (identityState.isEmpty()) return FederationTheme.DARK_MUTED;
        return switch (NetworkIdentityState.valueOf(identityState.toUpperCase(Locale.ROOT)).tone()) {
            case OK -> FederationTheme.OK;
            case WARN -> FederationTheme.WARN;
            case ERROR -> FederationTheme.ERROR;
            case INFO -> FederationTheme.INFO;
            case MUTED -> FederationTheme.DARK_MUTED;
        };
    }

    private String identityState(Network network) {
        var facts = overview.get(network.id());
        return facts == null ? "" : facts.get("identity").getAsString();
    }

    /** Mirrors the server: only a settled identity takes a name, and only while this player may edit the domain. */
    private boolean renamable(Network network) {
        return editable && identityState(network).equals("settled");
    }

    private void showThumbnail(FederationMapPreview thumbnail, Network network) {
        var facts = overview.get(network.id());
        if (network.foreign() || facts == null || !facts.has("x")) {
            thumbnail.setDisplay(false);
            return;
        }
        var blocks = new ArrayList<Integer>();
        if (facts.has("blocks")) facts.getAsJsonArray("blocks").forEach(value -> blocks.add(value.getAsInt()));
        var anchor = new space.controlnet.ae2federation.client.policy.BlockMarks.Mark(facts.get("x").getAsInt(),
                facts.get("y").getAsInt(), facts.get("z").getAsInt());
        thumbnail.show(facts.get("dimension").getAsString(), space.controlnet.ae2federation.client.policy.BlockMarks.fromFlat(blocks),
                network.accent(), List.of(anchor), FederationTheme.DARK_TITLE);
        // Only the player's own dimension can be drawn; elsewhere the position line says where it is.
        thumbnail.setDisplay(thumbnail.inPlayerDimension());
    }

    /**
     * The card's energy bar: stored energy against capacity, green when comfortable, yellow when low and red when
     * empty. Only the empty track is drawn while the figure is unknown, such as for a related domain's network.
     */
    private static com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture energyBar(float[] fraction) {
        return FederationTheme.painted((pen, x, y, width, height) -> {
            pen.rect(x, y, width, height, 0xff0d0d11);
            pen.rect(x + 1, y + 1, width - 2, height - 2, FederationTheme.WELL);
            if (fraction[0] < 0) return;
            pen.rect(x + 1, y + 1, Math.max(1, (width - 2) * fraction[0]), height - 2, energyColor(fraction[0]));
        });
    }

    private static int energyColor(float fraction) {
        if (fraction < 0) return FederationTheme.DARK_MUTED;
        return fraction <= 0 ? FederationTheme.ERROR : fraction < 0.25f ? FederationTheme.WARN : FederationTheme.OK;
    }

    private static int percent(JsonObject facts) {
        long max = facts.get("energyMax").getAsLong();
        return max <= 0 ? 0 : (int) Math.min(100, Math.round(facts.get("energy").getAsLong() * 100d / max));
    }

    /** 950, 12.3k, 1.44M: AE2-style short figures. */
    static String compact(long value) {
        if (Math.abs(value) < 1000) return Long.toString(value);
        var units = "kMGT";
        double scaled = value;
        int unit = -1;
        while (Math.abs(scaled) >= 1000 && unit < units.length() - 1) {
            scaled /= 1000;
            unit++;
        }
        var text = Math.abs(scaled) >= 100 ? String.format(Locale.ROOT, "%.0f", scaled)
                : Math.abs(scaled) >= 10 ? String.format(Locale.ROOT, "%.1f", scaled) : String.format(Locale.ROOT, "%.2f", scaled);
        return text + units.charAt(unit);
    }

    private static String decimal(double value) {
        return value >= 1000 ? compact(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }

    private static Component dimension(String id) {
        var path = id.substring(id.indexOf(':') + 1);
        return Component.translatableWithFallback("ae2federation.ui.topology.dimension." + path, path);
    }

    private void renderPair(Network a, Network b) {
        pairSections.clearAllChildren();
        if (a == null || b == null) return;
        var title = name(a).copy().append(" ⇄ ").append(name(b));
        var foreign = a.foreign() ? a : b.foreign() ? b : null;
        if (foreign == null && via != null) title.append("\n").append(viaText(via)
                .withStyle(Style.EMPTY.withColor(FederationTheme.DARK_MUTED & 0xffffff)));
        pairTitle.setText(title);
        var positions = foreign == null && via != null ? viaPositions(via) : null;
        pairTitle.style(style -> style.tooltips(positions == null ? new Component[0] : new Component[] {positions}));
        // Another domain's pair is shown for reference: only its configured rules, and where to change them.
        pairNote.setText(foreign == null ? tr("pair_note") : tr("pair_read_only", domainName(foreign.domain())));
        pairNote.removeClass("read-only-banner");
        if (foreign != null) pairNote.addClass("read-only-banner");
        int section = 0;
        for (var direction : List.of(new Network[] {a, b}, new Network[] {b, a})) {
            var consumer = direction[0];
            var provider = direction[1];
            var shownCapabilities = java.util.Arrays.stream(CAPABILITIES).filter(capability -> foreign == null
                    || rule(key(consumer.id(), provider.id(), capability.name())) != null).toList();
            if (shownCapabilities.isEmpty()) {
                section++;
                continue;
            }
            var panel = new UIElement();
            panel.addClass("dark-panel");
            panel.setId("policy_section_" + section);
            var heading = new Label();
            heading.addClass("pair-section-title");
            heading.setId("policy_section_title_" + section);
            heading.setText(tr("uses", name(consumer), name(provider)));
            panel.addChild(heading);
            for (var capability : shownCapabilities) panel.addChild(row(section, consumer, provider, capability, foreign == null));
            pairSections.addChild(panel);
            section++;
        }
    }

    private UIElement row(int section, Network consumer, Network provider, PolicyCapability capability, boolean inDomain) {
        boolean editable = this.editable && inDomain;
        var ruleKey = key(consumer.id(), provider.id(), capability.name());
        var rule = rule(ruleKey);
        var suffix = section + "_" + capability.name().toLowerCase(Locale.ROOT);
        var row = new UIElement();
        row.addClass("policy-row");
        row.setId("policy_row_" + suffix);
        var head = new UIElement();
        head.addClass("policy-row-head");
        var name = new Label();
        name.addClass("policy-capability");
        name.setText(capabilityName(capability));
        // Name, state and switch share one line; flow and explanations wrap under the state.
        var stateLabel = new Label();
        stateLabel.addClass("policy-state");
        stateLabel.setId("policy_state_" + suffix);
        head.addChildren(name, stateLabel);
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
        // No opacity here: a translucent AE2 sprite renders as a black box. The read-only note says why it is locked.
        toggle.style(style -> style.tooltips(ruleSummary(capability, rule), runtimeText(rule)));
        var observed = rule != null ? rule.get("revision").getAsLong() : revisions.getOrDefault(ruleKey, 0L);
        toggle.setOnClick(event -> {
            if (!editable) return;
            setPolicy.accept(new PolicySwitchTarget(new PolicyKey(NetworkId.parse(consumer.id()),
                    NetworkId.parse(provider.id()), capability), !on, new PolicyRevision(observed)).encode());
        });
        head.addChild(toggle);
        var state = ruleState(rule);
        var text = tr("rule_state." + state.code(), observed).withStyle(Style.EMPTY.withColor(state.color() & 0xffffff));
        var flow = flows.get(ruleKey);
        if (flow != null && on) text.append("\n").append(flowText(capability, flow));
        if (state.explain()) text.append("\n").append(runtimeText(rule).copy().withStyle(
                Style.EMPTY.withColor(FederationTheme.DARK_MUTED & 0xffffff)));
        stateLabel.setText(text);
        stateLabel.style(style -> style.tooltips(ruleSummary(capability, rule), runtimeText(rule)));
        row.addChild(head);
        if (capability == PolicyCapability.STORAGE && rule != null && rule.has("terms")) {
            var terms = new Label();
            terms.addClass("policy-terms");
            terms.setId("policy_terms_" + suffix);
            terms.setText(termsText(rule.getAsJsonObject("terms")));
            row.addChild(terms);
        }
        return row;
    }

    /** "Delivered 12× in the last 5 s", with the energy moved for ME power; only accepted transfers count. */
    /** "Operations view · insert · extract · Filter: all resources · Re-export: off", as the design lists a rule. */
    private static Component termsText(JsonObject terms) {
        var operations = new ArrayList<String>();
        terms.getAsJsonArray("operations").forEach(value -> operations.add(tr("operation." + value.getAsString()).getString()));
        var filter = terms.get("filter").getAsString();
        var filterText = filter.equals("all") ? tr("filter.all") : tr("filter." + filter, terms.get("filterEntries").getAsInt());
        return tr("terms", operations.isEmpty() ? tr("operation.none").getString() : String.join(" ", operations), filterText,
                tr(terms.get("reexport").getAsBoolean() ? "reexport.on" : "reexport.off"))
                .withStyle(Style.EMPTY.withColor(FederationTheme.DARK_MUTED & 0xffffff));
    }

    /** The readable name of another domain; its raw identity is internal. */
    private static Component domainName(String domain) {
        var label = RelatedDomainLabel.of(domain);
        return tr("domain_label." + label.kind(), label.tag());
    }

    /** "Via the Bridge at x, y, z" or "Via 5 Routers · first at x, y, z · this domain"; every position is in the tooltip. */
    private static MutableComponent viaText(JsonObject via) {
        var positions = new ArrayList<String>();
        for (var node : via.getAsJsonArray("nodes")) positions.add(node.getAsJsonObject().get("position").getAsString());
        int count = via.get("count").getAsInt();
        var first = positions.isEmpty() ? "-" : positions.getFirst();
        return switch (via.get("kind").getAsString()) {
            case "bridge" -> tr("via.bridge", first);
            case "router" -> count == 1 ? tr("via.router_one", first) : tr("via.router", count, first);
            default -> tr("via.other", first);
        };
    }

    private static Component viaPositions(JsonObject via) {
        var positions = new ArrayList<String>();
        for (var node : via.getAsJsonArray("nodes")) positions.add(node.getAsJsonObject().get("position").getAsString());
        int count = via.get("count").getAsInt();
        return Component.literal(String.join("\n", positions) + (count > positions.size() ? "\n…" : ""));
    }

    private static Component flowText(PolicyCapability capability, JsonObject flow) {
        var events = flow.get("events").getAsLong();
        var text = capability == PolicyCapability.ME_POWER
                ? tr("flow_energy", events, compact(flow.get("amount").getAsLong() / 1_000_000_000L))
                : tr("flow", events);
        return text.withStyle(Style.EMPTY.withColor(FederationTheme.TEAL & 0xffffff));
    }

    /** Whether the rule from {@code consumer} to {@code provider} delivered anything in the flow window. */
    private boolean flowing(String consumer, String provider) {
        for (var capability : CAPABILITIES) {
            if (flows.containsKey(key(consumer, provider, capability.name()))) return true;
        }
        return false;
    }

    private static RuleState ruleState(JsonObject rule) {
        if (rule == null) return new RuleState("unconfigured", FederationTheme.DARK_MUTED, false);
        if (!rule.get("enabled").getAsBoolean()) return new RuleState("off", FederationTheme.DARK_MUTED, false);
        var runtime = rule.getAsJsonObject("runtime");
        var code = runtime == null ? "unobserved" : runtime.get("code").getAsString();
        var backend = runtime != null && runtime.has("backend") ? runtime.get("backend").getAsString() : "";
        return switch (space.controlnet.ae2federation.client.policy.RuleHealth.of(true, code, backend)) {
            case ACTIVE -> new RuleState(code.equals("on_dispatch") ? "dispatch" : "active", FederationTheme.OK, false);
            case ERROR -> new RuleState("error", FederationTheme.ERROR, true);
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
        for (var network : shown()) {
            var card = cards.get(network.id());
            if (card == null) continue;
            boolean match = matchesSearch(network);
            any |= match;
            card.style(style -> style.opacity(match ? 1f : 0.3f));
        }
        searchEmpty.setDisplay(!any);
    }

    /** Name, identity and device kinds as text; the controller, devices and blocks as places. */
    private boolean matchesSearch(Network network) {
        if (search.isEmpty()) return true;
        var texts = new ArrayList<String>(List.of(name(network).getString(), network.id()));
        var places = new ArrayList<space.controlnet.ae2federation.client.policy.BlockMarks.Mark>();
        var facts = overview.get(network.id());
        if (facts != null) {
            if (facts.has("x")) places.add(new space.controlnet.ae2federation.client.policy.BlockMarks.Mark(
                    facts.get("x").getAsInt(), facts.get("y").getAsInt(), facts.get("z").getAsInt()));
            if (facts.has("devices")) for (var element : facts.getAsJsonArray("devices")) {
                var device = element.getAsJsonObject();
                texts.add(tr("device." + device.get("kind").getAsString()).getString());
                places.add(new space.controlnet.ae2federation.client.policy.BlockMarks.Mark(device.get("x").getAsInt(),
                        device.get("y").getAsInt(), device.get("z").getAsInt()));
            }
            if (facts.has("blocks")) {
                var flat = new ArrayList<Integer>();
                facts.getAsJsonArray("blocks").forEach(value -> flat.add(value.getAsInt()));
                places.addAll(space.controlnet.ae2federation.client.policy.BlockMarks.fromFlat(flat));
            }
        }
        return space.controlnet.ae2federation.client.policy.TopologySearch.matches(search, texts, places);
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
        for (var rule : shownRules()) {
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
        return shown().stream().filter(network -> network.id().equals(id)).findFirst().orElse(null);
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

    /** The network's name, or "Network 082A" by its identity tag while it has none. */
    static Component name(Network network) {
        return displayName(network.id(), network.name());
    }

    static Component displayName(String id, String name) {
        if (!name.isEmpty()) return Component.literal(name);
        return tr("network_name", id.substring(0, 4).toUpperCase(Locale.ROOT));
    }

    /** On a card the identity tag is already in the badge, so an unnamed network reads "Unnamed network". */
    private static Component cardName(Network network) {
        return network.name().isEmpty() ? tr("network_unnamed") : Component.literal(network.name());
    }

    /** "A▸B = A uses B's capability ■ active ■ off ■ not active yet ■ error", each square in its state colour. */
    private static Component legendText() {
        var legend = tr("legend.reads").copy();
        for (var entry : new Object[][] {{"active", FederationTheme.OK}, {"off", FederationTheme.DARK_MUTED},
                {"waiting", FederationTheme.WARN}, {"error", FederationTheme.ERROR}}) {
            legend.append("  ").append(Component.literal("■ ").append(tr("legend." + entry[0]))
                    .withStyle(Style.EMPTY.withColor((Integer) entry[1] & 0xffffff)));
        }
        return legend;
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

    /** {@code domain} is empty for members of this domain and names the related domain otherwise. */
    record Network(String id, String member, int index, String name, String domain) {
        boolean foreign() {
            return !domain.isEmpty();
        }

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
            if (fitted && (graph.getContentWidth() != fittedWidth || graph.getContentHeight() != fittedHeight)) {
                fitted = false;
                fitDelay = 1;
            }
            if (!fitted && fitDelay-- <= 0) {
                graph.fitToChildren(16, MIN_FIT_SCALE);
                fitted = true;
                fittedWidth = graph.getContentWidth();
                fittedHeight = graph.getContentHeight();
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
                var link = link(ends[0], ends[1]);
                line(context, link, selected ? FederationTheme.SELECT : FederationTheme.EDGE, selected ? 3f : 2f, false);
                // Each end is marked in its network's accent where the link meets the card.
                endMark(context, link.start(), network(ends[0]).accent());
                endMark(context, link.end(), network(ends[1]).accent());
            }
            if (!selectedNetwork.isEmpty() && positions.containsKey(selectedNetwork)) {
                for (var other : shown()) {
                    if (other.id().equals(selectedNetwork) || configured.contains(pair(selectedNetwork, other.id()))) continue;
                    line(context, link(selectedNetwork, other.id()), 0x668b83a0, 1.5f, true);
                }
            }
            pose.popPose();
        }

        private void line(GUIContext context, TopologyLink link, int color, float width, boolean dashed) {
            var points = link.curve().points(LINK_SEGMENTS);
            var line = new ArrayList<Vector2f>(LINK_SEGMENTS + 1);
            for (int index = 0; index < points.length; index += 2) line.add(new Vector2f(points[index], points[index + 1]));
            if (!dashed) {
                DrawerHelper.drawTexLines(context.graphics, LDLibRenderTypes.graphWire(), line, color, color, width);
                return;
            }
            for (int index = 1; index < line.size(); index += 2) {
                DrawerHelper.drawTexLines(context.graphics, LDLibRenderTypes.graphWire(), List.of(line.get(index - 1), line.get(index)),
                        color, color, width);
            }
        }

        private void endMark(GUIContext context, float[] point, int color) {
            int x = Math.round(point[0]);
            int y = Math.round(point[1]);
            context.graphics.fill(x - 3, y - 3, x + 3, y + 3, 0xff121016);
            context.graphics.fill(x - 2, y - 2, x + 2, y + 2, color);
        }
    }
}
