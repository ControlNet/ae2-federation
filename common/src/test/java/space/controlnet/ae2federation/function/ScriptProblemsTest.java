package space.controlnet.ae2federation.function;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Where the editor marks a script's problems: a line, the columns to underline, and the message. */
final class ScriptProblemsTest {
    private static List<ScriptProblems.Mark> marks(String text) {
        return ScriptProblems.check(text, new Workspace(Map.of(), CompletionTest.CATALOG));
    }

    @Test
    void aCallIsUnderlinedOverTheFunctionsName() {
        assertEquals(List.of(new ScriptProblems.Mark(2, 16, 21, "ae2 has no function 'amout'")),
                marks("def f() -> int:\n    return ae2.amout('x')\n"));
        assertEquals(List.of(new ScriptProblems.Mark(2, 5, 10, "name 'shout' is not defined")),
                marks("def f() -> None:\n    shout('x')\n"));
    }

    @Test
    void anOperationIsUnderlinedFromItsStartToItsEnd() {
        assertEquals(List.of(new ScriptProblems.Mark(2, 12, 19, "unsupported operand type(s) for +: 'int' and 'str'")),
                marks("def f() -> int:\n    return 1 + 'x'\n"));
    }

    @Test
    void aStringIsUnderlinedWithItsQuotes() {
        assertEquals(List.of(new ScriptProblems.Mark(2, 18, 39, "unknown item 'minecraft:iron_ingt'")),
                marks("def f() -> None:\n    iron: Item = \"minecraft:iron_ingt\"\n"));
    }

    @Test
    void aNodeOverSeveralLinesIsUnderlinedToTheEndOfItsFirstLine() {
        var text = "def f() -> int:\n    return 1 + (\n        'x')\n";
        assertEquals(List.of(new ScriptProblems.Mark(2, 12, 17, "unsupported operand type(s) for +: 'int' and 'str'")),
                marks(text));
    }

    @Test
    void aParseErrorIsUnderlinedOverTheWordItIsAt() {
        var parsed = marks("def f() -> int:\n    return 1 +\n");
        assertEquals(1, parsed.size());
        assertEquals(2, parsed.get(0).line());
        assertEquals(parsed.get(0).start() + 1, parsed.get(0).end());
    }

    @Test
    void problemsComeInTextOrder() {
        var text = "def f() -> None:\n    iron: Item = \"minecraft:iron_ingt\"\n    ae2.amout(iron)\n"
                + "    gold: Item = \"minecraft:gold_ingt\"\n";
        assertEquals(List.of(2, 3, 4), marks(text).stream().map(ScriptProblems.Mark::line).toList());
    }

    @Test
    void aCleanScriptHasNoMarks() {
        assertEquals(List.of(), marks("def f() -> int:\n    return 1\n"));
    }
}
