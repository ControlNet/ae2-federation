package space.controlnet.ae2federation.client.menu;

import com.lowdragmc.lowdraglib2.client.shader.LDLibRenderTypes;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.GraphView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import org.joml.Vector2f;
import space.controlnet.ae2federation.client.domain.FederationDomainGraphLayer;
import space.controlnet.ae2federation.client.domain.FederationDomainGraphLayout;
import space.controlnet.ae2federation.client.domain.FederationDomainGraphLayoutCache;
import space.controlnet.ae2federation.client.domain.FederationDomainGraphSnapshot;

/** Domain-owned graph with LDLib's textured wire rendering; layout never grants or changes permissions. */
final class FederationGraphPresenter {
    private final GraphView graph;
    private final Button open;
    private final java.util.function.BiConsumer<String, String> navigate;
    private final Map<String, String> locations = new HashMap<>();
    private final Label inspector;
    private final VirtualScrollerView<String> members;
    private final Label emptySearch;
    private String search = "";
    private String objectListSignature = "";
    private final FederationDomainGraphLayoutCache cache = new FederationDomainGraphLayoutCache();
    private final Map<String, Button> nodes = new HashMap<>();
    private final Map<String, FederationDomainGraphSnapshot.Node> descriptors = new HashMap<>();
    private final Map<String, FederationDomainGraphLayout.Node> positions = new HashMap<>();
    private FederationDomainGraphSnapshot snapshot = FederationDomainGraphSnapshot.empty();
    private String structure = "";
    private String selected = "";
    private boolean physical = true;
    private boolean capability = true;
    private boolean fitted;
    private String initialFocus = "";
    private boolean initialFocusApplied;
    private String requestedFocus = "";
    private String scope = "domain";

    FederationGraphPresenter(GraphView graph, Label inspector, VirtualScrollerView<String> members,
            Button open, TextField searchField, Label emptySearch, java.util.function.BiConsumer<String, String> navigate) {
        this.graph = graph;
        this.open = open;
        this.navigate = navigate;
        open.setActive(false);
        open.setOnClick(event -> snapshot.nodes().stream().filter(node -> node.id().equals(selected)).findFirst()
                .ifPresent(node -> navigate.accept(node.kind().name().equals("PROVIDER") ? "mapping_provider" : "endpoint", selected)));
        inspector.addEventListener(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents.HOVER_TOOLTIPS,
                event -> event.hoverTooltips = com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips.empty()
                        .append(identityText(selected)));
        this.inspector = inspector;
        this.members = members;
        this.emptySearch = emptySearch;
        emptySearch.setText(FederationWorkspace.tr("no_choices"));
        searchField.textFieldStyle(style -> style.placeholder(FederationWorkspace.tr("search_objects").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)));
        searchField.setTextResponder(value -> {
            search = value.strip().toLowerCase(java.util.Locale.ROOT);
            refreshObjectList();
        });
        members.setItemUIProvider(id -> {
            var button = new Button();
            button.setText(objectText(id));
            button.addClass("graph-object-row");
            button.setOnClick(event -> {
                select(id);
                var position = positions.get(id);
                if (position != null) {
                    float halfWidth = graph.getContentWidth() / graph.getScale() / 2;
                    float halfHeight = graph.getContentHeight() / graph.getScale() / 2;
                    float x = position.x() + 48, y = position.y() + 15.5f;
                    graph.fit(x - halfWidth, y - halfHeight, x + halfWidth, y + halfHeight, graph.getScale());
                }
            });
            button.addEventListener(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents.HOVER_TOOLTIPS,
                    event -> event.hoverTooltips = com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips.empty()
                            .append(identityText(id)));
            return button;
        });
    }

    void accept(String encoded) {
        if (encoded.isEmpty()) return;
        snapshot = FederationDomainGraphSnapshot.decode(encoded);
        descriptors.clear();
        snapshot.nodes().forEach(node -> descriptors.put(node.id(), node));
        var signature = snapshot.structuralSignature();
        if (!signature.equals(structure)) {
            structure = signature;
            graph.clearAllContentChildren();
            nodes.clear();
            positions.clear();
            var layout = cache.layout(snapshot);
            layout.nodes().forEach(node -> positions.put(node.id(), node));
            graph.addContentChild(new Wires());
            for (var node : layout.nodes()) {
                var button = new Button();
                button.addClass("graph-node-" + node.kind().name().toLowerCase(java.util.Locale.ROOT));
                button.setId("graph_node_" + node.id().replaceAll("[^a-zA-Z0-9_-]", "_"));
                button.layout(style -> style.positionType(TaffyPosition.ABSOLUTE).left(node.x()).top(node.y())
                        .width(96).height(31).paddingAll(2));
                button.textStyle(style -> style.fontSize(7).textShadow(false));
                int color = color(node.kind().name());
                button.buttonStyle(style -> style
                        .baseTexture(GuiTextureGroup.of(new ColorRectTexture(0xffe0e0e6), new ColorBorderTexture(1, color)))
                        .hoverTexture(GuiTextureGroup.of(new ColorRectTexture(0xffc5deed), new ColorBorderTexture(1, 0xff245f79)))
                        .pressedTexture(new ColorRectTexture(0xffacd3e5)));
                button.setOnClick(event -> select(node.id()));
                button.addEventListener(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents.HOVER_TOOLTIPS,
                        event -> event.hoverTooltips = com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips.empty()
                                .append(identityText(node.id())));
                graph.addContentChild(button);
                nodes.put(node.id(), button);
            }
            if (!positions.containsKey(selected)) selected = "";
        }
        updateNodeLabels();
        refreshObjectList();
        updateInspector();
    }

    private Component objectText(String id) {
        var node = java.util.Objects.requireNonNull(descriptors.get(id));
        var location = locations.getOrDefault(id, "");
        return FederationWorkspace.tr("kind." + node.kind().name().toLowerCase(java.util.Locale.ROOT))
                .append("\n" + (location.isEmpty() ? shortId(id) : location));
    }

    private void refreshObjectList() {
        var matches = snapshot.nodes().stream().map(FederationDomainGraphSnapshot.Node::id)
                .filter(id -> search.isEmpty() || (id + " " + objectText(id).getString())
                        .toLowerCase(java.util.Locale.ROOT).contains(search)).toList();
        var signature = matches.stream().map(id -> id + "=" + objectText(id).getString())
                .collect(java.util.stream.Collectors.joining("\n"));
        if (!signature.equals(objectListSignature)) {
            objectListSignature = signature;
            members.setItems(matches);
        }
        members.setDisplay(!matches.isEmpty());
        emptySearch.setDisplay(matches.isEmpty());
    }

    private void updateNodeLabels() {
        for (var node : snapshot.nodes()) {
            var button = nodes.get(node.id());
            if (button != null) button.setText(FederationWorkspace.tr("kind." + node.kind().name().toLowerCase(java.util.Locale.ROOT))
                    .append("\n" + (locations.getOrDefault(node.id(), "").isEmpty()
                            ? shortId(node.id()) : locations.get(node.id()))));
        }
    }

    void acceptChoices(String encoded) {
        if (encoded.isEmpty()) return;
        var root = com.google.gson.JsonParser.parseString(encoded).getAsJsonObject();
        scope = root.has("scope") ? root.get("scope").getAsString() : "domain";
        if (!initialFocusApplied && root.has("initialGraphFocus")) initialFocus = root.get("initialGraphFocus").getAsString();
        locations.clear();
        for (var group : new String[] {"mapping_provider", "endpoint"}) {
            root.getAsJsonArray(group).forEach(value -> {
                var choice = value.getAsJsonObject();
                locations.put(choice.get("id").getAsString(), choice.has("position") ? choice.get("position").getAsString() : "");
            });
        }
        updateNodeLabels();
        refreshObjectList();
        updateInspector();
    }

    void togglePhysical(Button button) { physical = !physical; mark(button, physical); }
    void toggleCapability(Button button) { capability = !capability; mark(button, capability); }

    static void mark(Button button, boolean active) {
        button.removeClass("selected");
        if (active) button.addClass("selected");
    }

    boolean focusObject(String id) {
        if (!positions.containsKey(id)) return false;
        requestedFocus = id;
        initialFocusApplied = true;
        return true;
    }

    private void select(String id) {
        requestedFocus = "";
        initialFocusApplied = true;
        selected = id;
        updateInspector();
    }

    private void updateInspector() {
        var node = snapshot.nodes().stream().filter(value -> value.id().equals(selected)).findFirst().orElse(null);
        if (node == null) {
            open.setActive(false);
            inspector.setText(FederationWorkspace.tr(snapshot.nodes().isEmpty()
                    ? (scope.equals("domain") ? "empty_graph" : "scope_summary." + scope) : "select_node"));
            return;
        }
        open.setActive(locations.containsKey(selected));
        open.setText(FederationWorkspace.tr(node.kind().name().equals("PROVIDER") ? "open_mapping" : "open_details"));
        var location = locations.getOrDefault(selected, "");
        inspector.setText(FederationWorkspace.tr("kind." + node.kind().name().toLowerCase(java.util.Locale.ROOT))
                .append("\n" + (location.isEmpty() ? shortId(node.id()) : location) + "\n")
                .append(node.kind().name().equals("PROVIDER")
                        ? FederationWorkspace.tr("last_target_check", Component.translatableWithFallback(
                                "ae2federation.ui.workspace.state." + node.status(), node.status()))
                        : Component.translatableWithFallback("ae2federation.ui.workspace.state." + node.status(), node.status()))
                .append("\n").append(FederationWorkspace.tr("connections", snapshot.edges().stream()
                        .filter(edge -> edge.from().equals(selected) || edge.to().equals(selected)).count())));
        for (var candidate : snapshot.nodes()) {
            var button = nodes.get(candidate.id());
            if (button != null) {
                boolean active = candidate.id().equals(selected);
                button.buttonStyle(style -> style.baseTexture(GuiTextureGroup.of(
                        new ColorRectTexture(active ? 0xffacd3e5 : 0xffe0e0e6),
                        new ColorBorderTexture(active ? 2 : 1, active ? 0xff245f79 : color(candidate.kind().name())))));
            }
        }
    }

    private static int color(String kind) {
        return switch (kind) {
            case "MEMBER" -> 0xff28667c;
            case "PROVIDER" -> 0xff69508d;
            default -> 0xff875d28;
        };
    }

    private static String shortId(String value) {
        var colon = value.indexOf(':');
        var id = colon < 0 ? value : value.substring(colon + 1);
        return id.substring(0, Math.min(id.length(), 10));
    }

    private static Component identityText(String value) {
        // Opaque graph IDs can exceed a screen width at high GUI scales. Preserve every character in wrapped lines.
        return Component.literal(value.replaceAll("(.{32})(?!$)", "$1\n"));
    }

    private final class Wires extends UIElement {
        Wires() {
            layout(style -> style.positionType(TaffyPosition.ABSOLUTE).left(0).top(0).width(positions.values().stream().mapToInt(node -> node.x() + 96).max().orElse(1))
                    .height(positions.values().stream().mapToInt(node -> node.y() + 31).max().orElse(1)));
        }

        @Override
        public void screenTick() {
            super.screenTick();
            if (!fitted && !nodes.isEmpty() && graph.getContentWidth() > 0 && graph.getContentHeight() > 0) {
                graph.fitToChildren(12, 0.25f);
                fitted = true;
            }
            String focus = !requestedFocus.isEmpty() ? requestedFocus : !initialFocusApplied ? initialFocus : "";
            if (fitted && graph.getContentWidth() > 0 && graph.getContentHeight() > 0 && positions.containsKey(focus)) {
                FederationGraphPresenter.this.select(focus);
                var position = positions.get(focus);
                float halfWidth = graph.getContentWidth() / graph.getScale() / 2;
                float halfHeight = graph.getContentHeight() / graph.getScale() / 2;
                float x = position.x() + 48, y = position.y() + 15.5f;
                graph.fit(x - halfWidth, y - halfHeight, x + halfWidth, y + halfHeight, graph.getScale());
            }
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            context.graphics.pose().pushPose();
            context.graphics.pose().translate(getPositionX(), getPositionY(), 0);
            for (var edge : snapshot.edges()) {
                if (edge.layer() == FederationDomainGraphLayer.PHYSICAL ? !physical : !capability) continue;
                var from = positions.get(edge.from());
                var to = positions.get(edge.to());
                if (from == null || to == null) continue;
                float x1 = from.x() + 96, y1 = from.y() + 15;
                float x2 = to.x(), y2 = to.y() + 15;
                float tangent = Math.max(20, Math.abs(x2 - x1) * 0.5f);
                var points = new ArrayList<Vector2f>(25);
                for (int i = 0; i <= 24; i++) {
                    float t = i / 24f, u = 1 - t;
                    points.add(new Vector2f(u*u*u*x1 + 3*u*u*t*(x1+tangent) + 3*u*t*t*(x2-tangent) + t*t*t*x2,
                            u*u*u*y1 + 3*u*u*t*y1 + 3*u*t*t*y2 + t*t*t*y2));
                }
                boolean highlighted = edge.from().equals(selected) || edge.to().equals(selected);
                int alpha = selected.isEmpty() || highlighted ? 0xff000000 : 0x44000000;
                int start = (color(from.kind().name()) & 0xffffff) | alpha;
                int end = (color(to.kind().name()) & 0xffffff) | alpha;
                DrawerHelper.drawTexLines(context.graphics, LDLibRenderTypes.graphWire(), points, start, end, highlighted ? 5f : 3f);
                context.graphics.fill((int)x1 - 2, (int)y1 - 2, (int)x1 + 2, (int)y1 + 2, start);
                context.graphics.fill((int)x2 - 2, (int)y2 - 2, (int)x2 + 2, (int)y2 + 2, end);
            }
            context.graphics.pose().popPose();
        }
    }
}
