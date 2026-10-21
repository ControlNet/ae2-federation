package space.controlnet.ae2federation.client.menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import space.controlnet.ae2federation.client.automation.ScriptChecker;
import space.controlnet.ae2federation.script.Canvas;

/**
 * A function's graph as the Automation tab draws it, for pages outside the editor such as the guide: the same cards,
 * wires and layout, with each parameter's default on the function's card, and none of the editor's buttons, fields,
 * drafts or dry run.
 */
public final class ProgramGraph {
    private record Placed(FederationGraphView.Drawn drawn, int x, int y) {
    }

    private final List<Placed> cards;
    private final List<FederationGraphView.Wire> wires;
    private final int width;
    private final int height;

    private ProgramGraph(List<Placed> cards, List<FederationGraphView.Wire> wires, int width, int height) {
        this.cards = cards;
        this.wires = wires;
        this.width = width;
        this.height = height;
    }

    /** Measures and places the graph's cards; needs the client's font, so not before the game has loaded it. */
    public static ProgramGraph of(ScriptChecker.Graph graph) {
        var font = Minecraft.getInstance().font;
        var params = FederationGraphView.plainParams(graph);
        var drawn = new HashMap<String, FederationGraphView.Drawn>();
        for (var card : graph.cards()) {
            drawn.put(card.id(), FederationGraphView.measure(card, font, null, FederationGraphView.DryState.NONE,
                    card.card().node().kind() == Canvas.Kind.ENTRY ? params : List.of()));
        }
        var boxes = FederationGraphView.places(graph, drawn);
        int right = 0;
        int bottom = 0;
        var cards = new ArrayList<Placed>();
        for (var card : graph.cards()) {
            var box = boxes.get(card.id());
            right = Math.max(right, box.right());
            bottom = Math.max(bottom, box.bottom());
            cards.add(new Placed(drawn.get(card.id()), box.x() + FederationGraphView.MARGIN,
                    box.y() + FederationGraphView.MARGIN));
        }
        return new ProgramGraph(cards, FederationGraphView.wires(graph, drawn, boxes),
                right + 2 * FederationGraphView.MARGIN, bottom + 2 * FederationGraphView.MARGIN);
    }

    /** The canvas's width, its margins included. */
    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** Draws the wires, then the cards over them, with the canvas's top left at {@code (x, y)}. */
    public void paint(GuiGraphics graphics, float x, float y) {
        FederationGraphView.paintWires(graphics, wires, x, y);
        for (var card : cards) {
            FederationGraphView.paintCard(graphics, card.drawn(), x + card.x(), y + card.y(), card.drawn().width(),
                    card.drawn().height());
        }
    }
}
