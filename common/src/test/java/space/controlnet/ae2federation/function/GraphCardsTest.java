package space.controlnet.ae2federation.function;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** What the graph view writes on the cards: titles, rows with labels and types, outputs, and the variables. */
final class GraphCardsTest {
    private static final String IRON_TOOLS = """
            def _low(name: Item, min: int) -> bool:
                return ae2.amount(name) < min

            def low_iron_alarm(lamp: Interface) -> None:
                iron: Item = "minecraft:iron_ingot"
                now = ae2.amount(iron)
                if now < 64:
                    ae2.craft(iron, 64 - now)
                    mc.set_redstone(lamp, 15)
                else:
                    mc.set_redstone(lamp, 0)

            def collect(items: list[Item]) -> int:
                names: list[str] = []
                for item in items:
                    if _low(item, min=8):
                        names.append(item)
                    total = len(names)
                return len(names)
            """;

    private static final Program PROGRAM = ScriptParser.parse(IRON_TOOLS).program();
    private static final Workspace WORKSPACE = new Workspace(Map.of(), CompletionTest.CATALOG);

    private static Canvas canvas(String function) {
        var result = Checker.check(PROGRAM, WORKSPACE);
        return Canvas.of(PROGRAM.functions().stream().filter(f -> f.name().equals(function)).findFirst().orElseThrow(),
                result);
    }

    private static List<GraphCards.Card> cards(String function) {
        return GraphCards.of(canvas(function), PROGRAM, WORKSPACE);
    }

    /** A card as one line: its title, a star when it acts, its rows and its outputs. */
    private static String line(GraphCards.Card card) {
        var rows = card.rows().stream().map(row -> row.label() + ":" + row.type() + "=" + switch (row.input()) {
            case Canvas.Input.Literal literal -> ScriptPrinter.expr(literal.value());
            case Canvas.Input.Chip chip -> "(" + chip.name().name() + ")";
            case Canvas.Input.Wire wire -> "wire";
            case Canvas.Input.Empty empty -> "";
        }).collect(Collectors.joining(" "));
        var outputs = card.outputs().stream().map(output -> output.label().isEmpty() ? ">" : ">" + output.label())
                .collect(Collectors.joining(" "));
        return card.title() + (card.acts() ? " *" : "") + (rows.isEmpty() ? "" : " | " + rows)
                + (card.result() == null ? "" : " -> " + card.result()) + (outputs.isEmpty() ? "" : " " + outputs);
    }

    @Test
    void theAlarmReadsAsTheBoardDrawsIt() {
        assertEquals(List.of(
                "low_iron_alarm >",
                "iron: Item = | value:Item=\"minecraft:iron_ingot\" >",
                "now = ae2.amount | resource:Item=(iron) >",
                "if | cond:bool=wire >then >else",
                "< | a:int=(now) b:int=64 -> bool",
                "ae2.craft * | resource:Item=(iron) amount:int=wire >",
                "- | a:int=64 b:int=(now) -> int",
                "mc.set_redstone * | interface:Interface=(lamp) level:int=15 >",
                "mc.set_redstone * | interface:Interface=(lamp) level:int=0 >"),
                cards("low_iron_alarm").stream().map(GraphCardsTest::line).toList());
    }

    @Test
    void playerFunctionsMethodsKeywordsAndLoopsAreLabelled() {
        assertEquals(List.of(
                "collect >",
                "names: list[str] = | value:list[str]=wire >",
                "list -> list[str]",
                "for item | in:list[Item]=(items) >body >next",
                "if | cond:bool=wire >then >next",
                "_low | name:Item=(item) min:int=8 -> bool",
                ".append | object:list[str]=(names) object:Item=(item) >",
                "total = len | :list[str]=(names) >",
                "return | value:int=wire",
                "len | :list[str]=(names) -> int"),
                cards("collect").stream().map(GraphCardsTest::line).toList());
    }

    @Test
    void theParametersComeFirstThenTheVariablesInOrder() {
        assertEquals(List.of("lamp: Interface", "iron: Item", "now: int"), GraphCards.variables(canvas("low_iron_alarm"))
                .stream().map(variable -> variable.name() + ": " + variable.type()).toList());
        assertEquals(List.of("items: list[Item]", "names: list[str]", "item: Item", "total: int"),
                GraphCards.variables(canvas("collect")).stream()
                        .map(variable -> variable.name() + ": " + variable.type()).toList());
    }

    /** The row of the card titled {@code title} labelled {@code label}. */
    private static GraphCards.Row row(String function, String title, String label) {
        return cards(function).stream().filter(card -> card.title().equals(title)).findFirst().orElseThrow().rows()
                .stream().filter(row -> row.label().equals(label)).findFirst().orElseThrow();
    }

    private static String put(String function, String title, String label, String typed) {
        var row = row(function, title, label);
        return GraphCards.put(IRON_TOOLS, canvas(function), row.node(), row.port(), row.type(), typed);
    }

    @Test
    void aValueTypedOnAPortChangesOnlyThatFunctionsText() {
        var text = put("low_iron_alarm", "<", "b", " 32 ");
        assertEquals(IRON_TOOLS.replace("if now < 64:", "if now < 32:"), text);
        assertEquals(IRON_TOOLS.replace("ae2.craft(iron, 64 - now)", "ae2.craft(iron, 64 - iron_count)"),
                put("low_iron_alarm", "-", "b", "iron_count"));
        assertEquals(IRON_TOOLS.replace("mc.set_redstone(lamp, 15)", "mc.set_redstone(lamp, min(15, 7 + 1))"),
                put("low_iron_alarm", "mc.set_redstone", "level", "min(15, 7 + 1)"));
    }

    @Test
    void anItemIdNeedsNoQuotesOnAResourcePort() {
        assertEquals(IRON_TOOLS.replace("\"minecraft:iron_ingot\"", "\"minecraft:gold_ingot\""),
                put("low_iron_alarm", "iron: Item =", "value", "minecraft:gold_ingot"));
        assertEquals("minecraft:iron_ingot", GraphCards.editText(row("low_iron_alarm", "iron: Item =", "value").input(),
                row("low_iron_alarm", "iron: Item =", "value").type()));
        assertEquals("now", GraphCards.editText(row("low_iron_alarm", "<", "a").input(),
                row("low_iron_alarm", "<", "a").type()));
    }

    @Test
    void whatIsNoValueIsRefused() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> put("low_iron_alarm", "<", "b", "1 +"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> put("low_iron_alarm", "<", "b", ""));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> put("low_iron_alarm", "<", "b", "x = 1"));
    }
}
