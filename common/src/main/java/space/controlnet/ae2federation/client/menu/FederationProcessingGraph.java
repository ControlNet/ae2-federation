package space.controlnet.ae2federation.client.menu;

import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.client.shader.LDLibRenderTypes;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.AlignContent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.joml.Vector2f;
import space.controlnet.ae2federation.client.policy.BlockMarks;
import space.controlnet.ae2federation.client.policy.MappingWireTarget;

/**
 * Processing wires for the domain's Pattern Providers: each Provider as a card of pattern rows with an output port on
 * the right edge, the Endpoints the selected Provider may use as cards with an input port on the left edge, and one
 * wire per mapped pattern and Endpoint. Dragging a pattern's row onto an Endpoint maps it; so does clicking a
 * pattern, then an Endpoint, then "Map". Clicking a wire offers to unlink it and clicking an Endpoint selects it for
 * details and release. The header search filters Providers, patterns and Endpoints. Every change is an explicit
 * {@link MappingWireTarget} request that the server checks against live ownership.
 */
public final class FederationProcessingGraph {
    private static final float ROW_HEIGHT = 18;
    private static final float HEADER_HEIGHT = 30;
    private static final float ENDPOINT_HEIGHT = 38;
    /** Card thumbnails are 16:10, as the network's footprint is drawn in the overview. */
    private static final float THUMBNAIL_WIDTH = 32;
    private static final float THUMBNAIL_HEIGHT = 20;
    /** Radius of the round ports on the cards' edges. */
    private static final int PORT = 5;
    /** How far the pressed pointer moves on a pattern row before it drags the pattern's wire. */
    private static final float DRAG_THRESHOLD = 3;
    /** Width of a pattern row's port, whose ring is centred on the card's right edge. */
    private static final float PORT_WIDTH = 12;
    private static final int CARD_FACE = 0xff2c2735;
    private static final int CARD_RING = 0xffd8d3e4;
    private static final int CARD_SHADOW = 0xff121016;
    private static final int PORT_FILL = 0xff17141e;
    /** Refusals are written on the light aside, where the dark theme's red is too pale. */
    private static final int REFUSAL = 0xff922e42;
    /** Card thumbnails refresh their network's blocks this often, as the overview arrives about once a second. */
    private static final int THUMBNAIL_REFRESH_TICKS = 40;
    /** Share of the canvas each column takes; the gap between them is left for the wires. */
    private static final float PROVIDER_COLUMN = 39;
    private static final float ENDPOINT_COLUMN = 39;
    /** Endpoint cards beyond this many carry no map tile; each tile samples the world. */
    private static final int MAX_ENDPOINT_THUMBNAILS = 12;
    /** A dropped wire the server has not confirmed within this long is no longer drawn as pending. */
    private static final long PENDING_MILLIS = 5000;
    private static final int CURVE_SEGMENTS = 24;
    private static final long FLOW_PERIOD_MILLIS = 1600;
    /** Dots drawn on busy wires in the last frame, for tests and diagnostics. */
    private static int drawnWireDots;

    private final Consumer<String> setMapping;
    private final Consumer<String> select;
    private final Runnable release;
    private final Function<JsonObject, Component> patternName;
    private final Function<JsonObject, net.minecraft.world.item.ItemStack> patternIcon;
    /** A pattern's outputs and inputs with exact amounts, one line each. */
    private final Function<JsonObject, List<Component>> patternFacts;
    private final UIElement root;
    private final ScrollerView scroll;
    private final Label title;
    private final Label detail;
    private final UIElement facts;
    private final Button unlink;
    private final Button releaseButton;
    /** Maps or unmaps the selected pattern on the selected Endpoint: the click alternative to dragging. */
    private final Button mappingToggle;
    private final Canvas canvas = new Canvas();
    /** Both ends of the selected wire, or the selected Endpoint alone, as the cards draw them but larger. */
    private final FederationMapPreview fromPreview = new FederationMapPreview(true);
    private final FederationMapPreview toPreview = new FederationMapPreview(true);
    private final UIElement fromEnd;
    private final UIElement toEnd;
    private final Label fromLabel;
    private final Label toLabel;
    private final Button highlight;
    /** The blocks of the network at a choice's {@code networkIndex} in a dimension, from the overview. */
    private java.util.function.BiFunction<Integer, String, List<BlockMarks.Mark>> networkBlocks = (index, dimension) -> List.of();
    private final List<Runnable> thumbnailRefresh = new ArrayList<>();
    private int refreshTicks;
    private String providerPosition = "";

    private final List<JsonObject> slots = new ArrayList<>();
    private final List<JsonObject> endpoints = new ArrayList<>();
    /** {@link #endpoints} by id; the wires look their Endpoint up every frame. */
    private final java.util.Map<String, JsonObject> endpointsById = new java.util.HashMap<>();
    /** Display names of the domain's networks, in the order of the choices' {@code networkIndex}. */
    private final List<String> networkNames = new ArrayList<>();
    private final Map<String, UIElement> ports = new LinkedHashMap<>();
    private final Map<String, UIElement> rows = new LinkedHashMap<>();
    private final Map<String, Button> endpointCards = new LinkedHashMap<>();
    private final Map<String, int[]> endpointRings = new LinkedHashMap<>();
    private final Map<String, int[]> endpointInsets = new LinkedHashMap<>();
    private final List<Wire> wires = new ArrayList<>();
    /** Every Provider of the domain, stacked above and below the selected one; only the selected one is editable. */
    private final List<JsonObject> providers = new ArrayList<>();
    /** Ports and wires of the other Providers, keyed by provider id and slot; drawn muted and never edited here. */
    private final Map<String, UIElement> otherPorts = new LinkedHashMap<>();
    private final List<Wire> otherWires = new ArrayList<>();
    /** Accent colour of each other Provider's network, keyed like {@link #otherPorts}. */
    private final Map<String, Integer> otherAccents = new LinkedHashMap<>();
    /** Accent colour of the selected Provider's network; the neutral edge colour when it is unknown. */
    private int wireAccent = FederationTheme.EDGE;
    /** Wires sent to the server and not yet confirmed, with when they were sent; drawn dashed until then. */
    private final Map<Wire, Long> pendingWires = new LinkedHashMap<>();
    private String structure = "";
    private String confirmedTarget = "";
    private boolean editable;
    /** Where an Endpoint's energy switch sends; only the Provider screen shows the switch here. */
    private @org.jetbrains.annotations.Nullable Consumer<String> setEndpointEnergy;
    /**
     * The energy switch shown last and what it showed. The facts are rebuilt with every choices update, which lane
     * flow sends while the Endpoint works; an unchanged switch is kept, so such an update cannot drop a press on it.
     */
    private @org.jetbrains.annotations.Nullable Button energySwitch;
    private String energySwitchShape = "";
    private Selection selection = Selection.NONE;
    private Component rejection = Component.empty();
    private String hoverEndpoint = "";
    /** The pattern slot whose drag the Endpoint cards currently show drop hints for; empty when nothing is dragged. */
    private String hintSlot = "";
    private final Map<String, Label> endpointStates = new LinkedHashMap<>();
    /** Provider cards and other Providers' pattern rows by id, so the search can hide them. */
    private final Map<String, UIElement> providerCards = new LinkedHashMap<>();
    private final Map<String, UIElement> otherRows = new LinkedHashMap<>();
    private final Map<String, JsonObject> otherSlots = new LinkedHashMap<>();
    private Label searchEmpty;
    private String query = "";
    /** Set by a press on a pattern row, so the canvas does not also pick the wire that starts at its port. */
    private boolean patternPressed;
    /** The pattern row a drag may start from: pressed outside its item slot and not yet moved far. */
    private String pressedRow = "";
    private float pressX;
    private float pressY;
    private Selection pendingFocus;
    /**
     * The Provider screen's real pattern slots, by slot index; null in the domain workspace. With them every slot has a
     * row, empty or not, and the row holds the slot itself where the workspace draws the pattern's output.
     */
    private java.util.function.IntFunction<UIElement> slotElements;

    FederationProcessingGraph(UI ui, Consumer<String> setMapping, Consumer<String> select, Runnable release,
            Function<JsonObject, Component> patternName, Function<JsonObject, net.minecraft.world.item.ItemStack> patternIcon,
            Function<JsonObject, List<Component>> patternFacts) {
        this.patternFacts = patternFacts;
        this.setMapping = setMapping;
        this.select = select;
        this.release = release;
        this.patternName = patternName;
        this.patternIcon = patternIcon;
        root = element(ui, "processing_graph", UIElement.class);
        scroll = element(ui, "processing_scroll", ScrollerView.class);
        title = element(ui, "processing_detail_title", Label.class);
        detail = element(ui, "processing_detail_text", Label.class);
        facts = element(ui, "processing_facts", UIElement.class);
        unlink = element(ui, "processing_unlink", Button.class);
        releaseButton = element(ui, "processing_release", Button.class);
        // Its label is set per selection; an empty text attribute makes LDLib hide the label, so turn it back on.
        mappingToggle = element(ui, "mapping_toggle", Button.class).enableText();
        mappingToggle.setOnClick(event -> toggleSelected());
        scroll.addScrollViewChild(canvas);
        element(ui, "processing_preview_from", UIElement.class).addChild(fromPreview);
        element(ui, "processing_preview_to", UIElement.class).addChild(toPreview);
        fromEnd = element(ui, "processing_end_from", UIElement.class);
        toEnd = element(ui, "processing_end_to", UIElement.class);
        fromLabel = element(ui, "processing_from_label", Label.class);
        toLabel = element(ui, "processing_to_label", Label.class);
        highlight = element(ui, "processing_highlight", Button.class);
        // Pressed while the selection is outlined in the world; pressing it again ends the outline early.
        highlight.setOnClick(event -> {
            var groups = focusGroups();
            if (groups.getFirst().blocks().isEmpty()) return;
            if (space.controlnet.ae2federation.client.WorldHighlight.brightness(playerDimension(), groups) > 0) {
                space.controlnet.ae2federation.client.WorldHighlight.hide(playerDimension(), groups);
            } else {
                space.controlnet.ae2federation.client.WorldHighlight.show(playerDimension(), groups);
            }
            syncHighlight();
        });
        highlight.addEventListener(UIEvents.TICK, event -> syncHighlight());
        unlink.setOnClick(event -> {
            if (selection.kind() != Kind.WIRE || !editable) return;
            setMapping.accept(new MappingWireTarget(selection.slot(), selection.endpoint(), false).encode());
            selection = Selection.NONE;
            render();
        });
        releaseButton.setOnClick(event -> {
            if (selection.kind() == Kind.ENDPOINT && releasable(selection.endpoint())) release.run();
        });
        canvas.addEventListener(UIEvents.MOUSE_DOWN, this::pickWire);
        // Any press or release elsewhere on the screen forgets the pressed row, so only its own press drags it.
        ui.rootElement.addEventListener(UIEvents.MOUSE_DOWN, event -> pressedRow = "", true);
        ui.rootElement.addEventListener(UIEvents.MOUSE_UP, event -> pressedRow = "", true);
        // A drag starts once the pressed pointer has moved a few pixels, wherever it now is, so a click still only
        // selects and a quick move off the row still drags.
        ui.rootElement.addEventListener(UIEvents.MOUSE_MOVE, event -> {
            var port = ports.get(pressedRow);
            if (!editable || port == null || !port.isMouseDown(0) || dragged() != null) return;
            if (Math.abs(event.x - pressX) + Math.abs(event.y - pressY) < DRAG_THRESHOLD) return;
            var slot = pressedRow;
            pressedRow = "";
            port.startDrag(new PortDrag(slot), null);
        }, true);
    }

    UIElement root() {
        return root;
    }

    /** Puts real item slots into the pattern rows, one per slot index; every slot then gets a row. */
    void setSlotElements(java.util.function.IntFunction<UIElement> elements) {
        slotElements = elements;
    }

    /**
     * An item slot's click belongs to the container screen: a listener on a row or the canvas it sits in must leave it
     * unhandled, or LDLib2 reports it handled and the slot never takes the click.
     */
    private static void passSlotClick(UIEvent event) {
        if (event.target instanceof com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot) event.hasHandler = false;
    }

    /** Shows only the Providers, patterns and Endpoints whose text or position contains {@code value}. */
    void filter(String value) {
        var next = value == null ? "" : value.strip().toLowerCase(java.util.Locale.ROOT);
        if (next.equals(query)) return;
        query = next;
        applyFilter();
    }

    /** Selects this Endpoint, with this pattern chosen, once both are in the next snapshot. */
    void focus(String endpointId, String slot) {
        if (endpointId != null) pendingFocus = new Selection(Kind.ENDPOINT, slot == null ? "" : slot, endpointId);
    }

    /** The selected pattern on the selected Endpoint: unmapped when it is wired there, mapped otherwise. */
    private void toggleSelected() {
        if (!editable || selection.kind() != Kind.ENDPOINT || selection.slot().isEmpty()) return;
        var endpoint = endpoint(selection.endpoint());
        if (endpoint == null || slot(selection.slot()) == null) return;
        var wire = new Wire(selection.slot(), selection.endpoint());
        boolean mapped = wires.contains(wire);
        if (!mapped && (!mappable(endpoint) || emptySlot(slot(selection.slot())))) return;
        rejection = Component.empty();
        setMapping.accept(new MappingWireTarget(selection.slot(), selection.endpoint(), !mapped).encode());
        if (!mapped) pendingWires.put(wire, System.currentTimeMillis());
        render();
    }

    /** Whether the server would take a new wire to this Endpoint; the drop refuses the same ones. */
    private static boolean mappable(JsonObject endpoint) {
        var claim = claim(endpoint);
        return claim == Claim.FREE || claim == Claim.IN_USE || claim == Claim.RETAINED;
    }

    /** Where the cards' thumbnails read each network's blocks; the overview keeps them current. */
    void setNetworkBlocks(java.util.function.BiFunction<Integer, String, List<BlockMarks.Mark>> source) {
        networkBlocks = source;
    }

    void onEndpointEnergy(Consumer<String> sender) {
        setEndpointEnergy = sender;
    }

    void setEditable(boolean value) {
        if (editable == value) return;
        editable = value;
        render();
    }

    /** "2 Providers · 3 Endpoints · 4 mappings", for the header while this page is shown. */
    Component summary() {
        int mappings = wires.size() + otherWires.size();
        return tr("summary", providers.size(), endpoints.size(), mappings);
    }

    /** Pattern slots and Endpoints of the selected Provider, from the same authorized choices as the list view. */
    void accept(List<JsonObject> slotChoices, List<JsonObject> targetChoices, String selectedTarget, String providerAt,
            List<JsonObject> providerSections, List<String> names) {
        providerPosition = providerAt == null ? "" : providerAt;
        networkNames.clear();
        networkNames.addAll(names);
        providers.clear();
        providers.addAll(providerSections);
        otherWires.clear();
        otherAccents.clear();
        wireAccent = FederationTheme.EDGE;
        for (var provider : providers) {
            if (selected(provider)) {
                wireAccent = accent(provider);
                continue;
            }
            for (var slot : provider.getAsJsonArray("slots")) {
                var key = otherPortKey(provider, slot.getAsJsonObject());
                otherAccents.put(key, accent(provider));
                for (var endpoint : slot.getAsJsonObject().getAsJsonArray("endpoints")) {
                    if (targetChoices.stream().anyMatch(choice -> choice.get("id").getAsString().equals(endpoint.getAsString()))) {
                        otherWires.add(new Wire(key, endpoint.getAsString()));
                    }
                }
            }
        }
        slots.clear();
        slotChoices.stream().filter(choice -> slotElements != null || !choice.get("empty").getAsBoolean()).forEach(slots::add);
        endpoints.clear();
        endpoints.addAll(targetChoices);
        endpointsById.clear();
        endpoints.forEach(endpoint -> endpointsById.putIfAbsent(endpoint.get("id").getAsString(), endpoint));
        confirmedTarget = selectedTarget == null ? "" : selectedTarget;
        wires.clear();
        for (var slot : slots) {
            if (!slot.has("endpoints")) continue;
            for (var endpoint : slot.getAsJsonArray("endpoints")) {
                var id = endpoint.getAsString();
                if (endpoint(id) != null) wires.add(new Wire(slot.get("id").getAsString(), id));
            }
        }
        long now = System.currentTimeMillis();
        pendingWires.keySet().removeIf(wires::contains);
        pendingWires.values().removeIf(sent -> now - sent > PENDING_MILLIS);
        if (selection.kind() == Kind.WIRE && !wires.contains(new Wire(selection.slot(), selection.endpoint()))
                && !pendingWires.containsKey(new Wire(selection.slot(), selection.endpoint()))) {
            selection = Selection.NONE;
        }
        if (selection.kind() == Kind.ENDPOINT && endpoint(selection.endpoint()) == null) selection = Selection.NONE;
        if (pendingFocus != null && endpoint(pendingFocus.endpoint()) != null) {
            selection = slot(pendingFocus.slot()) == null ? new Selection(Kind.ENDPOINT, "", pendingFocus.endpoint()) : pendingFocus;
            pendingFocus = null;
        }
        if (selection.kind() == Kind.PATTERN && (slot(selection.slot()) == null || emptySlot(slot(selection.slot())))) {
            selection = Selection.NONE;
        }
        // A pattern taken out while it was chosen for the click way of mapping is no longer chosen.
        if (selection.kind() == Kind.ENDPOINT && !selection.slot().isEmpty() && emptySlot(slot(selection.slot()))) {
            selection = new Selection(Kind.ENDPOINT, "", selection.endpoint());
        }
        var signature = new StringBuilder();
        slots.forEach(slot -> signature.append(slot).append(';'));
        // Lane flow counters change with every push and return; they are drawn and listed from the latest choices,
        // so they must not rebuild the canvas, which would also drop a port press about to start a drag.
        endpoints.forEach(endpoint -> {
            var shape = endpoint.deepCopy();
            shape.remove("laneSent");
            shape.remove("laneReturned");
            signature.append(shape).append(';');
        });
        providers.forEach(provider -> signature.append(provider).append(';'));
        signature.append(networkNames);
        if (!signature.toString().equals(structure)) {
            structure = signature.toString();
            rebuild();
        }
        render();
    }

    private void rebuild() {
        canvas.clearAllChildren();
        ports.clear();
        rows.clear();
        otherPorts.clear();
        endpointCards.clear();
        endpointRings.clear();
        endpointInsets.clear();
        endpointStates.clear();
        thumbnailRefresh.clear();
        providerCards.clear();
        otherRows.clear();
        otherSlots.clear();
        hintSlot = "";
        var headings = new UIElement();
        headings.layout(style -> style.widthPercent(100).height(10).flexDirection(FlexDirection.ROW)
                .justifyContent(AlignContent.SPACE_BETWEEN).flexShrink(0));
        headings.addChildren(heading(tr("column.providers"), 100 - ENDPOINT_COLUMN - 2), heading(tr("column.endpoints"), ENDPOINT_COLUMN));
        var body = new UIElement();
        body.layout(style -> style.widthPercent(100).flexDirection(FlexDirection.ROW).justifyContent(AlignContent.SPACE_BETWEEN)
                .alignItems(AlignItems.FLEX_START));
        var left = column(PROVIDER_COLUMN, 10);
        var right = column(ENDPOINT_COLUMN, 6);
        left.setId("processing_patterns");
        right.setId("processing_endpoints");
        boolean selectedShown = false;
        for (var provider : providers) {
            boolean current = selected(provider);
            selectedShown |= current;
            left.addChild(providerCard(provider, current));
        }
        if (!selectedShown) {
            var loose = providerBody();
            for (var slot : slots) loose.addChild(patternRow(slot));
            if (slots.isEmpty()) loose.addChild(note(tr("no_patterns")));
            left.addChild(loose);
        }
        int index = 0;
        for (var endpoint : endpoints) right.addChild(endpointCard(endpoint, index++ < MAX_ENDPOINT_THUMBNAILS));
        if (endpoints.isEmpty()) right.addChild(note(tr("no_endpoints")));
        body.addChildren(left, right);
        searchEmpty = note(tr("no_matches"));
        searchEmpty.setId("processing_search_empty");
        canvas.addChildren(headings, searchEmpty, body);
        applyFilter();
    }

    private static Label heading(Component text, float percent) {
        var label = new Label();
        label.addClass("processing-heading");
        label.setText(text);
        label.textStyle(style -> style.textWrap(TextWrap.HIDE).textColor(FederationTheme.DARK_TITLE));
        label.layout(style -> style.widthPercent(percent).height(10));
        return label;
    }

    private static UIElement column(float percent, float gap) {
        var column = new UIElement();
        column.layout(style -> style.widthPercent(percent).flexDirection(FlexDirection.COLUMN).gapAll(gap));
        return column;
    }

    private static UIElement providerBody() {
        var body = new UIElement();
        body.layout(style -> style.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(2));
        return body;
    }

    private static Label note(Component text) {
        var label = new Label();
        label.addClass("processing-note");
        label.setText(text);
        return label;
    }

    /**
     * One Provider as a card: a header with its map tile, network and slot use, then its patterns. The selected
     * Provider is the one being edited; clicking another one's header or rows selects it, so its wires become editable.
     */
    private UIElement providerCard(JsonObject provider, boolean current) {
        var card = new UIElement();
        card.addClass("processing-provider-card");
        card.layout(style -> style.widthPercent(100).flexDirection(FlexDirection.COLUMN).paddingAll(2).paddingBottom(3));
        int ring = current ? FederationTheme.SELECT : CARD_RING;
        card.style(style -> style.backgroundTexture(FederationTheme.painted((pen, x, y, width, height) -> {
            pen.rect(x - 1, y - 1, width + 2, height + 2, CARD_SHADOW);
            pen.rect(x, y, width, height, ring);
            pen.rect(x + 1, y + 1, width - 2, height - 2, CARD_FACE);
        })));
        card.addChild(providerHeader(provider, current));
        providerCards.put(provider.get("id").getAsString(), card);
        if (current) {
            for (var slot : slots) card.addChild(patternRow(slot));
            if (slots.isEmpty()) card.addChild(note(tr("no_patterns")));
        } else {
            for (var slot : provider.getAsJsonArray("slots")) card.addChild(otherPatternRow(provider, slot.getAsJsonObject()));
        }
        return card;
    }

    private Button providerHeader(JsonObject provider, boolean current) {
        var id = provider.get("id").getAsString();
        var header = new Button();
        header.noText();
        header.addClass("processing-provider");
        if (current) header.addClass("selected");
        header.setId("processing_provider_" + sanitize(id));
        header.layout(style -> style.widthPercent(100).height(HEADER_HEIGHT).paddingAll(4).paddingRight(5).gapAll(4)
                .marginBottom(1).flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER));
        var where = provider.has("position") ? provider.get("position").getAsString() : id.substring(0, Math.min(8, id.length()));
        var thumbnail = deviceThumbnail(provider, where);
        var lines = new UIElement();
        lines.layout(style -> style.flex(1).minWidth(0).gapAll(2).flexDirection(FlexDirection.COLUMN));
        var name = new UIElement();
        name.layout(style -> style.widthPercent(100).height(10).gapAll(3).flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER));
        var swatch = new UIElement();
        swatch.layout(style -> style.width(6).height(6).flexShrink(0));
        swatch.style(style -> style.backgroundTexture(FederationTheme.solid(accent(provider))));
        var nameText = text(tr("provider_title", networkName(provider)), current ? FederationTheme.DARK_TITLE : FederationTheme.DARK_TEXT);
        nameText.layout(style -> style.flex(1).minWidth(0));
        name.addChildren(swatch, nameText);
        var text = new Label();
        text.addClass("processing-provider-text");
        // The card's lit ring says which Provider is edited; the line keeps to where it is and its slots.
        text.setText(tr("provider_header", where, provider.get("slotsUsed").getAsInt(), provider.get("slotsTotal").getAsInt()));
        text.textStyle(style -> style.textWrap(TextWrap.HIDE).textColor(FederationTheme.DARK_MUTED));
        text.layout(style -> style.widthPercent(100).height(9));
        lines.addChildren(name, text);
        header.addChildren(thumbnail, lines);
        // A line under the header separates it from the patterns, as the card's divider.
        var base = FederationTheme.painted((pen, x, y, width, height) -> pen.rect(x - 2, y + height, width + 4, 1, CARD_SHADOW));
        var hover = FederationTheme.painted((pen, x, y, width, height) -> {
            pen.rect(x, y, width, height, 0x229cd3ff);
            pen.rect(x - 2, y + height, width + 4, 1, CARD_SHADOW);
        });
        header.buttonStyle(style -> style.baseTexture(base).hoverTexture(current ? base : hover).pressedTexture(base));
        header.style(style -> style.tooltips(tr(current ? "provider_editing_help" : "provider_open_help")));
        if (!current) header.setOnClick(event -> select.accept("mapping_provider:" + id));
        return header;
    }

    private static boolean emptySlot(@org.jetbrains.annotations.Nullable JsonObject slot) {
        return slot != null && slot.has("empty") && slot.get("empty").getAsBoolean();
    }

    private UIElement patternRow(JsonObject slot) {
        var id = slot.get("id").getAsString();
        var row = new UIElement();
        row.addClass("processing-pattern");
        row.setId("processing_pattern_" + id);
        row.layout(style -> style.widthPercent(100).height(ROW_HEIGHT).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).gapAll(4).paddingLeft(3));
        boolean empty = slot.has("empty") && slot.get("empty").getAsBoolean();
        var stack = patternIcon.apply(slot);
        if (slotElements != null) {
            var element = slotElements.apply(Integer.parseInt(id));
            if (element.getParent() != null) element.getParent().removeChild(element);
            row.addChild(element);
        } else if (!stack.isEmpty()) {
            var icon = new UIElement();
            icon.layout(style -> style.width(14).height(14).flexShrink(0));
            icon.style(style -> style.backgroundTexture(new com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture(stack)));
            row.addChild(icon);
        }
        var name = text(Component.literal("#" + id + " ").append(patternName.apply(slot)),
                empty ? FederationTheme.DARK_MUTED : FederationTheme.DARK_TEXT);
        name.layout(style -> style.flex(1).minWidth(0));
        row.addChild(name);
        int mapped = slot.has("endpoints") ? slot.getAsJsonArray("endpoints").size() : 0;
        if (mapped > 0) {
            var count = text(Component.literal("→ " + mapped), FederationTheme.VALUE);
            count.layout(style -> style.width(textWidth("→ " + mapped)).flexShrink(0));
            row.addChild(count);
        }
        var port = new UIElement();
        port.layout(style -> style.width(PORT_WIDTH).height(ROW_HEIGHT).flexShrink(0));
        row.addChild(port);
        rows.put(id, row);
        // An empty slot has nothing to wire: its row only holds the slot to put a pattern in, and the port's room.
        // Mappings stay with the slot, so wires left from a pattern taken out still end at a muted port to unlink.
        if (empty) {
            row.style(style -> style.tooltips(tr("empty_slot_help")));
            row.addEventListener(UIEvents.MOUSE_DOWN, FederationProcessingGraph::passSlotClick);
            if (mapped > 0) {
                port.style(style -> style.backgroundTexture(FederationTheme.painted((pen, x, y, width, height) ->
                        round(pen, x + width + 2, y + height / 2, PORT, FederationTheme.DARK_MUTED, PORT_FILL))));
                ports.put(id, port);
            }
            return row;
        }
        port.addClass("processing-port");
        port.setId("processing_port_" + id);
        // The ring sits on the card's right edge, half outside it, as the Endpoint's does on its left edge.
        port.style(style -> style.backgroundTexture(FederationTheme.painted((pen, x, y, width, height) -> {
            boolean dragged = id.equals(hintSlot);
            round(pen, x + width + 2, y + height / 2, PORT, wireAccent, dragged ? FederationTheme.DARK_TITLE : PORT_FILL);
        })));
        row.style(style -> style.backgroundTexture(rowFace(id)));
        // Selecting a pattern is the click way to map it: then click an Endpoint and "Map".
        row.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            patternPressed = true;
            selection = new Selection(Kind.PATTERN, id, "");
            rejection = Component.empty();
            render();
            // The whole row drags its wire, except the item slot, whose clicks belong to the pattern in it.
            boolean onSlot = event.target instanceof com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
            pressedRow = event.button == 0 && !onSlot ? id : "";
            pressX = event.x;
            pressY = event.y;
            passSlotClick(event);
        });

        ports.put(id, port);
        return row;
    }

    /** Rows sit on the card itself; the dragged one and the selected one are lit. */
    private IGuiTexture rowFace(String slot) {
        return FederationTheme.painted((pen, x, y, width, height) -> {
            boolean chosen = selection.kind() != Kind.WIRE && slot.equals(selection.slot());
            if (slot.equals(hintSlot) || chosen) pen.rect(x, y, width, height, 0x2e9cd3ff);
            if (chosen) pen.rect(x, y, 2, height, FederationTheme.SELECT);
        });
    }

    /** A pattern of another Provider: its name and a muted port its wires start from; it is edited by selecting it. */
    private UIElement otherPatternRow(JsonObject provider, JsonObject slot) {
        var key = otherPortKey(provider, slot);
        var row = new UIElement();
        row.addClass("processing-pattern-other");
        row.setId("processing_pattern_" + sanitize(key));
        row.layout(style -> style.widthPercent(100).height(ROW_HEIGHT).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).gapAll(4).paddingLeft(3));
        var stack = patternIcon.apply(slot);
        if (!stack.isEmpty()) {
            var icon = new UIElement();
            icon.layout(style -> style.width(14).height(14).flexShrink(0));
            icon.style(style -> style.backgroundTexture(new com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture(stack)));
            row.addChild(icon);
        }
        var name = text(Component.literal("#" + slot.get("id").getAsString() + " ").append(patternName.apply(slot)),
                FederationTheme.DARK_MUTED);
        name.layout(style -> style.flex(1).minWidth(0));
        row.addChild(name);
        int mapped = slot.getAsJsonArray("endpoints").size();
        if (mapped > 0) {
            var count = text(Component.literal("→ " + mapped), FederationTheme.DARK_MUTED);
            count.layout(style -> style.width(textWidth("→ " + mapped)).flexShrink(0));
            row.addChild(count);
        }
        var port = new UIElement();
        port.setId("processing_port_" + sanitize(key));
        port.layout(style -> style.width(PORT_WIDTH).height(ROW_HEIGHT).flexShrink(0));
        int portColor = accent(provider);
        port.style(style -> style.backgroundTexture(FederationTheme.painted((pen, x, y, width, height) ->
                round(pen, x + width + 2, y + height / 2, PORT, portColor, PORT_FILL))));
        row.addChild(port);
        row.style(style -> style.tooltips(tr("provider_open_help")));
        var id = provider.get("id").getAsString();
        row.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            select.accept("mapping_provider:" + id);
            event.stopPropagation();
        });
        otherPorts.put(key, port);
        otherRows.put(key, row);
        otherSlots.put(key, slot);
        return row;
    }

    /** The accent of the Provider's network, as the overview colours its card; neutral outside the domain's networks. */
    private static int accent(JsonObject choice) {
        int index = choice.has("networkIndex") ? choice.get("networkIndex").getAsInt() : -1;
        return index < 0 ? FederationTheme.EDGE : FederationTheme.networkAccent(index);
    }

    /** The name of the network a Provider or Endpoint is on, as the overview names it; empty when it is not a member. */
    private Component networkName(JsonObject choice) {
        int index = choice.has("networkIndex") ? choice.get("networkIndex").getAsInt() : -1;
        return index >= 0 && index < networkNames.size() ? Component.literal(networkNames.get(index)) : tr("network_unknown");
    }

    /** A card's name line: the network's accent swatch, then the name. */
    private static UIElement titled(JsonObject choice, Component value) {
        var line = new UIElement();
        line.layout(style -> style.widthPercent(100).height(9).gapAll(3).flexShrink(0).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER));
        var swatch = new UIElement();
        swatch.layout(style -> style.width(5).height(5).flexShrink(0));
        swatch.style(style -> style.backgroundTexture(FederationTheme.solid(accent(choice))));
        var name = text(value, FederationTheme.DARK_TITLE);
        name.layout(style -> style.flex(1).minWidth(0));
        line.addChildren(swatch, name);
        return line;
    }

    /**
     * A 16:10 tile of the device's surroundings: the ground darkened, its network's blocks in the network's accent
     * and the device itself in white, as the overview draws a network. It follows the overview as it arrives.
     */
    private FederationMapPreview deviceThumbnail(JsonObject choice, String where) {
        var tile = new FederationMapPreview(true);
        tile.layout(style -> style.width(THUMBNAIL_WIDTH).height(THUMBNAIL_HEIGHT).flexShrink(0));
        Runnable show = () -> showDevice(tile, choice, where);
        show.run();
        thumbnailRefresh.add(show);
        tile.setDisplay(position(where).isPresent());
        return tile;
    }

    private void showDevice(FederationMapPreview tile, JsonObject choice, String where) {
        var mark = position(where);
        if (mark.isEmpty()) {
            tile.clear();
            return;
        }
        int index = choice != null && choice.has("networkIndex") ? choice.get("networkIndex").getAsInt() : -1;
        var dimension = dimension(choice);
        tile.show(dimension, networkBlocks.apply(index, dimension), choice == null ? FederationTheme.EDGE : accent(choice),
                List.of(mark.get()), 0xffffffff);
    }

    private JsonObject currentProvider() {
        return providers.stream().filter(FederationProcessingGraph::selected).findFirst().orElse(null);
    }

    private static boolean selected(JsonObject provider) {
        return provider.has("selected") && provider.get("selected").getAsBoolean();
    }

    private static String otherPortKey(JsonObject provider, JsonObject slot) {
        return provider.get("id").getAsString() + "/" + slot.get("id").getAsString();
    }

    /**
     * An Endpoint as a card: its input port on the left edge, its map tile, network, position and claim. The ring
     * shows drop hints while a pattern is dragged, and the bottom line the claim's colour.
     */
    private Button endpointCard(JsonObject endpoint, boolean withThumbnail) {
        var id = endpoint.get("id").getAsString();
        var card = new Button();
        card.noText();
        card.addClass("processing-endpoint");
        card.addClass("claim-" + claim(endpoint).code());
        card.setId("processing_endpoint_" + sanitize(id));
        card.layout(style -> style.widthPercent(100).height(ENDPOINT_HEIGHT).paddingAll(4).paddingLeft(8).paddingBottom(5)
                .gapAll(4).flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER));
        var ring = new int[] {CARD_RING};
        var inset = new int[] {FederationTheme.DARK_MUTED};
        endpointRings.put(id, ring);
        endpointInsets.put(id, inset);
        int portColor = accent(endpoint);
        // On the Provider screen another Provider's Endpoint is only shown: a dashed ring and a padlock say so.
        boolean foreign = slotElements != null && claim(endpoint) == Claim.OCCUPIED;
        var dashed = FederationTheme.dashedBorder(CARD_RING);
        var lock = FederationTheme.lockMark(FederationTheme.DARK_MUTED);
        var face = FederationTheme.painted((pen, x, y, width, height) -> {
            pen.rect(x - 1, y - 1, width + 2, height + 2, CARD_SHADOW);
            pen.rect(x, y, width, height, foreign && ring[0] == CARD_RING ? CARD_FACE : ring[0]);
            pen.rect(x + 1, y + 1, width - 2, height - 2, CARD_FACE);
            pen.rect(x + 1, y + height - 2, width - 2, 1, inset[0]);
            round(pen, x - 1, y + height / 2, PORT, portColor, PORT_FILL);
        });
        IGuiTexture shown = foreign ? new com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup(face, dashed, lock) : face;
        card.buttonStyle(style -> style.baseTexture(shown).hoverTexture(shown).pressedTexture(shown));
        if (withThumbnail && endpoint.has("position")) card.addChild(deviceThumbnail(endpoint, endpoint.get("position").getAsString()));
        var lines = new UIElement();
        lines.layout(style -> style.flex(1).minWidth(0).gapAll(1).flexDirection(FlexDirection.COLUMN));
        var name = titled(endpoint, tr("endpoint_title", networkName(endpoint)));
        int returned = returnKinds(endpoint);
        if (returned > 0) {
            // Results waiting in this Endpoint's Lane buffer to enter the ME network; they keep it from being released.
            var badge = text(Component.literal("↩ " + returned), FederationTheme.WELL);
            badge.setId("processing_endpoint_returns_" + sanitize(id));
            badge.layout(style -> style.width(textWidth("↩ " + returned) + 4).height(8).paddingLeft(2).flexShrink(0));
            badge.style(style -> style.backgroundTexture(FederationTheme.solid(FederationTheme.WARN))
                    .tooltips(tr("returns_badge_help", returned)));
            name.addChild(badge);
        }
        var where = text(endpoint.has("position") ? place(endpoint)
                : Component.literal(endpoint.get("label").getAsString()), FederationTheme.DARK_MUTED);
        var state = new Label();
        state.addClass("processing-endpoint-state");
        state.setId("processing_endpoint_state_" + sanitize(id));
        var claim = claim(endpoint);
        state.setText(tr("claim." + claim.code()).withStyle(Style.EMPTY.withColor(claim.color() & 0xffffff)));
        state.textStyle(style -> style.textWrap(TextWrap.HIDE));
        state.layout(style -> style.widthPercent(100).height(9));
        lines.addChildren(name, where, state);
        card.addChild(lines);
        card.setOnClick(event -> {
            // A chosen pattern stays chosen, so the aside can offer to map it here.
            var chosen = selection.kind() == Kind.PATTERN || selection.kind() == Kind.ENDPOINT ? selection.slot() : "";
            selection = new Selection(Kind.ENDPOINT, chosen, id);
            rejection = Component.empty();
            if (!id.equals(confirmedTarget)) select.accept("target:" + id);
            render();
        });
        card.addEventListener(UIEvents.DRAG_ENTER, event -> {
            if (dragged() != null) hoverEndpoint = id;
        }, true);
        card.addEventListener(UIEvents.DRAG_LEAVE, event -> {
            if (hoverEndpoint.equals(id)) hoverEndpoint = "";
        }, true);
        card.addEventListener(UIEvents.DRAG_PERFORM, event -> {
            var drag = dragged();
            hoverEndpoint = "";
            if (drag != null) drop(drag.slot(), id);
        });
        endpointCards.put(id, card);
        endpointStates.put(id, state);
        return card;
    }

    /** Local refusal of what the server would refuse anyway: an Endpoint another Provider owns keeps its owner. */
    private void drop(String slot, String endpointId) {
        var endpoint = endpoint(endpointId);
        if (endpoint == null || !editable) return;
        var claim = claim(endpoint);
        if (claim == Claim.OCCUPIED) {
            rejection = tr("drop_occupied", endpointName(endpoint), owner(endpoint));
            selection = new Selection(Kind.ENDPOINT, "", endpointId);
        } else if (claim == Claim.UNOBSERVED || claim == Claim.LOCAL) {
            rejection = tr("drop_" + claim.code(), endpointName(endpoint));
            selection = new Selection(Kind.ENDPOINT, "", endpointId);
        } else if (wires.contains(new Wire(slot, endpointId))) {
            rejection = tr("drop_existing");
            selection = new Selection(Kind.WIRE, slot, endpointId);
        } else {
            rejection = Component.empty();
            setMapping.accept(new MappingWireTarget(slot, endpointId, true).encode());
            pendingWires.put(new Wire(slot, endpointId), System.currentTimeMillis());
            selection = new Selection(Kind.WIRE, slot, endpointId);
        }
        render();
    }

    /** Hides what the header search does not match; a Provider whose header matches keeps all its patterns. */
    private void applyFilter() {
        boolean any = query.isEmpty();
        for (var provider : providers) {
            var card = providerCards.get(provider.get("id").getAsString());
            if (card == null) continue;
            var where = provider.has("position") ? provider.get("position").getAsString() : "";
            boolean header = matches(tr("provider_title", networkName(provider)).getString() + " " + where);
            boolean shown = header;
            if (selected(provider)) {
                for (var slot : slots) {
                    var row = rows.get(slot.get("id").getAsString());
                    boolean match = header || matches(patternText(slot));
                    if (row != null) row.setDisplay(match);
                    shown |= match;
                }
            } else {
                for (var slot : provider.getAsJsonArray("slots")) {
                    var key = otherPortKey(provider, slot.getAsJsonObject());
                    var row = otherRows.get(key);
                    boolean match = header || matches(patternText(slot.getAsJsonObject()));
                    if (row != null) row.setDisplay(match);
                    shown |= match;
                }
            }
            card.setDisplay(shown);
            any |= shown;
        }
        for (var endpoint : endpoints) {
            var card = endpointCards.get(endpoint.get("id").getAsString());
            if (card == null) continue;
            boolean match = matches(tr("endpoint_title", networkName(endpoint)).getString() + " "
                    + (endpoint.has("position") ? place(endpoint).getString() : endpoint.get("label").getAsString()));
            card.setDisplay(match);
            any |= match;
        }
        if (searchEmpty != null) searchEmpty.setDisplay(!any);
    }

    private boolean matches(String text) {
        return query.isEmpty() || text.toLowerCase(java.util.Locale.ROOT).contains(query);
    }

    /** "#1 Gold Ingot" and its resources, as the search reads a pattern. */
    private String patternText(JsonObject slot) {
        var text = new StringBuilder("#").append(slot.get("id").getAsString()).append(' ').append(patternName.apply(slot).getString());
        for (var line : patternFacts.apply(slot)) text.append(' ').append(line.getString());
        return text.toString();
    }

    private void pickWire(UIEvent event) {
        passSlotClick(event);
        if (patternPressed) {
            patternPressed = false;
            return;
        }
        Wire nearest = null;
        float best = 4f;
        for (var wire : wires) {
            var ends = ends(wire);
            if (ends == null) continue;
            float distance = curve(ends).distance(event.x, event.y, CURVE_SEGMENTS);
            if (distance < best) {
                best = distance;
                nearest = wire;
            }
        }
        if (nearest == null) return;
        selection = new Selection(Kind.WIRE, nearest.slot(), nearest.endpoint());
        rejection = Component.empty();
        event.stopPropagation();
        render();
    }

    private void render() {
        boolean wire = selection.kind() == Kind.WIRE;
        boolean endpointSelected = selection.kind() == Kind.ENDPOINT;
        boolean patternSelected = selection.kind() == Kind.PATTERN && slot(selection.slot()) != null;
        // The chosen pattern for the click way of mapping, while an Endpoint is selected after it.
        var chosen = endpointSelected && !selection.slot().isEmpty() ? slot(selection.slot()) : null;
        facts.clearAllChildren();
        var text = Component.empty();
        // On the Provider screen another Provider's Endpoint is read-only: the aside says so in a yellow banner.
        boolean readOnly = false;
        if (wire) {
            var slot = slot(selection.slot());
            var endpoint = endpoint(selection.endpoint());
            title.setText(tr("wire", slot == null ? Component.literal("#" + selection.slot())
                    : Component.literal("#" + selection.slot() + " ").append(patternName.apply(slot)),
                    endpoint == null ? Component.literal(selection.endpoint()) : endpointName(endpoint)));
            if (endpoint != null) {
                fact("ownership", tr("ownership", providerPosition.isEmpty() ? "-" : providerPosition));
            }
            // Measured per lane: every pattern mapped to this Endpoint shares the Provider's channel to it.
            boolean sent = laneMoved(endpoint, "laneSent");
            boolean returned = laneMoved(endpoint, "laneReturned");
            fact("lane", !sent && !returned ? tr("lane_idle")
                    : tr("lane_flow", laneAmounts(endpoint, "laneSent"), laneAmounts(endpoint, "laneReturned"))
                            .withStyle(Style.EMPTY.withColor(FederationTheme.TEAL & 0xffffff)));
            boolean confirmed = wires.contains(new Wire(selection.slot(), selection.endpoint()));
            fact("state", !confirmed ? tr("wire_pending").withStyle(Style.EMPTY.withColor(FederationTheme.WARN & 0xffffff))
                    : endpoint != null ? subnet(endpoint) : Component.empty());
        } else if (endpointSelected) {
            var endpoint = endpoint(selection.endpoint());
            if (endpoint != null) {
                var claim = claim(endpoint);
                boolean shownOnly = slotElements != null && claim == Claim.OCCUPIED;
                if (shownOnly) {
                    readOnly = true;
                    text.append(tr("readonly_notice", owner(endpoint)));
                }
                title.setText(Component.empty()
                        .append(Component.literal("■ ").withStyle(Style.EMPTY.withColor(accent(endpoint) & 0xffffff)))
                        .append(tr("endpoint_title", networkName(endpoint)))
                        .append(endpoint.has("position") ? Component.literal(" @ ").append(place(endpoint))
                                : Component.empty()));
                fact("network", networkName(endpoint).copy().append(" · ").append(subnet(endpoint)));
                fact("owner", claim == Claim.OCCUPIED ? tr("owner", owner(endpoint))
                        : claim == Claim.IN_USE || claim == Claim.RETAINED ? tr("owner_here") : Component.literal("-"));
                var mapped = wires.stream().filter(value -> value.endpoint().equals(selection.endpoint()))
                        .map(value -> "#" + value.slot()).toList();
                if (!shownOnly) fact("mapped", Component.literal(mapped.isEmpty() ? "-" : String.join(", ", mapped)));
                if (claim == Claim.OCCUPIED && endpoint.has("ownerPatterns")) {
                    // The owner's own wires to it: shown so the player knows what it does, edited only at that Provider.
                    var patterns = new ArrayList<String>();
                    endpoint.getAsJsonArray("ownerPatterns").forEach(value -> patterns.add("#"
                            + value.getAsJsonObject().get("id").getAsString() + " "
                            + patternName.apply(value.getAsJsonObject()).getString()));
                    fact("owner_patterns", Component.literal(patterns.isEmpty() ? "-" : String.join(", ", patterns)));
                }
                if (endpoint.has("returnKinds")) fact("returns", returnKinds(endpoint) == 0 ? tr("returns_empty")
                        : tr("returns_waiting", returnKinds(endpoint)).withStyle(Style.EMPTY.withColor(FederationTheme.WARN & 0xffffff)));
                // In use by this Provider is what the owner row already says.
                if (!shownOnly && claim != Claim.IN_USE) fact("state", tr("claim." + claim.code() + ".detail").withStyle(Style.EMPTY.withColor(claim.color() & 0xffffff)));
                // Another Provider's Endpoint shows its switch locked, as its other facts are read-only.
                if (setEndpointEnergy != null && endpoint.has("energyTarget")) {
                    boolean active = editable && !shownOnly;
                    var shape = endpoint.get("energyTarget").getAsString() + endpoint.get("energy").getAsBoolean() + active;
                    if (energySwitch == null || !shape.equals(energySwitchShape)) {
                        energySwitch = FederationTopologyView.energySwitch(endpoint, active, setEndpointEnergy);
                        energySwitchShape = shape;
                    } else if (energySwitch.getParent() != null) {
                        energySwitch.getParent().removeChild(energySwitch);
                    }
                    fact("energy", FederationTopologyView.energyState(endpoint)).addChild(energySwitch);
                }
                if (chosen != null) {
                    boolean wiredHere = wires.contains(new Wire(selection.slot(), selection.endpoint()));
                    var name = Component.literal("#" + selection.slot() + " ").append(patternName.apply(chosen));
                    text.append(wiredHere ? tr("toggle_help.mapped", name) : mappable(endpoint) ? tr("toggle_help.free", name)
                            : tr("drop_" + (claim == Claim.OCCUPIED ? "occupied" : claim == Claim.LOCAL ? "local" : "unobserved"),
                                    endpointName(endpoint), owner(endpoint)));
                }
            }
        } else if (patternSelected) {
            var slot = slot(selection.slot());
            title.setText(Component.literal("#" + selection.slot() + " ").append(patternName.apply(slot)));
            for (var line : patternFacts.apply(slot)) text.append(text.getString().isEmpty() ? line : Component.literal("\n").append(line));
            var targets = wires.stream().filter(value -> value.slot().equals(selection.slot())).map(value -> endpoint(value.endpoint()))
                    .filter(java.util.Objects::nonNull).map(value -> endpointName(value).getString()).toList();
            fact("targets", Component.literal(targets.isEmpty() ? "-" : String.join(", ", targets)));
        } else {
            // With nothing selected the panel stays empty, unless there is nothing to wire yet.
            title.setText(Component.empty());
            if (slots.isEmpty() || endpoints.isEmpty()) text.append(tr("empty_help"));
        }
        title.setDisplay(wire || endpointSelected || patternSelected);
        if (!rejection.getString().isEmpty()) {
            if (!text.getString().isEmpty()) text.append("\n");
            text.append(rejection.copy().withStyle(Style.EMPTY.withColor(REFUSAL & 0xffffff)));
        }
        detail.setText(text);
        detail.setDisplay(!text.getString().isEmpty());
        if (readOnly) {
            detail.addClass("paper-note");
            detail.addClass("read-only-banner");
        } else {
            detail.removeClass("paper-note");
            detail.removeClass("read-only-banner");
        }
        facts.setDisplay(wire || endpointSelected || patternSelected);
        unlink.setDisplay(wire);
        boolean togglable = chosen != null && endpoint(selection.endpoint()) != null;
        boolean wiredHere = togglable && wires.contains(new Wire(selection.slot(), selection.endpoint()));
        mappingToggle.setDisplay(togglable);
        mappingToggle.setText(togglable ? tr(wiredHere ? "unmap_here" : "map_here", "#" + selection.slot()) : Component.empty());
        mappingToggle.setActive(editable && togglable
                && (wiredHere || mappable(endpoint(selection.endpoint())) && !emptySlot(chosen)));
        // With nothing selected the aside holds only the help, as in the design.
        highlight.setDisplay(selection.kind() != Kind.NONE);
        highlight.setText(Component.translatable(wire ? "ae2federation.ui.processing.highlight_ends"
                : endpointSelected ? "ae2federation.ui.location.highlight_endpoint"
                : "ae2federation.ui.processing.highlight_provider"));
        unlink.setActive(editable && wire && wires.contains(new Wire(selection.slot(), selection.endpoint())));
        // Release only applies to an Endpoint this Provider holds with no patterns left on it.
        boolean releasable = endpointSelected && releasable(selection.endpoint());
        var draining = releasable ? draining(endpoint(selection.endpoint())) : null;
        if (draining != null) {
            if (!text.getString().isEmpty()) text.append("\n");
            text.append(draining.copy().withStyle(Style.EMPTY.withColor(REFUSAL & 0xffffff)));
            detail.setText(text);
            detail.setDisplay(true);
        }
        releaseButton.setDisplay(releasable);
        releaseButton.setActive(editable && releasable && draining == null);
        renderEnds(wire, endpointSelected);
        endpointCards.forEach((id, card) -> {
            boolean selected = (endpointSelected || wire) && id.equals(selection.endpoint());
            var endpoint = endpoint(id);
            var hint = hint(id);
            var claim = endpoint == null ? Claim.UNOBSERVED : claim(endpoint);
            // Owned by another Provider shown here is the normal many-Provider case, not a fault: it reads as mapped,
            // its owner's wires lead to it and its detail names it. Only a drop onto it is refused, as the drag hint says.
            long elsewhere = claim == Claim.OCCUPIED ? otherWires.stream().filter(value -> value.endpoint().equals(id)).count() : 0;
            // The Provider screen draws no other Provider's wires, so an Endpoint another Provider owns is only
            // shown there: a grey dashed card naming its owner and the owner's patterns, not a fault.
            boolean shownOnly = slotElements != null && claim == Claim.OCCUPIED;
            endpointRings.get(id)[0] = hint != null ? hintColor(hint)
                    : claim == Claim.OCCUPIED && elsewhere == 0 && !shownOnly ? FederationTheme.ERROR
                    : selected ? FederationTheme.SELECT : CARD_RING;
            endpointInsets.get(id)[0] = elsewhere > 0 ? FederationTheme.OK : shownOnly ? FederationTheme.DARK_MUTED : claim.color();
            var state = endpointStates.get(id);
            if (state != null && endpoint != null) {
                long mapped = wires.stream().filter(value -> value.endpoint().equals(id)).count();
                var claimText = elsewhere > 0 ? tr("endpoint_mapped", elsewhere)
                        .withStyle(Style.EMPTY.withColor(FederationTheme.OK & 0xffffff))
                        : shownOnly ? ownerLine(endpoint).withStyle(Style.EMPTY.withColor(FederationTheme.DARK_MUTED & 0xffffff))
                        : (claim == Claim.IN_USE && mapped > 0 ? tr("endpoint_mapped", mapped) : tr("claim." + claim.code()))
                                .withStyle(Style.EMPTY.withColor(claim.color() & 0xffffff));
                state.setText(hint == null ? claimText
                        : tr("drop_hint." + hint.code(), owner(endpoint)).withStyle(Style.EMPTY.withColor(hintColor(hint) & 0xffffff)));
            }
            card.removeClass("drop-accepts");
            card.removeClass("drop-refused");
            if (hint != null) card.addClass(hint.accepts() ? "drop-accepts" : "drop-refused");
            card.removeClass("selected");
            if (selected) card.addClass("selected");
        });
    }

    /** One row of the detail's facts table: a muted name and its value, which wraps. */
    private UIElement fact(String key, Component value) {
        var text = new Label();
        text.setText(value);
        return fact(key, text);
    }

    private UIElement fact(String key, Label text) {
        var row = new UIElement();
        row.addClass("processing-fact");
        row.setId("processing_fact_" + key);
        var name = new Label();
        name.addClass("processing-fact-name");
        name.setText(tr("fact." + key));
        text.addClass("processing-fact-value");
        text.setId("processing_fact_value_" + key);
        row.addChildren(name, text);
        facts.addChild(row);
        return row;
    }

    private static Component subnet(JsonObject endpoint) {
        if (!endpoint.has("nodeReady")) return Component.literal("-");
        if (endpoint.has("subnetAlone") && endpoint.get("subnetAlone").getAsBoolean()) {
            return tr("subnet_alone").withStyle(Style.EMPTY.withColor(FederationTheme.WARN & 0xffffff));
        }
        boolean ready = endpoint.get("nodeReady").getAsBoolean();
        return tr(ready ? "subnet_ready" : "subnet_not_ready").withStyle(Style.EMPTY.withColor(
                (ready ? FederationTheme.OK : FederationTheme.WARN) & 0xffffff));
    }

    /**
     * The selected wire's two ends side by side, the Provider and the Endpoint, or the selected Endpoint alone; each
     * drawn like its card's thumbnail. The Provider is in the player's dimension; an Endpoint may be in another.
     */
    private void renderEnds(boolean wire, boolean endpointSelected) {
        var endpoint = wire || endpointSelected ? endpoint(selection.endpoint()) : null;
        fromEnd.setDisplay(wire && !providerPosition.isEmpty());
        toEnd.setDisplay(endpoint != null && endpoint.has("position"));
        if (wire) {
            showDevice(fromPreview, currentProvider(), providerPosition);
            fromLabel.setText(tr("end_provider", providerPosition));
        }
        if (endpoint != null && endpoint.has("position")) {
            var where = endpoint.get("position").getAsString();
            showDevice(toPreview, endpoint, where);
            toLabel.setText(tr("end_endpoint", place(endpoint)));
        }
        highlight.setActive(!focusMarks().isEmpty());
    }

    /** The selected Endpoint, both ends of the selected wire, or the Provider when nothing is selected. */
    private List<BlockMarks.Mark> focusMarks() {
        var focus = new ArrayList<BlockMarks.Mark>();
        var endpoint = selection.kind() == Kind.NONE ? null : endpoint(selection.endpoint());
        // The world outline is drawn only in the player's dimension, so an Endpoint elsewhere has no mark.
        if (endpoint != null && endpoint.has("position") && dimension(endpoint).equals(playerDimension())) {
            position(endpoint.get("position").getAsString()).ifPresent(focus::add);
        }
        if (selection.kind() != Kind.ENDPOINT) position(providerPosition).ifPresent(focus::add);
        return focus;
    }

    private List<space.controlnet.ae2federation.client.WorldHighlight.Group> focusGroups() {
        return List.of(new space.controlnet.ae2federation.client.WorldHighlight.Group(focusMarks(), FederationTheme.SELECT));
    }

    /** The button reads as pressed exactly while the selection's outline runs, which ends on its own after a while. */
    private void syncHighlight() {
        var groups = focusGroups();
        boolean on = !groups.getFirst().blocks().isEmpty()
                && space.controlnet.ae2federation.client.WorldHighlight.brightness(playerDimension(), groups) > 0;
        if (on == highlight.hasClass("selected")) return;
        if (on) highlight.addClass("selected");
        else highlight.removeClass("selected");
    }

    private static java.util.Optional<BlockMarks.Mark> position(String value) {
        return value.isEmpty() ? java.util.Optional.empty() : BlockMarks.parseShort(value);
    }

    /** The dimension a row's device is in; rows without one are the player's own Provider. */
    private static String dimension(JsonObject choice) {
        return choice != null && choice.has("dimension") ? choice.get("dimension").getAsString() : playerDimension();
    }

    private static String playerDimension() {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        return level == null ? "" : level.dimension().location().toString();
    }

    /** The server released a retained Endpoint only when this Provider still holds it and it is the confirmed target. */
    private boolean releasable(String endpointId) {
        var endpoint = endpoint(endpointId);
        return endpoint != null && endpointId.equals(confirmedTarget) && endpoint.has("retained")
                && endpoint.get("retained").getAsBoolean();
    }

    /** What a drop of the dragged pattern on this Endpoint would do, or null while nothing is dragged. */
    private space.controlnet.ae2federation.client.policy.DropHint hint(String endpointId) {
        var endpoint = endpoint(endpointId);
        if (hintSlot.isEmpty() || endpoint == null) return null;
        return space.controlnet.ae2federation.client.policy.DropHint.of(claim(endpoint).code(),
                wires.contains(new Wire(hintSlot, endpointId)));
    }

    private static int hintColor(space.controlnet.ae2federation.client.policy.DropHint hint) {
        return switch (hint.tone()) {
            case OK -> FederationTheme.OK;
            case WARN -> FederationTheme.WARN;
            case ERROR -> FederationTheme.ERROR;
            case MUTED -> FederationTheme.DARK_MUTED;
        };
    }

    public static int drawnWireDots() {
        return drawnWireDots;
    }

    /** How many kinds of result wait in the Endpoint's Lane buffer; 0 when none or when it is not reported. */
    private static int returnKinds(JsonObject endpoint) {
        return endpoint != null && endpoint.has("returnKinds") ? endpoint.get("returnKinds").getAsInt() : 0;
    }

    /**
     * Why the server would refuse to release this Endpoint yet: results still in its Lane buffer, or a send still in
     * progress through it; null when neither holds it.
     */
    private static Component draining(JsonObject endpoint) {
        if (endpoint == null) return null;
        if (returnKinds(endpoint) > 0) return tr("release_blocked_returns", returnKinds(endpoint));
        if (endpoint.has("pendingSend") && endpoint.get("pendingSend").getAsBoolean()) return tr("release_blocked_send");
        return null;
    }

    private static boolean laneMoved(JsonObject endpoint, String field) {
        return endpoint != null && endpoint.has(field) && !endpoint.getAsJsonObject(field).isEmpty();
    }

    /** A lane's amounts over the flow window, one per resource type, each in that type's own unit. */
    private static String laneAmounts(JsonObject endpoint, String field) {
        if (!laneMoved(endpoint, field)) return "0";
        var parts = new ArrayList<String>();
        endpoint.getAsJsonObject(field).entrySet().forEach(entry -> {
            long amount = entry.getValue().getAsLong();
            parts.add(keyType(entry.getKey()).map(type -> type.formatAmount(amount, appeng.api.stacks.AmountFormat.FULL))
                    .orElse(amount + " " + entry.getKey()));
        });
        return String.join(", ", parts);
    }

    private static java.util.Optional<appeng.api.stacks.AEKeyType> keyType(String id) {
        var location = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (location == null) return java.util.Optional.empty();
        try {
            return java.util.Optional.of(appeng.api.stacks.AEKeyTypes.get(location));
        } catch (IllegalArgumentException unknown) {
            return java.util.Optional.empty();
        }
    }

    private static Claim claim(JsonObject endpoint) {
        boolean ownedHere = endpoint.has("ownedHere") && endpoint.get("ownedHere").getAsBoolean();
        if (endpoint.has("owner") && !ownedHere) return Claim.OCCUPIED;
        if (endpoint.has("retained") && endpoint.get("retained").getAsBoolean()) return Claim.RETAINED;
        if (ownedHere) return Claim.IN_USE;
        var state = endpoint.has("state") ? endpoint.get("state").getAsString() : "unobserved";
        return switch (state) {
            case "unobserved" -> Claim.UNOBSERVED;
            case "local" -> Claim.LOCAL;
            default -> Claim.FREE;
        };
    }

    private static Component endpointName(JsonObject endpoint) {
        return endpoint.has("position") ? FederationWorkspace.tr("endpoint_at", place(endpoint))
                : Component.literal(endpoint.get("label").getAsString());
    }

    /** A device's coordinates, with its dimension when it is not in the player's. */
    private static Component place(JsonObject device) {
        return DevicePlace.of(device.get("position").getAsString(), dimension(device), playerDimension());
    }

    /** Where the Endpoint's owner is, "@ x, y, z", or its short identity when it is not a loaded block. */
    /** Who owns an Endpoint and which of its patterns reach it: "Provider @ 12, -57, 10 · #0 Glass, #1 Iron Ingot". */
    private MutableComponent ownerLine(JsonObject endpoint) {
        var patterns = new ArrayList<String>();
        if (endpoint.has("ownerPatterns")) {
            endpoint.getAsJsonArray("ownerPatterns").forEach(value -> patterns.add("#" + value.getAsJsonObject().get("id").getAsString()
                    + " " + patternName.apply(value.getAsJsonObject()).getString()));
        }
        return patterns.isEmpty() ? tr("owner", owner(endpoint)) : tr("owner_line", owner(endpoint), String.join(", ", patterns));
    }

    private static String owner(JsonObject endpoint) {
        if (endpoint.has("ownerPosition")) return "@ " + endpoint.get("ownerPosition").getAsString();
        return endpoint.has("owner") ? endpoint.get("owner").getAsString().substring(0, 8) : "-";
    }

    /** A round port of radius {@code r} centred on {@code (cx, cy)}: a ring in {@code edge} around {@code fill}. */
    private static void round(FederationTheme.Pen pen, float cx, float cy, int r, int edge, int fill) {
        for (int row = -r; row < r; row++) {
            float half = (float) Math.floor(Math.sqrt(r * r - (row + 0.5f) * (row + 0.5f)) + 0.5f);
            pen.rect(cx - half, cy + row, half * 2, 1, edge);
        }
        int inner = r - 2;
        for (int row = -inner; row < inner; row++) {
            float half = (float) Math.floor(Math.sqrt(inner * inner - (row + 0.5f) * (row + 0.5f)) + 0.5f);
            pen.rect(cx - half, cy + row, half * 2, 1, fill);
        }
    }

    private static float textWidth(String value) {
        return net.minecraft.client.Minecraft.getInstance().font.width(value) + 1;
    }

    private static Label text(Component value, int color) {
        var label = new Label();
        label.setText(value);
        label.textStyle(style -> style.textWrap(TextWrap.HIDE).textColor(color));
        label.layout(style -> style.widthPercent(100).height(9).flexShrink(0));
        return label;
    }

    private PortDrag dragged() {
        var ui = canvas.getModularUI();
        if (ui == null || !ui.getDragHandler().isDragging()) return null;
        return ui.getDragHandler().draggingObject instanceof PortDrag drag ? drag : null;
    }

    private Vector2f[] ends(Wire wire) {
        return ends(ports.get(wire.slot()), wire);
    }

    /** From the port's centre to the Endpoint card's input port on its left edge. */
    private Vector2f[] ends(UIElement port, Wire wire) {
        var card = endpointCards.get(wire.endpoint());
        // A search hides rows and cards; their wires go with them.
        if (port == null || card == null || !card.isDisplayed() || !port.getParent().isDisplayed()
                || !port.getParent().getParent().isDisplayed()) return null;
        return new Vector2f[] {portPoint(port), new Vector2f(card.getPositionX() - 1, card.getPositionY() + card.getSizeHeight() / 2)};
    }

    /** The centre of the port's ring, on the card's right edge. */
    private static Vector2f portPoint(UIElement port) {
        return new Vector2f(port.getPositionX() + port.getSizeWidth() + 2, port.getPositionY() + port.getSizeHeight() / 2);
    }

    /** Two dots travelling along the wire, from the port when {@code returning} is false and back when it is true. */
    private static int flowDots(GUIContext context, space.controlnet.ae2federation.client.policy.WireCurve curve,
            float phase, boolean returning, int color) {
        for (int dot = 0; dot < 2; dot++) {
            float t = (phase + dot / 2f) % 1f;
            var point = curve.at(returning ? 1 - t : t);
            int x = Math.round(point[0]);
            int y = Math.round(point[1]);
            context.graphics.fill(x - 2, y - 2, x + 2, y + 2, 0xff0b0a12);
            context.graphics.fill(x - 1, y - 1, x + 1, y + 1, color);
        }
        return 2;
    }

    /** A dashed curve: a wire the player made that the server has not confirmed yet, or one being dragged. */
    private static void dashed(GUIContext context, List<Vector2f> line, int color) {
        for (int index = 1; index < line.size(); index += 2) {
            DrawerHelper.drawTexLines(context.graphics, LDLibRenderTypes.graphWire(), List.of(line.get(index - 1), line.get(index)),
                    color, color, 2f);
        }
    }

    private static space.controlnet.ae2federation.client.policy.WireCurve curve(Vector2f[] ends) {
        return new space.controlnet.ae2federation.client.policy.WireCurve(ends[0].x, ends[0].y, ends[1].x, ends[1].y);
    }

    private static List<Vector2f> polyline(space.controlnet.ae2federation.client.policy.WireCurve curve) {
        var points = curve.points(CURVE_SEGMENTS);
        var line = new ArrayList<Vector2f>(CURVE_SEGMENTS + 1);
        for (int index = 0; index < points.length; index += 2) line.add(new Vector2f(points[index], points[index + 1]));
        return line;
    }

    private JsonObject slot(String id) {
        return slots.stream().filter(value -> value.get("id").getAsString().equals(id)).findFirst().orElse(null);
    }

    private JsonObject endpoint(String id) {
        return endpointsById.get(id);
    }

    static String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    static MutableComponent tr(String key, Object... arguments) {
        return Component.translatable("ae2federation.ui.processing." + key, arguments);
    }

    private static <T> T element(UI ui, String id, Class<T> type) {
        return ui.selectId(id, type).findFirst().orElseThrow(() -> new IllegalStateException("Missing UI element #" + id));
    }

    private enum Kind { NONE, PATTERN, WIRE, ENDPOINT }

    private record Selection(Kind kind, String slot, String endpoint) {
        static final Selection NONE = new Selection(Kind.NONE, "", "");
    }

    private record Wire(String slot, String endpoint) {
    }

    private record PortDrag(String slot) {
    }

    private enum Claim {
        FREE("free", FederationTheme.DARK_MUTED), IN_USE("in_use", FederationTheme.OK), RETAINED("retained", FederationTheme.WARN),
        OCCUPIED("occupied", FederationTheme.ERROR), LOCAL("local", FederationTheme.DARK_MUTED),
        UNOBSERVED("unobserved", FederationTheme.DARK_MUTED);

        private final String code;
        private final int color;

        Claim(String code, int color) {
            this.code = code;
            this.color = color;
        }

        String code() {
            return code;
        }

        int color() {
            return color;
        }
    }

    /** Column headings over the two columns; wires and the live drag wire are drawn behind the cards. */
    private final class Canvas extends UIElement {
        Canvas() {
            setId("processing_canvas");
            layout(style -> style.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(7).paddingAll(6)
                    .paddingLeft(8).paddingRight(9));
        }

        /** Cards switch to drop hints when a drag starts and back to their claim when it ends. */
        @Override
        public void screenTick() {
            super.screenTick();
            if (refreshTicks-- <= 0) {
                refreshTicks = THUMBNAIL_REFRESH_TICKS;
                thumbnailRefresh.forEach(Runnable::run);
            }
            var drag = dragged();
            var slot = drag == null ? "" : drag.slot();
            if (!slot.equals(hintSlot)) {
                hintSlot = slot;
                render();
            }
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            // Other Providers' wires sit behind this one's, thin and muted: seen, not edited here.
            for (var wire : otherWires) {
                var ends = ends(otherPorts.get(wire.slot()), wire);
                if (ends == null) continue;
                int muted = 0x66000000 | (otherAccents.getOrDefault(wire.slot(), 0x625d70) & 0xffffff);
                DrawerHelper.drawTexLines(context.graphics, LDLibRenderTypes.graphWire(), polyline(curve(ends)), muted, muted, 1.5f);
            }
            for (var wire : wires) {
                var ends = ends(wire);
                if (ends == null) continue;
                boolean selected = selection.kind() == Kind.WIRE && selection.slot().equals(wire.slot())
                        && selection.endpoint().equals(wire.endpoint());
                boolean related = selection.kind() == Kind.ENDPOINT && selection.endpoint().equals(wire.endpoint());
                // Wires take the colour of the Provider's network, as its card does in the overview.
                int color = selected || related ? FederationTheme.SELECT : wireAccent;
                DrawerHelper.drawTexLines(context.graphics, LDLibRenderTypes.graphWire(), polyline(curve(ends)), color, color,
                        selected ? 3f : 2f);
            }
            long now = System.currentTimeMillis();
            int dots = 0;
            float phase = (now % FLOW_PERIOD_MILLIS) / (float) FLOW_PERIOD_MILLIS;
            for (var wire : wires) {
                var ends = ends(wire);
                var endpoint = endpoint(wire.endpoint());
                if (ends == null) continue;
                var path = curve(ends);
                if (laneMoved(endpoint, "laneSent")) dots += flowDots(context, path, phase, false, FederationTheme.TEAL);
                if (laneMoved(endpoint, "laneReturned")) dots += flowDots(context, path, phase, true, FederationTheme.OK);
            }
            drawnWireDots = dots;
            pendingWires.forEach((wire, sent) -> {
                var ends = ends(wire);
                if (ends == null || wires.contains(wire) || now - sent > PENDING_MILLIS) return;
                dashed(context, polyline(curve(ends)), 0xffefeaf8);
            });
            var drag = dragged();
            var ui = getModularUI();
            if (drag != null && ui != null && ports.containsKey(drag.slot())) {
                var hint = hoverEndpoint.isEmpty() ? null : hint(hoverEndpoint);
                int color = hint == null ? 0xffefeaf8 : hintColor(hint);
                var from = portPoint(ports.get(drag.slot()));
                float mouseX = ui.getLastMouseX();
                float mouseY = ui.getLastMouseY();
                dashed(context, polyline(new space.controlnet.ae2federation.client.policy.WireCurve(from.x, from.y, mouseX, mouseY)), color);
                int x = Math.round(mouseX);
                int y = Math.round(mouseY);
                context.graphics.fill(x - 3, y - 3, x + 3, y + 3, color);
                context.graphics.fill(x - 2, y - 2, x + 2, y + 2, 0xff1f1d26);
            }
        }
    }
}
