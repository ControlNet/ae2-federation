package space.controlnet.ae2federation.client.menu;

import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.client.shader.LDLibRenderTypes;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
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
import space.controlnet.ae2federation.client.policy.MappingWireTarget;

/**
 * Processing wires for one Pattern Provider: pattern rows with an output port on the left, the Endpoints this
 * Provider may use on the right, and one wire per mapped pattern and Endpoint. Dragging a port onto an Endpoint maps
 * it, clicking a wire offers to unlink it and clicking an Endpoint selects it for details and release. Every change
 * is an explicit {@link MappingWireTarget} request that the server checks against live ownership.
 */
public final class FederationProcessingGraph {
    private static final float ROW_HEIGHT = 20;
    private static final float GAP = 44;
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
    private final UIElement root;
    private final ScrollerView scroll;
    private final Label detail;
    private final Button unlink;
    private final Button releaseButton;
    private final Canvas canvas = new Canvas();
    private final FederationMapPreview preview = new FederationMapPreview();
    private final Button highlight;
    private String providerPosition = "";

    private final List<JsonObject> slots = new ArrayList<>();
    private final List<JsonObject> endpoints = new ArrayList<>();
    private final Map<String, UIElement> ports = new LinkedHashMap<>();
    private final Map<String, Button> endpointCards = new LinkedHashMap<>();
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
    private Selection selection = Selection.NONE;
    private Component rejection = Component.empty();
    private String hoverEndpoint = "";
    /** The pattern slot whose drag the Endpoint cards currently show drop hints for; empty when nothing is dragged. */
    private String hintSlot = "";
    private final Map<String, Label> endpointStates = new LinkedHashMap<>();

    FederationProcessingGraph(UI ui, Consumer<String> setMapping, Consumer<String> select, Runnable release,
            Function<JsonObject, Component> patternName, Function<JsonObject, net.minecraft.world.item.ItemStack> patternIcon) {
        this.setMapping = setMapping;
        this.select = select;
        this.release = release;
        this.patternName = patternName;
        this.patternIcon = patternIcon;
        root = element(ui, "processing_graph", UIElement.class);
        scroll = element(ui, "processing_scroll", ScrollerView.class);
        detail = element(ui, "processing_detail_text", Label.class);
        unlink = element(ui, "processing_unlink", Button.class);
        releaseButton = element(ui, "processing_release", Button.class);
        scroll.addScrollViewChild(canvas);
        element(ui, "processing_preview", UIElement.class).addChild(preview);
        highlight = element(ui, "processing_highlight", Button.class);
        highlight.style(style -> style.tooltips(FederationWorkspace.trLocation("highlight_help")));
        highlight.setOnClick(event -> {
            var focus = focusMarks();
            if (!focus.isEmpty()) space.controlnet.ae2federation.client.WorldHighlight.show(playerDimension(), focus,
                    FederationTheme.SELECT);
        });
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
    }

    UIElement root() {
        return root;
    }

    void setEditable(boolean value) {
        if (editable == value) return;
        editable = value;
        render();
    }

    /** Pattern slots and Endpoints of the selected Provider, from the same authorized choices as the list view. */
    void accept(List<JsonObject> slotChoices, List<JsonObject> targetChoices, String selectedTarget, String providerAt,
            List<JsonObject> providerSections) {
        providerPosition = providerAt == null ? "" : providerAt;
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
        slotChoices.stream().filter(choice -> !choice.get("empty").getAsBoolean()).forEach(slots::add);
        endpoints.clear();
        endpoints.addAll(targetChoices);
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
        var signature = new StringBuilder();
        slots.forEach(slot -> signature.append(slot).append(';'));
        endpoints.forEach(endpoint -> signature.append(endpoint).append(';'));
        providers.forEach(provider -> signature.append(provider).append(';'));
        if (!signature.toString().equals(structure)) {
            structure = signature.toString();
            rebuild();
        }
        render();
    }

    private void rebuild() {
        canvas.clearAllChildren();
        ports.clear();
        otherPorts.clear();
        endpointCards.clear();
        endpointStates.clear();
        hintSlot = "";
        var left = column();
        var right = column();
        left.setId("processing_patterns");
        right.setId("processing_endpoints");
        boolean selectedShown = false;
        for (var provider : providers) {
            left.addChild(providerHeader(provider));
            if (selected(provider)) {
                selectedShown = true;
                for (var slot : slots) left.addChild(patternRow(slot));
                if (slots.isEmpty()) left.addChild(note(tr("no_patterns")));
            } else {
                for (var slot : provider.getAsJsonArray("slots")) left.addChild(otherPatternRow(provider, slot.getAsJsonObject()));
            }
        }
        if (!selectedShown) {
            for (var slot : slots) left.addChild(patternRow(slot));
            if (slots.isEmpty()) left.addChild(note(tr("no_patterns")));
        }
        for (var endpoint : endpoints) right.addChild(endpointCard(endpoint));
        if (endpoints.isEmpty()) right.addChild(note(tr("no_endpoints")));
        canvas.addChildren(left, right);
    }

    private static UIElement column() {
        var column = new UIElement();
        column.layout(style -> style.flex(1).minWidth(0).flexDirection(FlexDirection.COLUMN).gapAll(3));
        return column;
    }

    private static Label note(Component text) {
        var label = new Label();
        label.addClass("processing-note");
        label.setText(text);
        return label;
    }

    private UIElement patternRow(JsonObject slot) {
        var id = slot.get("id").getAsString();
        var row = new UIElement();
        row.addClass("processing-pattern");
        row.setId("processing_pattern_" + id);
        row.layout(style -> style.widthPercent(100).height(ROW_HEIGHT).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).gapAll(3).paddingLeft(3));
        var stack = patternIcon.apply(slot);
        if (!stack.isEmpty()) {
            var icon = new UIElement();
            icon.layout(style -> style.width(16).height(16).flexShrink(0));
            icon.style(style -> style.backgroundTexture(new com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture(stack)));
            row.addChild(icon);
        }
        var name = new Label();
        name.setText(Component.literal("#" + id + " ").append(patternName.apply(slot)));
        name.textStyle(style -> style.textWrap(TextWrap.HIDE).textColor(FederationTheme.DARK_TEXT));
        name.layout(style -> style.flex(1).minWidth(0).height(10));
        row.addChild(name);
        var port = new UIElement();
        port.addClass("processing-port");
        port.setId("processing_port_" + id);
        port.layout(style -> style.width(10).height(ROW_HEIGHT).flexShrink(0));
        port.style(style -> style.backgroundTexture(FederationTheme.painted((pen, x, y, width, height) -> {
            pen.rect(x + 2, y + height / 2 - 3, 6, 6, FederationTheme.SELECT);
            pen.rect(x + 3, y + height / 2 - 2, 4, 4, 0xff1f1d26);
        })).tooltips(tr("port_help")));
        // A drag starts when the pressed pointer leaves the port, as LDLib2 drag sources do.
        port.addEventListener(UIEvents.MOUSE_LEAVE, event -> {
            if (editable && port.isMouseDown(0)) port.startDrag(new PortDrag(id), null);
        }, true);
        row.addChild(port);
        row.style(style -> style.backgroundTexture(FederationTheme.WELL_RECT));
        ports.put(id, port);
        return row;
    }

    /**
     * One Provider's header: where it is and how many of its pattern slots hold patterns. The selected Provider is the
     * one being edited; clicking another one selects it, so its wires become editable.
     */
    private Button providerHeader(JsonObject provider) {
        var id = provider.get("id").getAsString();
        boolean current = selected(provider);
        var header = new Button();
        header.noText();
        header.addClass("processing-provider");
        if (current) header.addClass("selected");
        header.setId("processing_provider_" + sanitize(id));
        header.layout(style -> style.widthPercent(100).height(12).paddingLeft(3).paddingRight(3).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER));
        var where = provider.has("position") ? provider.get("position").getAsString() : id.substring(0, Math.min(8, id.length()));
        var text = new Label();
        text.addClass("processing-provider-text");
        text.setText(tr("provider_header", where, provider.get("slotsUsed").getAsInt(), provider.get("slotsTotal").getAsInt())
                .append(" · ").append(tr(current ? "provider_editing" : "provider_open")));
        text.textStyle(style -> style.textWrap(TextWrap.HIDE).textColor(current ? FederationTheme.DARK_TEXT : FederationTheme.DARK_MUTED));
        text.layout(style -> style.flex(1).minWidth(0).height(9));
        header.addChild(text);
        var face = GuiTextureGroup.of(FederationTheme.WELL_RECT, new ColorBorderTexture(1, current ? FederationTheme.TEAL : 0xff47434f));
        header.buttonStyle(style -> style.baseTexture(face).hoverTexture(GuiTextureGroup.of(FederationTheme.WELL_RECT,
                new ColorBorderTexture(1, FederationTheme.SELECT))).pressedTexture(face));
        header.style(style -> style.tooltips(tr(current ? "provider_editing_help" : "provider_open_help")));
        if (!current) header.setOnClick(event -> select.accept("mapping_provider:" + id));
        return header;
    }

    /** A pattern of another Provider: its name and a muted port its wires start from; it is edited by selecting it. */
    private UIElement otherPatternRow(JsonObject provider, JsonObject slot) {
        var key = otherPortKey(provider, slot);
        var row = new UIElement();
        row.addClass("processing-pattern-other");
        row.setId("processing_pattern_" + sanitize(key));
        row.layout(style -> style.widthPercent(100).height(ROW_HEIGHT - 6).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).gapAll(3).paddingLeft(3));
        var stack = patternIcon.apply(slot);
        if (!stack.isEmpty()) {
            var icon = new UIElement();
            icon.layout(style -> style.width(12).height(12).flexShrink(0));
            icon.style(style -> style.backgroundTexture(new com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture(stack)));
            row.addChild(icon);
        }
        var name = new Label();
        name.setText(Component.literal("#" + slot.get("id").getAsString() + " ").append(patternName.apply(slot)));
        name.textStyle(style -> style.textWrap(TextWrap.HIDE).textColor(FederationTheme.DARK_MUTED));
        name.layout(style -> style.flex(1).minWidth(0).height(9));
        var port = new UIElement();
        port.setId("processing_port_" + sanitize(key));
        port.layout(style -> style.width(10).height(ROW_HEIGHT - 6).flexShrink(0));
        port.style(style -> style.backgroundTexture(FederationTheme.painted((pen, x, y, width, height) ->
                pen.rect(x + 3, y + height / 2 - 2, 4, 4, FederationTheme.DARK_MUTED))));
        row.addChildren(name, port);
        row.style(style -> style.backgroundTexture(FederationTheme.WELL_RECT).tooltips(tr("provider_open_help")));
        var id = provider.get("id").getAsString();
        row.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            select.accept("mapping_provider:" + id);
            event.stopPropagation();
        });
        otherPorts.put(key, port);
        return row;
    }

    /** The accent of the Provider's network, as the overview colours its card; neutral outside the domain's networks. */
    private static int accent(JsonObject provider) {
        int index = provider.has("networkIndex") ? provider.get("networkIndex").getAsInt() : -1;
        return index < 0 ? FederationTheme.EDGE : FederationTheme.networkAccent(index);
    }

    private static boolean selected(JsonObject provider) {
        return provider.has("selected") && provider.get("selected").getAsBoolean();
    }

    private static String otherPortKey(JsonObject provider, JsonObject slot) {
        return provider.get("id").getAsString() + "/" + slot.get("id").getAsString();
    }

    private Button endpointCard(JsonObject endpoint) {
        var id = endpoint.get("id").getAsString();
        var card = new Button();
        card.noText();
        card.addClass("processing-endpoint");
        card.setId("processing_endpoint_" + sanitize(id));
        card.layout(style -> style.widthPercent(100).height(ROW_HEIGHT + 4).paddingAll(3).flexDirection(FlexDirection.COLUMN)
                .alignItems(AlignItems.FLEX_START));
        var title = new Label();
        title.setText(endpointName(endpoint));
        title.textStyle(style -> style.textWrap(TextWrap.HIDE).textColor(FederationTheme.DARK_TEXT));
        title.layout(style -> style.widthPercent(100).height(9));
        var state = new Label();
        state.addClass("processing-endpoint-state");
        state.setId("processing_endpoint_state_" + sanitize(id));
        var claim = claim(endpoint);
        state.setText(tr("claim." + claim.code()).withStyle(Style.EMPTY.withColor(claim.color() & 0xffffff)));
        state.textStyle(style -> style.textWrap(TextWrap.HIDE));
        state.layout(style -> style.widthPercent(100).height(9));
        card.addChildren(title, state);
        card.setOnClick(event -> {
            selection = new Selection(Kind.ENDPOINT, "", id);
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
            rejection = tr("drop_occupied", endpointName(endpoint), shortOwner(endpoint));
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

    private void pickWire(UIEvent event) {
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
        var text = Component.empty();
        boolean wire = selection.kind() == Kind.WIRE;
        boolean endpointSelected = selection.kind() == Kind.ENDPOINT;
        if (wire) {
            var slot = slot(selection.slot());
            var endpoint = endpoint(selection.endpoint());
            text.append(tr("wire", slot == null ? Component.literal("#" + selection.slot())
                    : Component.literal("#" + selection.slot() + " ").append(patternName.apply(slot)),
                    endpoint == null ? Component.literal(selection.endpoint()) : endpointName(endpoint)));
            if (endpoint != null) text.append("\n").append(ruleLine(endpoint));
            // Measured per lane: every pattern mapped to this Endpoint shares the Provider's channel to it.
            long sent = laneAmount(endpoint, "laneSent");
            long returned = laneAmount(endpoint, "laneReturned");
            text.append("\n").append(sent == 0 && returned == 0 ? tr("lane_idle")
                    : tr("lane_flow", sent, returned).withStyle(Style.EMPTY.withColor(FederationTheme.TEAL & 0xffffff)));
            if (!wires.contains(new Wire(selection.slot(), selection.endpoint()))) text.append("\n").append(tr("wire_pending"));
        } else if (endpointSelected) {
            var endpoint = endpoint(selection.endpoint());
            if (endpoint != null) {
                var claim = claim(endpoint);
                text.append(endpointName(endpoint)).append("\n").append(tr("claim." + claim.code() + ".detail"));
                if (claim == Claim.OCCUPIED) text.append("\n").append(tr("owner", shortOwner(endpoint)));
                if (endpoint.has("claimEpoch")) text.append("\n").append(tr("claim_epoch", endpoint.get("claimEpoch").getAsLong()));
                if (endpoint.has("nodeReady")) {
                    boolean ready = endpoint.get("nodeReady").getAsBoolean();
                    text.append("\n").append(tr(ready ? "subnet_ready" : "subnet_not_ready").withStyle(Style.EMPTY.withColor(
                            (ready ? FederationTheme.OK : FederationTheme.WARN) & 0xffffff)));
                }
                var mapped = wires.stream().filter(value -> value.endpoint().equals(selection.endpoint()))
                        .map(value -> "#" + value.slot()).toList();
                text.append("\n").append(tr("endpoint_patterns", mapped.isEmpty() ? "-" : String.join(", ", mapped)));
            }
        } else {
            text.append(tr(slots.isEmpty() || endpoints.isEmpty() ? "empty_help" : "help"));
        }
        if (!rejection.getString().isEmpty()) text.append("\n").append(rejection.copy().withStyle(
                Style.EMPTY.withColor(FederationTheme.ERROR & 0xffffff)));
        detail.setText(text);
        unlink.setDisplay(wire);
        highlight.setText(Component.translatable(wire ? "ae2federation.ui.processing.highlight_ends"
                : "ae2federation.ui.location.highlight"));
        unlink.setActive(editable && wire && wires.contains(new Wire(selection.slot(), selection.endpoint())));
        // Release only applies to an Endpoint this Provider holds with no patterns left on it.
        boolean releasable = endpointSelected && releasable(selection.endpoint());
        releaseButton.setDisplay(releasable);
        releaseButton.setActive(editable && releasable);
        renderLocation();
        endpointCards.forEach((id, card) -> {
            boolean selected = (endpointSelected || wire) && id.equals(selection.endpoint());
            var endpoint = endpoint(id);
            var hint = hint(id);
            var border = hint != null ? hintColor(hint)
                    : endpoint != null && claim(endpoint) == Claim.OCCUPIED ? FederationTheme.ERROR
                    : selected ? FederationTheme.SELECT : 0xff47434f;
            var state = endpointStates.get(id);
            if (state != null && endpoint != null) {
                var claim = claim(endpoint);
                state.setText(hint == null ? tr("claim." + claim.code()).withStyle(Style.EMPTY.withColor(claim.color() & 0xffffff))
                        : tr("drop_hint." + hint.code(), shortOwner(endpoint)).withStyle(Style.EMPTY.withColor(hintColor(hint) & 0xffffff)));
            }
            card.removeClass("drop-accepts");
            card.removeClass("drop-refused");
            if (hint != null) card.addClass(hint.accepts() ? "drop-accepts" : "drop-refused");
            var face = GuiTextureGroup.of(FederationTheme.WELL_RECT, new ColorBorderTexture(1, border));
            card.buttonStyle(style -> style.baseTexture(face).hoverTexture(GuiTextureGroup.of(FederationTheme.WELL_RECT,
                    new ColorBorderTexture(1, FederationTheme.SELECT))).pressedTexture(face));
            card.removeClass("selected");
            if (selected) card.addClass("selected");
        });
    }

    /**
     * Map of the Provider and its Endpoints: the selected Endpoint (or both ends of the selected wire) is marked, the
     * rest are tinted. Devices of one Provider session are in the player's dimension.
     */
    private void renderLocation() {
        var others = new ArrayList<space.controlnet.ae2federation.client.policy.BlockMarks.Mark>();
        position(providerPosition).ifPresent(others::add);
        for (var endpoint : endpoints) {
            if (endpoint.has("position")) position(endpoint.get("position").getAsString()).ifPresent(others::add);
        }
        var focus = focusMarks();
        preview.show(playerDimension(), others, FederationTheme.TEAL, focus, FederationTheme.SELECT);
        highlight.setActive(!focus.isEmpty());
    }

    /** The selected Endpoint, both ends of the selected wire, or the Provider when nothing is selected. */
    private List<space.controlnet.ae2federation.client.policy.BlockMarks.Mark> focusMarks() {
        var focus = new ArrayList<space.controlnet.ae2federation.client.policy.BlockMarks.Mark>();
        var endpoint = selection.kind() == Kind.NONE ? null : endpoint(selection.endpoint());
        if (endpoint != null && endpoint.has("position")) position(endpoint.get("position").getAsString()).ifPresent(focus::add);
        if (selection.kind() != Kind.ENDPOINT) position(providerPosition).ifPresent(focus::add);
        return focus;
    }

    private static java.util.Optional<space.controlnet.ae2federation.client.policy.BlockMarks.Mark> position(String value) {
        return value.isEmpty() ? java.util.Optional.empty() : space.controlnet.ae2federation.client.policy.BlockMarks.parseShort(value);
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
                wires.contains(new Wire(hintSlot, endpointId)), ruleOn(endpoint));
    }

    private static int hintColor(space.controlnet.ae2federation.client.policy.DropHint hint) {
        return switch (hint.tone()) {
            case OK -> FederationTheme.OK;
            case WARN -> FederationTheme.WARN;
            case ERROR -> FederationTheme.ERROR;
            case MUTED -> FederationTheme.DARK_MUTED;
        };
    }

    /** Whether the server reports an enabled processing rule from this Provider's network to the Endpoint's. */
    private static boolean ruleOn(JsonObject endpoint) {
        return endpoint.has("rule") && endpoint.get("rule").getAsString().equals("on");
    }

    public static int drawnWireDots() {
        return drawnWireDots;
    }

    private static long laneAmount(JsonObject endpoint, String field) {
        return endpoint != null && endpoint.has(field) ? endpoint.get(field).getAsLong() : 0L;
    }

    /** The wire's rule line: the processing rule it dispatches under, or why it will pause. */
    private static Component ruleLine(JsonObject endpoint) {
        var rule = endpoint.has("rule") ? endpoint.get("rule").getAsString() : "none";
        var revision = endpoint.has("ruleRevision") ? endpoint.get("ruleRevision").getAsLong() : 0L;
        return tr("wire_rule." + rule, revision).withStyle(Style.EMPTY.withColor(
                (rule.equals("on") ? FederationTheme.OK : FederationTheme.WARN) & 0xffffff));
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
        return endpoint.has("position") ? FederationWorkspace.tr("endpoint_at", endpoint.get("position").getAsString())
                : Component.literal(endpoint.get("label").getAsString());
    }

    private static String shortOwner(JsonObject endpoint) {
        return endpoint.has("owner") ? endpoint.get("owner").getAsString().substring(0, 8) : "-";
    }

    private PortDrag dragged() {
        var ui = canvas.getModularUI();
        if (ui == null || !ui.getDragHandler().isDragging()) return null;
        return ui.getDragHandler().draggingObject instanceof PortDrag drag ? drag : null;
    }

    private Vector2f[] ends(Wire wire) {
        return ends(ports.get(wire.slot()), wire);
    }

    private Vector2f[] ends(UIElement port, Wire wire) {
        var card = endpointCards.get(wire.endpoint());
        if (port == null || card == null) return null;
        return new Vector2f[] {portPoint(port), new Vector2f(card.getPositionX(), card.getPositionY() + card.getSizeHeight() / 2)};
    }

    private static Vector2f portPoint(UIElement port) {
        return new Vector2f(port.getPositionX() + port.getSizeWidth() - 2, port.getPositionY() + port.getSizeHeight() / 2);
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

    /** A white dashed curve: a wire the player made that the server has not confirmed yet. */
    private static void dashed(GUIContext context, space.controlnet.ae2federation.client.policy.WireCurve curve, int color) {
        var line = polyline(curve);
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
        return endpoints.stream().filter(value -> value.get("id").getAsString().equals(id)).findFirst().orElse(null);
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

    private enum Kind { NONE, WIRE, ENDPOINT }

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

    /** Two columns with the wire gap between them; wires and the live drag wire are drawn behind the rows. */
    private final class Canvas extends UIElement {
        Canvas() {
            setId("processing_canvas");
            layout(style -> style.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(GAP).paddingAll(3));
        }

        /** Cards switch to drop hints when a drag starts and back to their claim when it ends. */
        @Override
        public void screenTick() {
            super.screenTick();
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
                DrawerHelper.drawTexLines(context.graphics, LDLibRenderTypes.graphWire(), polyline(curve(ends)), muted, muted, 1f);
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
                if (laneAmount(endpoint, "laneSent") > 0) dots += flowDots(context, path, phase, false, FederationTheme.TEAL);
                if (laneAmount(endpoint, "laneReturned") > 0) dots += flowDots(context, path, phase, true, FederationTheme.OK);
            }
            drawnWireDots = dots;
            pendingWires.forEach((wire, sent) -> {
                var ends = ends(wire);
                if (ends == null || wires.contains(wire) || now - sent > PENDING_MILLIS) return;
                dashed(context, curve(ends), 0xffefeaf8);
            });
            var drag = dragged();
            var ui = getModularUI();
            if (drag != null && ui != null && ports.containsKey(drag.slot())) {
                var hint = hoverEndpoint.isEmpty() ? null : hint(hoverEndpoint);
                int color = hint == null ? 0xaa8b83a0 : hintColor(hint);
                DrawerHelper.drawTexLines(context.graphics, LDLibRenderTypes.graphWire(), List.of(portPoint(ports.get(drag.slot())),
                        new Vector2f(ui.getLastMouseX(), ui.getLastMouseY())), color, color, 2f);
            }
        }
    }
}
