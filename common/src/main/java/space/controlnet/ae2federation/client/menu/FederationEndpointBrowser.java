package space.controlnet.ae2federation.client.menu;

import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import dev.vfyjxf.taffy.style.FlexDirection;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;

/** Domain-scoped endpoint observations; selecting a row uses the existing authoritative selection action. */
final class FederationEndpointBrowser {
    private final UI ui;
    private final Consumer<String> select;
    private List<JsonObject> endpoints = List.of();
    private String selectedId = "";
    private String query = "";
    private Dialog dialog;
    private VirtualScrollerView<JsonObject> list;
    private Label empty;

    FederationEndpointBrowser(UI ui, Consumer<String> select) {
        this.ui = ui;
        this.select = select;
    }

    void accept(JsonObject root) {
        var replacement = root.getAsJsonArray("endpoint").asList().stream().map(value -> value.getAsJsonObject()).toList();
        var selected = root.getAsJsonObject("selected");
        var nextId = selected.has("endpoint") ? selected.get("endpoint").getAsString() : "";
        if (replacement.equals(endpoints) && nextId.equals(selectedId)) return;
        endpoints = replacement;
        selectedId = nextId;
        refresh();
    }

    void open() {
        if (dialog != null) return;
        query = "";
        dialog = new Dialog().darkenBackground();
        dialog.setId("endpoint_browser");
        dialog.overlay.layout(style -> style.width(Math.min(420, ui.rootElement.getContentWidth() - 12))
                .height(Math.min(120 + Math.min(5, endpoints.size()) * 50, ui.rootElement.getContentHeight() - 12)));
        dialog.overlay.style(style -> style.backgroundTexture(FederationTheme.FRAME));
        dialog.titleBar.style(style -> style.backgroundTexture(new ColorRectTexture(FederationTheme.INSET)));
        dialog.contentContainer.layout(style -> style.flex(1).minHeight(0).gapAll(4));
        dialog.contentContainer.style(style -> style.backgroundTexture(new ColorRectTexture(FederationTheme.PANEL)));
        var search = new TextField();
        search.setId("endpoint_table_search");
        search.layout(style -> style.widthPercent(100).height(18).flexShrink(0));
        search.textFieldStyle(style -> style.placeholder(FederationWorkspace.tr("endpoints_search").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)));
        search.setTextResponder(value -> { query = value.strip().toLowerCase(Locale.ROOT); refresh(); });
        var heading = columns(FederationWorkspace.tr("endpoints_position"),
                FederationWorkspace.tr("endpoints_modes"), FederationWorkspace.tr("endpoints_owner"));
        list = new VirtualScrollerView<>();
        list.setId("endpoint_table");
        list.layout(style -> style.widthPercent(100).flex(1).minHeight(0));
        list.virtualScrollerViewStyle(style -> style.estimatedItemHeight(50));
        list.viewPort.style(style -> style.backgroundTexture(FederationTheme.FIELD));
        list.setItemUIProvider(this::row);
        empty = new Label();
        empty.setId("endpoint_table_empty");
        empty.layout(style -> style.widthPercent(100));
        empty.textStyle(style -> style.adaptiveHeight(true).textWrap(TextWrap.WRAP));
        dialog.addContent(search).addContent(heading).addContent(empty).addContent(list);
        dialog.buttonContainer.style(style -> style.backgroundTexture(new ColorRectTexture(FederationTheme.PANEL)));
        var close = new Button();
        close.setId("endpoint_browser_close");
        close.layout(style -> style.widthPercent(100).height(20));
        close.setText(FederationWorkspace.tr("endpoints_close"));
        close.setOnClick(event -> dialog.close());
        dialog.addButton(close);
        dialog.setOnClose(() -> { dialog = null; list = null; empty = null; });
        refresh();
        dialog.show(ui.rootElement);
    }

    private void refresh() {
        if (dialog == null) return;
        dialog.setTitle(FederationWorkspace.tr("endpoints_title", endpoints.size()).getString());
        var visible = endpoints.stream().filter(endpoint -> query.isEmpty() || searchable(endpoint).contains(query)).toList();
        list.setItems(visible);
        list.setDisplay(!visible.isEmpty());
        empty.setDisplay(visible.isEmpty());
        empty.setText(FederationWorkspace.tr(endpoints.isEmpty() ? "endpoints_none" : "endpoints_no_matches"));
    }

    private String searchable(JsonObject endpoint) {
        return (endpoint.get("endpointIdentity").getAsString() + " " + endpoint.get("id").getAsString() + " "
                + position(endpoint).getString() + " " + modes(endpoint).getString() + " "
                + owner(endpoint, false).getString() + " " + reason(endpoint).getString()).toLowerCase(Locale.ROOT);
    }

    private Component position(JsonObject endpoint) {
        return Component.literal(endpoint.get("position").getAsString()).append("\n")
                .append(FederationWorkspace.tr(endpoint.get("nodeReady").getAsBoolean() ? "endpoints_node_ready" : "endpoints_node_unready"));
    }

    private Component modes(JsonObject endpoint) {
        return mode(endpoint.get("configuredMode").getAsString()).copy().append(" → ")
                .append(mode(endpoint.get("runtimeMode").getAsString()));
    }

    private Component mode(String value) {
        return FederationWorkspace.tr("endpoint_mode." + value);
    }

    private Component owner(JsonObject endpoint, boolean compact) {
        if (!endpoint.has("owner")) return FederationWorkspace.tr("unclaimed");
        var id = endpoint.get("owner").getAsString();
        return Component.literal(compact ? id.substring(0, Math.min(8, id.length())) : id);
    }

    private Component reason(JsonObject endpoint) {
        var result = endpoint.get("claimResult").getAsString();
        return FederationWorkspace.tr("endpoints_last_claim", Component.translatableWithFallback(
                "ae2federation.ui.workspace.claim_result." + result, result));
    }

    private UIElement columns(Component position, Component modes, Component owner) {
        var row = new UIElement();
        row.layout(style -> style.widthPercent(100).height(22).flexShrink(0).flexDirection(FlexDirection.ROW).gapAll(4));
        var values = List.of(position, modes, owner);
        var classes = List.of("endpoint-table-position", "endpoint-table-modes", "endpoint-table-owner");
        for (int index = 0; index < values.size(); index++) {
            var value = values.get(index);
            var label = new Label();
            label.addClass(classes.get(index));
            label.layout(style -> style.flex(1).minWidth(0).height(22));
            label.textStyle(style -> style.fontSize(7).adaptiveWidth(false).textWrap(TextWrap.WRAP));
            label.setText(value);
            row.addChild(label);
        }
        return row;
    }

    private UIElement row(JsonObject endpoint) {
        var button = new Button();
        button.addClass("endpoint-table-row");
        boolean selected = endpoint.get("id").getAsString().equals(selectedId);
        if (selected) button.addClass("selected");
        button.buttonStyle(style -> style.baseTexture(selected ? FederationTheme.PRESSED : FederationTheme.FIELD));
        button.layout(style -> style.height(50).paddingAll(2).gapAll(2).flexDirection(FlexDirection.COLUMN));
        button.text.setDisplay(false);
        button.addChild(columns(position(endpoint), modes(endpoint), owner(endpoint, true)));
        var detail = new Label();
        detail.addClass("endpoint-table-reason");
        detail.layout(style -> style.widthPercent(100).height(20));
        detail.textStyle(style -> style.fontSize(7).textWrap(TextWrap.WRAP));
        detail.setText(reason(endpoint));
        button.addChild(detail);
        var tooltip = new java.util.ArrayList<Component>();
        tooltip.add(Component.literal(endpoint.get("endpointIdentity").getAsString()));
        tooltip.add(position(endpoint));
        tooltip.add(modes(endpoint));
        tooltip.add(reason(endpoint));
        tooltip.add(endpoint.has("owner") ? FederationWorkspace.tr("target_owner", endpoint.get("owner").getAsString(),
                endpoint.get("ownerInstance").getAsLong()) : FederationWorkspace.tr("unclaimed"));
        button.style(style -> style.tooltips(tooltip.toArray(Component[]::new)));
        button.setOnClick(event -> { select.accept("endpoint:" + endpoint.get("id").getAsString()); dialog.close(); });
        return button;
    }
}
