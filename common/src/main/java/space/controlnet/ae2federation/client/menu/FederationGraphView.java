package space.controlnet.ae2federation.client.menu;

import com.lowdragmc.lowdraglib2.client.shader.LDLibRenderTypes;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.GraphView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import space.controlnet.ae2federation.automation.LibraryRows;
import space.controlnet.ae2federation.client.automation.CompletionPopup;
import space.controlnet.ae2federation.client.automation.ScriptChecker;
import space.controlnet.ae2federation.client.policy.WireCurve;
import space.controlnet.ae2federation.function.Canvas;
import space.controlnet.ae2federation.function.Expr;
import space.controlnet.ae2federation.function.GraphCards;
import space.controlnet.ae2federation.function.Layout;
import space.controlnet.ae2federation.function.ScriptPrinter;
import space.controlnet.ae2federation.function.Signature;
import space.controlnet.ae2federation.function.StandardLibrary;
import space.controlnet.ae2federation.function.TypeRef;

/**
 * The Automation tab's graph view of the shown program, as the {@code AutoGraph} board draws it: a tab per function,
 * the function's variables in a strip, and its cards on a canvas that pans and zooms, placed by {@link Layout}. The
 * cards come from the script, checked on the client worker ({@link ScriptChecker#graph}); the script stays the only
 * stored form. A script that does not parse has no graph, so Graph is inactive until it does.
 *
 * <p>A literal or a variable on a card is edited in place: a click opens a field, and Enter or leaving it writes what
 * is typed into the script ({@link GraphCards#put}), which is checked and drawn again as any edit is.
 *
 * <p>A function dragged from the Library becomes a dashed draft card where it is dropped, kept on the client only and
 * lost with the screen (user decision, 2026-10-19). Dragging its value pin onto a card's input port, or its exec pin
 * onto a card's exec output, writes the call there ({@link GraphCards#putCall}, {@link GraphCards#insertCall}). A
 * card's own pins drag the same way: a value onto another port ({@link GraphCards#move}), a statement onto another
 * exec output ({@link GraphCards#relocate}). The × in a card's head removes it ({@link GraphCards#remove}; user
 * decision, 2026-10-19), and a draft's discards the draft.
 */
final class FederationGraphView {
    /** A card's geometry in GUI units, as the board draws it: its head, the first row's centre and the row pitch. */
    private static final int HEAD = 11;
    private static final int FIRST = 19;
    private static final int ROW = 12;
    /** A card's height without rows; each row adds {@link #ROW}. */
    private static final int BASE = 17;
    private static final int MIN_WIDTH = 40;
    /** A row's label starts here; its value follows after a gap; the card ends this far after its widest row. */
    private static final int LABEL_X = 5;
    private static final int GAP = 3;
    private static final int END = 6;
    /** Between an input row and the output label on the same line. */
    private static final int APART = 10;
    /** The canvas's margin around the cards. */
    private static final int MARGIN = 10;
    /** A function opens zoomed out to show it whole, but never below this, where the pixel font blurs. */
    private static final float MIN_OPEN_SCALE = 0.75f;
    /** Longer literals are cut on the card. */
    private static final int MAX_TEXT = 24;

    private static final int FACE = 0xff2c2735;
    private static final int EDGE = 0xff4a4655;
    private static final int TITLE = 0xff3a3646;
    private static final int ACTS = 0xff4b2f73;
    private static final int PORT_EDGE = 0xff121016;
    private static final int EXEC = FederationTheme.DARK_TITLE;
    private static final int LABEL = FederationTheme.DARK_MUTED;
    private static final int FIELD_TEXT = FederationTheme.TEXT;
    /** A draft's dashed edge and see-through face, as the {@code AutoLibraryDrag} board draws it. */
    private static final int DRAFT_EDGE = 0xff9cd3ff;
    private static final int DRAFT_FACE = 0xb32c2735;
    private static final int DRAFT_HEAD = 0xb33a3646;
    /** A pin's square that takes a press or a drop, centred on the card's edge where the pin is drawn. */
    private static final int PIN = 8;
    /** The remove button in a card's head: its square, and the room it takes after the title. */
    private static final int CLOSE = 9;
    private static final int CLOSE_ROOM = 9;
    /** How far the pressed pointer moves on a draft's pin before it drags a wire, as on the Install view. */
    private static final float DRAG_THRESHOLD = 3;

    /** A Library function being dragged onto the canvas. */
    record LibraryDrag(String namespace, String function) {
    }

    /** A dropped Library function not yet in the script: its card's top left on the canvas, per shown function. */
    private record Draft(int number, String namespace, Signature function, float x, float y) {
        boolean execIn() {
            return function.effect() || function.result().name().equals("None");
        }

        boolean valueOut() {
            return !function.result().name().equals("None");
        }

        List<Signature.Arg> rows() {
            return function.args().stream().filter(arg -> arg.defaultValue() == null).toList();
        }
    }

    /**
     * A wire dragged from an exec pin or a value pin: a draft's, or a card's ({@code card}, the statement or value
     * it moves).
     */
    private record PinDrag(@Nullable Draft draft, @Nullable String card, boolean exec, int color) {
    }

    private final GraphView graph;
    private final UIElement script;
    private final UIElement box;
    private final UIElement functionTabs;
    private final UIElement variables;
    private final Button scriptChip;
    private final Button graphChip;
    private final Consumer<Boolean> switched;
    /** The editor's text, and where an edited text goes: into the editor, as typing there would. */
    private final Supplier<String> text;
    private final Consumer<String> write;
    /** Where a refused edit says why: the status line. */
    private final Consumer<String> refuse;
    /** The text the shown graphs were drawn from; an edit applies only to it. */
    private String drawnFrom = "";
    private boolean shown;
    private boolean parses;
    private Map<String, ScriptChecker.Graph> graphs = Map.of();
    private @Nullable String function;
    /** Set when another function is shown, so the canvas starts again at its top left. */
    private boolean recentre = true;
    /** The shown function's canvas size, margins included; zero before one is drawn. */
    private int extentX;
    private int extentY;
    private final Map<String, List<Draft>> drafts = new HashMap<>();
    private int drafted;
    /** The draft pin pressed, which drags a wire once the pointer moves. */
    private @Nullable UIElement pressed;
    private @Nullable PinDrag pressedDrag;
    private float pressX;
    private float pressY;

    FederationGraphView(UI ui, Consumer<Boolean> switched, Supplier<String> text, Consumer<String> write,
            Consumer<String> refuse) {
        this.switched = switched;
        this.text = text;
        this.write = write;
        this.refuse = refuse;
        graph = element(ui, "auto_graph", GraphView.class);
        script = element(ui, "auto_script", UIElement.class);
        box = element(ui, "auto_graph_box", UIElement.class);
        functionTabs = element(ui, "auto_function_tabs", UIElement.class);
        variables = element(ui, "auto_variables", UIElement.class);
        scriptChip = element(ui, "auto_view_script", Button.class);
        graphChip = element(ui, "auto_view_graph", Button.class);
        scriptChip.setOnClick(event -> setShown(false));
        graphChip.setOnClick(event -> setShown(true));
        graphChip.setActive(false);
        graph.addEventListener(UIEvents.TICK, event -> recentre());
        // A Library function dropped anywhere on the canvas, over a card or not, becomes a draft there.
        graph.addEventListener(UIEvents.DRAG_PERFORM, event -> {
            if (event.dragHandler.draggingObject instanceof LibraryDrag drag) drop(drag, event.x, event.y);
        });
        // Any press or release forgets the pressed pin, so only its own press drags.
        ui.rootElement.addEventListener(UIEvents.MOUSE_DOWN, event -> pressed = null, true);
        ui.rootElement.addEventListener(UIEvents.MOUSE_UP, event -> pressed = null, true);
        ui.rootElement.addEventListener(UIEvents.MOUSE_MOVE, event -> {
            var pin = pressed;
            if (pin == null || Math.abs(event.x - pressX) + Math.abs(event.y - pressY) < DRAG_THRESHOLD) return;
            pressed = null;
            pin.startDrag(pressedDrag, null);
        }, true);
        apply();
    }

    boolean shown() {
        return shown;
    }

    /** Shows the graph, or the script; the graph only while the script parses. */
    void setShown(boolean shown) {
        shown = shown && parses;
        if (this.shown == shown) return;
        this.shown = shown;
        apply();
        if (shown) redraw();
        switched.accept(shown);
    }

    private void apply() {
        script.setDisplay(!shown);
        box.setDisplay(shown);
        functionTabs.setDisplay(shown);
        scriptChip.removeClass("selected");
        graphChip.removeClass("selected");
        (shown ? graphChip : scriptChip).addClass("selected");
    }

    /** Another program is opened: nothing to draw until its script has been checked. */
    void clear() {
        graphs = Map.of();
        drafts.clear();
        function = null;
        recentre = true;
        parses = false;
        graphChip.setActive(false);
        if (shown) setShown(false);
    }

    /** The worker's graphs of {@code from}, the shown script, or nothing when it does not parse. */
    void show(String from, Optional<Map<String, ScriptChecker.Graph>> answer) {
        // Every index from the server checks the script again; drawing the same text again would close an open field.
        boolean same = parses && answer.isPresent() && from.equals(drawnFrom);
        drawnFrom = from;
        parses = answer.isPresent();
        graphChip.setActive(parses);
        if (!parses) {
            setShown(false);
            return;
        }
        graphs = answer.get();
        if (function == null || !graphs.containsKey(function)) {
            function = graphs.keySet().stream().findFirst().orElse(null);
            recentre = true;
        }
        if (shown && !same) redraw();
    }

    /** Shows {@code name}'s graph, as clicking its tab does. */
    void showFunction(String name) {
        if (!graphs.containsKey(name)) return;
        if (!name.equals(function)) recentre = true;
        function = name;
        redraw();
    }

    private void redraw() {
        rebuildTabs();
        var shownGraph = function == null ? null : graphs.get(function);
        rebuildVariables(shownGraph == null ? List.of() : shownGraph.variables());
        graph.clearAllContentChildren();
        extentX = 0;
        if (shownGraph == null) return;
        var font = net.minecraft.client.Minecraft.getInstance().font;
        var drawn = new HashMap<String, Drawn>();
        for (var card : shownGraph.cards()) drawn.put(card.id(), measure(card, font));
        var boxes = Layout.of(shownGraph.canvas(), new Layout.Metrics() {
            public int width(Layout.Card card) {
                return drawn.get(card.id()).width();
            }

            public int height(Layout.Card card) {
                return drawn.get(card.id()).height();
            }

            public int inputY(Layout.Card card, int row) {
                return drawn.get(card.id()).rowY(row);
            }

            public int outputY(Layout.Card card) {
                return FIRST;
            }
        });
        int right = 0;
        int bottom = 0;
        for (var box : boxes.values()) {
            right = Math.max(right, box.right());
            bottom = Math.max(bottom, box.bottom());
        }
        var wires = new Wires(wires(shownGraph, drawn, boxes), right + 2 * MARGIN, bottom + 2 * MARGIN);
        graph.addContentChild(wires);
        for (var card : shownGraph.cards()) {
            var place = boxes.get(card.id());
            graph.addContentChild(new CardElement(drawn.get(card.id()), place.x() + MARGIN, place.y() + MARGIN,
                    (row, typed) -> put(shownGraph, row, typed), () -> remove(shownGraph, card.id())));
        }
        for (var draft : drafts.getOrDefault(function, List.of())) graph.addContentChild(new DraftElement(draft));
        extentX = right + 2 * MARGIN;
        extentY = bottom + 2 * MARGIN;
    }

    /** Opens a newly shown function whole, from its top left, once the view has its size: it has none while hidden. */
    private void recentre() {
        float viewWidth = graph.getContentWidth();
        float viewHeight = graph.getContentHeight();
        if (!recentre || extentX == 0 || viewWidth <= 0 || viewHeight <= 0) return;
        float scale = Math.max(MIN_OPEN_SCALE, Math.min(1, Math.min(viewWidth / extentX, viewHeight / extentY)));
        // Fitting a region the view's size over the scale gives that scale with the top left in the corner.
        graph.fit(0, 0, viewWidth / scale, viewHeight / scale, scale);
        recentre = false;
    }

    /** Writes a value typed on a card into the script, or says on the status line why it cannot be. */
    private void put(ScriptChecker.Graph shownGraph, GraphCards.Row row, String typed) {
        if (!text.get().equals(drawnFrom)) return;
        try {
            write.accept(GraphCards.put(drawnFrom, shownGraph.canvas(), row.node(), row.port(), row.type(), typed));
        } catch (IllegalArgumentException e) {
            refuse.accept(e.getMessage());
        }
    }

    /** Removes a card from the script, or says on the status line why it cannot be. */
    private void remove(ScriptChecker.Graph shownGraph, String card) {
        if (!text.get().equals(drawnFrom)) return;
        try {
            write.accept(GraphCards.remove(drawnFrom, shownGraph.canvas(), card));
        } catch (IllegalArgumentException e) {
            refuse.accept(e.getMessage());
        }
    }

    /** A Library function dropped at a point of the screen: a draft card with its head under the pointer. */
    private void drop(LibraryDrag drag, float mouseX, float mouseY) {
        var signature = LibraryRows.find(StandardLibrary.NAMESPACES, drag.namespace(), drag.function());
        if (function == null || signature == null) return;
        var point = graph.contentRoot.getLocalMouse(mouseX, mouseY);
        var draft = new Draft(drafted++, drag.namespace(), signature,
                point.x - graph.contentRoot.getPositionX() - 10, point.y - graph.contentRoot.getPositionY() - 5);
        drafts.computeIfAbsent(function, name -> new ArrayList<>()).add(draft);
        graph.addContentChild(new DraftElement(draft));
    }

    /**
     * A wire dropped on a card's pin: a value pin's on an input row ({@code row}), or an exec pin's on an exec output
     * ({@code output} of {@code card}). A draft's writes its call there and leaves the canvas; a card's moves its value
     * or its statement there. Dropped where it already is, nothing changes.
     */
    private void wired(UIEvent event, @Nullable GraphCards.Row row, @Nullable String card, @Nullable String output) {
        if (!(event.dragHandler.draggingObject instanceof PinDrag drag) || drag.exec() != (row == null)) return;
        var shownGraph = function == null ? null : graphs.get(function);
        if (shownGraph == null || !text.get().equals(drawnFrom)) return;
        var canvas = shownGraph.canvas();
        var draft = drag.draft();
        if (draft == null && (row != null ? row.input() instanceof Canvas.Input.Wire wire
                && wire.node().equals(drag.card()) : canvas.links().contains(new Canvas.Link(card, output, drag.card())))) {
            return;
        }
        try {
            String edited;
            if (draft == null) {
                edited = row != null ? GraphCards.move(drawnFrom, canvas, drag.card(), row.node(), row.port())
                        : GraphCards.relocate(drawnFrom, canvas, drag.card(), card, output);
            } else {
                edited = row != null
                        ? GraphCards.putCall(drawnFrom, canvas, row.node(), row.port(), draft.namespace(),
                                draft.function())
                        : GraphCards.insertCall(drawnFrom, canvas, card, output, draft.namespace(), draft.function());
                drafts.getOrDefault(function, new ArrayList<>()).remove(draft);
                graph.contentRoot.getChildren().stream()
                        .filter(child -> child instanceof DraftElement element && element.draft == draft).toList()
                        .forEach(graph::removeContentChild);
            }
            write.accept(edited);
        } catch (IllegalArgumentException e) {
            refuse.accept(e.getMessage());
        }
    }

    private void rebuildTabs() {
        functionTabs.clearAllChildren();
        for (var name : graphs.keySet()) {
            var tab = new Button();
            tab.addClass("kind-chip");
            tab.addClass("function-tab");
            tab.setId("auto_function_" + name);
            if (name.equals(function)) tab.addClass("selected");
            var text = Component.literal(name);
            tab.setText(text);
            int width = net.minecraft.client.Minecraft.getInstance().font.width(text) + 8;
            tab.layout(layout -> layout.width(width));
            // On the click, not the press: the tabs are built again.
            tab.addEventListener(UIEvents.CLICK, event -> {
                if (!name.equals(function)) showFunction(name);
            });
            functionTabs.addChild(tab);
        }
    }

    private void rebuildVariables(List<GraphCards.Variable> shownVariables) {
        variables.clearAllChildren();
        var title = new Label();
        title.addClass("auto-variables-title");
        title.setText(Component.translatable("ae2federation.ui.automation.variables"));
        variables.addChild(title);
        for (var variable : shownVariables) {
            var chip = new Label();
            chip.addClass("auto-variable");
            var text = Component.literal(variable.name() + (variable.type() == null ? "" : ": " + variable.type()));
            chip.setText(text);
            int width = net.minecraft.client.Minecraft.getInstance().font.width(text) + 8;
            int color = color(variable.type());
            chip.layout(layout -> layout.width(width));
            chip.style(style -> style.backgroundTexture(FederationTheme.painted((pen, x, y, w, h) ->
                    pill(pen, x, y, w, h, color))));
            variables.addChild(chip);
        }
    }

    /** A port's colour: the type of the value on it, as the board colours items, numbers and conditions. */
    static int color(@Nullable TypeRef type) {
        if (type == null) return LABEL;
        return switch (type.name()) {
            case "Item", "Fluid", "Resource" -> 0xfff7b13a;
            case "int", "float" -> 0xff5aa9ff;
            case "bool" -> FederationTheme.ERROR;
            case "str", "Side", "Capability", "RuleMode", "FilterMode" -> 0xff98c379;
            case "Interface", "Block" -> FederationTheme.REEXPORT;
            case "Network" -> 0xffc678dd;
            case "list", "Optional" -> type.args().isEmpty() ? LABEL : color(type.args().get(0));
            default -> LABEL;
        };
    }

    /** A chip with rounded corners: an edge in the variable's colour around the chip's face. */
    private static void pill(FederationTheme.Pen pen, float x, float y, float w, float h, int edge) {
        pen.rect(x + 1, y, w - 2, h, edge);
        pen.rect(x, y + 1, w, h - 2, edge);
        pen.rect(x + 2, y + 1, w - 4, h - 2, TITLE);
        pen.rect(x + 1, y + 2, w - 2, h - 4, TITLE);
    }

    /** What a row shows after its label: a variable's chip, or a literal in a field; nothing for a wire. */
    private record Value(String text, ItemStack icon, boolean chip, int color, int width) {
    }

    /** Where a row's value sits on its card, after its label. */
    private static float valueX(GraphCards.Row row) {
        return LABEL_X + net.minecraft.client.Minecraft.getInstance().font.width(row.label()) + GAP;
    }

    /** A card measured for drawing: its rows' values, its size, and where its first input row is. */
    private record Drawn(GraphCards.Card card, List<Value> values, int width, int height, int firstRow,
            boolean execIn) {
        int rowY(int row) {
            return FIRST + ROW * (firstRow + row);
        }

        int outputY(int output) {
            return FIRST + ROW * output;
        }
    }

    private static Drawn measure(GraphCards.Card card, net.minecraft.client.gui.Font font) {
        var values = new ArrayList<Value>();
        for (var row : card.rows()) values.add(value(row, font));
        boolean execIn = card.statement() && card.card().node().kind() != Canvas.Kind.ENTRY;
        int firstRow = execIn ? 1 : 0;
        int lines = Math.max(1, Math.max(firstRow + card.rows().size(), card.outputs().size()));
        boolean entry = card.card().node().kind() == Canvas.Kind.ENTRY;
        int width = Math.max(MIN_WIDTH, 4 + font.width(card.title()) + END + (entry ? 0 : CLOSE_ROOM));
        // Each line holds an input on the left (the exec pin, or a row) and an output's label on the right.
        for (int line = 0; line < lines; line++) {
            int row = line - firstRow;
            int left = 0;
            if (row >= 0 && row < card.rows().size()) {
                var value = values.get(row);
                left = LABEL_X + font.width(card.rows().get(row).label()) + (value == null ? 0 : GAP + value.width());
            }
            var output = line < card.outputs().size() ? card.outputs().get(line).label() : "";
            width = Math.max(width, left + (output.isEmpty() ? 0 : (left == 0 ? GAP : APART) + font.width(output)) + END);
        }
        return new Drawn(card, values, width, BASE + ROW * lines, firstRow, execIn);
    }

    private static @Nullable Value value(GraphCards.Row row, net.minecraft.client.gui.Font font) {
        int color = color(row.type());
        return switch (row.input()) {
            case Canvas.Input.Chip chip -> {
                var text = chip.name().name();
                yield new Value(text, ItemStack.EMPTY, true, color, font.width(text) + 8);
            }
            case Canvas.Input.Literal literal -> {
                var icon = literal.value() instanceof Expr.StrLit string && resource(row.type())
                        ? CompletionPopup.icon(string.value()) : ItemStack.EMPTY;
                var text = icon.isEmpty() ? cut(ScriptPrinter.expr(literal.value())) : cut(icon.getHoverName().getString());
                yield new Value(text, icon, false, color, font.width(text) + 6 + (icon.isEmpty() ? 0 : 10));
            }
            default -> null;
        };
    }

    private static boolean resource(@Nullable TypeRef type) {
        return type != null && (type.name().equals("Item") || type.name().equals("Fluid")
                || type.name().equals("Resource"));
    }

    private static String cut(String text) {
        return text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT - 3) + "...";
    }

    /** A wire between two points of the canvas, in a colour and a width. */
    private record Wire(float fromX, float fromY, float toX, float toY, int color, float width) {
    }

    /** Exec links from their output to the next card's top left; values from their output to the row they feed. */
    private static List<Wire> wires(ScriptChecker.Graph shownGraph, Map<String, Drawn> drawn,
            Map<String, Layout.Rect> boxes) {
        var wires = new ArrayList<Wire>();
        for (var link : shownGraph.canvas().links()) {
            var from = drawn.get(link.from());
            var to = boxes.get(link.to());
            if (from == null || to == null) continue;
            int output = from.card().outputs().stream().map(GraphCards.Output::name).toList().indexOf(link.output());
            if (output < 0) continue;
            var start = boxes.get(link.from());
            wires.add(new Wire(start.right() + MARGIN + 3, start.y() + MARGIN + from.outputY(output),
                    to.x() + MARGIN - 3, to.y() + MARGIN + FIRST, EXEC, 1.6f));
        }
        for (var card : shownGraph.cards()) {
            var d = drawn.get(card.id());
            var place = boxes.get(card.id());
            for (int i = 0; i < card.rows().size(); i++) {
                if (!(card.rows().get(i).input() instanceof Canvas.Input.Wire wire)) continue;
                var source = boxes.get(wire.node());
                if (source == null) continue;
                wires.add(new Wire(source.right() + MARGIN + 2, source.y() + MARGIN + FIRST, place.x() + MARGIN - 3,
                        place.y() + MARGIN + d.rowY(i), color(card.rows().get(i).type()), 1.2f));
            }
        }
        return wires;
    }

    /** The wires, behind the cards; it spans the canvas and takes no presses, which pan the view. */
    private static final class Wires extends UIElement {
        private final List<Wire> wires;

        Wires(List<Wire> wires, int width, int height) {
            this.wires = wires;
            layout(style -> style.positionType(TaffyPosition.ABSOLUTE).left(0).top(0).width(width).height(height));
            setAllowHitTest(false);
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            float x = getPositionX();
            float y = getPositionY();
            for (var wire : wires) {
                DrawerHelper.drawTexLines(context.graphics, LDLibRenderTypes.graphWire(), FederationProcessingGraph
                        .polyline(new WireCurve(x + wire.fromX(), y + wire.fromY(), x + wire.toX(), y + wire.toY())),
                        wire.color(), wire.color(), wire.width());
            }
        }
    }

    /** A pin's square centred on {@code (x, y)} of its card; it may stick out of the card, as the pin drawn does. */
    private static UIElement pin(String id, float x, float y) {
        var pin = new UIElement();
        pin.setId(id);
        pin.addClass("graph-pin");
        pin.layout(style -> style.positionType(TaffyPosition.ABSOLUTE).left(x - PIN / 2f).top(y - PIN / 2f).width(PIN)
                .height(PIN));
        return pin;
    }

    /** A press on {@code pin} that drags a wire once the pointer moves. */
    private UIElement source(UIElement pin, PinDrag drag) {
        pin.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button != 0) return;
            pressed = pin;
            pressedDrag = drag;
            pressX = event.x;
            pressY = event.y;
        });
        return pin;
    }

    /** The wire being dragged from {@code element}'s pin at {@code (x, y)}, dashed to the pointer; none otherwise. */
    @OnlyIn(Dist.CLIENT)
    private static void dragged(UIElement element, GUIContext context, java.util.function.Predicate<PinDrag> from,
            float x, float y) {
        var ui = element.getModularUI();
        if (ui == null || !ui.getDragHandler().isDragging()
                || !(ui.getDragHandler().draggingObject instanceof PinDrag drag) || !from.test(drag)) {
            return;
        }
        var mouse = element.getLocalMouse(ui.getLastMouseX(), ui.getLastMouseY());
        // An exec wire comes into its statement from the output it is dragged to; a value wire leaves its value.
        var curve = drag.exec() ? new WireCurve(mouse.x, mouse.y, x, y) : new WireCurve(x, y, mouse.x, mouse.y);
        FederationProcessingGraph.dashed(context, FederationProcessingGraph.polyline(curve), drag.color());
    }

    /** Whether an exec wire is being dragged, so the cards show where it may go. */
    private static boolean execDragged(UIElement element) {
        var ui = element.getModularUI();
        return ui != null && ui.getDragHandler().isDragging()
                && ui.getDragHandler().draggingObject instanceof PinDrag drag && drag.exec();
    }

    /** The × in the head of a card {@code width} wide, which removes it on a click. */
    private static UIElement closer(String id, int width, Runnable remove) {
        var close = new UIElement();
        close.setId(id);
        close.addClass("graph-remove");
        close.layout(style -> style.positionType(TaffyPosition.ABSOLUTE).left(width - CLOSE - 1).top(1).width(CLOSE)
                .height(CLOSE));
        // On the click, not the press: the card goes with it.
        close.addEventListener(UIEvents.CLICK, event -> remove.run());
        return close;
    }

    /** An × of five pixels at {@code (x, y)}, red while {@code close} is hovered. */
    @OnlyIn(Dist.CLIENT)
    private static void cross(net.minecraft.client.gui.GuiGraphics graphics, @Nullable UIElement close, float x,
            float y) {
        if (close == null) return;
        int color = close.isHover() ? FederationTheme.ERROR : LABEL;
        for (int i = 0; i < 5; i++) {
            DrawerHelper.drawSolidRect(graphics, x + i, y + i, 1, 1, color);
            DrawerHelper.drawSolidRect(graphics, x + 4 - i, y + i, 1, 1, color);
        }
    }

    /** One card, drawn whole: face, head and title, its pins and ports, and each row's label and value. */
    private final class CardElement extends UIElement {
        private final Drawn drawn;
        private final @Nullable UIElement close;
        /** After an if or a loop with nothing after it, its {@code next}: shown only while an exec wire is dragged. */
        private final boolean openNext;

        CardElement(Drawn drawn, int x, int y, BiConsumer<GraphCards.Row, String> put, Runnable remove) {
            this.drawn = drawn;
            setId("auto_graph_card_" + drawn.card().id());
            addClass("graph-card");
            if (drawn.card().acts()) addClass("acts");
            layout(style -> style.positionType(TaffyPosition.ABSOLUTE).left(x).top(y).width(drawn.width())
                    .height(drawn.height()));
            // Only its values take presses; a press anywhere else reaches the canvas, which pans.
            setAllowHitTest(false);
            for (int i = 0; i < drawn.card().rows().size(); i++) {
                var row = drawn.card().rows().get(i);
                var value = drawn.values().get(i);
                if (value != null) addChild(editable(row, value, drawn.rowY(i), put));
                // Each port and exec output takes a dragged wire.
                var in = pin("auto_graph_in_" + row.node() + "_" + row.port().replace(':', '_'), 0, drawn.rowY(i));
                in.addEventListener(UIEvents.DRAG_PERFORM, event -> wired(event, row, null, null));
                addChild(in);
            }
            var card = drawn.card();
            var kind = card.card().node().kind();
            openNext = (kind == Canvas.Kind.IF || kind == Canvas.Kind.WHILE || kind == Canvas.Kind.FOR)
                    && card.outputs().stream().noneMatch(output -> output.name().equals("next"));
            int outputs = card.outputs().size() + (openNext ? 1 : 0);
            for (int i = 0; i < outputs; i++) {
                var output = i < card.outputs().size() ? card.outputs().get(i).name() : "next";
                var out = pin("auto_graph_out_" + card.id() + "_" + output, drawn.width(), drawn.outputY(i));
                out.addEventListener(UIEvents.DRAG_PERFORM, event -> wired(event, null, card.id(), output));
                addChild(out);
            }
            // A statement's exec pin drags it to another output; a value's pin wires it to another port.
            if (drawn.execIn()) {
                addChild(source(pin("auto_graph_exec_" + card.id(), 0, FIRST),
                        new PinDrag(null, card.id(), true, EXEC)));
            }
            if (card.result() != null) {
                addChild(source(pin("auto_graph_result_" + card.id(), drawn.width(), FIRST),
                        new PinDrag(null, card.id(), false, color(card.result()))));
            }
            // Every card but the function's own can be removed.
            close = kind == Canvas.Kind.ENTRY ? null : closer("auto_graph_remove_" + card.id(), drawn.width(), remove);
            if (close != null) addChild(close);
        }

        /** The value's place on the card: a click opens a field over it, and Enter or leaving it writes it. */
        private static UIElement editable(GraphCards.Row row, Value value, int rowY,
                BiConsumer<GraphCards.Row, String> put) {
            var area = new UIElement();
            area.setId("auto_graph_value_" + row.node() + "_" + row.port().replace(':', '_'));
            area.addClass("graph-value");
            float left = valueX(row);
            area.layout(style -> style.positionType(TaffyPosition.ABSOLUTE).left(left).top(rowY - 6)
                    .width(value.width()).height(11));
            var shown = GraphCards.editText(row.input(), row.type());
            // On the click, not the press: the field opens under the pointer.
            area.addEventListener(UIEvents.CLICK, event -> {
                if (area.getChildren().isEmpty()) open(area, shown, value.width(), typed -> put.accept(row, typed));
            });
            return area;
        }

        private static void open(UIElement area, String shown, int width, Consumer<String> send) {
            var field = new TextField();
            field.setId("auto_graph_field");
            field.addClass("graph-field");
            field.textFieldStyle(style -> style.placeholder(Component.empty()));
            int fieldWidth = Math.max(width, 60);
            field.layout(style -> style.positionType(TaffyPosition.ABSOLUTE).left(0).top(0).width(fieldWidth)
                    .height(11));
            field.setText(shown, false);
            boolean[] done = {false};
            Runnable leave = () -> {
                if (done[0]) return;
                done[0] = true;
                var typed = field.getText().strip();
                area.removeChild(field);
                if (!typed.equals(shown)) send.accept(typed);
            };
            field.addEventListener(UIEvents.KEY_DOWN, event -> {
                if (event.keyCode == GLFW.GLFW_KEY_ENTER || event.keyCode == GLFW.GLFW_KEY_KP_ENTER) leave.run();
            });
            field.addEventListener(UIEvents.BLUR, event -> leave.run());
            area.addChild(field);
            field.focus();
            // All of the text selected, so typing replaces it.
            field.setCursor(shown.length());
            field.setSelection(0, shown.length());
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            var graphics = context.graphics;
            var font = net.minecraft.client.Minecraft.getInstance().font;
            var card = drawn.card();
            float x = getPositionX();
            float y = getPositionY();
            float w = getSizeWidth();
            DrawerHelper.drawSolidRect(graphics, x, y, w, getSizeHeight(), EDGE);
            DrawerHelper.drawSolidRect(graphics, x + 1, y + 1, w - 2, getSizeHeight() - 2, FACE);
            DrawerHelper.drawSolidRect(graphics, x + 1, y + 1, w - 2, HEAD, card.acts() ? ACTS : TITLE);
            text(context, card.title(), x + 4, y + 2, EXEC);
            cross(graphics, close, x + w - CLOSE + 1, y + 3);
            if (drawn.execIn()) triangle(graphics, x - 3, y + FIRST - 3);
            for (int i = 0; i < card.outputs().size(); i++) {
                float cy = y + drawn.outputY(i);
                triangle(graphics, x + w - 3, cy - 3);
                var label = card.outputs().get(i).label();
                if (!label.isEmpty()) text(context, label, x + w - 5 - font.width(label), cy - 4, LABEL);
            }
            if (openNext && execDragged(this)) {
                float cy = y + drawn.outputY(card.outputs().size());
                triangle(graphics, x + w - 3, cy - 3);
                text(context, "next", x + w - 5 - font.width("next"), cy - 4, LABEL);
            }
            if (card.result() != null) port(graphics, x + w - 3, y + FIRST - 2, color(card.result()));
            dragged(this, context, drag -> card.id().equals(drag.card()), drawn.execIn() ? x - 1 : x + w + 2,
                    y + FIRST);
            for (int i = 0; i < card.rows().size(); i++) {
                var row = card.rows().get(i);
                float cy = y + drawn.rowY(i);
                port(graphics, x - 3, cy - 2, color(row.type()));
                text(context, row.label(), x + LABEL_X, cy - 4, LABEL);
                var value = drawn.values().get(i);
                if (value == null) continue;
                float vx = x + valueX(row);
                float vy = cy - 6;
                if (value.chip()) {
                    float left = vx;
                    FederationTheme.painted((pen, px, py, pw, ph) -> pill(pen, px, py, pw, ph, value.color()))
                            .draw(graphics, 0, 0, left, vy, value.width(), 11, 0);
                    text(context, value.text(), vx + 4, vy + 2, EXEC);
                } else {
                    FederationTheme.FIELD.draw(graphics, 0, 0, vx, vy, value.width(), 11, 0);
                    float tx = vx + 3;
                    if (!value.icon().isEmpty()) {
                        context.pose.pushPose();
                        context.pose.translate(tx, vy + 1.5f, 0);
                        context.pose.scale(0.5f, 0.5f, 1);
                        graphics.renderItem(value.icon(), 0, 0);
                        context.pose.popPose();
                        tx += 10;
                    }
                    text(context, value.text(), tx, vy + 2, FIELD_TEXT);
                }
            }
        }
    }

    /**
     * A draft: a dropped Library function's card, dashed, with its required arguments' ports, its exec pin when it
     * acts and its value pin when it gives one. A press on a pin drags a wire, drawn dashed to the pointer.
     */
    private final class DraftElement extends UIElement {
        private final Draft draft;
        private final UIElement close;
        private final int width;
        private final int height;
        private final int firstRow;

        DraftElement(Draft draft) {
            this.draft = draft;
            var font = net.minecraft.client.Minecraft.getInstance().font;
            firstRow = draft.execIn() ? 1 : 0;
            int lines = Math.max(1, firstRow + draft.rows().size());
            int widest = 4 + font.width(title()) + END + CLOSE_ROOM;
            for (var arg : draft.rows()) widest = Math.max(widest, LABEL_X + font.width(arg.name()) + END);
            width = Math.max(MIN_WIDTH, widest);
            height = BASE + ROW * lines;
            setId("auto_graph_draft_" + draft.number());
            addClass("graph-draft");
            layout(style -> style.positionType(TaffyPosition.ABSOLUTE).left(draft.x()).top(draft.y()).width(width)
                    .height(height));
            // Only its pins take presses; a press anywhere else reaches the canvas, which pans.
            setAllowHitTest(false);
            close = closer(getId() + "_remove", width, () -> {
                drafts.getOrDefault(function, new ArrayList<>()).remove(draft);
                graph.removeContentChild(this);
            });
            addChild(close);
            if (draft.execIn()) addChild(source(pin(getId() + "_in", 0, FIRST), new PinDrag(draft, null, true, EXEC)));
            if (draft.valueOut()) {
                addChild(source(pin(getId() + "_out", width, FIRST),
                        new PinDrag(draft, null, false, color(draft.function().result()))));
            }
        }

        private String title() {
            return draft.namespace() + "." + draft.function().name();
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            var graphics = context.graphics;
            float x = getPositionX();
            float y = getPositionY();
            DrawerHelper.drawSolidRect(graphics, x, y, width, height, DRAFT_FACE);
            DrawerHelper.drawSolidRect(graphics, x + 1, y + 1, width - 2, HEAD, DRAFT_HEAD);
            // The edge in dashes of two pixels.
            for (int i = 0; i < width; i += 4) {
                DrawerHelper.drawSolidRect(graphics, x + i, y, Math.min(2, width - i), 1, DRAFT_EDGE);
                DrawerHelper.drawSolidRect(graphics, x + i, y + height - 1, Math.min(2, width - i), 1, DRAFT_EDGE);
            }
            for (int i = 0; i < height; i += 4) {
                DrawerHelper.drawSolidRect(graphics, x, y + i, 1, Math.min(2, height - i), DRAFT_EDGE);
                DrawerHelper.drawSolidRect(graphics, x + width - 1, y + i, 1, Math.min(2, height - i), DRAFT_EDGE);
            }
            text(context, title(), x + 4, y + 2, EXEC);
            cross(graphics, close, x + width - CLOSE + 1, y + 3);
            if (draft.execIn()) triangle(graphics, x - 3, y + FIRST - 3);
            if (draft.valueOut()) port(graphics, x + width - 3, y + FIRST - 2, color(draft.function().result()));
            var rows = draft.rows();
            for (int i = 0; i < rows.size(); i++) {
                float cy = y + FIRST + ROW * (firstRow + i);
                port(graphics, x - 3, cy - 2, color(rows.get(i).type()));
                text(context, rows.get(i).name(), x + LABEL_X, cy - 4, LABEL);
            }
            dragged(this, context, drag -> drag.draft() == draft && drag.exec(), x - 1, y + FIRST);
            dragged(this, context, drag -> drag.draft() == draft && !drag.exec(), x + width + 2, y + FIRST);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static void text(GUIContext context, String text, float x, float y, int color) {
        context.pose.pushPose();
        context.pose.translate(x, y, 0);
        context.graphics.drawString(net.minecraft.client.Minecraft.getInstance().font, text, 0, 0, color, false);
        context.pose.popPose();
    }

    /** An exec pin: a six-pixel triangle pointing right. */
    @OnlyIn(Dist.CLIENT)
    private static void triangle(net.minecraft.client.gui.GuiGraphics graphics, float x, float y) {
        int[] widths = {1, 3, 5, 5, 3, 1};
        for (int r = 0; r < widths.length; r++) {
            DrawerHelper.drawSolidRect(graphics, x, y + r, widths[r], 1, EXEC);
        }
    }

    /** A data port: a five-pixel square in the value's colour. */
    @OnlyIn(Dist.CLIENT)
    private static void port(net.minecraft.client.gui.GuiGraphics graphics, float x, float y, int color) {
        DrawerHelper.drawSolidRect(graphics, x, y, 5, 5, PORT_EDGE);
        DrawerHelper.drawSolidRect(graphics, x + 1, y + 1, 3, 3, color);
    }

    private static <T> T element(UI ui, String id, Class<T> type) {
        return ui.selectId(id, type).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing UI element #" + id));
    }
}
