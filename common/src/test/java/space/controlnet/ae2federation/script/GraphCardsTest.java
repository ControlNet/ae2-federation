package space.controlnet.ae2federation.script;

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

            def tally(items: list[Item]) -> dict[str, int]:
                seen = {"minecraft:copper_ingot"}
                counts = {"minecraft:copper_ingot": 0}
                for item in items:
                    seen.add(item)
                    counts[item] = counts.get(item, 0) + 1
                return counts
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
    void longActionCardsAreMarkedApartFromActions() {
        var program = ScriptParser.parse("""
                def fetch(url: str) -> str:
                    answer = net.http("GET", url)
                    return answer.text

                def run(lamp: Interface) -> None:
                    page = fetch("http://localhost")
                    mc.wait(20)
                    mc.set_redstone(lamp, 15)
                """).program();
        var result = Checker.check(program, WORKSPACE);
        assertEquals(List.of(), result.problems());
        var run = program.functions().get(1);
        var marks = GraphCards.of(Canvas.of(run, result), program, WORKSPACE).stream()
                .filter(GraphCards.Card::acts)
                .collect(Collectors.toMap(GraphCards.Card::title, GraphCards.Card::lasts));
        assertEquals(Map.of("page = fetch", true, "mc.wait", true, "mc.set_redstone", false), marks);
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
                "if | cond:bool=wire >then >else >next",
                "_low | name:Item=(item) min:int=8 -> bool",
                ".append | object:list[str]=(names) object:Item=(item) >",
                "total = len | :list[str]=(names) >",
                "return | value:int=wire",
                "len | :list[str]=(names) -> int"),
                cards("collect").stream().map(GraphCardsTest::line).toList());
    }

    @Test
    void dictAndSetLiteralsAndMethodsAreLabelled() {
        assertEquals(List.of(
                "tally >",
                "seen = | value:set[str]=wire >",
                "set | 0:str=\"minecraft:copper_ingot\" -> set[str]",
                "counts = | value:dict[str, int]=wire >",
                "dict | k0:str=\"minecraft:copper_ingot\" v0:int=0 -> dict[str, int]",
                "for item | in:list[Item]=(items) >body >next",
                ".add | object:set[str]=(seen) elem:Item=(item) >",
                "= | target:int=wire value:int=wire >",
                "[ ] | object:dict[str, int]=(counts) index:Item=(item) -> int",
                "+ | a:int=wire b:int=1 -> int",
                ".get | object:dict[str, int]=(counts) key:Item=(item) default:int=0 -> int",
                "return | value:dict[str, int]=(counts)"),
                cards("tally").stream().map(GraphCardsTest::line).toList());
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
                GraphCards.put(IRON_TOOLS, canvas("low_iron_alarm"), row.node(), row.port(), null,
                        Palette.call("ae2", library("ae2", "amount")).text()));
    }

    @Test
    void aDraftWiredToAnExecOutputIsWrittenAsAStatementThere() {
        var assign = cards("low_iron_alarm").stream().filter(card -> card.title().equals("now = ae2.amount"))
                .findFirst().orElseThrow();
        assertEquals(IRON_TOOLS.replace("    now = ae2.amount(iron)\n",
                        "    now = ae2.amount(iron)\n    mc.set_redstone(None, 0)\n"),
                GraphCards.insertStatement(IRON_TOOLS, canvas("low_iron_alarm"), assign.id(), "next",
                        Palette.call("mc", library("mc", "set_redstone")).text()));
        var branch = cards("low_iron_alarm").stream().filter(card -> card.title().equals("if")).findFirst()
                .orElseThrow();
        assertEquals(IRON_TOOLS.replace("    else:\n", "    else:\n        ae2.craftable(\"\")\n"),
                GraphCards.insertStatement(IRON_TOOLS, canvas("low_iron_alarm"), branch.id(), "else",
                        Palette.call("ae2", library("ae2", "craftable")).text()));
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
    void aNewVariableTakesANameNothingHasYet() {
        var text = """
                def f(lamp: Interface) -> None:
                    value = 1
                    amount = 2
                """;
        var program = ScriptParser.parse(text).program();
        var canvas = Canvas.of(program.functions().get(0), Checker.check(program, WORKSPACE));
        assertEquals("value_2", GraphCards.freshName(canvas, "value", program, WORKSPACE));
        assertEquals("item", GraphCards.freshName(canvas, "item", program, WORKSPACE));
        assertEquals("len_2", GraphCards.freshName(canvas, "len", program, WORKSPACE), "a built-in's name");
        assertEquals("f_2", GraphCards.freshName(canvas, "f", program, WORKSPACE), "a function's name");
    }

    private static Palette.Entry entry(String group, String name) {
        return Palette.groups(PROGRAM, WORKSPACE).stream().filter(g -> g.key().equals(group))
                .flatMap(g -> g.entries().stream()).filter(e -> e.name().equals(name)).findFirst().orElseThrow();
    }

    private static final String EMPTY = """
            def f(n: int) -> None:
                n = 1
            """;

    /** The text with {@code statement} put after the function's first statement, as a dropped card wired there. */
    private static String after(String statement) {
        var program = ScriptParser.parse(EMPTY).program();
        var canvas = Canvas.of(program.functions().get(0), Checker.check(program, WORKSPACE));
        var first = GraphCards.of(canvas, program, WORKSPACE).get(1).id();
        return GraphCards.insertStatement(EMPTY, canvas, first, "next", statement);
    }

    @Test
    void eachStatementIsWrittenAfterTheOneItIsWiredTo() {
        java.util.function.Function<String, String> written = name -> after(entry(Palette.STATEMENTS, name)
                .text(name.equals("for") ? "item" : "value"));
        assertEquals(EMPTY + "    value = None\n", written.apply("value ="));
        assertEquals(EMPTY + "    if False:\n        pass\n", written.apply("if"));
        assertEquals(EMPTY + "    for item in []:\n        pass\n", written.apply("for"));
        assertEquals(EMPTY + "    while False:\n        pass\n", written.apply("while"));
        assertEquals(EMPTY + "    return None\n", written.apply("return"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> written.apply("break"),
                "break only stands in a loop");
        assertEquals(EMPTY + "    print(\"\")\n", after(entry(Palette.BUILT_INS, "print").text()),
                "a call that gives nothing is a statement too");
        assertEquals(EMPTY + "    value_2 = None\n", after(entry(Palette.STATEMENTS, "value =").text("value_2")));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> after("x = "));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> after("x = 1\ny = 2"));
    }

    @Test
    void everyValueIsWrittenOnAPortAndChecksThere() {
        var row = row("low_iron_alarm", "-", "b");
        for (var group : List.of(Palette.OPERATORS, Palette.BUILT_INS, Palette.VALUES)) {
            for (var entry : Palette.groups(PROGRAM, WORKSPACE).stream().filter(g -> g.key().equals(group))
                    .findFirst().orElseThrow().entries()) {
                if (!entry.valueOut()) continue;
                var text = GraphCards.put(IRON_TOOLS, canvas("low_iron_alarm"), row.node(), row.port(), null,
                        entry.text());
                // Written in place of the value, in parentheses where the operator binds less tightly.
                var start = text.indexOf("ae2.craft(iron, 64 - ") + "ae2.craft(iron, 64 - ".length();
                // The line ends with the craft's own parenthesis.
                var shown = text.substring(start, text.indexOf("\n", start) - 1);
                assertEquals(true, shown.equals(entry.text()) || shown.equals("(" + entry.text() + ")"),
                        entry.name() + ": " + shown);
                assertEquals(true, ScriptParser.parse(text).ok(), entry.name());
            }
        }
    }

    @Test
    void aStatementWiredIntoABodyOfPassTakesThePassesPlace() {
        var text = after(entry(Palette.STATEMENTS, "if").text());
        var program = ScriptParser.parse(text).program();
        var canvas = Canvas.of(program.functions().get(0), Checker.check(program, WORKSPACE));
        var branch = GraphCards.of(canvas, program, WORKSPACE).stream().filter(card -> card.title().equals("if"))
                .findFirst().orElseThrow();
        assertEquals(List.of("then", "else"), branch.outputs().stream().map(GraphCards.Output::label).toList(),
                "an if without an else still shows one");
        var then = GraphCards.insertStatement(text, canvas, branch.id(), "arm0", "n = 2");
        assertEquals(EMPTY + "    if False:\n        n = 2\n", then);
        var orElse = GraphCards.insertStatement(text, canvas, branch.id(), "else", "n = 3");
        assertEquals(EMPTY + "    if False:\n        pass\n    else:\n        n = 3\n", orElse,
                "the first statement put on else adds it");
    }

    @Test
    void anIfTakesAndLosesItsLastElif() {
        var branch = card("low_iron_alarm", "if", 0);
        var added = GraphCards.addArm(IRON_TOOLS, canvas("low_iron_alarm"), branch.id());
        assertEquals(IRON_TOOLS.replace("    else:\n        mc.set_redstone(lamp, 0)\n",
                "    elif False:\n        pass\n    else:\n        mc.set_redstone(lamp, 0)\n"), added);
        var program = ScriptParser.parse(added).program();
        var canvas = Canvas.of(program.functions().get(1), Checker.check(program, WORKSPACE));
        var again = GraphCards.of(canvas, program, WORKSPACE).stream().filter(card -> card.title().equals("if"))
                .findFirst().orElseThrow();
        assertEquals(IRON_TOOLS, GraphCards.removeLastArm(added, canvas, again.id()));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> GraphCards.removeLastArm(IRON_TOOLS, canvas("low_iron_alarm"), branch.id()));
    }

    @Test
    void aVariableIsRenamedWhereverTheFunctionUsesIt() {
        assertEquals(IRON_TOOLS.replace("now", "stock"),
                GraphCards.renameVariable(IRON_TOOLS, canvas("low_iron_alarm"), "now", " stock "));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> GraphCards.renameVariable(IRON_TOOLS, canvas("low_iron_alarm"), "now", "2x"));
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
