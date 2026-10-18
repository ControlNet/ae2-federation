package space.controlnet.ae2federation.function;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

/** Layout A: the exec line runs left to right, values hang below-left of their statement, bodies below-right. */
final class LayoutTest {
    /**
     * Test-only stand-in for the GUI's font metrics: sizes vary with the rows and the kind, so that the layout is
     * exercised with nodes of different sizes.
     */
    static final Layout.Metrics METRICS = new Layout.Metrics() {
        public int width(Layout.Card card) {
            return 40 + 14 * card.rows().size() + 10 * (card.node().kind().ordinal() % 3);
        }

        public int height(Layout.Card card) {
            int rows = card.rows().size();
            return card.statement() ? 29 + 12 * Math.max(rows, card.outputs().size() - 1) : 17 + 12 * Math.max(1, rows);
        }

        public int inputY(Layout.Card card, int row) {
            return (card.statement() ? 27 : 15) + 12 * row + 4;
        }

        public int outputY(Layout.Card card) {
            return 19;
        }
    };

    static Stream<String> sources() {
        var sources = new ArrayList<String>();
        CanvasTest.sources().forEach(sources::add);
        sources.add(REFILL);
        return sources.stream();
    }

    static final String REFILL = """
            def refill(items: list[Item], target: int = 64) -> int:
                started = 0
                for item in items:
                    missing = target - ae2.amount(item) - ae2.crafting(item)
                    if missing <= 0:
                        continue
                    elif ae2.craftable(item) and ae2.free_cpus() > 0:
                        ae2.craft(item, missing)
                        started += 1
                    else:
                        mc.wait(20)
                return started
            """;

    private static List<Canvas> canvases(String source) {
        var parsed = ScriptParser.parse(source);
        var result = Checker.check(parsed.program(), new Workspace(Map.of(), CompletionTest.CATALOG));
        return parsed.program().functions().stream().map(f -> Canvas.of(f, result)).toList();
    }

    @ParameterizedTest
    @MethodSource("sources")
    void cardsNeverOverlapAndTheSameCanvasGivesTheSameLayout(String source) {
        for (var canvas : canvases(source)) {
            var boxes = Layout.of(canvas, METRICS);
            assertEquals(boxes, Layout.of(canvas, METRICS));
            var cards = Layout.cards(canvas);
            assertEquals(cards.size(), boxes.size(), "every card has a box");
            var list = List.copyOf(boxes.entrySet());
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) {
                    var a = list.get(i).getValue();
                    var b = list.get(j).getValue();
                    boolean overlap = a.x() < b.right() && b.x() < a.right() && a.y() < b.bottom() && b.y() < a.bottom();
                    assertFalse(overlap, list.get(i).getKey() + " and " + list.get(j).getKey());
                }
            }
            assertEquals(0, boxes.values().stream().mapToInt(Layout.Rect::x).min().orElse(0));
            assertEquals(0, boxes.values().stream().mapToInt(Layout.Rect::y).min().orElse(0));
        }
    }

    @ParameterizedTest
    @MethodSource("sources")
    void everyValueSitsLeftOfThePortItFeeds(String source) {
        for (var canvas : canvases(source)) {
            var boxes = Layout.of(canvas, METRICS);
            for (var card : Layout.cards(canvas)) {
                for (var row : card.rows()) {
                    if (row.input() instanceof Canvas.Input.Wire w) {
                        var producer = boxes.get(w.node());
                        assertNotNull(producer, w.node());
                        assertTrue(producer.right() < boxes.get(card.id()).x(), w.node() + " feeds " + card.id());
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("sources")
    void everyBodyStartsRightOfItsStatementAndBelowIt(String source) {
        for (var canvas : canvases(source)) {
            var boxes = Layout.of(canvas, METRICS);
            for (var link : canvas.links()) {
                if (!link.output().equals("next")) {
                    var from = boxes.get(link.from());
                    var to = boxes.get(link.to());
                    assertTrue(to.x() > from.right() && to.y() > from.bottom(), link.toString());
                }
            }
        }
    }

    @Test
    void aValueLinesUpWithTheRowItFeeds() {
        var canvas = canvases("""
                def f(n: int) -> int:
                    return 2 * (n + 1)
                """).get(0);
        var boxes = Layout.of(canvas, METRICS);
        var cards = byKind(Layout.cards(canvas), Canvas.Kind.BINARY);
        var product = cards.get(0);
        var sum = cards.get(1);
        assertEquals(boxes.get(product.id()).y() + METRICS.inputY(product, 1),
                boxes.get(sum.id()).y() + METRICS.outputY(sum));
    }

    @Test
    void aStatementAfterAReturnStillHasItsExecLink() {
        var canvas = canvases("""
                def f(n: int) -> int:
                    return n
                    n = 1
                """).get(0);
        var ret = byKind(Layout.cards(canvas), Canvas.Kind.RETURN).get(0);
        assertEquals(List.of("next"), ret.outputs());
    }

    @Test
    void anAssignedCallAndACallStatementAreOneCard() {
        var canvas = canvases("""
                def f(n: int) -> None:
                    now = ae2.amount("minecraft:iron_ingot")
                    ae2.craft("minecraft:iron_ingot", n - now)
                """).get(0);
        var cards = Layout.cards(canvas);
        assertEquals(List.of(Canvas.Kind.ENTRY, Canvas.Kind.ASSIGN, Canvas.Kind.CALL_STATEMENT, Canvas.Kind.BINARY),
                cards.stream().map(c -> c.node().kind()).toList());
        assertEquals("amount", cards.get(1).call().name());
        assertEquals(List.of("arg0"), cards.get(1).rows().stream().map(Layout.Row::port).toList());
        assertEquals(List.of("arg0", "arg1"), cards.get(2).rows().stream().map(Layout.Row::port).toList());
        assertEquals(cards.get(2).call().id(), cards.get(2).rows().get(0).node(), "the rows belong to the call");
        assertFalse(Layout.of(canvas, METRICS).containsKey(cards.get(1).call().id()));
    }

    @Test
    void theExecLineStaysOnOneRowAndBodiesHangBelowRight() {
        var canvas = canvases(REFILL).get(0);
        var boxes = Layout.of(canvas, METRICS);
        var cards = Layout.cards(canvas);
        var entry = cards.get(0);
        var assign = byKind(cards, Canvas.Kind.ASSIGN).get(0);
        var loop = byKind(cards, Canvas.Kind.FOR).get(0);
        var ret = byKind(cards, Canvas.Kind.RETURN).get(0);
        var branch = byKind(cards, Canvas.Kind.IF).get(0);
        for (var card : List.of(assign, loop, ret)) {
            assertEquals(boxes.get(entry.id()).y(), boxes.get(card.id()).y(), card.id() + " is on the main line");
        }
        assertTrue(boxes.get(assign.id()).x() < boxes.get(loop.id()).x());
        var bodyStart = byKind(cards, Canvas.Kind.ASSIGN).get(1);
        assertTrue(boxes.get(bodyStart.id()).x() > boxes.get(loop.id()).right());
        assertTrue(boxes.get(bodyStart.id()).y() > boxes.get(loop.id()).bottom());
        for (var box : boxes.values()) {
            if (box.y() > boxes.get(loop.id()).bottom()) {
                assertTrue(box.right() < boxes.get(ret.id()).x(), "the loop's body ends before return");
            }
        }
        var continueCard = byKind(cards, Canvas.Kind.CONTINUE).get(0);
        var craft = byKind(cards, Canvas.Kind.CALL_STATEMENT).get(0);
        var wait = byKind(cards, Canvas.Kind.CALL_STATEMENT).get(1);
        assertTrue(boxes.get(continueCard.id()).y() > boxes.get(branch.id()).bottom());
        assertTrue(boxes.get(craft.id()).y() >= boxes.get(continueCard.id()).bottom(), "arm1 below arm0");
        assertTrue(boxes.get(wait.id()).y() >= boxes.get(craft.id()).bottom(), "else below arm1");
        assertEquals(List.of("arm0", "arm1", "else", "next"), branch.outputs());
        assertEquals(List.of("body", "next"), loop.outputs());
        assertEquals(List.of(), continueCard.outputs());
    }

    @Test
    void valuesOfAStatementHangBelowItsExecRow() {
        var canvas = canvases(REFILL).get(0);
        var boxes = Layout.of(canvas, METRICS);
        var missing = byKind(Layout.cards(canvas), Canvas.Kind.ASSIGN).get(1);
        var value = ((Canvas.Input.Wire) missing.rows().get(0).input()).node();
        assertTrue(boxes.get(value).y() >= boxes.get(missing.id()).y() + Layout.BELOW_EXEC);
    }

    private static List<Layout.Card> byKind(List<Layout.Card> cards, Canvas.Kind kind) {
        return cards.stream().filter(c -> c.node().kind() == kind).toList();
    }
}
