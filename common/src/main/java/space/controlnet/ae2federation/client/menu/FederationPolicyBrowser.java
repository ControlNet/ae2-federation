package space.controlnet.ae2federation.client.menu;

import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;

/** A configured-rule browser; selection is one server-authorized change of the complete policy key. */
final class FederationPolicyBrowser {
    private final UI ui;
    private final Consumer<String> select;
    private List<JsonObject> rules = List.of();
    private Dialog dialog;
    private VirtualScrollerView<JsonObject> list;
    private Label empty;
    private String query = "";
    private String selectedKey = "";

    FederationPolicyBrowser(UI ui, Consumer<String> select) {
        this.ui = ui;
        this.select = select;
    }

    void accept(JsonObject root) {
        var values = root.getAsJsonArray("rules");
        var selected = root.getAsJsonObject("selected");
        var nextKey = selected.has("consumer") ? key(selected) : "";
        var replacement = values == null ? List.<JsonObject>of()
                : values.asList().stream().map(value -> value.getAsJsonObject()).toList();
        if (replacement.equals(rules) && nextKey.equals(selectedKey)) return;
        rules = replacement;
        selectedKey = nextKey;
        refresh();
    }

    void open() {
        if (dialog != null) return;
        query = "";
        dialog = new Dialog().darkenBackground();
        dialog.setId("policy_browser");
        dialog.overlay.layout(style -> style.width(Math.min(360, ui.rootElement.getContentWidth() - 12))
                .height(Math.min(Math.max(130, 88 + Math.min(6, rules.size()) * 30), ui.rootElement.getContentHeight() - 12)));
        dialog.overlay.style(style -> style.backgroundTexture(FederationTheme.FRAME));
        dialog.titleBar.style(style -> style.backgroundTexture(new ColorRectTexture(FederationTheme.INSET)));
        dialog.contentContainer.layout(style -> style.flex(1).minHeight(0).gapAll(4));
        dialog.contentContainer.style(style -> style.backgroundTexture(new ColorRectTexture(FederationTheme.PANEL)));
        var search = new TextField();
        search.setId("policy_rule_search");
        search.layout(style -> style.widthPercent(100).height(18).flexShrink(0));
        search.textFieldStyle(style -> style.placeholder(FederationWorkspace.tr("rules_search").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)));
        search.setTextResponder(value -> { query = value.toLowerCase(Locale.ROOT); refresh(); });
        list = new VirtualScrollerView<>();
        list.setId("policy_rule_list");
        list.layout(style -> style.widthPercent(100).flex(1).minHeight(0));
        list.virtualScrollerViewStyle(style -> style.estimatedItemHeight(30));
        list.viewPort.style(style -> style.backgroundTexture(FederationTheme.FIELD));
        list.setItemUIProvider(this::row);
        empty = new Label();
        empty.setId("policy_rule_empty");
        empty.layout(style -> style.widthPercent(100));
        empty.textStyle(style -> style.adaptiveHeight(true).textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.WRAP));
        dialog.addContent(search).addContent(empty).addContent(list);
        dialog.buttonContainer.style(style -> style.backgroundTexture(new ColorRectTexture(FederationTheme.PANEL)));
        var close = new Button();
        close.layout(style -> style.widthPercent(100).height(20));
        close.setId("policy_browser_close");
        close.setText(FederationWorkspace.tr("rules_close"));
        close.setOnClick(event -> dialog.close());
        dialog.addButton(close);
        dialog.setOnClose(() -> { dialog = null; list = null; empty = null; });
        refresh();
        dialog.show(ui.rootElement);
    }

    private void refresh() {
        if (dialog == null) return;
        dialog.setTitle(FederationWorkspace.tr("rules_title", rules.size()).getString());
        var visible = rules.stream().filter(rule -> query.isEmpty()
                || (key(rule) + " " + summary(rule).getString()).toLowerCase(Locale.ROOT).contains(query)).toList();
        list.setItems(visible);
        list.setDisplay(!visible.isEmpty());
        empty.setDisplay(visible.isEmpty());
        empty.setText(FederationWorkspace.tr(rules.isEmpty() ? "rules_none" : "rules_no_matches"));
    }

    private static String key(JsonObject rule) {
        return rule.get("consumer").getAsString() + "/" + rule.get("provider").getAsString()
                + "/" + rule.get("capability").getAsString();
    }

    private Component summary(JsonObject rule) {
        return FederationWorkspace.tr("rules_summary",
                FederationWorkspace.tr("capability." + rule.get("capability").getAsString().toLowerCase(Locale.ROOT)),
                Component.translatable("ae2federation.ui.domain.rule." + (rule.get("enabled").getAsBoolean() ? "on" : "off")),
                rule.get("revision").getAsLong());
    }

    private UIElement row(JsonObject rule) {
        var consumer = rule.get("consumer").getAsString();
        var provider = rule.get("provider").getAsString();
        var button = new Button();
        button.addClass("policy-rule-row");
        if (key(rule).equals(selectedKey)) button.addClass("selected");
        button.layout(style -> style.height(30));
        button.text.textStyle(style -> style.fontSize(7));
        button.setText(summary(rule).copy().append("\n" + consumer.substring(0, 12) + " → " + provider.substring(0, 12)));
        button.style(style -> style.tooltips(summary(rule), Component.literal(consumer + "\n→ " + provider)));
        button.setOnClick(event -> {
            select.accept("policy:" + key(rule));
            dialog.close();
        });
        return button;
    }
}
