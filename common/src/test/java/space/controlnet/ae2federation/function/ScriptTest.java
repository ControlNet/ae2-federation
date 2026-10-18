package space.controlnet.ae2federation.function;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** The Python subset: script -> IR -> script, canonical printing, and a named error for everything outside it. */
final class ScriptTest {
    /** The sample shown on the design canvas. */
    static final String SAMPLE = """
            # Keep the workshop stocked.
            def _low(name: Item, min: int) -> bool:
                return ae2.stock(name) < min

            def refill(name: Item, min: int) -> None:
                \"""Craft more when the stock runs low.\"""
                if _low(name, min):
                    ae2.craft(name, min - ae2.stock(name))
                    mc.redstone(15)
                else:
                    mc.redstone(0)

            def low_iron_alarm() -> None:
                iron: Item = "minecraft:iron_ingot"
                now = ae2.stock(iron)
                if now < 64:
                    ae2.craft(iron, 64 - now)
                    mc.redstone(15)
                else:
                    mc.redstone(0)
            """;

    /** Loops, every operator, literals of every kind, and comments in every place they can belong. */
    static final String LOOPS = """
            def spread(items: list[Item], each: int) -> int:
                \"""Craft each item in turn.
                Stops at the first that cannot be crafted.\"""
                done = 0
                for item in items:  # in list order
                    if not ae2.craftable(item):
                        break
                    elif ae2.stock(item) >= each:
                        continue
                    # one request per item
                    ae2.craft(item, each - ae2.stock(item))
                    done += 1
                return done

            def wait_for_power(limit: float) -> None:
                ticks = 0
                while ae2.stored_power() < limit * 0.5 and ticks % 20 != 0 or ticks // 2 > 100:
                    ticks = ticks + 1
                    mc.wait(1)
                else_value: bool = False
                names: list[str] = ["a", "it's \\"quoted\\"", "tab\\there"]
                nothing = None
                if -ticks < -(1 + 2) * 3 - (4 - 5):
                    pass
                # before else
                else:
                    return None

            # trailing note
            """;

    @Test
    void theCanvasSampleRoundTrips() {
        assertRoundTrip(SAMPLE);
    }

    @Test
    void loopsOperatorsAndCommentsRoundTrip() {
        assertRoundTrip(LOOPS);
    }

    @Test
    void theSampleParsesToTheExpectedProgram() {
        var program = parseClean(SAMPLE);
        assertEquals(List.of("_low", "refill", "low_iron_alarm"), program.functions().stream().map(FunctionDef::name).toList());

        var low = program.functions().get(0);
        assertEquals(List.of(" Keep the workshop stocked."), low.notes().above());
        assertEquals(List.of(new TypeRef("Item", List.of()), new TypeRef("int", List.of())),
                low.params().stream().map(Param::type).toList());
        assertEquals("bool", low.result().name());

        var refill = program.functions().get(1);
        assertEquals("Craft more when the stock runs low.", refill.docstring());
        var branch = assertInstanceOf(Stmt.If.class, refill.body().get(0));
        var craft = assertInstanceOf(Stmt.CallStmt.class, branch.body().get(0)).call();
        assertEquals("ae2", craft.qualifier());
        assertEquals("craft", craft.name());
        assertNotNull(branch.orElse());

        var alarm = program.functions().get(2);
        var iron = assertInstanceOf(Stmt.Assign.class, alarm.body().get(0));
        assertEquals("iron", iron.target());
        assertEquals("Item", iron.type().name());
        assertEquals("minecraft:iron_ingot", assertInstanceOf(Expr.StrLit.class, iron.value()).value());
        var now = assertInstanceOf(Stmt.Assign.class, alarm.body().get(1));
        assertNull(now.type(), "an unannotated local takes its type from the value later");
    }

    @Test
    void intAndFloatLiteralsStayApart() {
        var body = parseClean("""
                def f() -> None:
                    a = 64
                    b = 0.5
                    c = 7 // 2
                    d = 7 % 2
                    e = 7 / 2
                """).functions().get(0).body();
        assertEquals(64L, ((Expr.IntLit) ((Stmt.Assign) body.get(0)).value()).value());
        assertEquals(0.5, ((Expr.FloatLit) ((Stmt.Assign) body.get(1)).value()).value());
        assertEquals(BinaryOp.FLOOR_DIV, ((Expr.Binary) ((Stmt.Assign) body.get(2)).value()).op());
        assertEquals(BinaryOp.MOD, ((Expr.Binary) ((Stmt.Assign) body.get(3)).value()).op());
        assertEquals(BinaryOp.DIV, ((Expr.Binary) ((Stmt.Assign) body.get(4)).value()).op());
    }

    @Test
    void printingIsCanonical() {
        assertEquals("""
                def f(xs: list[int]) -> None:
                    s = "it's"
                    t = 1000.0
                    u = a + b * c
                    v = (a + b) * c
                    w = a - (b - c)
                    x = not a and b
                    y = (not a) == b
                    mc.redstone(15)
                    z = [1, 2]
                """, print("""
                def f( xs :list[ int ],)->None :
                    s = 'it\\'s'
                    t = 1e3
                    u = a + (b * c)
                    v = (a + b) * c
                    w = a - (b - c)
                    x = (not a) and b
                    y = (not a) == b
                    mc.redstone(
                        15,
                    )
                    z = [1, 2,]
                """));
    }

    @Test
    void aCommentAboveTheDocstringMovesBelowIt() {
        assertEquals("""
                def f() -> None:
                    \"""Doc.\"""
                    # note
                    mc.redstone(1)
                """, print("""
                def f() -> None:
                    # note
                    \"""Doc.\"""
                    mc.redstone(1)
                """));
    }

    @Test
    void anEmptyBodyPrintsPass() {
        var f = new FunctionDef("f1", Notes.NONE, "noop", List.of(), new TypeRef("None", List.of()), null, List.of());
        assertEquals("def noop() -> None:\n    pass\n", ScriptPrinter.print(new Program(List.of(f), List.of())));
    }

    @Test
    void nodesRememberWhereTheyStart() {
        var result = ScriptParser.parse(SAMPLE);
        var refill = result.program().functions().get(1);
        assertEquals(new Position(5, 1), result.positions().get(refill.id()));
        var branch = (Stmt.If) refill.body().get(0);
        assertEquals(new Position(7, 5), result.positions().get(branch.id()));
        var call = ((Stmt.CallStmt) branch.body().get(0)).call();
        assertEquals(new Position(8, 9), result.positions().get(call.id()));
    }

    static Stream<Arguments> unsupported() {
        return Stream.of(
                // whole-file constructs
                Arguments.of("import math\n", 1, "'import' is not supported"),
                Arguments.of("from math import pi\n", 1, "'from' is not supported"),
                Arguments.of("class A:\n    pass\n", 1, "'class' is not supported"),
                Arguments.of("x = 1\n", 1, "only function definitions"),
                Arguments.of("@cache\ndef f() -> None:\n    pass\n", 1, "decorators are not supported"),
                Arguments.of("async def f() -> None:\n    pass\n", 1, "'async' is not supported"),
                // signatures
                Arguments.of("def f(x) -> None:\n    pass\n", 1, "parameter 'x' needs a type hint"),
                Arguments.of("def f(x: int):\n    pass\n", 1, "function 'f' needs a return type hint"),
                Arguments.of("def f(x: int = 1) -> None:\n    pass\n", 1, "default values are not supported"),
                Arguments.of("def f(*xs: int) -> None:\n    pass\n", 1, "*args"),
                Arguments.of("def f(x: int | None) -> None:\n    pass\n", 1, "union types"),
                Arguments.of("def f(x: \"int\") -> None:\n    pass\n", 1, "string annotations"),
                Arguments.of("def f() -> None: pass\n", 1, "own indented lines"),
                Arguments.of("def f() -> None:\n    \"\"\"Doc.\"\"\"  # note\n", 2, "comment after a docstring"),
                // statements
                Arguments.of(body("def g() -> None:\n        pass"), 2, "nested functions are not supported"),
                Arguments.of(body("try:\n        pass\n    except:\n        pass"), 2, "'try' is not supported"),
                Arguments.of(body("with x:\n        pass"), 2, "'with' is not supported"),
                Arguments.of(body("while x:\n        pass\n    else:\n        pass"), 4, "'else' after a loop"),
                Arguments.of(body("for i in xs:\n        pass\n    else:\n        pass"), 4, "'else' after a loop"),
                Arguments.of(body("global y"), 2, "'global' is not supported"),
                Arguments.of(body("assert x"), 2, "'assert' is not supported"),
                Arguments.of(body("raise x"), 2, "'raise' is not supported"),
                Arguments.of(body("del x"), 2, "'del' is not supported"),
                Arguments.of(body("yield x"), 2, "'yield' is not supported"),
                Arguments.of(body("if x: f()"), 2, "own indented lines"),
                Arguments.of(body("f(); f()"), 2, "semicolons are not supported"),
                Arguments.of(body("x == 1"), 2, "only a call can stand on its own line"),
                Arguments.of(body("y: int"), 2, "needs a value"),
                Arguments.of(body("y = z = 1"), 2, "chained assignment"),
                Arguments.of(body("a, b = 1, 2"), 2, "tuples are not supported"),
                Arguments.of(body("return x, 1"), 2, "tuples are not supported"),
                Arguments.of(body("for i, j in xs:\n        pass"), 2, "tuples are not supported"),
                Arguments.of(body("y = x\\\n        + 1"), 2, "'\\'"),
                // expressions
                Arguments.of(body("y = x if x else 0"), 2, "conditional expressions"),
                Arguments.of(body("y = 0 < x < 9"), 2, "chained comparisons"),
                Arguments.of(body("y = x in [1]"), 2, "'in' is only supported in 'for'"),
                Arguments.of(body("y = x not in [1]"), 2, "'not in' is not supported"),
                Arguments.of(body("y = x is None"), 2, "'is' is not supported"),
                Arguments.of(body("y = x ** 2"), 2, "'**' is not supported"),
                Arguments.of(body("y = x & 1"), 2, "'&' is not supported"),
                Arguments.of(body("y = x << 1"), 2, "'<<' is not supported"),
                Arguments.of(body("y = ~x"), 2, "'~' is not supported"),
                Arguments.of(body("y = +x"), 2, "unary '+' is not supported"),
                Arguments.of(body("y = [x for x in xs]"), 2, "comprehensions are not supported"),
                Arguments.of(body("y = {1: 2}"), 2, "dicts and sets are not supported"),
                Arguments.of(body("y = lambda: 0"), 2, "'lambda' is not supported"),
                Arguments.of(body("y = xs[0]"), 2, "indexing is not supported"),
                Arguments.of(body("mc.say(x, end=\"\")"), 2, "keyword arguments are not supported"),
                Arguments.of(body("y = ae2.stock"), 2, "attribute access is not supported"),
                Arguments.of(body("y = a.b.c(1)"), 2, "at most one qualifier"),
                Arguments.of(body("f(x)(1)"), 2, "calling the result of a call"),
                Arguments.of(body("y = (x := 1)"), 2, "':=' is not supported"),
                Arguments.of(body("y = ..."), 2, "'...' is not supported"),
                Arguments.of(body("y = \"a\" \"b\""), 2, "adjacent strings"),
                Arguments.of(body("y = f\"{x}\""), 2, "string prefixes"),
                Arguments.of(body("y = 0x1F"), 2, "hexadecimal"),
                Arguments.of(body("y = 1_000"), 2, "underscores in numbers"),
                Arguments.of(body("y = 1j"), 2, "complex numbers"),
                Arguments.of(body("y = \"\\x41\""), 2, "escape"),
                Arguments.of(body("y = 99999999999999999999"), 2, "64-bit"));
    }

    private static String body(String statement) {
        return "def f(x: int, xs: list[int]) -> None:\n    " + statement + "\n";
    }

    @ParameterizedTest
    @MethodSource("unsupported")
    void eachUnsupportedConstructIsNamedAtItsLine(String source, int line, String message) {
        var errors = ScriptParser.parse(source).errors();
        assertFalse(errors.isEmpty(), "no error for:\n" + source);
        var first = errors.get(0);
        assertEquals(line, first.line(), first.toString());
        assertTrue(first.message().contains(message), first.toString());
    }

    @Test
    void indentationMistakesAreReported() {
        var tab = ScriptParser.parse("def f() -> None:\n\tpass\n").errors();
        assertEquals(2, tab.get(0).line());
        assertTrue(tab.get(0).message().contains("spaces"), tab.toString());

        var dedent = ScriptParser.parse("def f(x: int) -> None:\n    if x > 0:\n        pass\n  pass\n").errors();
        assertEquals(4, dedent.get(0).line());
        assertTrue(dedent.get(0).message().contains("indentation"), dedent.toString());
    }

    @Test
    void parsingGoesOnAfterAnErrorAndKeepsWhatParsed() {
        var result = ScriptParser.parse("""
                def ok() -> None:
                    mc.redstone(1)

                def broken(x: int) -> None:
                    y = x ** 2
                    mc.redstone(x)
                    if x > 0:
                        z = x if x else 0
                        mc.redstone(2)

                def bad_header(x) -> None:
                    mc.redstone(1)

                def tail() -> None:
                    pass
                """);
        assertEquals(List.of(5, 8, 11), result.errors().stream().map(ScriptError::line).toList(), result.errors().toString());
        assertEquals(List.of("ok", "broken", "tail"), result.program().functions().stream().map(FunctionDef::name).toList());
        var broken = result.program().functions().get(1).body();
        assertEquals(2, broken.size(), "the bad assignment is left out, the rest stays");
        assertEquals(1, ((Stmt.If) broken.get(1)).body().size());
    }

    /** The subset stays valid Python: CPython's own parser accepts every sample and every rejected construct. */
    @Test
    void pythonAcceptsTheSamplesAndTheRejectedConstructs() throws Exception {
        Assumptions.assumeTrue(pythonAvailable(), "python3 is not on PATH");
        assertPythonParses(SAMPLE);
        assertPythonParses(LOOPS);
        assertPythonParses(print(SAMPLE));
        assertPythonParses(print(LOOPS));
        for (var arguments : unsupported().toList()) {
            assertPythonParses((String) arguments.get()[0]);
        }
    }

    private static void assertRoundTrip(String source) {
        var printed = ScriptPrinter.print(parseClean(source));
        assertEquals(source, printed);
        assertEquals(printed, print(printed));
    }

    private static Program parseClean(String source) {
        var result = ScriptParser.parse(source);
        assertEquals(List.of(), result.errors());
        return result.program();
    }

    private static String print(String source) {
        return ScriptPrinter.print(parseClean(source));
    }

    private static boolean pythonAvailable() {
        try {
            return new ProcessBuilder("python3", "--version").start().waitFor(10, TimeUnit.SECONDS);
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private static void assertPythonParses(String source) throws Exception {
        var process = new ProcessBuilder("python3", "-I", "-c", "import ast, sys; ast.parse(sys.stdin.read())")
                .redirectErrorStream(true).start();
        try (var in = process.getOutputStream()) {
            in.write(source.getBytes(StandardCharsets.UTF_8));
        }
        assertTrue(process.waitFor(30, TimeUnit.SECONDS));
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), "Python rejects:\n" + source + "\n" + output);
    }
}
