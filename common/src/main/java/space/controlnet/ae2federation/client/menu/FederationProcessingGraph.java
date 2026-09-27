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
final class FederationProcessingGraph {
    private static final float ROW_HEIGHT = 20;
    private static final float GAP = 44;

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

    private final List<JsonObject> slots = new ArrayList<>();
    private final List<JsonObject> endpoints = new ArrayList<>();
    private final Map<String, UIElement> ports = new LinkedHashMap<>();
    private final Map<String, Button> endpointCards = new LinkedHashMap<>();
    private final List<Wire> wires = new ArrayList<>();
    private String structure = "";
    private String confirmedTarget = "";
    private boolean editable;
    private Selection selection = Selection.NONE;
    private Component rejection = Component.empty();
    private String hoverEndpoint = "";

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
    void accept(List<JsonObject> slotChoices, List<JsonObject> targetChoices, String selectedTarget) {
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
        if (selection.kind() == Kind.WIRE && !wires.contains(new Wire(selection.slot(), selection.endpoint()))) {
            selection = Selection.NONE;
        }
        if (selection.kind() == Kind.ENDPOINT && endpoint(selection.endpoint()) == null) selection = Selection.NONE;
        var signature = new StringBuilder();
        slots.forEach(slot -> signature.append(slot).append(';'));
        endpoints.forEach(endpoint -> signature.append(endpoint).append(';'));
        if (!signature.toString().equals(structure)) {
            structure = signature.toString();
            rebuild();
        }
        render();
    }

    private void rebuild() {
        canvas.clearAllChildren();
        ports.clear();
        endpointCards.clear();
        var left = column();
        var right = column();
        left.setId("processing_patterns");
        right.setId("processing_endpoints");
        for (var slot : slots) left.addChild(patternRow(slot));
        for (var endpoint : endpoints) right.addChild(endpointCard(endpoint));
        if (slots.isEmpty()) left.addChild(note(tr("no_patterns")));
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
            float distance = distance(new Vector2f(event.x, event.y), ends[0], ends[1]);
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
            if (!wires.contains(new Wire(selection.slot(), selection.endpoint()))) text.append("\n").append(tr("wire_pending"));
        } else if (endpointSelected) {
            var endpoint = endpoint(selection.endpoint());
            if (endpoint != null) {
                var claim = claim(endpoint);
                text.append(endpointName(endpoint)).append("\n").append(tr("claim." + claim.code() + ".detail"));
                if (claim == Claim.OCCUPIED) text.append("\n").append(tr("owner", shortOwner(endpoint)));
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
        unlink.setActive(editable && wire && wires.contains(new Wire(selection.slot(), selection.endpoint())));
        releaseButton.setDisplay(endpointSelected);
        releaseButton.setActive(editable && endpointSelected && releasable(selection.endpoint()));
        endpointCards.forEach((id, card) -> {
            boolean selected = (endpointSelected || wire) && id.equals(selection.endpoint());
            var endpoint = endpoint(id);
            var border = endpoint != null && claim(endpoint) == Claim.OCCUPIED ? FederationTheme.ERROR
                    : selected ? FederationTheme.SELECT : 0xff47434f;
            var face = GuiTextureGroup.of(FederationTheme.WELL_RECT, new ColorBorderTexture(1, border));
            card.buttonStyle(style -> style.baseTexture(face).hoverTexture(GuiTextureGroup.of(FederationTheme.WELL_RECT,
                    new ColorBorderTexture(1, FederationTheme.SELECT))).pressedTexture(face));
            card.removeClass("selected");
            if (selected) card.addClass("selected");
        });
    }

    /** The server released a retained Endpoint only when this Provider still holds it and it is the confirmed target. */
    private boolean releasable(String endpointId) {
        var endpoint = endpoint(endpointId);
        return endpoint != null && endpointId.equals(confirmedTarget) && endpoint.has("retained")
                && endpoint.get("retained").getAsBoolean();
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
        var port = ports.get(wire.slot());
        var card = endpointCards.get(wire.endpoint());
        if (port == null || card == null) return null;
        return new Vector2f[] {portPoint(port), new Vector2f(card.getPositionX(), card.getPositionY() + card.getSizeHeight() / 2)};
    }

    private static Vector2f portPoint(UIElement port) {
        return new Vector2f(port.getPositionX() + port.getSizeWidth() - 2, port.getPositionY() + port.getSizeHeight() / 2);
    }

    private static float distance(Vector2f point, Vector2f from, Vector2f to) {
        var segment = new Vector2f(to).sub(from);
        float length = segment.lengthSquared();
        float t = length == 0 ? 0 : Math.max(0, Math.min(1, new Vector2f(point).sub(from).dot(segment) / length));
        return point.distance(new Vector2f(from).add(segment.mul(t)));
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

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            for (var wire : wires) {
                var ends = ends(wire);
                if (ends == null) continue;
                boolean selected = selection.kind() == Kind.WIRE && selection.slot().equals(wire.slot())
                        && selection.endpoint().equals(wire.endpoint());
                boolean related = selection.kind() == Kind.ENDPOINT && selection.endpoint().equals(wire.endpoint());
                int color = selected || related ? FederationTheme.SELECT : FederationTheme.EDGE;
                DrawerHelper.drawTexLines(context.graphics, LDLibRenderTypes.graphWire(), List.of(ends[0], ends[1]), color, color,
                        selected ? 3f : 2f);
            }
            var drag = dragged();
            var ui = getModularUI();
            if (drag != null && ui != null && ports.containsKey(drag.slot())) {
                int color = hoverEndpoint.isEmpty() ? 0xaa8b83a0 : endpoint(hoverEndpoint) != null
                        && claim(endpoint(hoverEndpoint)) == Claim.OCCUPIED ? FederationTheme.ERROR : FederationTheme.OK;
                DrawerHelper.drawTexLines(context.graphics, LDLibRenderTypes.graphWire(), List.of(portPoint(ports.get(drag.slot())),
                        new Vector2f(ui.getLastMouseX(), ui.getLastMouseY())), color, color, 2f);
            }
        }
    }
}
