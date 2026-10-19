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
}
