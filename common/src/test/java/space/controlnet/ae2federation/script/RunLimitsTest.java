package space.controlnet.ae2federation.script;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A program can make the server spend only bounded time in one step and bounded memory in one run, whatever it
 * writes; and the answers where the review of 2026-10-30 found the runtime differing from CPython 3.12.
 */
final class RunLimitsTest {
    /** Test-only host: the actions here never reach it, prints are kept. */
    private static final class Host implements Evaluator.Host {
        final List<String> printed = new ArrayList<>();

        @Override
        public Object call(String namespace, String name, List<Object> args) {
            throw new RunError("RuntimeError", "the test host has no " + namespace + "." + name);
        }

        @Override
        public void print(String text) {
            printed.add(text);
        }
    }

    private static Workspace workspace(String source) {
        var parsed = ScriptParser.parse(source);
        assertEquals(List.of(), parsed.errors(), "the program must parse");
        var workspace = new Workspace(Map.of("main", parsed.program()), CompletionTest.CATALOG);
        assertEquals(List.of(), Checker.check(parsed.program(), workspace).problems(), "the program must check");
        return workspace;
    }

    /** Runs {@code main.entry(args)} to its end; the result's repr, or {@code Type: message}. */
    private static String run(String source, String entry, Object... args) {
        var workspace = workspace(source);
        var graphs = new HashMap<String, Map<String, Graph>>();
        workspace.files().keySet().forEach(file -> graphs.put(file, Compiler.compile(file, workspace)));
        var runner = new Runner(workspace, graphs, new Host(), null, "main", entry, List.of(args));
        for (int tick = 0; tick < 10_000; tick++) {
            var status = runner.tick(10_000);
            if (status == Runner.Status.DONE) {
                return Ops.repr(runner.result());
            }
            if (status == Runner.Status.FAILED) {
                return runner.failure().error().toString();
            }
        }
        return fail("the run did not end");
    }

    private static String value(String expression, String type) {
        return run("def f() -> " + type + ":\n    return " + expression + "\n", "f");
    }

    @Test
    void anEmptySequenceRepeatedAnyNumberOfTimesIsEmptyAtOnce() {
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            assertEquals("[]", run("""
                    def f() -> list[int]:
                        xs: list[int] = []
                        xs *= 9223372036854775807
                        return xs
                    """, "f"));
            assertEquals("''", value("'' * 9223372036854775807", "str"));
        });
    }

    @Test
    void deepNestingIsASyntaxErrorNotAStackOverflow() {
        for (var expression : List.of("-".repeat(20_000) + "1", "(".repeat(20_000) + "1" + ")".repeat(20_000),
                "1" + " + 1".repeat(20_000), "'a'" + ".strip()".repeat(20_000), "not ".repeat(20_000) + "True",
                "[".repeat(20_000) + "]".repeat(20_000))) {
            var parsed = assertDoesNotThrow(() -> ScriptParser.parse("def f() -> int:\n    return " + expression
                    + "\n"));
            assertFalse(parsed.errors().isEmpty(), expression.substring(0, 20));
            assertTrue(parsed.errors().get(0).message().contains("nests too deeply"),
                    parsed.errors().get(0).message());
        }
        // Within the limit everything after the parser still works.
        assertEquals("-1", value("-".repeat(199) + "1", "int"));
        assertEquals("200", value("1" + " + 1".repeat(199), "int"));
    }

    @Test
    void blocksNestDeeperThanAHundredLevelsAreRefused() {
        var source = new StringBuilder("def f() -> None:\n");
        for (int i = 0; i < 150; i++) {
            source.append("    ".repeat(i + 1)).append("if True:\n");
        }
        source.append("    ".repeat(151)).append("pass\n");
        var parsed = assertDoesNotThrow(() -> ScriptParser.parse(source.toString()));
        assertTrue(parsed.errors().stream().anyMatch(e -> e.message().contains("too many levels of indentation")),
                parsed.errors().toString());
    }

    @Test
    void printChecksTheLineWhileBuildingIt() {
        var arguments = String.join(", ", java.util.Collections.nCopies(2000, "s"));
        assertEquals("MemoryError: lists and strings hold at most 100000 items", run("""
                def f() -> None:
                    s = 'x' * 100000
                    print(%s)
                """.formatted(arguments), "f"));
    }

    @Test
    void caseChangesAndSplitsKeepTheCaps() {
        var tooMany = "MemoryError: lists and strings hold at most 100000 items";
        assertEquals(tooMany, value("len(('ß' * 100000).upper())", "int"));
        assertEquals(tooMany, value("len((',' * 100000).split(','))", "int"));
        assertEquals("100000", value("len((',' * 99999).split(','))", "int"));
    }

    private static final String LINK = """
            class Link:
                data: str
                next: Optional[Link] = None

            """;

    @Test
    void aRecordInsideItselfIsWrittenWithDotsAndComparedToALimit() {
        assertEquals("\"Link(data='a', next=...)\"", run(LINK + """
                def f() -> str:
                    a = Link('a')
                    a.next = a
                    return str(a)
                """, "f"));
        assertEquals("RecursionError: maximum recursion depth exceeded in comparison", run(LINK + """
                def f() -> bool:
                    a = Link('a')
                    a.next = a
                    b = Link('a')
                    b.next = b
                    return a == b
                """, "f"));
    }

    @Test
    void comparingValuesThatShareTheirPartsStopsPastTheStepsWork() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> assertEquals(
                "RuntimeError: one step may compare or write out at most 5000000 values", run("""
                        class Tree:
                            children: list[Tree]

                        def f() -> bool:
                            a = Tree([])
                            b = Tree([])
                            for i in range(40):
                                a = Tree([a, a])
                                b = Tree([b, b])
                            return a == b
                        """, "f")));
    }

    @Test
    void aRunHoldingTooMuchStopsWithMemoryError() {
        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> assertEquals(
                "MemoryError: a run holds at most 4000000 items (characters, list items, fields) at once", run(LINK + """
                        def f() -> None:
                            a = Link('')
                            while True:
                                a = Link('x' * 100000, a)
                        """, "f")));
        // Making as much and letting it go is fine.
        assertEquals("None", run(LINK + """
                def f() -> None:
                    for i in range(200):
                        a = Link('x' * 100000)
                """, "f"));
    }

    @Test
    void aLongStringBecomesAListInOnePass() {
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            assertEquals("100000", value("len(list('x' * 100000))", "int"));
            assertEquals("'y'", run("""
                    def f() -> str:
                        s = 'x' * 99999 + 'y'
                        last = ''
                        for i in range(len(s)):
                            last = s[i]
                        return last
                    """, "f"));
        });
        assertEquals("['a', '😀', 'b']", value("list('a😀b')", "list[str]"));
        assertEquals("'b'", value("'a😀b'[2]", "str"));
    }

    /** Expected values from CPython 3.12. */
    @Test
    void rangesPastSixtyFourBitDistancesAnswerAsPython() {
        assertEquals("True", value("9223372036854775806 in range(-2, 9223372036854775807, 2)", "bool"));
        assertEquals("False", value("9223372036854775805 in range(-2, 9223372036854775807, 2)", "bool"));
        assertEquals("4611686018427387904", value("len(range(0, 9223372036854775807, 2)[:])", "int"));
        assertEquals("'range(-9223372036854775808, -9223372036854775806, 2)'",
                value("str(range(-9223372036854775807 - 1, 0, 2)[:1])", "str"));
        assertEquals("OverflowError: integers are 64-bit; this result is too large",
                value("len(range(0, 4, 4)[::4611686018427387904])", "int"));
    }

    /** Expected values from CPython 3.12. */
    @Test
    void floatFloorDivisionFollowsTheRemainder() {
        assertEquals("9.0", value("1.0 // 0.1", "float"));
        assertEquals("-10.0", value("-1.0 // 0.1", "float"));
        assertEquals("3.0", value("7.5 // 2", "float"));
        assertEquals("-4.0", value("-7.5 // 2", "float"));
        assertEquals("-0.0", value("-0.0 // 5.0", "float"));
        assertEquals("0.0", value("1.0 // 1e308", "float"));
    }

    @Test
    void joinTakesTheKeysOfADictAndTheItemsOfASet() {
        assertEquals("'a,b'", value("','.join({'a': 1, 'b': 2})", "str"));
        assertEquals("'x-y'", value("'-'.join({'x', 'y'})", "str"));
    }

    @Test
    void aTypedStringMadeAtRunTimeIsCheckedBeforeTheCall() {
        var source = """
                def f(text: str, a: Network, b: Network) -> bool:
                    capability: Capability = text
                    return ae2f.set_rule(a, b, capability, "on")
                """;
        assertEquals("ValueError: capability of ae2f.set_rule() must be one of storage, crafting, power, not 'Storage'",
                run(source, "f", "Storage", "main_base", "network-082a5c"));
        assertEquals("ValueError: mode of ae2f.set_rule() must be one of off, on, reexport, not 'enable'", run("""
                def f(text: str, a: Network, b: Network) -> bool:
                    mode: RuleMode = text
                    return ae2f.set_rule(a, b, "storage", mode)
                """, "f", "enable", "main_base", "network-082a5c"));
    }
}
