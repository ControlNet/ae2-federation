package space.controlnet.ae2federation.client.menu;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import space.controlnet.ae2federation.automation.LibraryRows;
import space.controlnet.ae2federation.script.Palette;

/**
 * The graph's Add menu: a right-click on the canvas opens it at the pointer, with a search and the Library's groups
 * ({@link LibraryRows}), opened with a click as there. Picking an entry, or Enter on the first match, drops its draft
 * card where the menu was opened; Escape, or a press outside it, closes it (user decision, 2026-10-23, board
 * {@code PaletteMenu}).
 */
final class FederationAddMenu {
    private static final int WIDTH = 150;
    private static final int HEIGHT = 140;

    /** What picking an entry does: drop its draft at a point of the screen. */
    interface Picked {
        void pick(Palette.Entry entry, float mouseX, float mouseY);
    }

    private final UIElement box;
    private final UIElement menu;
    private final TextField search;
    private final ScrollerView list;
    private final Supplier<List<Palette.Group>> palette;
    private final Picked picked;
    private final Set<String> open = new HashSet<>();
    /** Where on the screen the menu was opened, which is where a picked entry's draft goes. */
    private float atX;
    private float atY;

    FederationAddMenu(UIElement box, Supplier<List<Palette.Group>> palette, Picked picked) {
        this.box = box;
        this.palette = palette;
        this.picked = picked;
        menu = new UIElement();
        menu.setId("auto_graph_add");
        menu.addClass("graph-add-menu");
        menu.style(style -> style.backgroundTexture(FederationTheme.painted((pen, x, y, w, h) -> {
            pen.rect(x, y, w, h, 0xff9cd3ff);
            pen.rect(x + 1, y + 1, w - 2, h - 2, 0xff17141e);
        })));
        search = new TextField();
        search.setId("auto_graph_add_search");
        search.addClass("auto-library-search");
        search.textFieldStyle(style -> style.placeholder(
                Component.translatable("ae2federation.ui.automation.library_search")));
        search.setTextResponder(text -> rebuild());
        search.addEventListener(UIEvents.KEY_DOWN, event -> {
            if (event.keyCode == GLFW.GLFW_KEY_ESCAPE) close();
            if (event.keyCode == GLFW.GLFW_KEY_ENTER || event.keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                LibraryRows.rows(palette.get(), search.getText(), open).stream()
                        .filter(row -> row.entry() != null).findFirst().ifPresent(row -> pick(row.entry()));
            }
        });
        list = new ScrollerView();
        list.addClass("auto-list");
        menu.addChild(search);
        menu.addChild(list);
        menu.setDisplay(false);
        box.addChild(menu);
        // A press anywhere else closes it; its own rows take theirs.
        box.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (menu.isDisplayed() && !inside(event.target)) close();
        }, true);
    }

    boolean isOpen() {
        return menu.isDisplayed();
    }

    /** Opens the menu with its corner at a point of the screen, kept inside the graph's box. */
    void open(float mouseX, float mouseY) {
        atX = mouseX;
        atY = mouseY;
        float left = Math.max(0, Math.min(mouseX - box.getPositionX(), box.getSizeWidth() - WIDTH));
        float top = Math.max(0, Math.min(mouseY - box.getPositionY(), box.getSizeHeight() - HEIGHT));
        menu.layout(style -> style.positionType(TaffyPosition.ABSOLUTE).left(left).top(top).width(WIDTH)
                .height(HEIGHT));
        open.clear();
        search.setText("", false);
        rebuild();
        menu.setDisplay(true);
        search.focus();
    }

    void close() {
        menu.setDisplay(false);
    }

    private boolean inside(UIElement element) {
        for (var at = element; at != null; at = at.getParent()) {
            if (at == menu) return true;
        }
        return false;
    }

    private void pick(Palette.Entry entry) {
        close();
        picked.pick(entry, atX, atY);
    }

    private void rebuild() {
        list.clearAllScrollViewChildren();
        for (var row : LibraryRows.rows(palette.get(), search.getText(), open)) {
            var element = new UIElement();
            element.addClass("library-row");
            if (row.entry() == null) {
                element.setId("auto_graph_add_group_" + row.group());
                element.addChild(label("library-arrow", Component.literal(row.open() ? "▼" : "▶")));
                element.addChild(label("program-name", row.namespace() ? Component.literal(row.group())
                        : Component.translatable("ae2federation.ui.automation.group." + row.group())));
                element.addChild(label("library-count", Component.literal(String.valueOf(row.count()))));
                element.addEventListener(UIEvents.CLICK, event -> {
                    if (!search.getText().isBlank()) return;
                    if (!open.remove(row.group())) open.add(row.group());
                    rebuild();
                });
            } else {
                var entry = row.entry();
                element.addClass("library-function");
                element.setId("auto_graph_add_" + row.group() + "_" + FederationLibraryPanel.idPart(entry.name()));
                element.addChild(label("program-name", Component.literal(entry.name())));
                if (entry.acts()) {
                    element.addChild(new UIElement().addClass(entry.lasts() ? "library-lasts" : "library-acts"));
                }
                element.addEventListener(UIEvents.CLICK, event -> pick(entry));
            }
            list.addScrollViewChild(element);
        }
    }

    private static Label label(String type, Component text) {
        var label = new Label();
        label.addClass(type);
        label.setText(text);
        return label;
    }
}
