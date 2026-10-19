package space.controlnet.ae2federation.function;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** A dry run's values as the script view writes them after each line of the function. */
final class DryRunNotesTest {
    private static final String ALARM = """
            # Light the lamp while iron runs low.
            def low_iron_alarm(lamp: Interface, min: int = 64) -> None:
                iron: Item = "minecraft:iron_ingot"
                now = ae2.amount(iron)
                if now < min:
                    ae2.craft(iron, min - now)
                    mc.set_redstone(lamp, 15)
                else:
                    mc.set_redstone(lamp, 0)

            def total(items: list[Item]) -> int:
                n = 0
                for item in items:
                    n += ae2.amount(item) * 2
                return n
            """;

    private static Map<Integer, DryRunNotes.Note> notes(String function, List<Object> args) {
        var parsed = ScriptParser.parse(ALARM);
        assertEquals(List.of(), parsed.errors(), "the program must parse");
        var workspace = new Workspace(Map.of("main", parsed.program()), CompletionTest.CATALOG);
        assertEquals(List.of(), Checker.check(parsed.program(), workspace).problems(), "the program must check");
        var graphs = new HashMap<String, Map<String, Graph>>();
        graphs.put("main", Compiler.compile("main", workspace));
        var result = DryRun.run(workspace, graphs, new DryRunTest.StockHost(), "main", function, args, DryRun.BUDGET);
        var failure = result.failure();
        return DryRunNotes.of(parsed, function, result.values(), failure == null ? "" : failure.node(),
                failure == null ? "" : failure.error().type() + ": " + failure.error().getMessage());
    }

    private static String text(Map<Integer, DryRunNotes.Note> notes, int line) {
        var note = notes.get(line);
        return note == null ? null : note.text();
    }

    @Test
    void eachLineThatRanShowsItsValuesAndTheIntermediateOnesBeforeThem() {
        var notes = notes("low_iron_alarm", List.of("north_gate"));
        assertEquals("lamp = 'north_gate' · min = 64", text(notes, 2));
        assertNull(text(notes, 3), "a literal assigned shows nothing new");
        assertEquals("37", text(notes, 4));
        assertEquals("True", text(notes, 5));
        assertEquals("min - now = 27 · True", text(notes, 6));
        assertEquals("True", text(notes, 7));
        assertNull(text(notes, 9), "the else branch did not run");
        assertFalse(notes.get(6).error());
    }

    @Test
    void theLineThatStoppedTheRunShowsTheError() {
        var parsed = ScriptParser.parse(ALARM);
        var notes = DryRunNotes.of(parsed, "low_iron_alarm", Map.of(), idOfLine(parsed, 7),
                "ValueError: no interface '' in this domain");
        assertEquals(new DryRunNotes.Note("ValueError: no interface '' in this domain", true), notes.get(7));
    }

    @Test
    void aLoopShowsItsLastItemAndValues() {
        var notes = notes("total", List.of(new ListValue(List.<Object>of("minecraft:iron_ingot", "minecraft:gold_ingot"))));
        assertEquals("'minecraft:gold_ingot'", text(notes, 13));
        assertEquals("ae2.amount(item) = 37 · ae2.amount(item) * 2 = 74 · 148", text(notes, 14));
        assertEquals("148", text(notes, 15));
    }

    @Test
    void theFunctionAtALineIsTheOneWhoseDefinitionItFollows() {
        var parsed = ScriptParser.parse(ALARM);
        assertEquals(Optional.empty(), DryRunNotes.functionAt(parsed, 1));
        assertEquals(Optional.of("low_iron_alarm"), DryRunNotes.functionAt(parsed, 2));
        assertEquals(Optional.of("low_iron_alarm"), DryRunNotes.functionAt(parsed, 10));
        assertEquals(Optional.of("total"), DryRunNotes.functionAt(parsed, 11));
        assertEquals(Optional.of("total"), DryRunNotes.functionAt(parsed, 99));
    }

    /** A node that starts on {@code line}: the failing node is known by its line only. */
    private static String idOfLine(ParseResult parsed, int line) {
        return parsed.positions().entrySet().stream().filter(entry -> entry.getValue().line() == line)
                .map(Map.Entry::getKey).findFirst().orElseThrow();
    }
}
