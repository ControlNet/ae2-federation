package space.controlnet.ae2federation.script;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where the graph view draws each card of a function, computed again from the canvas on every change (no stored
 * positions). Layout A, picked by the user (2026-10-18): the exec line runs left to right without breaks; the values
 * a statement reads hang below-left of it; loop bodies and {@code if} arms hang below-right, one under the other; the
 * statement after a loop or {@code if} continues on the same line, right of everything inside it.
 *
 * <p>Sizes come from the GUI through {@link Metrics}, in GUI units. The entry card is at 0, 0 and nothing reaches left
 * of or above it.
 */
public final class Layout {
    /** Space between statements on a line, and between a value tree and its statement. */
    static final int GAP_X = 20;
    /** Space between a statement's row and the bodies below it, and between bodies. */
    static final int GAP_Y = 14;
    /** Extra space before the bodies, so the exec wires to several arms spread out. */
    static final int ARM_X = 24;
    /** Space between a value and the card it feeds, and between stacked values. */
    static final int CHILD_X = 16;
    static final int CHILD_Y = 8;
    /** A statement's values start this far below its top, under the exec wire that enters it. */
    static final int BELOW_EXEC = 30;

    private Layout() {
    }

    /** The size of a drawn card and where its pins are, as the GUI draws them. */
    public interface Metrics {
        int width(Card card);

        int height(Card card);

        /** From the card's top to the pin of its input row. */
        int inputY(Card card, int row);

        /** From a value card's top to its output pin. */
        int outputY(Card card);
    }

    public record Rect(int x, int y, int w, int h) {
        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }
    }

    /** An input row of a card: a port of {@code node}, which is the card's own node or the call it absorbed. */
    public record Row(String node, String port, Canvas.Input input) {
    }

    /**
     * A drawn card: one canvas node, or an assignment or call statement together with its call ({@code call}), drawn
     * as one card such as {@code now = ae2.amount}. {@code outputs} are the exec outputs it shows.
     */
    public record Card(String id, Canvas.Node node, Canvas.Node call, List<Row> rows, List<String> outputs) {
        public Card {
            rows = List.copyOf(rows);
            outputs = List.copyOf(outputs);
        }

        public boolean statement() {
            return node.kind() != Canvas.Kind.CALL && node.kind() != Canvas.Kind.BINARY
                    && node.kind() != Canvas.Kind.UNARY && node.kind() != Canvas.Kind.ATTRIBUTE
                    && node.kind() != Canvas.Kind.INDEX && node.kind() != Canvas.Kind.SLICE
                    && node.kind() != Canvas.Kind.LIST && node.kind() != Canvas.Kind.DICT
                    && node.kind() != Canvas.Kind.SET;
        }
    }

    /** The cards of a canvas, in its node order; a call merged into its statement has no card of its own. */
    public static List<Card> cards(Canvas canvas) {
        var nodes = new HashMap<String, Canvas.Node>();
        canvas.nodes().forEach(n -> nodes.put(n.id(), n));
        var linked = new HashSet<String>();
        canvas.links().forEach(l -> linked.add(l.from() + "/" + l.output()));
        var merged = new HashSet<String>();
        var cards = new ArrayList<Card>();
        for (var n : canvas.nodes()) {
            if (merged.contains(n.id())) {
                continue;
            }
            Canvas.Node call = null;
            var rows = new ArrayList<Row>();
            switch (n.kind()) {
                case ASSIGN, AUG_ASSIGN -> {
                    var target = n.inputs().get("target");
                    var value = n.inputs().get("value");
                    if (n.kind() == Canvas.Kind.ASSIGN && target instanceof Canvas.Input.Chip
                            && value instanceof Canvas.Input.Wire w && nodes.get(w.node()).kind() == Canvas.Kind.CALL) {
                        call = nodes.get(w.node());
                    } else {
                        // A chip target is the card's title; a field or item written to is a value of its own.
                        if (!(target instanceof Canvas.Input.Chip)) {
                            rows.add(new Row(n.id(), "target", target));
                        }
                        rows.add(new Row(n.id(), "value", value));
                    }
                }
                case CALL_STATEMENT -> call = nodes.get(((Canvas.Input.Wire) n.inputs().get("call")).node());
                default -> n.inputs().forEach((port, input) -> rows.add(new Row(n.id(), port, input)));
            }
            if (call != null) {
                merged.add(call.id());
                var owner = call.id();
                call.inputs().forEach((port, input) -> rows.add(new Row(owner, port, input)));
            }
            cards.add(new Card(n.id(), n, call, rows, outputs(n, linked)));
        }
        return cards;
    }

    private static List<String> outputs(Canvas.Node n, Set<String> linked) {
        var outputs = new ArrayList<String>();
        switch (n.kind()) {
            case IF -> {
                int arms = (int) n.inputs().keySet().stream().filter(k -> k.startsWith("condition")).count();
                for (int i = 0; i < arms; i++) {
                    outputs.add("arm" + i);
                }
                // Shown with no else too: a statement put there adds it.
                outputs.add("else");
                outputs.add("next");
            }
            case WHILE, FOR -> outputs.addAll(List.of("body", "next"));
            case RETURN, BREAK, CONTINUE -> {
                // Control leaves here; a statement after it is never reached, but it is still shown.
                if (linked.contains(n.id() + "/next")) {
                    outputs.add("next");
                }
            }
            case CALL, BINARY, UNARY, ATTRIBUTE, INDEX, SLICE, LIST, DICT, SET -> {
            }
            default -> outputs.add("next");
        }
        return outputs;
    }

    /** The box of every card, by card ID. */
    public static Map<String, Rect> of(Canvas canvas, Metrics metrics) {
        var placer = new Placer(canvas, metrics);
        var entry = placer.cards.get(canvas.nodes().get(0).id());
        placer.put(entry, 0, 0);
        placer.chain(placer.chain(entry.id(), "next"), placer.sizes.get(entry.id())[0] + GAP_X, 0);
        var result = new LinkedHashMap<String, Rect>();
        placer.cards.keySet().forEach(id -> result.put(id, placer.boxes.get(id)));
        return result;
    }

    private static final class Placer {
        final Metrics metrics;
        final Map<String, Card> cards = new LinkedHashMap<>();
        final Map<String, int[]> sizes = new HashMap<>();
        final Map<String, Rect> boxes = new HashMap<>();
        final Map<String, String> next = new HashMap<>();

        Placer(Canvas canvas, Metrics metrics) {
            this.metrics = metrics;
            for (var card : cards(canvas)) {
                cards.put(card.id(), card);
                sizes.put(card.id(), new int[] {metrics.width(card), metrics.height(card)});
            }
            canvas.links().forEach(l -> next.put(l.from() + "/" + l.output(), l.to()));
        }

        void put(Card card, int x, int y) {
            var size = sizes.get(card.id());
            boxes.put(card.id(), new Rect(x, y, size[0], size[1]));
        }

        /** The statements on an exec output, following {@code next}. */
        List<String> chain(String from, String output) {
            var ids = new ArrayList<String>();
            for (var id = next.get(from + "/" + output); id != null; id = next.get(id + "/next")) {
                ids.add(id);
            }
            return ids;
        }

        /**
         * Places a card with its right edge at {@code right} and the values it reads stacked to its left, each level
         * by the row it feeds; {@code lowered} keeps a statement's values under its exec row. Gives the leftmost x
         * and the lowest y used.
         */
        int[] tree(Card card, int right, int top, boolean lowered) {
            var size = sizes.get(card.id());
            int x = right - size[0];
            put(card, x, top);
            int minX = x;
            int maxY = top + size[1];
            int y = lowered ? top + BELOW_EXEC : top;
            for (int row = 0; row < card.rows().size(); row++) {
                if (card.rows().get(row).input() instanceof Canvas.Input.Wire w) {
                    var child = cards.get(w.node());
                    y = Math.max(y, top + metrics.inputY(card, row) - metrics.outputY(child));
                    var used = tree(child, x - CHILD_X, y, false);
                    minX = Math.min(minX, used[0]);
                    maxY = Math.max(maxY, used[1]);
                    y = used[1] + CHILD_Y;
                }
            }
            return new int[] {minX, maxY};
        }

        /** Lays out statements left to right from {@code x}; gives the rightmost x and the lowest y used. */
        int[] chain(List<String> ids, int x, int y) {
            int right = x;
            int bottom = y;
            for (var id : ids) {
                var card = cards.get(id);
                int width = sizes.get(id)[0];
                // A first pass with the card at 0 measures how far its values reach to the left.
                int reach = -tree(card, width, 0, true)[0];
                int left = x + reach + (reach > 0 ? GAP_X : 0);
                int[] used = tree(card, left + width, y, true);
                bottom = Math.max(bottom, used[1]);
                var box = boxes.get(id);
                int armsRight = box.right();
                int armY = box.bottom() + GAP_Y;
                for (var output : card.outputs()) {
                    var arm = output.equals("next") ? List.<String>of() : chain(id, output);
                    if (!arm.isEmpty()) {
                        int[] armUsed = chain(arm, box.right() + GAP_X + ARM_X, armY);
                        armsRight = Math.max(armsRight, armUsed[0]);
                        bottom = Math.max(bottom, armUsed[1]);
                        armY = armUsed[1] + GAP_Y;
                    }
                }
                right = armsRight;
                x = armsRight + GAP_X;
            }
            return new int[] {right, bottom};
        }
    }
}
