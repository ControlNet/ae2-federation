package space.controlnet.ae2federation.client.menu;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.automation.LibraryRows;
import space.controlnet.ae2federation.client.automation.CompletionPopup;
import space.controlnet.ae2federation.client.automation.PythonLanguage;
import space.controlnet.ae2federation.client.automation.ScriptEditor;
import space.controlnet.ae2federation.script.Palette;
import space.controlnet.ae2federation.script.Signature;
import space.controlnet.ae2federation.script.StandardLibrary;

/**
 * The Automation tab's Library panel, under the programs: the statements, operators, built-ins, values, the program's
 * own functions and the standard library's namespaces ({@link Palette}), each group opened with a click, and a search
 * over them ({@link LibraryRows}; user decision, 2026-10-23, board {@code PaletteLibrary}). A library function opens its
 * page in the same box, with a back chip in the head: its qualified name, its signature, whether it acts, its summary
 * and its example (checked by a test) in a small read-only editor, as the {@code AutoLibraryDoc} board shows it. Any
 * entry's row drags onto the graph with its card under the pointer, and becomes a draft card where it is dropped
 * ({@link FederationGraphView}).
 */
final class FederationLibraryPanel {
    /** The example's lines, and its box's padding with the editor's, as the style sheet sizes them. */
    private static final float EXAMPLE_LINE = 9;
    private static final float EXAMPLE_PAD = 7;
    /** How far the pressed pointer moves on a function's row before it drags, as on the Install view. */
    private static final float DRAG_THRESHOLD = 3;

    private final TextField search;
    private final ScrollerView tree;
    private final ScrollerView page;
    private final Button back;
    private final Set<String> open = new HashSet<>();
    private final FederationGraphView graphs;
    /** The program's own functions as last listed, so the rows are built again only when they change. */
    private List<Palette.Entry> mine = List.of();
    /** The function row pressed, which drags once the pointer moves. */
    private @Nullable UIElement pressed;
    private @Nullable FederationGraphView.LibraryDrag pressedDrag;
    private float pressX;
    private float pressY;

    FederationLibraryPanel(UI ui, FederationGraphView graphs) {
        this.graphs = graphs;
        search = element(ui, "auto_library_search", TextField.class);
        tree = element(ui, "auto_library_tree", ScrollerView.class);
        page = element(ui, "auto_library_doc", ScrollerView.class);
        back = element(ui, "auto_library_back", Button.class);
        search.textFieldStyle(style -> style.placeholder(
                Component.translatable("ae2federation.ui.automation.library_search")));
        search.setTextResponder(text -> rebuild());
        // On the click, not the press: the chip hides with the page.
        back.addEventListener(UIEvents.CLICK, event -> showList());
        // Any press or release forgets the pressed row, so only its own press drags.
        ui.rootElement.addEventListener(UIEvents.MOUSE_DOWN, event -> pressed = null, true);
        ui.rootElement.addEventListener(UIEvents.MOUSE_UP, event -> pressed = null, true);
        ui.rootElement.addEventListener(UIEvents.MOUSE_MOVE, event -> {
            var row = pressed;
            if (row == null || Math.abs(event.x - pressX) + Math.abs(event.y - pressY) < DRAG_THRESHOLD) return;
            pressed = null;
            var drag = pressedDrag;
            row.startDrag(drag, null);
            var modular = row.getModularUI();
            if (modular != null && drag != null) graphs.ghost(drag.entry(), modular.getDragHandler());
        }, true);
        graphs.onPalette(() -> {
            var now = mine(graphs.palette());
            if (now.equals(mine)) return;
            mine = now;
            if (tree.isDisplayed()) rebuild();
        });
        showList();
    }

    private void showList() {
        page.setDisplay(false);
        back.setDisplay(false);
        search.setDisplay(true);
        tree.setDisplay(true);
        rebuild();
    }

    private static List<Palette.Entry> mine(List<Palette.Group> groups) {
        return groups.stream().filter(group -> group.key().equals(Palette.MINE)).findFirst()
                .map(Palette.Group::entries).orElse(List.of());
    }

    private void rebuild() {
        tree.clearAllScrollViewChildren();
        for (var row : LibraryRows.rows(graphs.palette(), search.getText(), open)) {
            tree.addScrollViewChild(row.entry() == null ? groupRow(row) : entryRow(row));
        }
    }

    private UIElement groupRow(LibraryRows.Row row) {
        var element = new UIElement();
        element.addClass("library-row");
        element.setId("auto_library_ns_" + row.group());
        element.addChild(label("library-arrow", Component.literal(row.open() ? "\u25bc" : "\u25b6")));
        // A namespace is named as the script writes it; the groups above them in the player's language.
        element.addChild(label("program-name", row.namespace() ? Component.literal(row.group())
                : Component.translatable("ae2federation.ui.automation.group." + row.group())));
        element.addChild(label("library-count", Component.literal(String.valueOf(row.count()))));
        element.addEventListener(UIEvents.CLICK, event -> {
            // A search shows every group with matches open; clicking one then does nothing.
            if (!search.getText().isBlank()) return;
            if (!open.remove(row.group())) open.add(row.group());
            rebuild();
        });
        return element;
    }

    private UIElement entryRow(LibraryRows.Row row) {
        var element = new UIElement();
        element.addClass("library-row");
        element.addClass("library-function");
        element.setId("auto_library_fn_" + row.group() + "_" + idPart(row.function()));
        element.addChild(label("program-name", Component.literal(row.function())));
        if (row.acts()) element.addChild(new UIElement().addClass(row.lasts() ? "library-lasts" : "library-acts"));
        // A library function has a page; the other entries say what they are by their names.
        if (row.namespace()) element.addEventListener(UIEvents.CLICK, event -> showPage(row.group(), row.function()));
        element.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button != 0) return;
            pressed = element;
            pressedDrag = new FederationGraphView.LibraryDrag(row.entry());
            pressX = event.x;
            pressY = event.y;
        });
        return element;
    }

    /** An entry's name as part of an element's ID: letters, digits and {@code _} kept, anything else by its code. */
    static String idPart(String name) {
        var id = new StringBuilder();
        name.chars().forEach(c -> {
            if (c < 128 && (Character.isLetterOrDigit(c) || c == '_')) id.append((char) c);
            else id.append('x').append(Integer.toHexString(c));
        });
        return id.toString();
    }

    private void showPage(String namespace, String name) {
        var function = StandardLibrary.NAMESPACES.getOrDefault(namespace, List.of()).stream()
                .filter(f -> f.name().equals(name)).findFirst().orElse(null);
        if (function == null) return;
        search.setDisplay(false);
        tree.setDisplay(false);
        // The layout file gives the chip a text: LDLib2 drops the label of a button whose text is empty.
        var text = Component.literal("\u2039 " + namespace);
        back.setText(text);
        int width = net.minecraft.client.Minecraft.getInstance().font.width(text) + 8;
        back.layout(layout -> layout.width(width));
        back.setDisplay(true);
        page.clearAllScrollViewChildren();
        page.addScrollViewChild(label("library-text", Component.literal(namespace + ".")
                .withColor(PythonLanguage.NAMESPACE).append(Component.literal(name).withColor(CompletionPopup.NAME))));
        page.addScrollViewChild(label("library-text signature", Component.literal(function.display())));
        if (function.effect()) {
            var lasts = StandardLibrary.lasts(function);
            var acts = new UIElement();
            acts.addClass("library-acts-row");
            acts.addChild(new UIElement().addClass(lasts ? "library-lasts" : "library-acts"));
            acts.addChild(label("library-text", Component.translatable(lasts
                    ? "ae2federation.ui.automation.lasts" : "ae2federation.ui.automation.acts")));
            page.addScrollViewChild(acts);
        }
        page.addScrollViewChild(label("library-text", Component.translatableWithFallback(
                StandardLibrary.summaryKey(function), function.summary())));
        page.addScrollViewChild(label("library-text heading", Component.translatable("ae2federation.ui.automation.example")));
        page.addScrollViewChild(example(function));
        page.setDisplay(true);
    }

    private static UIElement example(Signature function) {
        var lines = function.example().stripTrailing().split("\n");
        var editor = new ScriptEditor();
        editor.setNumbered(false);
        editor.setReadOnlyLines(lines);
        var box = new UIElement();
        box.addClass("library-example");
        // The editor fills the box, as the script's fills its own; the box takes the example's height.
        box.layout(layout -> layout.height(lines.length * EXAMPLE_LINE + EXAMPLE_PAD));
        box.addChild(editor);
        return box;
    }

    private static Label label(String classes, Component text) {
        var label = new Label();
        for (var type : classes.split(" ")) label.addClass(type);
        label.setText(text);
        return label;
    }

    private static <T> T element(UI ui, String id, Class<T> type) {
        return ui.selectId(id, type).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing UI element #" + id));
    }
}
