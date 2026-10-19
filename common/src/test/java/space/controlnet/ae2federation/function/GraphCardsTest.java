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

    private static Signature library(String namespace, String name) {
        return StandardLibrary.NAMESPACES.get(namespace).stream().filter(f -> f.name().equals(name)).findFirst()
                .orElseThrow();
    }

    @Test
    void aDroppedFunctionIsCalledWithAValueOfEachRequiredArgumentsType() {
        assertEquals("ae2.craftable(\"\")", GraphCards.callText("ae2", library("ae2", "craftable")));
        assertEquals("mc.set_redstone(None, 0)", GraphCards.callText("mc", library("mc", "set_redstone")));
    }

    @Test
    void aDraftWiredToAPortIsWrittenAsItsValue() {
        var row = row("low_iron_alarm", "-", "b");
        assertEquals(IRON_TOOLS.replace("ae2.craft(iron, 64 - now)", "ae2.craft(iron, 64 - ae2.amount(\"\"))"),
                GraphCards.putCall(IRON_TOOLS, canvas("low_iron_alarm"), row.node(), row.port(), "ae2",
                        library("ae2", "amount")));
    }

    @Test
    void aDraftWiredToAnExecOutputIsWrittenAsAStatementThere() {
        var assign = cards("low_iron_alarm").stream().filter(card -> card.title().equals("now = ae2.amount"))
                .findFirst().orElseThrow();
        assertEquals(IRON_TOOLS.replace("    now = ae2.amount(iron)\n",
                        "    now = ae2.amount(iron)\n    mc.set_redstone(None, 0)\n"),
                GraphCards.insertCall(IRON_TOOLS, canvas("low_iron_alarm"), assign.id(), "next", "mc",
                        library("mc", "set_redstone")));
        var branch = cards("low_iron_alarm").stream().filter(card -> card.title().equals("if")).findFirst()
                .orElseThrow();
        assertEquals(IRON_TOOLS.replace("    else:\n", "    else:\n        ae2.craftable(\"\")\n"),
                GraphCards.insertCall(IRON_TOOLS, canvas("low_iron_alarm"), branch.id(), "else", "ae2",
                        library("ae2", "craftable")));
    }

    private static GraphCards.Card card(String function, String title, int nth) {
        return cards(function).stream().filter(card -> card.title().equals(title)).skip(nth).findFirst().orElseThrow();
    }

    @Test
    void aValueWiredToAnotherPortLeavesAPlainValueOfItsTypeBehind() {
        var minus = card("low_iron_alarm", "-", 0);
        var bound = row("low_iron_alarm", "<", "b");
        assertEquals(IRON_TOOLS.replace("if now < 64:", "if now < 64 - now:")
                        .replace("ae2.craft(iron, 64 - now)", "ae2.craft(iron, 0)"),
                GraphCards.move(IRON_TOOLS, canvas("low_iron_alarm"), minus.id(), bound.node(), bound.port()));
    }

    @Test
    void aStatementWiredToAnotherExecOutputMovesThere() {
        var lamp = card("low_iron_alarm", "mc.set_redstone", 0);
        var branch = card("low_iron_alarm", "if", 0);
        assertEquals(IRON_TOOLS.replace("""
                        mc.set_redstone(lamp, 15)
                    else:
                        mc.set_redstone(lamp, 0)
                """, """
                    else:
                        mc.set_redstone(lamp, 0)
                    mc.set_redstone(lamp, 15)
                """),
                GraphCards.relocate(IRON_TOOLS, canvas("low_iron_alarm"), lamp.id(), branch.id(), "next"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> GraphCards.relocate(
                IRON_TOOLS, canvas("low_iron_alarm"), branch.id(), lamp.id(), "next"));
    }

    @Test
    void aRemovedStatementTakesWhatIsInsideItAndTheRestClosesUp() {
        var lamp = card("low_iron_alarm", "mc.set_redstone", 0);
        assertEquals(IRON_TOOLS.replace("        mc.set_redstone(lamp, 15)\n", ""),
                GraphCards.remove(IRON_TOOLS, canvas("low_iron_alarm"), lamp.id()));
        var branch = card("low_iron_alarm", "if", 0);
        assertEquals(IRON_TOOLS.replace("""
                    if now < 64:
                        ae2.craft(iron, 64 - now)
                        mc.set_redstone(lamp, 15)
                    else:
                        mc.set_redstone(lamp, 0)
                """, ""), GraphCards.remove(IRON_TOOLS, canvas("low_iron_alarm"), branch.id()));
    }

    @Test
    void aRemovedValueLeavesAPlainValueOfItsTypeOnItsPort() {
        var minus = card("low_iron_alarm", "-", 0);
        assertEquals(IRON_TOOLS.replace("ae2.craft(iron, 64 - now)", "ae2.craft(iron, 0)"),
                GraphCards.remove(IRON_TOOLS, canvas("low_iron_alarm"), minus.id()));
    }

    @Test
    void theLastStatementOfABranchLeavesPass() {
        var lamp = card("low_iron_alarm", "mc.set_redstone", 1);
        var text = GraphCards.remove(IRON_TOOLS, canvas("low_iron_alarm"), lamp.id());
        assertEquals(IRON_TOOLS.replace("        mc.set_redstone(lamp, 0)\n", "        pass\n"), text);
    }
}
