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
import space.controlnet.ae2federation.client.policy.DirectionLabelPose;
import space.controlnet.ae2federation.client.policy.EndpointNodeLayout;
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
import space.controlnet.ae2federation.policy.RuleMode;

/**
 * The domain seen as networks: a card per ME network, one edge per network pair with configured rules, and a pair
 * editor whose switches each request one explicit rule state. Layout and colours are local presentation only.
 */
final class FederationTopologyView {
    static final float CARD_WIDTH = 200;
    static final float CARD_HEIGHT = 88;
    private static final float THUMBNAIL_WIDTH = 44;
    private static final float THUMBNAIL_HEIGHT = 30;
    private static final float ENDPOINT_HEIGHT = 16;
    private static final PolicyCapability[] CAPABILITIES = PolicyCapability.values();
    private static final int LINK_SEGMENTS = 24;
    /** One run of a quartz bead along a shared-energy link, from end to end. */
    private static final long QUARTZ_BEAD_MILLIS = 3200;
    /** How close, in screen pixels, a press must land to a link to select its pair. */
    private static final float LINK_PICK_PIXELS = 5;
    /** Fitting may zoom out this far, so a narrow canvas still shows every card. */
    private static final float MIN_FIT_SCALE = 0.1f;

    private final GraphView graph;
    /** The canvas layer the links are drawn on, whose coordinates a press is measured in to pick a link. */
    private UIElement linkLayer;
    private final Consumer<String> setPolicy;
    private Consumer<String> setEndpointEnergy = target -> { };
    private final Consumer<String> rename;
    private final BiConsumer<String, String> openObject;
    private final Label title;
    private final Label identity;
    private final UIElement accent;
    private final Label detail;
    /** The panel of {@link #detail}, shown only when there is something to explain, such as an identity in doubt. */
    private final UIElement explain;
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
    private final UIElement endpointDetail;
    private final FederationMapPreview preview = new FederationMapPreview();
    private final UIElement location;
    private final Label locationNote;
    private final Label locationLegend;
    private final Button highlight;
    private List<space.controlnet.ae2federation.client.policy.BlockMarks.Mark> highlightBlocks = List.of();
    /** The parts of a network whose identity is in doubt, each outlined in its own colour. */
    private List<space.controlnet.ae2federation.client.WorldHighlight.Group> highlightParts = List.of();
    private String highlightDimension = "";
    private int highlightColor;

    private final List<Network> networks = new ArrayList<>();
    /**
     * Networks of the domains connected to this one through shared networks, however far; shown read-only in the "all
     * related" scope. The server sends at most a cap of them, nearest first, and says how many there are.
     */
    private final List<Network> related = new ArrayList<>();
    /** {@link #shown()} by id, rebuilt after the shown networks change; the canvas looks networks up every frame. */
    private Map<String, Network> shownById;
    private int relatedTotal;
    private final Map<String, JsonObject> relatedRules = new HashMap<>();
    /** Accepted deliveries per rule key over the last five seconds, from real transfers only. */
    private final Map<String, JsonObject> flows = new HashMap<>();
    private Label throughput;
    /** The domain's Router group or Bridge, as the server reports it. */
    private JsonObject via;
    private boolean showRelated;
    private final Button scopeButton;
    private final Button domainScopeButton;
    private final Label scopeCaption;
    private final Map<String, JsonObject> rules = new HashMap<>();
    private final Map<String, Long> revisions = new HashMap<>();
    private final Map<String, String> memberStatus = new HashMap<>();
    private final Map<String, List<String>> providersByMember = new HashMap<>();
    /** The domain's Processing Endpoints, drawn as small nodes beside the network whose Provider maps them. */
    private final List<EndpointNode> endpointNodes = new ArrayList<>();
    private final Map<String, EndpointNodeLayout.Placed> endpointPlaces = new HashMap<>();
    private final Map<String, Button> endpointButtons = new HashMap<>();
    /** What each Endpoint's owner sent it and got back over the flow window, by Endpoint. */
    private final Map<String, JsonObject> endpointFlows = new HashMap<>();
    /** The related domains each shown network is in; networks that share no domain do not discover each other. */
    private final Map<String, java.util.Set<String>> networkDomains = new HashMap<>();
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
    /** The Endpoint whose panel the aside shows; at most one of it, the network and the pair is selected. */
    private String selectedEndpoint = "";
    /** The server's facts about each Endpoint, by id: its modes, claim, identity and the patterns mapped to it. */
    private final Map<String, JsonObject> endpointFacts = new HashMap<>();
    /** Whether the server lets this viewer follow an Endpoint to its owner's mappings. */
    private boolean endpointNavigation;
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
    /** Half sizes of the energy chips that sit on their link, by pair, which hide the link's middle from flow dots. */
    private final Map<String, Vector2f> centredLabelHalfSizes = new HashMap<>();
    private final List<java.util.function.Consumer<String>> searchListeners = new ArrayList<>();
    /** Where each link's label sits along it, by pair; the middle unless crossing links would stack their labels. */
    private final Map<String, Float> labelSpots = new HashMap<>();
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
        // GraphView pans and zooms only for a press or a wheel turn that reaches the bare canvas. Cards, link labels and
        // Endpoint nodes cover most of it, so they hand theirs on, in the capture phase before anything under them can
        // stop it: a press still selects what it lands on, and dragging from there pans the view. A press on the bare
        // canvas that lands on a link selects its pair.
        graph.addEventListener(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents.MOUSE_DOWN, event -> {
            if (event.target == graph) {
                if (event.button == 0) pickLink(event.x, event.y);
            } else if (event.button == 0 && onCanvas(event.target)) {
                graph.startDrag(new GraphView.DragOffset(graph.getOffsetX(), graph.getOffsetY()), null);
            }
        }, true);
        graph.addEventListener(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents.MOUSE_WHEEL, event -> {
            if (event.target != graph && onCanvas(event.target)) zoomAt(event.x, event.y, event.deltaY);
        }, true);
        title = element(ui, "network_title", Label.class);
        identity = element(ui, "network_identity", Label.class);
        accent = element(ui, "network_accent", UIElement.class);
        detail = element(ui, "graph_selection", Label.class);
        explain = element(ui, "network_explain", UIElement.class);
        devices = element(ui, "graph_open", Button.class);
        linksHeading = element(ui, "network_links_heading", Label.class);
        links = element(ui, "network_links", UIElement.class);
        networkDetail = element(ui, "network_detail", UIElement.class);
        endpointDetail = element(ui, "endpoint_detail", UIElement.class);
        pairEditor = element(ui, "pair_editor", UIElement.class);
        pairTitle = element(ui, "pair_title", Label.class);
        pairNote = element(ui, "pair_note", Label.class);
        pairSections = element(ui, "pair_sections", UIElement.class);
        searchEmpty = element(ui, "graph_search_empty", Label.class);
        searchEmpty.setText(tr("no_network_matches"));
        var searchField = element(ui, "graph_search", TextField.class);
        searchField.textFieldStyle(style -> style.placeholder(tr("search").withStyle(Style.EMPTY.withColor(FederationTheme.PLACEHOLDER & 0xffffff))));
        searchField.setTextResponder(value -> {
            search = value.strip().toLowerCase(Locale.ROOT);
            applySearch();
            searchListeners.forEach(listener -> listener.accept(search));
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
        FederationIcons.apply(flowToggle, FederationIcons.FLOW);
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
        // On an Endpoint's panel the same button follows it to its owner's mappings.
        devices.setOnClick(event -> {
            if (selectedEndpoint.isEmpty()) openDevices();
            else if (endpointNavigation) openObject.accept("endpoint_mapping", selectedEndpoint);
        });
        pairEditor.setDisplay(false);
        scopeButton = element(ui, "graph_scope", Button.class);
        domainScopeButton = element(ui, "graph_scope_domain", Button.class);
        scopeCaption = element(ui, "scope_caption", Label.class);
        FederationIcons.apply(scopeButton, FederationIcons.SCOPE_ALL);
        FederationIcons.apply(domainScopeButton, FederationIcons.SCOPE_DOMAIN);
        domainScopeButton.style(style -> style.tooltips(tr("scope.domain_help")));
        domainScopeButton.setOnClick(event -> setScope(false));
        scopeButton.setOnClick(event -> setScope(true));
        updateScopeButton();
        stats = element(ui, "network_stats", UIElement.class);
        renameButton = element(ui, "network_rename", Button.class);
        renameRow = element(ui, "network_rename_row", UIElement.class);
        renameField = element(ui, "network_rename_field", TextField.class);
        renameSave = element(ui, "network_rename_save", Button.class);
        renameField.textFieldStyle(style -> style.placeholder(tr("rename_placeholder").withStyle(Style.EMPTY.withColor(FederationTheme.PLACEHOLDER & 0xffffff))));
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
        locationLegend = element(ui, "network_location_legend", Label.class);
        highlight = element(ui, "network_highlight", Button.class);
        element(ui, "network_preview", UIElement.class).addChild(preview);
        preview.setModeButtons(element(ui, "network_view_map", Button.class), element(ui, "network_view_3d", Button.class));
        // The button stays pressed while its blocks are outlined; pressing it again ends the outline early.
        highlight.setOnClick(event -> {
            if (highlightBlocks.isEmpty()) return;
            if (highlightBrightness() > 0) {
                space.controlnet.ae2federation.client.WorldHighlight.hide(highlightDimension, highlightGroups());
            } else {
                space.controlnet.ae2federation.client.WorldHighlight.show(highlightDimension, highlightGroups());
            }
            syncHighlight();
        });
        highlight.addEventListener(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents.TICK, event -> syncHighlight());
        preview.setHighlight(this::highlightBrightness);
    }

    /** The design's segmented scope: this domain only, or also the related domains' networks, read-only. */
    private void setScope(boolean related) {
        if (showRelated == related) return;
        showRelated = related;
        shownById = null;
        if (!showRelated && network(selectedNetwork) == null) selectedNetwork = "";
        if (!showRelated && !selectedPair.isEmpty() && java.util.Arrays.stream(selectedPair.split("\\|"))
                .anyMatch(id -> network(id) == null)) selectedPair = "";
        // Fit once the rebuilt cards have been laid out; fitting in the same tick measures the old graph.
        fitted = false;
        fitDelay = 2;
        updateScopeButton();
        refresh();
    }

    /** Device-to-network membership and live network status come from the scoped graph projection. */
    void accept(String encoded) {
        if (encoded.isEmpty()) return;
        var snapshot = FederationDomainGraphSnapshot.decode(encoded);
        memberStatus.clear();
        providersByMember.clear();
        var kinds = new HashMap<String, FederationDomainGraphNodeKind>();
        for (var node : snapshot.nodes()) {
            kinds.put(node.id(), node.kind());
            if (node.kind() == FederationDomainGraphNodeKind.MEMBER) memberStatus.put(node.id(), node.status());
        }
        for (var edge : snapshot.edges()) {
            // An Endpoint is drawn beside the network that maps it, not the one its subnet may also be.
            if (edge.layer() != FederationDomainGraphLayer.PHYSICAL || kinds.get(edge.to()) != FederationDomainGraphNodeKind.PROVIDER) continue;
            providersByMember.computeIfAbsent(edge.from(), key -> new ArrayList<>()).add(edge.to());
        }
        refresh();
    }

    void acceptChoices(JsonObject root) {
        // An unpublished domain sends no members; keep the last view until the server reports the context stale.
        if (!root.has("networks") && !networks.isEmpty()) return;
        scope = root.has("scope") ? root.get("scope").getAsString() : "domain";
        via = root.has("via") ? root.getAsJsonObject("via") : null;
        networks.clear();
        networkDomains.clear();
        shownById = null;
        if (root.has("networks")) {
            var index = 0;
            for (var value : root.getAsJsonArray("networks")) {
                var json = value.getAsJsonObject();
                networks.add(new Network(json.get("id").getAsString(), json.get("member").getAsString(), index++,
                        json.has("name") ? json.get("name").getAsString() : "", ""));
                networkDomains.put(json.get("id").getAsString(), domains(json));
            }
        }
        related.clear();
        relatedTotal = root.has("relatedTotal") ? root.get("relatedTotal").getAsInt() : 0;
        if (root.has("relatedNetworks")) {
            var index = networks.size();
            for (var value : root.getAsJsonArray("relatedNetworks")) {
                var json = value.getAsJsonObject();
                var id = json.get("id").getAsString();
                related.add(new Network(id, "related_" + id, index++, json.has("name") ? json.get("name").getAsString() : "",
                        json.get("domain").getAsString()));
                networkDomains.put(id, domains(json));
            }
        }
        shownById = null;
        endpointNodes.clear();
        endpointFacts.clear();
        // Without a domain an Endpoint opened from its own block is shown alone, read-only.
        var endpointValues = new ArrayList<com.google.gson.JsonElement>();
        if (root.has("endpoint")) root.getAsJsonArray("endpoint").forEach(endpointValues::add);
        if (root.has("localEndpoint")) root.getAsJsonArray("localEndpoint").forEach(endpointValues::add);
        for (var value : endpointValues) {
            var json = value.getAsJsonObject();
            endpointNodes.add(new EndpointNode(json.get("id").getAsString(),
                    json.has("position") ? json.get("position").getAsString() : "",
                    json.has("ownerNetwork") ? json.get("ownerNetwork").getAsString() : "",
                    json.has("runtimeMode") ? json.get("runtimeMode").getAsString() : "UNBOUND",
                    json.has("nodeReady") && json.get("nodeReady").getAsBoolean(),
                    json.has("subnetAlone") && json.get("subnetAlone").getAsBoolean(),
                    json.has("energyShared") && json.get("energyShared").getAsBoolean()));
            endpointFacts.put(json.get("id").getAsString(), json);
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
        // An Endpoint's own entrance opens with that Endpoint selected and centred.
        if (!focusApplied && root.has("initialEndpoint") && endpointFacts.containsKey(root.get("initialEndpoint").getAsString())) {
            selectedEndpoint = root.get("initialEndpoint").getAsString();
            selectedNetwork = "";
            selectedPair = "";
            pendingCenter = selectedEndpoint;
            focusApplied = true;
        }
        if (!selectedEndpoint.isEmpty() && !endpointFacts.containsKey(selectedEndpoint)) selectedEndpoint = "";
        if (selectedNetwork.isEmpty() && selectedPair.isEmpty() && selectedEndpoint.isEmpty() && !networks.isEmpty()) {
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

    /** The network's blocks from its overview when it is in {@code dimension}; empty otherwise or before one arrives. */
    List<space.controlnet.ae2federation.client.policy.BlockMarks.Mark> networkBlocks(String networkId, String dimension) {
        var facts = overview.get(networkId);
        if (facts == null || !facts.has("blocks") || !facts.has("dimension")
                || !facts.get("dimension").getAsString().equals(dimension)) return List.of();
        var blocks = new ArrayList<Integer>();
        facts.getAsJsonArray("blocks").forEach(value -> blocks.add(value.getAsInt()));
        return space.controlnet.ae2federation.client.policy.BlockMarks.fromFlat(blocks);
    }

    /** Recent accepted deliveries per rule; an edge animates only while its rule actually moved something. */
    void acceptFlows(JsonArray values) {
        flows.clear();
        endpointFlows.clear();
        for (var value : values) {
            var flow = value.getAsJsonObject();
            if (flow.has("endpoint")) {
                endpointFlows.put(flow.get("endpoint").getAsString(), flow);
                continue;
            }
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
        for (var endpoint : endpointNodes) {
            var flow = endpointFlows.get(endpoint.id());
            if (flow != null && shownIds.contains(endpoint.owner())) events += flow.get("events").getAsLong();
        }
        // An idle canvas says nothing; the line appears while rules deliver.
        throughput.setText(events == 0 ? Component.empty()
                : tr("throughput", events).withStyle(Style.EMPTY.withColor(FederationTheme.TEAL & 0xffffff)));
    }

    private void updateScopeButton() {
        var caption = Component.literal("· ").append(tr(showRelated ? "scope.related" : "scope.domain"));
        boolean truncated = relatedTotal > related.size();
        if (showRelated && truncated) caption.append(" · ").append(tr("scope.truncated", related.size(), relatedTotal));
        scopeCaption.setText(caption);
        scopeButton.style(style -> style.tooltips(truncated
                ? tr("scope.help_truncated", related.size(), relatedTotal) : tr("scope.help", related.size())));
        scopeButton.removeClass("selected");
        domainScopeButton.removeClass("selected");
        (showRelated ? scopeButton : domainScopeButton).addClass("selected");
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

    /** Where an Endpoint's energy switch sends its {@code EndpointEnergyTarget}. */
    void onEndpointEnergy(Consumer<String> sender) {
        setEndpointEnergy = sender;
    }

    void setEditable(boolean value) {
        if (editable == value) return;
        editable = value;
        asideSignature = "";
        refresh();
    }

    private void refresh() {
        var signature = new StringBuilder();
        shown().forEach(network -> signature.append(network.id()).append('=').append(network.name()).append(','));
        signature.append('|').append(selectedNetwork).append('|').append(selectedPair).append('|').append(selectedEndpoint).append('|');
        pairsWithRules().forEach(value -> signature.append(value).append(';'));
        // The mode too: a rule that starts re-exporting turns its chip cyan without changing its state.
        shownRules().forEach(rule -> signature.append(rule.get("capability").getAsString())
                .append(mode(rule)).append(ruleState(rule).code()));
        memberStatus.forEach((member, status) -> signature.append(member).append('=').append(status));
        endpointNodes.forEach(endpoint -> signature.append(endpoint).append(';'));
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
        centredLabelHalfSizes.clear();
        endpointButtons.clear();
        layout();
        linkLayer = new Links();
        graph.addContentChild(linkLayer);
        // Above the lines but below the link labels and cards, so dots never cover text.
        var pulses = new FederationFlowPulses(this::flows);
        var extent = extent();
        pulses.layout(style -> style.positionType(TaffyPosition.ABSOLUTE).left(0).top(0).width(extent.x + 8).height(extent.y + 8));
        graph.addContentChild(pulses);
        for (var pair : pairsWithRules()) linkLabels(pair).forEach(graph::addContentChild);
        for (var network : shown()) graph.addContentChild(card(network));
        for (var endpoint : endpointNodes) {
            if (endpointPlaces.containsKey(endpoint.id())) graph.addContentChild(endpointNode(endpoint));
        }
    }

    /** Whether {@code element} is drawn on the graph's canvas, as cards, link labels and Endpoint nodes are. */
    private boolean onCanvas(UIElement element) {
        for (var at = element; at != null && at != graph; at = at.getParent()) {
            if (at == graph.contentRoot) return true;
        }
        return false;
    }

    /** GraphView's own wheel zoom, kept on the point under the mouse, for a wheel turn over something on the canvas. */
    private void zoomAt(float x, float y, double deltaY) {
        var style = graph.getGraphViewStyle();
        float scale = graph.getScale();
        float newScale = net.minecraft.util.Mth.clamp(scale + (float) deltaY * 0.1f, style.minScale(), style.maxScale());
        if (newScale == scale) return;
        var local = graph.getLocalMouse(x, y);
        float rx = local.x - graph.getPositionX();
        float ry = local.y - graph.getPositionY();
        graph.setOffsetX(graph.getOffsetX() + rx / scale - rx / newScale);
        graph.setOffsetY(graph.getOffsetY() + ry / scale - ry / newScale);
        graph.setScale(newScale);
    }

    /**
     * Selects the pair whose link passes under a press on the bare canvas: a link with rules, or, while a network is
     * selected, one of its dashed links to the networks it could be linked with. The link must lie within a few
     * screen pixels, whatever the zoom.
     */
    private void pickLink(float x, float y) {
        if (linkLayer == null) return;
        var local = linkLayer.getLocalMouse(x, y);
        float pointX = local.x - linkLayer.getPositionX();
        float pointY = local.y - linkLayer.getPositionY();
        var candidates = new ArrayList<>(pairsWithRules());
        if (!selectedNetwork.isEmpty() && positions.containsKey(selectedNetwork)) {
            for (var other : shown()) {
                if (other.id().equals(selectedNetwork) || !discovers(network(selectedNetwork), other)) continue;
                var pair = pair(selectedNetwork, other.id());
                if (!candidates.contains(pair)) candidates.add(pair);
            }
        }
        String nearest = null;
        float best = LINK_PICK_PIXELS / Math.max(0.01f, graph.getScale());
        for (var pair : candidates) {
            var ends = pair.split("\\|");
            if (!positions.containsKey(ends[0]) || !positions.containsKey(ends[1])) continue;
            float distance = link(ends[0], ends[1]).curve().distance(pointX, pointY, LINK_SEGMENTS);
            if (distance < best) {
                best = distance;
                nearest = pair;
            }
        }
        if (nearest == null) return;
        // A link is picked from the canvas, not a Button, so it clicks the way the labels and cards do.
        com.lowdragmc.lowdraglib2.gui.util.UISoundUtils.playButtonClickSound();
        selectedPair = nearest;
        selectedNetwork = "";
        selectedEndpoint = "";
        focusApplied = true;
        refresh();
    }

    /** The bottom-right corner of everything on the canvas: cards and Endpoint nodes. */
    private Vector2f extent() {
        float width = positions.values().stream().map(point -> point.x + CARD_WIDTH).max(Float::compare).orElse(1f);
        float height = positions.values().stream().map(point -> point.y + CARD_HEIGHT).max(Float::compare).orElse(1f);
        for (var place : endpointPlaces.values()) {
            width = Math.max(width, place.x() + place.width());
            height = Math.max(height, place.y() + ENDPOINT_HEIGHT);
        }
        return new Vector2f(width, height);
    }

    /** Links, provider to consumer, of every rule that delivered something in the flow window. */
    private List<FederationFlowPulses.Flow> flows() {
        var flows = new ArrayList<FederationFlowPulses.Flow>();
        if (liveFlowHidden) return flows;
        for (var pair : pairsWithRules()) {
            var ends = pair.split("\\|");
            if (!positions.containsKey(ends[0]) || !positions.containsKey(ends[1])) continue;
            var link = pairLink(pair);
            var label = centredLabelHalfSizes.getOrDefault(pair, new Vector2f());
            // Resources travel from the providing network to the consumer: from the link's end when the first network consumes.
            if (flowing(ends[0], ends[1])) flows.add(new FederationFlowPulses.Flow(link, true, label.x, label.y));
            if (flowing(ends[1], ends[0])) flows.add(new FederationFlowPulses.Flow(link, false, label.x, label.y));
        }
        // Inputs travel from the owner's network to its Endpoint, and results come back.
        for (var place : endpointPlaces.values()) {
            var flow = endpointFlows.get(place.id());
            if (flow == null || place.link() == null) continue;
            var link = new TopologyLink(place.link(), 0.5f);
            if (flow.get("events").getAsLong() > 0) flows.add(new FederationFlowPulses.Flow(link, false, 0, 0));
            if (flow.get("returnedEvents").getAsLong() > 0) flows.add(new FederationFlowPulses.Flow(link, true, 0, 0));
        }
        return flows;
    }

    /** The link of a pair with rules, its label where the layout placed it. */
    private TopologyLink pairLink(String pair) {
        var ends = pair.split("\\|");
        return link(ends[0], ends[1]).withLabelAt(labelSpots.getOrDefault(pair, 0.5f));
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
        // Spread the ellipse until every link label clears the cards, as the design keeps its labels in open canvas.
        var corners = new float[count][];
        var index = new HashMap<String, Integer>();
        for (int i = 0; i < count; i++) {
            corners[i] = new float[] {raw.get(i).x, raw.get(i).y};
            index.put(ordered.get(i).id(), i);
        }
        var labels = new ArrayList<space.controlnet.ae2federation.client.policy.TopologySpacing.Label>();
        var labelled = new ArrayList<String>();
        var font = net.minecraft.client.Minecraft.getInstance().font;
        for (var pair : pairsWithRules()) {
            var ends = pair.split("\\|");
            if (!index.containsKey(ends[0]) || !index.containsKey(ends[1])) continue;
            var a = corners[index.get(ends[0])];
            var b = corners[index.get(ends[1])];
            var half = labelExtent(network(ends[0]), network(ends[1]),
                    TopologyLink.between(a[0], a[1], b[0], b[1], CARD_WIDTH, CARD_HEIGHT), font);
            labels.add(new space.controlnet.ae2federation.client.policy.TopologySpacing.Label(index.get(ends[0]),
                    index.get(ends[1]), half.x, half.y));
            labelled.add(pair);
        }
        float spread = space.controlnet.ae2federation.client.policy.TopologySpacing.factor(corners, labels, CARD_WIDTH, CARD_HEIGHT, 8);
        raw.forEach(point -> point.mul(spread));
        // Endpoints beside the network that maps them, on its outer side; the others wait below. Spread the cards
        // further while one network's Endpoints would cover another card or its Endpoints.
        var nodes = endpointNodes.stream().map(endpoint -> new EndpointNodeLayout.Node(endpoint.id(),
                index.containsKey(endpoint.owner()) ? endpoint.owner() : "", endpointWidth(endpoint, font))).toList();
        var cardCorners = new ArrayList<EndpointNodeLayout.Card>();
        var endpointsPlaced = List.<EndpointNodeLayout.Placed>of();
        for (int attempt = 0; ; attempt++) {
            cardCorners.clear();
            for (int i = 0; i < count; i++) cardCorners.add(new EndpointNodeLayout.Card(ordered.get(i).id(), raw.get(i).x, raw.get(i).y));
            endpointsPlaced = EndpointNodeLayout.place(cardCorners, CARD_WIDTH, CARD_HEIGHT, nodes, ENDPOINT_HEIGHT);
            if (count <= 1 || attempt >= 12
                    || EndpointNodeLayout.clear(cardCorners, CARD_WIDTH, CARD_HEIGHT, endpointsPlaced, ENDPOINT_HEIGHT)) break;
            raw.forEach(point -> point.mul(1.15f));
        }
        float minX = raw.stream().map(point -> point.x).min(Float::compare).orElse(0f);
        float minY = raw.stream().map(point -> point.y).min(Float::compare).orElse(0f);
        for (var place : endpointsPlaced) {
            minX = Math.min(minX, place.x());
            minY = Math.min(minY, place.y());
        }
        for (int i = 0; i < count; i++) {
            positions.put(ordered.get(i).id(), new Vector2f(raw.get(i).x - minX + 8, raw.get(i).y - minY + 8));
        }
        endpointPlaces.clear();
        for (var place : endpointsPlaced) endpointPlaces.put(place.id(), place.moved(-minX + 8, -minY + 8));
        // Links that cross, such as a diamond's diagonals, would stack their labels in the middle; move one along.
        var placed = new float[count][];
        for (int i = 0; i < count; i++) placed[i] = new float[] {raw.get(i).x, raw.get(i).y};
        var spots = space.controlnet.ae2federation.client.policy.TopologySpacing.labelSpots(placed, labels, CARD_WIDTH, CARD_HEIGHT, 4);
        labelSpots.clear();
        for (int i = 0; i < labelled.size(); i++) labelSpots.put(labelled.get(i), spots[i]);
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
        // Not drawn: lets the UI tests find a network's card by its identity, whatever its name.
        button.addClass("network-" + network.id());
        button.setId("graph_node_" + sanitize(network.member()));
        boolean selected = network.id().equals(selectedNetwork) || selectedPair.contains(network.id());
        if (network.foreign()) button.addClass("related-network");
        var state = cardState.computeIfAbsent(network.id(), ignored -> new int[] {FederationTheme.DARK_MUTED});
        // A related domain's network is outlined dashed and locked, as its links are: shown here, edited elsewhere. Its
        // dashes take the place of the card's solid edge, in the selection colour while selected or hovered, and its
        // state strip along the bottom edge is dashed too, so no solid line runs along any of its edges.
        boolean dashed = network.foreign();
        var inset = FederationTheme.painted((pen, x, y, width, height) -> {
            if (!dashed) {
                pen.rect(x + 2, y + height - 4, width - 4, 2, state[0]);
                return;
            }
            for (float at = 0; at < width - 4; at += 6) pen.rect(x + 2 + at, y + height - 4, Math.min(4, width - 4 - at), 2, state[0]);
        });
        com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture face;
        com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture hover;
        if (network.foreign()) {
            var lock = FederationTheme.lockMark(FederationTheme.DARK_MUTED);
            var hoverOutline = FederationTheme.dashedBorder(FederationTheme.SELECT, 1);
            face = GuiTextureGroup.of(FederationTheme.CARD_RELATED, inset,
                    selected ? hoverOutline : FederationTheme.dashedBorder(FederationTheme.DARK_MUTED, 1), lock);
            hover = GuiTextureGroup.of(FederationTheme.CARD_RELATED, inset, hoverOutline, lock);
        } else {
            face = GuiTextureGroup.of(selected ? FederationTheme.CARD_SELECTED : FederationTheme.CARD, inset);
            hover = GuiTextureGroup.of(FederationTheme.CARD_SELECTED, inset);
        }
        button.buttonStyle(style -> style.baseTexture(face).hoverTexture(hover).pressedTexture(face));
        button.layout(style -> style.positionType(TaffyPosition.ABSOLUTE).left(position.x).top(position.y)
                .width(CARD_WIDTH).height(CARD_HEIGHT).paddingAll(6).paddingBottom(7).gapAll(4)
                .flexDirection(FlexDirection.COLUMN).alignItems(AlignItems.FLEX_START));
        var top = new UIElement();
        top.layout(style -> style.widthPercent(100).height(THUMBNAIL_HEIGHT).flexDirection(FlexDirection.ROW).gapAll(5)
                .flexShrink(0));
        // Where the network is, as a map tile of its blocks.
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
        // The name in bold, "Network 0A1F" by its identity tag while it has none.
        var heading = text(name(network).copy().withStyle(net.minecraft.ChatFormatting.BOLD), FederationTheme.DARK_TITLE);
        heading.addClass("card-name");
        heading.setId("graph_node_name_" + sanitize(network.member()));
        heading.layout(style -> style.flex(1).minWidth(0).widthAuto());
        head.addChildren(swatch, heading);
        // Room for the lock a related network's card carries in its corner.
        if (network.foreign()) head.layout(style -> style.paddingRight(4));
        var positionLine = text(Component.empty(), FederationTheme.DARK_MUTED);
        var stateLine = text(Component.empty(), FederationTheme.DARK_TEXT);
        positionLine.setId("graph_node_position_" + sanitize(network.member()));
        stateLine.setId("graph_node_state_" + sanitize(network.member()));
        // A long state ("Waiting for domain status · Identity confirmed") keeps to the card, at a word boundary.
        stateLine.textStyle(style -> style.textWrap(TextWrap.HIDE));
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
        cards.put(network.id(), button);
        cardLines.put(network.id(), new Label[] {stateLine, storageValue, positionLine, heading, energyValue, cpus, channels});
        return button;
    }

    /**
     * A Processing Endpoint as a small node, "Endpoint · 12, 64, -3" after a dot in its state colour; its border lights
     * with the network that maps it. A click opens its diagnostics.
     */
    private Button endpointNode(EndpointNode endpoint) {
        var place = endpointPlaces.get(endpoint.id());
        var button = new Button();
        button.noText();
        button.addClass("graph-node-endpoint");
        button.setId("graph_endpoint_" + sanitize(endpoint.id()));
        boolean selected = endpoint.id().equals(selectedEndpoint)
                || !selectedNetwork.isEmpty() && endpoint.owner().equals(selectedNetwork);
        var face = GuiTextureGroup.of(FederationTheme.WELL_RECT,
                new com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture(1, selected ? FederationTheme.SELECT : 0xff47434f));
        var hover = GuiTextureGroup.of(FederationTheme.WELL_RECT,
                new com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture(1, FederationTheme.SELECT));
        button.buttonStyle(style -> style.baseTexture(face).hoverTexture(hover).pressedTexture(face));
        button.layout(style -> style.positionType(TaffyPosition.ABSOLUTE).left(place.x()).top(place.y()).width(place.width())
                .height(ENDPOINT_HEIGHT).paddingLeft(4).paddingRight(4).gapAll(3).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER));
        // Undrawn, so tests read what the dot says.
        button.addClass(endpointHealth(endpoint).cssClass());
        var dot = new UIElement();
        int color = endpointColor(endpoint);
        dot.layout(style -> style.width(5).height(5).flexShrink(0));
        dot.style(style -> style.backgroundTexture(FederationTheme.solid(color)));
        var label = text(endpointLabel(endpoint), FederationTheme.DARK_TEXT);
        label.layout(style -> style.flex(1).minWidth(0).widthAuto().height(9));
        button.addChildren(dot, label);
        button.setOnClick(event -> selectEndpoint(endpoint.id()));
        endpointButtons.put(endpoint.id(), button);
        return button;
    }

    private static Component endpointLabel(EndpointNode endpoint) {
        return FederationWorkspace.tr("endpoint_at", endpoint.position());
    }

    private static float endpointWidth(EndpointNode endpoint, net.minecraft.client.gui.Font font) {
        return font.width(endpointLabel(endpoint)) + 4 + 5 + 3 + 4 + 2;
    }

    private space.controlnet.ae2federation.client.policy.EndpointHealth endpointHealth(EndpointNode endpoint) {
        return space.controlnet.ae2federation.client.policy.EndpointHealth.of(endpoint.mode(),
                network(endpoint.owner()) != null, endpoint.ready(), endpoint.alone());
    }

    /** Usable in the OK colour; claimed but without its ME node or anything behind it in warning; Local or free muted. */
    private int endpointColor(EndpointNode endpoint) {
        return switch (endpointHealth(endpoint)) {
            case ACTIVE -> FederationTheme.OK;
            case WAITING -> FederationTheme.WARN;
            case OFF -> FederationTheme.DARK_MUTED;
        };
    }

    /** The energy a card and the stats show: the shared pool for a network in one, else its own cells. */
    private static space.controlnet.ae2federation.client.policy.EnergyFigures energyFigures(JsonObject facts) {
        if (facts == null || !facts.has("energyMax")) return null;
        return space.controlnet.ae2federation.client.policy.EnergyFigures.of(facts.get("energy").getAsLong(),
                facts.get("energyMax").getAsLong(), facts.has("energyPool") ? facts.get("energyPool").getAsLong() : null,
                facts.has("energyPoolMax") ? facts.get("energyPoolMax").getAsLong() : null,
                facts.has("energyPoolGrids") ? facts.get("energyPoolGrids").getAsInt() : 0);
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
            var facts = overview.get(network.id());
            var identityState = identityState(network);
            lines[2].setText(facts == null || !facts.has("x") ? Component.empty() : tr("card_position",
                    dimension(facts.get("dimension").getAsString()), facts.get("x").getAsInt() + ", " + facts.get("y").getAsInt()
                            + ", " + facts.get("z").getAsInt()));
            var figures = energyFigures(facts);
            if (energy != null) energy[0] = figures == null ? -1 : figures.fraction();
            // Nothing for an online, confirmed network; otherwise what needs attention: an identity in doubt, then low
            // energy. A related domain's network is not in this domain's graph; it reads online while its Grid has power.
            var status = memberStatus.getOrDefault(network.member(), "pending");
            boolean online = network.foreign() ? facts != null && facts.has("powered") && facts.get("powered").getAsBoolean()
                    : status.equals("online");
            int stateColor;
            Component detail;
            if (!identityState.equals("settled") && !identityState.isEmpty()) {
                stateColor = toneColor(identityState);
                detail = tr("identity." + identityState);
            } else if (network.foreign()) {
                // Which domain it belongs to comes first: low energy there is that domain's to act on.
                stateColor = online ? FederationTheme.OK : FederationTheme.WARN;
                detail = tr("related_card", domainName(network.domain()));
            } else if (energy != null && energy[0] >= 0 && energy[0] < 0.25f) {
                stateColor = FederationTheme.WARN;
                detail = tr("card_low_energy");
            } else if (figures != null && figures.shared()) {
                stateColor = online ? FederationTheme.OK : FederationTheme.WARN;
                detail = tr("card_shared_energy");
            } else {
                stateColor = online ? FederationTheme.OK : FederationTheme.WARN;
                detail = null;
            }
            // An identity in doubt takes the whole line: it is what the player has to act on. Being online is the
            // normal case and goes unsaid.
            boolean doubt = !identityState.equals("settled") && !identityState.isEmpty();
            var head = online ? null : tr(network.foreign() ? "card_unpowered" : "card_waiting");
            var line = doubt || head == null ? detail : detail == null ? head : tr("card_state", head, detail);
            lines[0].setText(line == null ? Component.empty() : line.copy().withStyle(Style.EMPTY.withColor(stateColor & 0xffffff)));
            if (state != null) state[0] = stateColor;
            if (facts == null || !facts.has("energyMax")) {
                lines[1].setText(tr("stats_unavailable"));
                lines[4].setText(Component.empty());
                lines[5].setText(Component.empty());
                lines[6].setText(Component.empty());
                continue;
            }
            lines[4].setText(Component.literal(figures.percent() + "%").withStyle(Style.EMPTY.withColor(energyColor(energy == null ? -1 : energy[0]) & 0xffffff)));
            lines[1].setText(tr("card.types", compact(facts.get("types").getAsLong())));
            lines[5].setText(tr("card.cpus", Component.literal(facts.get("cpusBusy").getAsInt() + "/" + facts.get("cpus").getAsInt())
                    .withStyle(Style.EMPTY.withColor(FederationTheme.VALUE & 0xffffff))));
            lines[6].setText(tr("card.channels", Component.literal(Integer.toString(facts.get("channels").getAsInt()))
                    .withStyle(Style.EMPTY.withColor(FederationTheme.DARK_TEXT & 0xffffff))));
        }
    }

    private static final int LABEL_PADDING = 3;
    private static final int LABEL_HEIGHT = 11 + 2 * LABEL_PADDING;
    private static final int LABEL_CAP = 6;
    private static final int LABEL_CLEARANCE = 3;
    /** Between stacked chips. */
    private static final int LABEL_STACK_GAP = 2;
    /** Room at the label's end for a related domain's padlock. */
    private static final int LABEL_LOCK = 8;

    /**
     * The labels of a link: one per direction that has rules, on the left of the way the capabilities travel, its cap
     * pointing at the network that uses them; a chip per capability in the colour of its state, marked "!" on error.
     * Shared energy is energy both ways, so both directions end with its chip; a pair with nothing but energy has one
     * level chip on the link instead. A related domain's labels are dashed and locked.
     */
    private List<Button> linkLabels(String pair) {
        var ends = pair.split("\\|");
        var a = network(ends[0]);
        var b = network(ends[1]);
        var link = pairLink(pair);
        boolean related = a.foreign() || b.foreign();
        var font = net.minecraft.client.Minecraft.getInstance().font;
        var labels = new ArrayList<Button>();
        var at = link.label();
        if (!directed(a, b)) {
            var energy = energyChip(a, b);
            if (energy == null) return labels;
            var chips = List.of(energy);
            float width = labelBodyWidth(chips, related, font);
            centredLabelHalfSizes.put(pair, new Vector2f(width / 2, LABEL_HEIGHT / 2f));
            labels.add(linkLabel(pair, "graph_pair_" + sanitize(a.member()) + "_" + sanitize(b.member()), chips, null,
                    false, related, at[0], at[1], width, LABEL_HEIGHT, 0));
            return labels;
        }
        var tangent = link.curve().tangent(link.labelAt());
        for (var direction : List.of(new Network[] {a, b}, new Network[] {b, a})) {
            var chips = directionChips(direction[0], direction[1]);
            if (chips.isEmpty()) continue;
            var pose = labelPose(tangent, direction[1] == a, chips, related, font);
            labels.add(linkLabel(pair, "graph_pair_" + sanitize(direction[0].member()) + "_" + sanitize(direction[1].member()),
                    chips, pose.cap(), pose.stacked(), related, at[0] + pose.offsetX(), at[1] + pose.offsetY(), pose.width(),
                    pose.height(), pose.rotation()));
        }
        return labels;
    }

    /**
     * One label centred on {@code (x, y)}, turned by {@code rotation} degrees; a null cap is the level energy chip.
     * Stacked chips stand one under another, as wide as the widest, their words centred.
     */
    private Button linkLabel(String pair, String id, List<Chip> chips, DirectionLabelPose.Cap cap, boolean stacked,
            boolean related, float x, float y, float width, float height, float rotation) {
        var button = new Button();
        button.noText();
        button.setId(id);
        button.addClass("graph-pair");
        // A related domain's link is shown, not edited here: dashed and locked, as its cards are read-only.
        if (related) button.addClass("related-pair");
        boolean selected = pair.equals(selectedPair);
        var face = labelFace(cap, selected ? FederationTheme.SELECT : related ? FederationTheme.EDGE : 0xff47434f, related);
        var hover = labelFace(cap, FederationTheme.SELECT, related);
        button.buttonStyle(style -> style.baseTexture(face).hoverTexture(hover).pressedTexture(face));
        button.layout(style -> style.positionType(TaffyPosition.ABSOLUTE).left(x - width / 2).top(y - height / 2)
                .width(width).height(height)
                .paddingLeft(LABEL_PADDING + (cap == DirectionLabelPose.Cap.LEFT ? LABEL_CAP : 0))
                .paddingRight(LABEL_PADDING + (related ? LABEL_LOCK : 0) + (cap == DirectionLabelPose.Cap.RIGHT ? LABEL_CAP : 0))
                .paddingTop(LABEL_PADDING + (cap == DirectionLabelPose.Cap.UP ? LABEL_CAP : 0))
                .paddingBottom(LABEL_PADDING + (cap == DirectionLabelPose.Cap.DOWN ? LABEL_CAP : 0))
                .gapAll(stacked ? LABEL_STACK_GAP : 3).flexDirection(stacked ? FlexDirection.COLUMN : FlexDirection.ROW)
                .alignItems(AlignItems.CENTER));
        // Turned about its centre, which stays on the spot the pose chose.
        if (rotation != 0) button.transform(transform -> transform.rotation(rotation));
        var font = net.minecraft.client.Minecraft.getInstance().font;
        float widest = widestChip(chips, font);
        for (var chip : chips) {
            var label = text(chip.text(), chip.color());
            label.addClass("pill-chip");
            if (chip.reexport()) label.addClass("reexport");
            float chipWidth = stacked ? widest : font.width(chip.text()) + 5;
            label.layout(style -> style.width(chipWidth).height(11).paddingLeft(2).paddingRight(3).paddingTop(1).flexShrink(0));
            if (stacked) label.textStyle(style -> style.textAlignHorizontal(com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal.CENTER));
            label.style(style -> style.backgroundTexture(new com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture(1, chip.color())));
            button.addChild(label);
        }
        button.setOnClick(event -> {
            selectedPair = pair;
            selectedNetwork = "";
            selectedEndpoint = "";
            focusApplied = true;
            refresh();
        });
        return button;
    }

    /**
     * A label's face: the well inside its border, solid, or dashed with a padlock for a related domain; then its cap,
     * stepped in whole pixels, on the end the pose chose.
     */
    private static com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture labelFace(DirectionLabelPose.Cap cap, int border, boolean related) {
        return FederationTheme.painted((pen, x, y, width, height) -> {
            boolean across = cap == DirectionLabelPose.Cap.LEFT || cap == DirectionLabelPose.Cap.RIGHT;
            boolean upright = cap == DirectionLabelPose.Cap.UP || cap == DirectionLabelPose.Cap.DOWN;
            float left = cap == DirectionLabelPose.Cap.LEFT ? x + LABEL_CAP : x;
            float top = cap == DirectionLabelPose.Cap.UP ? y + LABEL_CAP : y;
            float bodyWidth = width - (across ? LABEL_CAP : 0);
            float bodyHeight = height - (upright ? LABEL_CAP : 0);
            pen.rect(left, top, bodyWidth, bodyHeight, FederationTheme.WELL);
            if (related) {
                FederationTheme.dashes(pen, left, top, bodyWidth, bodyHeight, border);
                FederationTheme.padlock(pen, left + bodyWidth - 8, top + 2, FederationTheme.DARK_MUTED);
            } else {
                pen.rect(left, top, bodyWidth, 1, border);
                pen.rect(left, top + bodyHeight - 1, bodyWidth, 1, border);
                pen.rect(left, top, 1, bodyHeight, border);
                pen.rect(left + bodyWidth - 1, top, 1, bodyHeight, border);
            }
            if (cap == null) return;
            for (int step = 0; step < LABEL_CAP; step++) {
                if (across) {
                    float inset = Math.round(step * bodyHeight / (2f * LABEL_CAP));
                    float column = cap == DirectionLabelPose.Cap.RIGHT ? left + bodyWidth + step : left - 1 - step;
                    pen.rect(column, top + inset, 1, bodyHeight - 2 * inset, FederationTheme.DARK_TEXT);
                } else {
                    float half = LABEL_CAP - step;
                    float row = cap == DirectionLabelPose.Cap.DOWN ? top + bodyHeight + step : top - 1 - step;
                    pen.rect(left + bodyWidth / 2 - half, row, 2 * half, 1, FederationTheme.DARK_TEXT);
                }
            }
        });
    }

    /**
     * Half the size of a box on the link's middle that holds the pair's labels, so the layout keeps them off the cards:
     * the energy chip on the link, or the direction labels beside it, on either side as the curve may turn.
     */
    private Vector2f labelExtent(Network a, Network b, TopologyLink link, net.minecraft.client.gui.Font font) {
        boolean related = a.foreign() || b.foreign();
        if (!directed(a, b)) {
            var energy = energyChip(a, b);
            return energy == null ? new Vector2f() : new Vector2f(labelBodyWidth(List.of(energy), related, font) / 2, LABEL_HEIGHT / 2f);
        }
        var tangent = link.curve().tangent(link.labelAt());
        float halfWidth = 0;
        float halfHeight = 0;
        for (var direction : List.of(new Network[] {a, b}, new Network[] {b, a})) {
            var chips = directionChips(direction[0], direction[1]);
            if (chips.isEmpty()) continue;
            var pose = labelPose(tangent, direction[1] == a, chips, related, font);
            double radians = Math.toRadians(pose.rotation());
            float cos = (float) Math.abs(Math.cos(radians));
            float sin = (float) Math.abs(Math.sin(radians));
            halfWidth = Math.max(halfWidth, Math.abs(pose.offsetX()) + pose.width() / 2 * cos + pose.height() / 2 * sin);
            halfHeight = Math.max(halfHeight, Math.abs(pose.offsetY()) + pose.width() / 2 * sin + pose.height() / 2 * cos);
        }
        return new Vector2f(halfWidth, halfHeight);
    }

    /** The pose of a direction's label; {@code alongCurve} when its capabilities travel the way the curve is drawn. */
    private static DirectionLabelPose labelPose(float[] tangent, boolean alongCurve, List<Chip> chips, boolean related,
            net.minecraft.client.gui.Font font) {
        float sign = alongCurve ? 1 : -1;
        float columnWidth = 2 * LABEL_PADDING + (related ? LABEL_LOCK : 0) + widestChip(chips, font);
        float columnHeight = 2 * LABEL_PADDING + chips.size() * 11 + (chips.size() - 1) * LABEL_STACK_GAP;
        return DirectionLabelPose.of(sign * tangent[0], sign * tangent[1], labelBodyWidth(chips, related, font), LABEL_HEIGHT,
                columnWidth, columnHeight, LABEL_CAP, LABEL_CLEARANCE);
    }

    private static float widestChip(List<Chip> chips, net.minecraft.client.gui.Font font) {
        float widest = 0;
        for (var chip : chips) widest = Math.max(widest, font.width(chip.text()) + 5);
        return widest;
    }

    private static float labelBodyWidth(List<Chip> chips, boolean related, net.minecraft.client.gui.Font font) {
        float width = 2 * LABEL_PADDING + (related ? LABEL_LOCK : 0) + 3 * (chips.size() - 1);
        for (var chip : chips) width += font.width(chip.text()) + 5;
        return width;
    }

    /** Whether either direction has a rule switched on besides energy, which has no direction of its own. */
    private boolean directed(Network a, Network b) {
        return !chips(a, b).isEmpty() || !chips(b, a).isEmpty();
    }

    /** One direction's chips: its capabilities, then the pair's energy, which goes both ways. */
    private List<Chip> directionChips(Network consumer, Network provider) {
        var chips = chips(consumer, provider);
        var energy = energyChip(consumer, provider);
        if (energy != null) chips.add(energy);
        return chips;
    }

    /** The pair's energy chip: quartz while it shares, else its rule's state colour; null without a rule switched on. */
    private Chip energyChip(Network a, Network b) {
        var rule = energyRule(a, b);
        if (rule == null || !rule.get("enabled").getAsBoolean()) return null;
        var state = ruleState(rule);
        var text = tr("energy").copy();
        if (state.code().equals("error")) text.append("!");
        return new Chip(text, sharesEnergy(a.id(), b.id()) ? FederationTheme.QUARTZ : state.color(), false);
    }

    /** The ME power rule that stands for the pair's energy switch, or null when neither way has one. */
    private JsonObject energyRule(Network a, Network b) {
        var direction = energyDirection(a, b);
        return rule(key(direction[0].id(), direction[1].id(), PolicyCapability.ME_POWER.name()));
    }

    /** Consumer and provider of the rule the pair's energy switch reads and writes. */
    private Network[] energyDirection(Network a, Network b) {
        var forward = rule(key(a.id(), b.id(), PolicyCapability.ME_POWER.name()));
        var reverse = rule(key(b.id(), a.id(), PolicyCapability.ME_POWER.name()));
        return space.controlnet.ae2federation.client.policy.SharedEnergySwitch.reversed(enabledFlag(forward), enabledFlag(reverse))
                ? new Network[] {b, a} : new Network[] {a, b};
    }

    private static Boolean enabledFlag(JsonObject rule) {
        return rule == null ? null : rule.get("enabled").getAsBoolean();
    }

    /** Whether the two networks share one energy pool now: an ME power rule either way is active. */
    private boolean sharesEnergy(String a, String b) {
        return space.controlnet.ae2federation.client.policy.SharedEnergySwitch.shares(
                health(rule(key(a, b, PolicyCapability.ME_POWER.name()))),
                health(rule(key(b, a, PolicyCapability.ME_POWER.name()))));
    }

    private static space.controlnet.ae2federation.client.policy.RuleHealth health(JsonObject rule) {
        if (rule == null) return null;
        return switch (ruleState(rule).code()) {
            case "active" -> space.controlnet.ae2federation.client.policy.RuleHealth.ACTIVE;
            case "off" -> space.controlnet.ae2federation.client.policy.RuleHealth.OFF;
            case "error" -> space.controlnet.ae2federation.client.policy.RuleHealth.ERROR;
            default -> space.controlnet.ae2federation.client.policy.RuleHealth.WAITING;
        };
    }

    /**
     * The capabilities {@code consumer} uses from {@code provider}, in the colour of their configured and observed state.
     * A rule switched off grants nothing, so it shows no chip; the pair editor still lists it, switched off.
     */
    private List<Chip> chips(Network consumer, Network provider) {
        var chips = new ArrayList<Chip>();
        for (var capability : CAPABILITIES) {
            // Energy is one switch per pair, added to both directions by directionChips.
            if (capability == PolicyCapability.ME_POWER) continue;
            var rule = rule(key(consumer.id(), provider.id(), capability.name()));
            if (rule == null || !rule.get("enabled").getAsBoolean()) continue;
            var state = ruleState(rule);
            var text = capabilityName(capability).copy();
            if (state.code().equals("error")) text.append("!");
            // A rule in effect that passes its access on says so by colour alone.
            boolean reexport = mode(rule) == RuleMode.REEXPORT;
            chips.add(new Chip(text, reexport && state.code().equals("active") ? FederationTheme.REEXPORT : state.color(), reexport));
        }
        return chips;
    }

    /** A capability chip; {@code reexport} when its rule passes access on, an undrawn class the UI tests read. */
    private record Chip(MutableComponent text, int color, boolean reexport) {
    }

    private void selectNetwork(String id) {
        selectedNetwork = id;
        selectedPair = "";
        selectedEndpoint = "";
        focusApplied = true;
        refresh();
    }

    private void selectEndpoint(String id) {
        selectedEndpoint = id;
        selectedNetwork = "";
        selectedPair = "";
        focusApplied = true;
        refresh();
    }

    /** Whether the server currently lets this viewer follow an Endpoint to its owner's mappings. */
    void setEndpointNavigation(boolean allowed) {
        if (endpointNavigation == allowed) return;
        endpointNavigation = allowed;
        renderAside();
    }

    /**
     * An Endpoint's panel, laid out as a network's: where it is on the map or in 3D among the blocks of the network it
     * sits on, its modes and owner, and the patterns in effect on it.
     */
    private void renderEndpoint(EndpointNode endpoint) {
        var json = endpointFacts.get(endpoint.id());
        links.clearAllChildren();
        stats.clearAllChildren();
        endpointDetail.clearAllChildren();
        renaming = "";
        renameRow.setDisplay(false);
        renameButton.setDisplay(false);
        stats.setDisplay(false);
        var owner = network(endpoint.owner());
        var host = network(string(json, "nativeNetwork"));
        accent.style(style -> style.backgroundTexture(FederationTheme.solid(endpointColor(endpoint))));
        title.setText(endpointLabel(endpoint));
        var uuid = string(json, "endpointIdentity");
        // The title already names its position.
        identity.setText(json.has("dimension") ? dimension(json.get("dimension").getAsString()) : Component.empty());
        identity.style(style -> style.tooltips(Component.literal(uuid)));
        // What it is used for, what it moved lately, then why the last claim request went as it did. Without a
        // domain there is no Provider to name, only that the panel is read-only.
        boolean local = !json.has("mappingNavigation");
        // Its owner is a fact row below, so the box only says what stands out.
        var notes = new ArrayList<Component>();
        if (local) notes.add(tr("endpoint_local_help"));
        else if (endpoint.alone()) notes.add(tr("endpoint_node.alone"));
        else if (!endpoint.ready()) notes.add(tr("endpoint_node.not_ready"));
        else if (endpoint.mode().equals("LOCAL")) notes.add(tr("endpoint_node.local"));
        var flow = endpointFlows.get(endpoint.id());
        if (flow != null && flow.get("events").getAsLong() > 0) notes.add(tr("flow", flow.get("events").getAsLong()));
        if (flow != null && flow.get("returnedEvents").getAsLong() > 0) {
            notes.add(tr("endpoint_node.returned", flow.get("returnedEvents").getAsLong()));
        }
        var claim = string(json, "claimResult");
        if (!claim.isEmpty() && !claim.equals("NONE") && !claim.equals("ACQUIRED") && !claim.equals("RETAINED")) {
            notes.add(claimResult(claim).copy().withStyle(Style.EMPTY.withColor(FederationTheme.WARN & 0xffffff)));
        }
        var explanation = Component.empty();
        for (int index = 0; index < notes.size(); index++) explanation.append(index == 0 ? "" : "\n").append(notes.get(index));
        detail.setText(explanation);
        explain.setDisplay(!notes.isEmpty());
        renderEndpointLocation(endpoint, json, host);
        // The Endpoint's operation, one fact per row.
        // One mode row while the configured and running modes agree, as they do once it settles.
        var configured = string(json, "configuredMode");
        if (configured.equals(endpoint.mode())) {
            endpointDetail.addChild(endpointFact("mode", FederationWorkspace.tr("endpoint_mode." + configured)));
        } else {
            endpointDetail.addChildren(endpointFact("configured", FederationWorkspace.tr("endpoint_mode." + configured)),
                    endpointFact("runtime", FederationWorkspace.tr("endpoint_mode." + endpoint.mode())));
        }
        endpointDetail.addChildren(
                endpointFact("face", FederationWorkspace.tr("face." + string(json, "face"))),
                endpointFact("owner", json.has("ownerPosition") ? tr("endpoint_owner_at", json.get("ownerPosition").getAsString())
                        : json.has("owner") ? tr("endpoint_owner_unloaded") : FederationWorkspace.tr("unclaimed")),
                endpointFact("native", host != null ? name(host) : json.has("nativeNetwork")
                        ? tr("network_name", json.get("nativeNetwork").getAsString().substring(0, 4).toUpperCase(Locale.ROOT))
                        : FederationWorkspace.tr("network_unconfirmed")));
        if (json.has("energyTarget")) endpointDetail.addChild(endpointEnergyFact(json));
        boolean navigable = json.has("mappingNavigation") && json.get("mappingNavigation").getAsBoolean();
        devices.setDisplay(!local);
        devices.setText(FederationWorkspace.tr("endpoint_mapping"));
        devices.setActive(endpointNavigation && navigable);
        devices.style(style -> style.tooltips(navigable ? new Component[0]
                : new Component[] {FederationWorkspace.tr("endpoint_navigation_unavailable")}));
        // The patterns in effect on this Endpoint: mapped to it and present in their slot, wherever their Provider is.
        var patterns = json.has("patterns") ? json.getAsJsonArray("patterns") : new JsonArray();
        linksHeading.setText(tr("endpoint_patterns", patterns.size()));
        if (patterns.isEmpty()) links.addChild(sectionNote(tr(local ? "endpoint_patterns_local" : "endpoint_patterns_none")));
        var choices = new PatternChoices();
        for (var value : patterns) {
            var pattern = value.getAsJsonObject();
            links.addChild(endpointPattern(pattern, choices));
        }
    }

    /** One pattern as the wires view lists it: its first output's icon and name, then the Provider holding it. */
    private static UIElement endpointPattern(JsonObject pattern, PatternChoices choices) {
        var row = new UIElement();
        row.addClass("endpoint-pattern");
        var stack = choices.outputStack(pattern);
        var icon = new UIElement();
        icon.addClass("endpoint-pattern-icon");
        if (!stack.isEmpty()) icon.style(style -> style.backgroundTexture(new com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture(stack)));
        var lines = new UIElement();
        lines.addClass("endpoint-pattern-lines");
        var name = text(Component.literal("#" + pattern.get("slot").getAsInt() + " ").append(choices.name(pattern)),
                FederationTheme.DARK_TEXT);
        name.addClass("endpoint-pattern-name");
        var provider = text(pattern.has("provider") ? tr("endpoint_pattern_provider", pattern.get("provider").getAsString())
                : Component.empty(), FederationTheme.DARK_MUTED);
        provider.addClass("endpoint-pattern-provider");
        lines.addChildren(name, provider);
        row.addChildren(icon, lines);
        return row;
    }

    /** The map tile around the Endpoint, its own network's blocks tinted, and the in-world outline of its block. */
    private void renderEndpointLocation(EndpointNode endpoint, JsonObject json, Network host) {
        if (!json.has("x") || !json.has("dimension")) {
            location.setDisplay(false);
            preview.clear();
            highlightBlocks = List.of();
            highlightParts = List.of();
            highlight.setActive(false);
            return;
        }
        location.setDisplay(true);
        var dimension = json.get("dimension").getAsString();
        var anchor = new space.controlnet.ae2federation.client.policy.BlockMarks.Mark(json.get("x").getAsInt(),
                json.get("y").getAsInt(), json.get("z").getAsInt());
        var facts = host == null ? null : overview.get(host.id());
        var blocks = new ArrayList<Integer>();
        if (facts != null && facts.has("blocks") && dimension.equals(string(facts, "dimension"))) {
            facts.getAsJsonArray("blocks").forEach(value -> blocks.add(value.getAsInt()));
        }
        var mask = space.controlnet.ae2federation.client.policy.BlockMarks.fromFlat(blocks);
        int color = host != null ? host.accent() : FederationTheme.DARK_MUTED;
        preview.show(dimension, mask, color, List.of(anchor), FederationTheme.DARK_TITLE);
        var legend = Component.empty();
        if (!mask.isEmpty()) legend.append(Component.literal("■ ").append(FederationWorkspace.trLocation("legend_network"))
                .withStyle(Style.EMPTY.withColor(color & 0xffffff))).append("  ");
        legend.append(Component.literal("□ ").append(tr("endpoint_legend")).withStyle(Style.EMPTY.withColor(0xffffff)));
        locationLegend.setText(legend);
        locationLegend.style(style -> style.tooltips(FederationWorkspace.trLocation("network_blocks", mask.size())));
        highlightDimension = dimension;
        highlightColor = FederationTheme.SELECT;
        highlightBlocks = List.of(anchor);
        highlightParts = List.of();
        highlight.setText(FederationWorkspace.trLocation("highlight_endpoint"));
        boolean here = preview.inPlayerDimension();
        highlight.setActive(here);
        preview.setHighlightOnMarks(true);
        syncHighlight();
        locationNote.setText(here ? Component.empty() : FederationWorkspace.trLocation("other_dimension", dimension(dimension)));
        locationNote.setDisplay(!here);
    }

    /** The Endpoint's energy switch as one more fact row, worded as the pair's. */
    private UIElement endpointEnergyFact(JsonObject json) {
        var row = endpointFact("energy", energyState(json));
        row.addChild(energySwitch(json, editable, setEndpointEnergy));
        return row;
    }

    /**
     * An Endpoint's energy switch setting: green On while its subnet shares the claiming Provider's energy, yellow On
     * with the reason as its tooltip while it waits, grey Off. The Provider screen shows it too.
     */
    static Label energyState(JsonObject json) {
        boolean on = json.get("energy").getAsBoolean();
        boolean shared = json.has("energyShared") && json.get("energyShared").getAsBoolean();
        int color = !on ? FederationTheme.DARK_MUTED : shared ? FederationTheme.OK : FederationTheme.WARN;
        var state = new Label();
        state.setText(tr("rule_state." + (on ? "on" : "off")).withStyle(Style.EMPTY.withColor(color & 0xffffff)));
        // Undrawn, so tests tell a sharing Endpoint from a waiting one.
        state.addClass(!on ? "health-off" : shared ? "health-active" : "health-waiting");
        if (json.has("energyReason")) {
            var reason = tr("endpoint_energy." + json.get("energyReason").getAsString());
            state.style(style -> style.tooltips(reason));
        }
        return state;
    }

    /** The switch that turns an Endpoint's energy sharing on or off; locked, as a rule's is, when not {@code active}. */
    static Button energySwitch(JsonObject json, boolean active, Consumer<String> send) {
        boolean on = json.get("energy").getAsBoolean();
        var toggle = new Button();
        toggle.noText();
        toggle.addClass("policy-switch");
        toggle.setId("endpoint_energy_switch");
        if (on) toggle.addClass("on");
        var base = !active ? on ? FederationTheme.SWITCH_ON_LOCKED : FederationTheme.SWITCH_OFF_LOCKED
                : on ? FederationTheme.SWITCH_ON : FederationTheme.SWITCH_OFF;
        var hover = !active ? base : on ? FederationTheme.SWITCH_ON_HOVER : FederationTheme.SWITCH_OFF_HOVER;
        toggle.buttonStyle(style -> style.baseTexture(base).hoverTexture(hover).pressedTexture(hover));
        toggle.setActive(active);
        var target = json.get("energyTarget").getAsString();
        toggle.setOnClick(event -> {
            if (active) send.accept(new space.controlnet.ae2federation.client.policy.EndpointEnergyTarget(target, !on).encode());
        });
        return toggle;
    }

    private static UIElement endpointFact(String name, Component value) {
        var text = new Label();
        text.setText(value);
        return endpointFact(name, text);
    }

    private static UIElement endpointFact(String name, Label text) {
        var row = new UIElement();
        row.addClass("endpoint-fact");
        row.setId("endpoint_fact_" + name + "_row");
        var caption = new Label();
        caption.addClass("endpoint-fact-name");
        caption.setText(tr("endpoint_fact." + name));
        text.addClass("endpoint-fact-value");
        text.setId("endpoint_fact_" + name);
        row.addChildren(caption, text);
        return row;
    }

    private static Component claimResult(String code) {
        return Component.translatableWithFallback("ae2federation.ui.workspace.claim_result." + code, code);
    }

    private static String string(JsonObject json, String key) {
        return json != null && json.has(key) ? json.get(key).getAsString() : "";
    }

    private static long number(JsonObject json, String key) {
        return json.has(key) ? json.get(key).getAsLong() : 0;
    }

    private void renderAside() {
        var signature = scope + "|" + selectedNetwork + "|" + selectedPair + "|" + selectedEndpoint + "|" + endpointFacts + "|"
                + endpointNavigation + "|" + editable + "|" + rules + "|" + revisions + "|"
                + memberStatus + "|" + providersByMember + "|" + endpointNodes + "|" + networks + "|" + overview + "|" + renaming + "|" + showRelated + related + relatedRules + "|" + flows;
        if (signature.equals(asideSignature)) return;
        asideSignature = signature;
        var pairEnds = selectedPair.isEmpty() ? null : selectedPair.split("\\|");
        networkDetail.setDisplay(pairEnds == null);
        pairEditor.setDisplay(pairEnds != null);
        var endpoint = endpointNodes.stream().filter(node -> node.id().equals(selectedEndpoint)).findFirst().orElse(null);
        endpointDetail.setDisplay(pairEnds == null && endpoint != null);
        if (pairEnds != null) renderPair(network(pairEnds[0]), network(pairEnds[1]));
        else if (endpoint != null) renderEndpoint(endpoint);
        else renderNetwork(network(selectedNetwork));
    }

    private void renderNetwork(Network network) {
        links.clearAllChildren();
        stats.clearAllChildren();
        if (network == null || !network.id().equals(renaming)) renaming = "";
        renameRow.setDisplay(!renaming.isEmpty());
        renameButton.setDisplay(network != null);
        stats.setDisplay(network != null);
        devices.setDisplay(true);
        if (network == null) {
            var scoped = networks.isEmpty() && !scope.equals("domain") && !scope.equals("available");
            title.setText(scoped ? FederationWorkspace.tr("scope_summary." + scope) : tr("no_network"));
            identity.setText(Component.empty());
            detail.setText(FederationWorkspace.tr(scoped ? "device_scope." + scope
                    : networks.isEmpty() ? "empty_graph" : "select_node"));
            explain.setDisplay(true);
            devices.setActive(false);
            linksHeading.setText(Component.empty());
            return;
        }
        accent.style(style -> style.backgroundTexture(FederationTheme.solid(network.accent())));
        title.setText(name(network));
        boolean canRename = renamable(network) && renaming.isEmpty();
        renameButton.setActive(canRename);
        // A usable button needs no explanation; a greyed one says why.
        renameButton.style(style -> style.opacity(canRename ? 1f : 0.55f).tooltips(canRename ? new Component[0]
                : new Component[] {tr(editable ? "rename_locked" : "rename_read_only")}));
        var facts = overview.get(network.id());
        // "Overworld · 120, 12, -30" under the name; the full identity is in the tooltip.
        identity.setText(facts != null && facts.has("x")
                ? tr("network_identity_at", dimension(facts.get("dimension").getAsString()), facts.get("x").getAsInt() + ", "
                        + facts.get("y").getAsInt() + ", " + facts.get("z").getAsInt())
                : Component.empty());
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
        }
        // The card already says a member is online and confirmed; the panel only explains what needs attention.
        if (!network.foreign() && !status.equals("online")) {
            if (!explanation.getString().isEmpty()) explanation.append("\n");
            explanation.append(tr("network_status." + status));
        }
        var explained = network.foreign() ? tr("related_detail", domainName(network.domain())) : explanation;
        detail.setText(explained);
        explain.setDisplay(!explained.getString().isEmpty());
        renderStats(facts);
        renderLocation(network, facts);
        devices.setText(tr("devices", providers(network).size() + endpoints(network).size()));
        devices.style(style -> style.tooltips(tr("devices_help", providers(network).size(), endpoints(network).size())));
        devices.setActive(!providers(network).isEmpty() || !endpoints(network).isEmpty());
        // Linked networks first, as the design's "Connections (N)"; the others follow so rules can still be created.
        var others = shown().stream().filter(other -> !other.id().equals(network.id()) && discovers(network, other)).toList();
        var linked = others.stream().filter(other -> !linkSummary(network, other).getString().isEmpty()).toList();
        var unlinked = others.stream().filter(other -> !linked.contains(other)).toList();
        linksHeading.setText(tr("connections", linked.size()));
        var ordered = new ArrayList<>(linked);
        ordered.addAll(unlinked);
        for (var other : ordered) {
            var summary = linkSummary(network, other);
            var link = new Button();
            link.addClass("network-link");
            link.setId("network_link_" + sanitize(other.member()));
            // "Mine  ▸ Storage · Crafting ◂ ME power!  ›" on one line, the name in bold as the design has it.
            link.setText(name(other).copy().withStyle(net.minecraft.ChatFormatting.BOLD).append(Component.literal("  ")
                    .withStyle(Style.EMPTY.withBold(false))).append(summary.getString().isEmpty() ? tr("no_rules")
                    .withStyle(Style.EMPTY.withBold(false)) : summary.withStyle(Style.EMPTY.withBold(false))));
            link.style(style -> style.tooltips(name(other).copy().append("\n").append(summary.getString().isEmpty()
                    ? tr("no_rules") : summary)));
            link.layout(style -> style.widthPercent(100).height(18));
            // A long summary keeps to its row, cut at a word; the tooltip has all of it.
            link.textStyle(style -> style.textWrap(TextWrap.HIDE).textAlignHorizontal(
                    com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal.LEFT));
            var open = text(Component.literal("›"), FederationTheme.TEXT_MUTED);
            open.setAllowHitTest(false);
            open.layout(style -> style.positionType(TaffyPosition.ABSOLUTE).right(4).top(3).width(6));
            link.addChild(open);
            link.setOnClick(event -> {
                selectedPair = pair(network.id(), other.id());
                selectedNetwork = "";
                selectedEndpoint = "";
                refresh();
            });
            links.addChild(link);
        }
    }

    /**
     * Both directions of a pair as chips: "▸ Storage · Crafting ◂ ME power", what this network uses first. The rows
     * sit on the light aside, so the state colours are their darker counterparts.
     */
    private MutableComponent linkSummary(Network network, Network other) {
        var summary = Component.empty();
        for (var direction : List.of(new Network[] {network, other}, new Network[] {other, network})) {
            var chips = chips(direction[0], direction[1]);
            if (chips.isEmpty()) continue;
            if (!summary.getString().isEmpty()) summary.append(" ");
            summary.append(Component.literal(direction[0] == network ? "▸ " : "◂ ")
                    .withStyle(Style.EMPTY.withColor(FederationTheme.TEXT_MUTED & 0xffffff)));
            for (int index = 0; index < chips.size(); index++) {
                if (index > 0) summary.append(Component.literal(" · ").withStyle(Style.EMPTY.withColor(FederationTheme.TEXT_MUTED & 0xffffff)));
                summary.append(chips.get(index).text().withStyle(Style.EMPTY.withColor(onPaper(chips.get(index).color()) & 0xffffff)));
            }
        }
        return summary;
    }

    /** A rule state colour made for the dark canvas, as it reads on the light aside. */
    private static int onPaper(int color) {
        if (color == FederationTheme.OK) return 0xff17803a;
        if (color == FederationTheme.WARN) return 0xff9a6700;
        if (color == FederationTheme.ERROR) return 0xffb3261e;
        if (color == FederationTheme.TEAL) return 0xff136f80;
        return color == FederationTheme.DARK_MUTED ? FederationTheme.TEXT_MUTED : FederationTheme.TEXT;
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
        var legend = Component.literal("■ ").append(FederationWorkspace.trLocation("legend_network"))
                .withStyle(Style.EMPTY.withColor(network.accent() & 0xffffff));
        legend.append(Component.literal("  □ ").append(FederationWorkspace.trLocation("legend_controller"))
                .withStyle(Style.EMPTY.withColor(0xffffff)));
        locationLegend.setText(legend);
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
        highlight.setText(FederationWorkspace.trLocation(highlightParts.size() > 1 ? "highlight_parts" : "highlight_network"));
        boolean here = preview.inPlayerDimension();
        highlight.setActive(here);
        preview.setHighlightOnMarks(false);
        syncHighlight();
        locationLegend.style(style -> style.tooltips(FederationWorkspace.trLocation("network_blocks", mask.size())));
        // The legend explains the map; the note only says why nothing can be drawn.
        locationNote.setText(here ? Component.empty() : FederationWorkspace.trLocation("other_dimension", dimension(dimension)));
        locationNote.setDisplay(!here);
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

    /** The outline the highlight button asks for: the identity parts in their colours, or the blocks in one colour. */
    private List<space.controlnet.ae2federation.client.WorldHighlight.Group> highlightGroups() {
        return highlightParts.size() > 1 ? highlightParts
                : List.of(new space.controlnet.ae2federation.client.WorldHighlight.Group(highlightBlocks, highlightColor));
    }

    /** The selection's outline in the world as it blinks right now; zero while it is not outlined. */
    private float highlightBrightness() {
        return highlightBlocks.isEmpty() ? 0
                : space.controlnet.ae2federation.client.WorldHighlight.brightness(highlightDimension, highlightGroups());
    }

    /** The highlight button reads as pressed exactly while its outline runs; the outline ends on its own after a while. */
    private void syncHighlight() {
        boolean on = highlightBrightness() > 0;
        if (on == highlight.hasClass("selected")) return;
        if (on) highlight.addClass("selected");
        else highlight.removeClass("selected");
    }

    /**
     * The design's five figure rows, each "label [bar] value" in the figure's colour: energy against capacity, I/O,
     * storage types, crafting CPUs busy, and channels. A bar stays an empty track where AE2 reports no capacity.
     */
    private void renderStats(JsonObject facts) {
        if (facts == null || !facts.has("energyMax")) {
            var unavailable = new Label();
            unavailable.addClass("stat-line");
            unavailable.setId("network_stat_unavailable");
            unavailable.setText(tr("stats_unavailable"));
            stats.addChild(unavailable);
            return;
        }
        var figures = energyFigures(facts);
        float fill = Math.max(0, figures.fraction());
        int energy = fill < 0.25f ? FederationTheme.WARN : FederationTheme.ENERGY;
        stats.addChild(statRow("energy", tr("stat.energy"), fill, energy, tr("stat.energy_value", compact(figures.stored()),
                compact(figures.max())), tr(figures.shared() ? "stat.energy_pool_help" : "stat.energy_help", figures.percent())));
        stats.addChild(statRow("io", tr("stat.io"), 0, FederationTheme.INFO, tr("stat.io_value",
                decimal(facts.get("energyIn").getAsDouble()), decimal(facts.get("energyOut").getAsDouble())), null));
        stats.addChild(statRow("types", tr("stat.types"), 0, FederationTheme.TEAL,
                tr("card.types", compact(facts.get("types").getAsLong())), null));
        int cpus = facts.get("cpus").getAsInt();
        int busy = facts.get("cpusBusy").getAsInt();
        stats.addChild(statRow("cpus", tr("stat.cpus"), cpus <= 0 ? 0 : busy / (float) cpus, FederationTheme.VALUE,
                tr("stat.cpus_value", busy, cpus), null));
        stats.addChild(statRow("channels", tr("stat.channels"), 0, FederationTheme.DARK_TEXT,
                tr("stat.channels_value", facts.get("channels").getAsInt(), facts.get("nodes").getAsInt()),
                tr("controller." + facts.get("controller").getAsString())));
    }

    /** One figure row; the value label carries {@code network_stat_<name>} and the explanation as its tooltip. */
    private static UIElement statRow(String name, Component label, float fill, int color, Component value, Component help) {
        var row = new UIElement();
        row.addClass("stat-row");
        row.setId("network_stat_" + name + "_row");
        var caption = new Label();
        caption.addClass("stat-name");
        caption.setText(label);
        var bar = new UIElement();
        bar.addClass("stat-bar");
        bar.setId("network_stat_" + name + "_bar");
        bar.style(style -> style.backgroundTexture(FederationTheme.painted((pen, x, y, width, height) -> {
            pen.rect(x, y, width, height, 0xff0d0d11);
            pen.rect(x + 1, y + 1, width - 2, height - 2, FederationTheme.WELL);
            if (fill > 0) pen.rect(x + 1, y + 1, Math.max(1, (width - 2) * Math.min(1, fill)), height - 2, color);
        })));
        var text = new Label();
        text.addClass("stat-value");
        text.setId("network_stat_" + name);
        text.setText(value);
        text.textStyle(style -> style.textColor(color));
        if (help != null) {
            text.style(style -> style.tooltips(help));
            caption.style(style -> style.tooltips(help));
        }
        row.addChildren(caption, bar, text);
        return row;
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
        return editable && !network.foreign() && identityState(network).equals("settled");
    }

    private void showThumbnail(FederationMapPreview thumbnail, Network network) {
        var facts = overview.get(network.id());
        if (facts == null || !facts.has("x")) {
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
        var title = Component.empty().append(name(a).copy().append(" ⇄ ").append(name(b)).withStyle(net.minecraft.ChatFormatting.BOLD));
        var foreign = a.foreign() ? a : b.foreign() ? b : null;
        if (foreign == null && via != null) title.append("\n").append(viaText(via)
                .withStyle(Style.EMPTY.withColor(FederationTheme.DARK_MUTED & 0xffffff)));
        pairTitle.setText(title);
        var positions = foreign == null && via != null ? viaPositions(via) : null;
        pairTitle.style(style -> style.tooltips(positions == null ? new Component[0] : new Component[] {positions}));
        // Another domain's pair is shown for reference: only its configured rules, and where to change them. This
        // domain's own pairs need no note.
        pairNote.setDisplay(foreign != null);
        if (foreign != null) pairNote.setText(tr("pair_read_only", domainName(foreign.domain())));
        int section = 0;
        for (var direction : List.of(new Network[] {a, b}, new Network[] {b, a})) {
            var consumer = direction[0];
            var provider = direction[1];
            var shownCapabilities = java.util.Arrays.stream(CAPABILITIES).filter(capability -> capability != PolicyCapability.ME_POWER)
                    .filter(capability -> foreign == null || rule(key(consumer.id(), provider.id(), capability.name())) != null).toList();
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
            heading.setText(tr("uses", name(consumer), name(provider)).withStyle(net.minecraft.ChatFormatting.BOLD));
            panel.addChild(heading);
            for (var capability : shownCapabilities) {
                panel.addChild(row(section + "_" + capability.name().toLowerCase(Locale.ROOT), consumer, provider, capability,
                        capabilityName(capability), foreign == null));
            }
            pairSections.addChild(panel);
            section++;
        }
        // One switch shares energy both ways, however the pair's rule is written.
        var energy = energyDirection(a, b);
        if (foreign != null && energyRule(a, b) == null) return;
        var panel = new UIElement();
        panel.addClass("dark-panel");
        panel.setId("policy_section_energy");
        var heading = new Label();
        heading.addClass("pair-section-title");
        heading.setId("policy_section_title_energy");
        heading.setText(tr("energy_section", name(a), name(b)).withStyle(net.minecraft.ChatFormatting.BOLD));
        panel.addChildren(heading, row("energy", energy[0], energy[1], PolicyCapability.ME_POWER,
                capabilityName(PolicyCapability.ME_POWER),
                foreign == null));
        pairSections.addChild(panel);
    }

    private UIElement row(String suffix, Network consumer, Network provider, PolicyCapability capability, Component title,
            boolean inDomain) {
        boolean editable = this.editable && inDomain;
        var ruleKey = key(consumer.id(), provider.id(), capability.name());
        var rule = rule(ruleKey);
        var row = new UIElement();
        row.addClass("policy-row");
        row.setId("policy_row_" + suffix);
        var head = new UIElement();
        head.addClass("policy-row-head");
        var name = new Label();
        name.addClass("policy-capability");
        name.setText(title);
        // Name, state and switch share one line; flow and explanations wrap under the state.
        var stateLabel = new Label();
        stateLabel.addClass("policy-state");
        stateLabel.setId("policy_state_" + suffix);
        head.addChildren(name, stateLabel);
        var mode = mode(rule);
        boolean on = mode.enabled();
        // Crafting takes the other network's materials through the same direction's storage rule, which therefore
        // stays on while crafting is: it steps only between its two on states.
        boolean heldByCrafting = capability == PolicyCapability.STORAGE
                && mode(rule(key(consumer.id(), provider.id(), PolicyCapability.CRAFTING.name()))).enabled();
        var toggle = new Button();
        toggle.noText();
        toggle.addClass("policy-switch");
        toggle.setId("policy_switch_" + suffix);
        if (on) toggle.addClass("on");
        if (mode == RuleMode.REEXPORT) toggle.addClass("reexport");
        // A locked switch is drawn at half strength here: the button's own textures take precedence over the
        // stylesheet's disabled state.
        var base = !editable ? switch (mode) {
            case DISABLED -> FederationTheme.SWITCH_OFF_LOCKED;
            case ENABLED -> FederationTheme.SWITCH_ON_LOCKED;
            case REEXPORT -> FederationTheme.SWITCH_REEXPORT_LOCKED;
        } : switch (mode) {
            case DISABLED -> FederationTheme.SWITCH_OFF;
            case ENABLED -> FederationTheme.SWITCH_ON;
            case REEXPORT -> FederationTheme.SWITCH_REEXPORT;
        };
        var hover = switch (mode) {
            case DISABLED -> FederationTheme.SWITCH_OFF_HOVER;
            case ENABLED -> FederationTheme.SWITCH_ON_HOVER;
            case REEXPORT -> FederationTheme.SWITCH_REEXPORT_HOVER;
        };
        var lit = editable ? hover : base;
        toggle.buttonStyle(style -> style.baseTexture(base).hoverTexture(lit).pressedTexture(lit));
        toggle.setActive(editable);
        var observed = rule != null ? rule.get("revision").getAsLong() : revisions.getOrDefault(ruleKey, 0L);
        var policyKey = new PolicyKey(NetworkId.parse(consumer.id()), NetworkId.parse(provider.id()), capability);
        // Left click steps forward and right click back, as AE2's setting buttons do, so a player can switch a rule
        // off without passing through re-export. Shared energy has only off and on.
        boolean threeState = RuleMode.REEXPORT.allowedFor(capability);
        toggle.setOnClick(event -> {
            if (!editable) return;
            var next = !threeState ? on ? RuleMode.DISABLED : RuleMode.ENABLED
                    : heldByCrafting ? held(mode) : mode.next();
            setPolicy.accept(new PolicySwitchTarget(policyKey, next, new PolicyRevision(observed)).encode());
        });
        if (threeState) {
            toggle.addEventListener(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents.MOUSE_DOWN, event -> {
                if (event.button != 1 || !editable || !toggle.isActive()) return;
                com.lowdragmc.lowdraglib2.gui.util.UISoundUtils.playButtonClickSound();
                setPolicy.accept(new PolicySwitchTarget(policyKey,
                        heldByCrafting ? held(mode) : mode.previous(), new PolicyRevision(observed)).encode());
            });
        }
        head.addChild(toggle);
        var state = ruleState(rule);
        // The words name only the switch's setting; the colour says whether it works, the tooltip why not. The
        // undrawn health class lets tests tell a working rule from a waiting one.
        var setting = state.code().equals("off") ? "off" : mode == RuleMode.REEXPORT ? "reexport" : "on";
        stateLabel.addClass("health-" + state.code());
        var text = tr("rule_state." + setting).withStyle(Style.EMPTY.withColor(state.color() & 0xffffff));
        var flow = flows.get(ruleKey);
        if (flow != null && on) text.append("\n").append(flowText(flow));
        stateLabel.setText(text);
        // The reason is the tooltip alone, not repeated under the state; off and working rules need
        // nothing from the player, so they have none.
        var attention = attention(rule, state);
        stateLabel.style(style -> style.tooltips(attention == null ? new Component[0] : new Component[] {attention}));
        row.addChild(head);
        return row;
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

    private static Component flowText(JsonObject flow) {
        return tr("flow", flow.get("events").getAsLong()).withStyle(Style.EMPTY.withColor(FederationTheme.TEAL & 0xffffff));
    }

    /** Whether the rule from {@code consumer} to {@code provider} delivered anything in the flow window. */
    private boolean flowing(String consumer, String provider) {
        for (var capability : CAPABILITIES) {
            if (flows.containsKey(key(consumer, provider, capability.name()))) return true;
        }
        return false;
    }

    /** A storage rule crafting depends on steps between enabled and re-export, either way. */
    private static RuleMode held(RuleMode mode) {
        return mode == RuleMode.ENABLED ? RuleMode.REEXPORT : RuleMode.ENABLED;
    }

    private static RuleMode mode(JsonObject rule) {
        if (rule == null || !rule.get("enabled").getAsBoolean()) return RuleMode.DISABLED;
        return rule.has("reexport") && rule.get("reexport").getAsBoolean() ? RuleMode.REEXPORT : RuleMode.ENABLED;
    }

    private static RuleState ruleState(JsonObject rule) {
        // A rule never written behaves exactly as one switched off, so it reads the same.
        if (rule == null || !rule.get("enabled").getAsBoolean()) return new RuleState("off", FederationTheme.DARK_MUTED, false);
        var runtime = rule.getAsJsonObject("runtime");
        var code = runtime == null ? "unobserved" : runtime.get("code").getAsString();
        var backend = runtime != null && runtime.has("backend") ? runtime.get("backend").getAsString() : "";
        var storage = runtime != null && runtime.has("storage") ? runtime.get("storage").getAsString() : "";
        return switch (space.controlnet.ae2federation.client.policy.RuleHealth.of(true, code, backend, storage)) {
            case ACTIVE -> new RuleState("active", FederationTheme.OK, false);
            case ERROR -> new RuleState("error", FederationTheme.ERROR, true);
            default -> new RuleState("waiting", FederationTheme.WARN, true);
        };
    }

    /**
     * What the player should know about a rule: why an enabled rule is not in effect, or which of the other network's
     * storages a working storage rule cannot share. Null when there is nothing to act on.
     */
    private static Component attention(JsonObject rule, RuleState state) {
        if (rule == null) return null;
        var prefix = "ae2federation.ui.domain.runtime.";
        var runtime = rule.getAsJsonObject("runtime");
        if (!state.explain()) {
            if (runtime == null || !runtime.has("skipped") || !runtime.has("storage")) return null;
            return Component.translatable(prefix + "storage_skipped", runtime.get("skipped").getAsInt(),
                    Component.translatable(prefix + "provenance." + runtime.get("storage").getAsString()));
        }
        if (runtime == null) return Component.translatable(prefix + "unobserved");
        var code = runtime.get("code").getAsString();
        if (code.equals("operation_missing")) return Component.translatable(prefix + code,
                Component.translatable(prefix + "operation." + runtime.get("operation").getAsString()));
        // The most specific reasons the server gave, each on its own line; the code alone when it gave none.
        var reasons = new ArrayList<Component>();
        if (runtime.has("backend")) reasons.add(Component.translatable(prefix + "backend." + runtime.get("backend").getAsString()));
        if (runtime.has("storage")) reasons.add(Component.translatable(prefix + "provenance." + runtime.get("storage").getAsString()));
        if (runtime.has("note")) reasons.add(Component.translatable(prefix + "note." + runtime.get("note").getAsString()));
        if (reasons.isEmpty()) return Component.translatable(prefix + code);
        var text = Component.empty().append(reasons.getFirst());
        reasons.stream().skip(1).forEach(reason -> text.append("\n").append(reason));
        return text;
    }

    /** Also tell {@code listener} the header search, lower-cased, as it changes; the processing page filters by it. */
    void onSearch(java.util.function.Consumer<String> listener) {
        searchListeners.add(listener);
        listener.accept(search);
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
        else if (!endpoints(network).isEmpty()) selectEndpoint(endpoints(network).getFirst());
    }

    /** Pairs with at least one rule switched on: only those get a link and a label on the graph. */
    private List<String> pairsWithRules() {
        var pairs = new java.util.TreeSet<String>();
        for (var rule : shownRules()) {
            if (!rule.get("enabled").getAsBoolean()) continue;
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
        if (shownById == null) {
            shownById = new HashMap<>();
            for (var network : shown()) shownById.putIfAbsent(network.id(), network);
        }
        return shownById.get(id);
    }

    private List<String> providers(Network network) {
        return providersByMember.getOrDefault(network.member(), List.of());
    }

    /** The Endpoints this network's Providers map. */
    private List<String> endpoints(Network network) {
        return endpointNodes.stream().filter(endpoint -> endpoint.owner().equals(network.id())).map(EndpointNode::id).toList();
    }

    /**
     * Whether two shown networks discover each other: members of this domain always do; otherwise they must share a
     * related domain. Only such pairs get a link or a place in each other's connection list.
     */
    private boolean discovers(Network a, Network b) {
        if (a == null || b == null) return false;
        if (!a.foreign() && !b.foreign()) return true;
        var shared = new java.util.HashSet<>(networkDomains.getOrDefault(a.id(), java.util.Set.of()));
        shared.retainAll(networkDomains.getOrDefault(b.id(), java.util.Set.of()));
        return !shared.isEmpty();
    }

    private static java.util.Set<String> domains(JsonObject network) {
        var domains = new java.util.HashSet<String>();
        if (network.has("domains")) network.getAsJsonArray("domains").forEach(value -> domains.add(value.getAsString()));
        return domains;
    }

    private Vector2f center(String id) {
        var place = endpointPlaces.get(id);
        if (place != null) return new Vector2f(place.x() + place.width() / 2, place.y() + ENDPOINT_HEIGHT / 2);
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

    /**
     * "■ active ■ active with re-export ■ not active yet ■ error ■ energy shared", each square in its chip colour, over
     * "▸ points to the network that uses it". The swatches come first: an adaptive-width label is as wide as its
     * first line and does not wrap.
     */
    private static Component legendText() {
        var legend = Component.empty();
        for (var entry : new Object[][] {{"active", FederationTheme.OK}, {"reexport", FederationTheme.REEXPORT},
                {"waiting", FederationTheme.WARN}, {"error", FederationTheme.ERROR}, {"energy", FederationTheme.QUARTZ}}) {
            if (!legend.getSiblings().isEmpty()) legend.append("  ");
            legend.append(Component.literal("■ ").append(tr("legend." + entry[0]))
                    .withStyle(Style.EMPTY.withColor((Integer) entry[1] & 0xffffff)));
        }
        legend.append("\n").append(tr("legend.reads"));
        return legend;
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

    /** {@code owner} is the id of the network whose Provider maps the Endpoint, or empty. */
    private record EndpointNode(String id, String position, String owner, String mode, boolean ready, boolean alone,
            boolean energy) {
    }

    /** Relationship lines behind the cards: configured pairs solid, unconfigured pairs of the selection dashed. */
    private final class Links extends UIElement {
        /** Quartz beads drawn in the current frame. */
        private int beads;
        Links() {
            var extent = extent();
            layout(style -> style.positionType(TaffyPosition.ABSOLUTE).left(0).top(0).width(extent.x + 8).height(extent.y + 8));
            // It spans every card; taking hits would steal the press GraphView pans on.
            setAllowHitTest(false);
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
            if (!pendingCenter.isEmpty() && (positions.containsKey(pendingCenter) || endpointPlaces.containsKey(pendingCenter))) {
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
            beads = 0;
            var configured = pairsWithRules();
            for (var pair : configured) {
                var ends = pair.split("\\|");
                boolean selected = pair.equals(selectedPair);
                var link = link(ends[0], ends[1]);
                if (sharesEnergy(ends[0], ends[1])) energyLine(context, link, selected);
                else line(context, link, selected ? FederationTheme.SELECT : FederationTheme.EDGE, selected ? 3f : 2f, false);
                // Each end is marked in its network's accent where the link meets the card.
                endMark(context, link.start(), network(ends[0]).accent());
                endMark(context, link.end(), network(ends[1]).accent());
            }
            // A selected pair with no rule switched on yet keeps its dashed link, in the selection colour, while its
            // rules are being set; otherwise selecting it from a network's dashed link would make the link vanish.
            if (!selectedPair.isEmpty() && !configured.contains(selectedPair)) {
                var ends = selectedPair.split("\\|");
                if (positions.containsKey(ends[0]) && positions.containsKey(ends[1])) {
                    var link = link(ends[0], ends[1]);
                    line(context, link, FederationTheme.SELECT, 2f, true);
                    endMark(context, link.start(), network(ends[0]).accent());
                    endMark(context, link.end(), network(ends[1]).accent());
                }
            }
            if (!selectedNetwork.isEmpty() && positions.containsKey(selectedNetwork)) {
                for (var other : shown()) {
                    if (other.id().equals(selectedNetwork) || configured.contains(pair(selectedNetwork, other.id()))
                            || !discovers(network(selectedNetwork), other)) continue;
                    line(context, link(selectedNetwork, other.id()), 0x668b83a0, 1.5f, true);
                }
            }
            for (var endpoint : endpointNodes) {
                var place = endpointPlaces.get(endpoint.id());
                if (place == null || place.link() == null) continue;
                var owner = network(endpoint.owner());
                boolean selected = endpoint.owner().equals(selectedNetwork) || endpoint.id().equals(selectedEndpoint);
                // A subnet in its owner's energy pool is joined to it by a Quartz Fiber, as two sharing networks are.
                if (endpoint.energy()) energyLine(context, new TopologyLink(place.link(), 0.5f), selected);
                else line(context, new TopologyLink(place.link(), 0.5f), selected ? FederationTheme.SELECT : FederationTheme.EDGE,
                        selected ? 2f : 1.5f, false);
                if (owner != null) endMark(context, new float[] {place.link().fromX(), place.link().fromY()}, owner.accent());
            }
            pose.popPose();
            FederationFlowPulses.beadsDrawn(beads);
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

        /**
         * A link whose networks share one energy pool, drawn like AE2's Quartz Fiber: a pale rail with quartz beads
         * drifting slowly both ways, since each network draws from and charges the same pool. No glow, so it stays
         * quiet next to the other links.
         */
        private void energyLine(GUIContext context, TopologyLink link, boolean selected) {
            var points = link.curve().points(LINK_SEGMENTS);
            var line = new ArrayList<Vector2f>(LINK_SEGMENTS + 1);
            for (int index = 0; index < points.length; index += 2) line.add(new Vector2f(points[index], points[index + 1]));
            int edge = 0xff121016;
            DrawerHelper.drawTexLines(context.graphics, LDLibRenderTypes.graphWire(), line, edge, edge, selected ? 5f : 4f);
            int rail = selected ? FederationTheme.QUARTZ_BEAD : FederationTheme.QUARTZ;
            DrawerHelper.drawTexLines(context.graphics, LDLibRenderTypes.graphWire(), line, rail, rail, selected ? 3f : 2f);
            int core = selected ? FederationTheme.SELECT : FederationTheme.QUARTZ_CORE;
            DrawerHelper.drawTexLines(context.graphics, LDLibRenderTypes.graphWire(), line, core, core, 0.75f);
            // The beads are the link's live flow; hiding live flow leaves the rail, which only says the pool is shared.
            if (liveFlowHidden) return;
            float phase = (net.minecraft.Util.getMillis() % QUARTZ_BEAD_MILLIS) / (float) QUARTZ_BEAD_MILLIS;
            // Two beads each way, staggered so no two meet at the same point.
            bead(context, points, phase);
            bead(context, points, (phase + 0.5f) % 1);
            bead(context, points, 1 - (phase + 0.25f) % 1);
            bead(context, points, 1 - (phase + 0.75f) % 1);
        }

        /**
         * A quartz bead at {@code along} (0 to 1) of the link: a square turned 45 degrees, its edge and core the same
         * sizes as a storage flow dot. It is placed at the exact point and drawn as geometry, so it moves smoothly and
         * keeps straight edges at any GUI or graph scale.
         */
        private void bead(GUIContext context, float[] points, float along) {
            beads++;
            int segments = points.length / 2 - 1;
            float position = along * segments;
            int index = Math.min(segments - 1, (int) position);
            float fraction = position - index;
            float x = points[2 * index] + (points[2 * index + 2] - points[2 * index]) * fraction;
            float y = points[2 * index + 1] + (points[2 * index + 3] - points[2 * index + 1]) * fraction;
            var pose = context.graphics.pose();
            pose.pushPose();
            pose.translate(x, y, 0);
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(45));
            context.graphics.fill(-3, -3, 3, 3, FederationTheme.QUARTZ_BEAD_EDGE);
            context.graphics.fill(-2, -2, 2, 2, FederationTheme.QUARTZ_BEAD);
            pose.popPose();
        }

        private void endMark(GUIContext context, float[] point, int color) {
            int x = Math.round(point[0]);
            int y = Math.round(point[1]);
            context.graphics.fill(x - 3, y - 3, x + 3, y + 3, 0xff121016);
            context.graphics.fill(x - 2, y - 2, x + 2, y + 2, color);
        }
    }
}
