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
                return ae2.amount(name) < min

            def refill(name: Item, min: int, lamp: Interface) -> None:
                \"""Craft more when the stock runs low.\"""
                if _low(name, min):
                    ae2.craft(name, min - ae2.amount(name))
                    mc.set_redstone(lamp, 15)
                else:
                    mc.set_redstone(lamp, 0)

            def low_iron_alarm() -> None:
                iron: Item = "minecraft:iron_ingot"
                now = ae2.amount(iron)
                if now < 64:
                    ae2.craft(iron, 64 - now)
                    mc.set_redstone("north_gate", 15)
                else:
                    mc.set_redstone("north_gate", 0)
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
                    elif ae2.amount(item) >= each:
                        continue
                    # one request per item
                    ae2.craft(item, each - ae2.amount(item))
                    done += 1
                return done

            def wait_for_power(limit: float) -> None:
                ticks = 0
                while ae2.energy() < limit * 0.5 and ticks % 20 != 0 or ticks // 2 > 100:
                    ticks = ticks + 1
                    mc.wait(1)
                else_value: bool = False
                names: list[str] = ["a", "it's \\"quoted\\"", "tab\\there"]
                nothing: Optional[int] = None
                if -ticks < -(1 + 2) * 3 - (4 - 5):
                    pass
                # before else
                else:
                    return None

            # trailing note
            """;

    /** Default parameter values and keyword arguments, as Python writes them. */
    static final String DEFAULTS = """
            def refill(name: Item, min: int = 64, network: Network = None) -> bool:
                return ae2.craft(name, min, network=network)

            def caller() -> None:
                refill("minecraft:iron_ingot", network="main_base")
                refill(name="minecraft:gold_ingot", min=-1)
            """;

    /** Fields, items, slices and methods, chained in any order, and assignments to fields and items. */
    static final String POSTFIX = """
            def chains(xs: list[int], name: str, order: Order) -> None:
                first = xs[0]
                middle = xs[1:-1]
                every_other = xs[::2]
                tail = xs[2:]
                head = xs[:2]
                xs.append(first)
                xs[0] = xs[-1] + 1
                xs[1:3] = [4, 5]
                xs[0] += 1
                lowered = name.strip().lower()
                parts = "a,b".split(",")
                amount = ae2.storage()[0].amount
                order.amount += 1
                order.item = "minecraft:iron_ingot"
                total = iron_tools.helper(1).total
            """;

    /** Membership and identity tests. */
    static final String MEMBERSHIP = """
            def has(xs: list[int], name: str, network: Network = None) -> bool:
                if 3 in xs and "a" not in name:
                    return not 4 in xs
                if network is None or xs is not None:
                    return True
                return False
            """;

    /** Player records: classes with typed fields only, used across files. */
    static final String RECORDS = """
            # A crafting order.
            class Order:
                \"""What to craft and how much.\"""
                item: Item
                # at least one
                amount: int = 1  # per request
                network: Network = None

            class Empty:
                pass

            def place(o: Order) -> bool:
                o.amount += 1
                return ae2.craft(o.item, o.amount, network=o.network)

            def make() -> Order:
                first = Order("minecraft:iron_ingot", 64)
                other: iron_tools.Order = iron_tools.Order(item="minecraft:gold_ingot")
                return first
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
    void defaultsAndKeywordArgumentsRoundTrip() {
        assertRoundTrip(DEFAULTS);
    }

    @Test
    void defaultsAndKeywordArgumentsParse() {
        var program = parseClean(DEFAULTS);
        var refill = program.functions().get(0);
        assertNull(refill.params().get(0).defaultValue());
        assertEquals(64L, assertInstanceOf(Expr.IntLit.class, refill.params().get(1).defaultValue()).value());
        assertInstanceOf(Expr.NoneLit.class, refill.params().get(2).defaultValue());

        var craft = assertInstanceOf(Expr.Call.class, ((Stmt.Return) refill.body().get(0)).value());
        assertEquals(2, craft.args().size());
        assertEquals(List.of("network"), craft.keywords().stream().map(Expr.Keyword::name).toList());

        var second = ((Stmt.CallStmt) program.functions().get(1).body().get(1)).call();
        assertEquals(List.of(), second.args());
        assertEquals(List.of("name", "min"), second.keywords().stream().map(Expr.Keyword::name).toList());
    }

    @Test
    void postfixChainsRoundTrip() {
        assertRoundTrip(POSTFIX);
    }

    @Test
    void postfixChainsParse() {
        var body = parseClean(POSTFIX).functions().get(0).body();
        var first = (Stmt.Assign) body.get(0);
        assertEquals(new Expr.Name(first.target().id(), "first"), first.target());
        assertInstanceOf(Expr.Index.class, first.value());
        var tail = assertInstanceOf(Expr.Slice.class, ((Stmt.Assign) body.get(3)).value());
        assertNotNull(tail.lower());
        assertNull(tail.upper());
        assertNull(tail.step());

        var append = ((Stmt.CallStmt) body.get(5)).call();
        assertEquals("xs", assertInstanceOf(Expr.Name.class, append.receiver()).name());
        assertEquals("append", append.name());
        assertInstanceOf(Expr.Index.class, ((Stmt.Assign) body.get(6)).target());
        assertInstanceOf(Expr.Slice.class, ((Stmt.Assign) body.get(7)).target());

        var amount = assertInstanceOf(Expr.Attribute.class, ((Stmt.Assign) body.get(11)).value());
        assertEquals("amount", amount.name());
        var storage = assertInstanceOf(Expr.Call.class, assertInstanceOf(Expr.Index.class, amount.target()).target());
        assertEquals("ae2", assertInstanceOf(Expr.Name.class, storage.receiver()).name());

        var increment = assertInstanceOf(Stmt.AugAssign.class, body.get(12));
        assertEquals("amount", assertInstanceOf(Expr.Attribute.class, increment.target()).name());
    }

    @Test
    void membershipRoundTripsAndParses() {
        assertRoundTrip(MEMBERSHIP);
        var branch = (Stmt.If) parseClean(MEMBERSHIP).functions().get(0).body().get(0);
        var both = (Expr.Binary) branch.condition();
        assertEquals(BinaryOp.IN, ((Expr.Binary) both.left()).op());
        assertEquals(BinaryOp.NOT_IN, ((Expr.Binary) both.right()).op());
        var negated = assertInstanceOf(Expr.Unary.class, ((Stmt.Return) branch.body().get(0)).value());
        assertEquals(BinaryOp.IN, ((Expr.Binary) negated.operand()).op(), "not binds looser than in, as in Python");
        var identity = (Expr.Binary) ((Stmt.If) parseClean(MEMBERSHIP).functions().get(0).body().get(1)).condition();
        assertEquals(BinaryOp.IS, ((Expr.Binary) identity.left()).op());
        assertEquals(BinaryOp.IS_NOT, ((Expr.Binary) identity.right()).op());
    }

    @Test
    void recordsRoundTrip() {
        assertRoundTrip(RECORDS);
    }

    @Test
    void recordsParse() {
        var program = parseClean(RECORDS);
        assertEquals(List.of("Order", "Empty", "place", "make"),
                program.definitions().stream().map(Definition::name).toList());
        assertEquals(List.of("place", "make"), program.functions().stream().map(FunctionDef::name).toList());
        var order = program.classes().get(0);
        assertEquals(List.of(" A crafting order."), order.notes().above());
        assertEquals("What to craft and how much.", order.docstring());
        assertEquals(List.of("item", "amount", "network"), order.fields().stream().map(Field::name).toList());
        var amount = order.fields().get(1);
        assertEquals(new TypeRef("int", List.of()), amount.type());
        assertEquals(1L, assertInstanceOf(Expr.IntLit.class, amount.defaultValue()).value());
        assertEquals(new Notes(List.of(" at least one"), " per request"), amount.notes());
        assertNull(order.fields().get(0).defaultValue());
        assertEquals(List.of(), program.classes().get(1).fields());

        var other = (Stmt.Assign) program.functions().get(1).body().get(1);
        assertEquals("iron_tools.Order", other.type().name(), "a class of another file");
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
        assertEquals("ae2", assertInstanceOf(Expr.Name.class, craft.receiver()).name());
        assertEquals("craft", craft.name());
        assertNotNull(branch.orElse());

        var alarm = program.functions().get(2);
        var iron = assertInstanceOf(Stmt.Assign.class, alarm.body().get(0));
        assertEquals("iron", assertInstanceOf(Expr.Name.class, iron.target()).name());
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
                    g(1, b=2)
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
                    g(1, b = 2,)
                """));
        assertEquals("def f(x: int = 1) -> None:\n    pass\n", print("def f(x:int=1)->None:\n    pass\n"));
        assertEquals("class A:\n    x: int\n", print("class A():\n    x : int\n    pass\n"));
        assertEquals(body("y = (-x).real + (1).real + xs[1:2] + a.b[0]"),
                print(body("y = ( - x ) . real + (1).real + xs[ 1 : 2 : ] + (a.b)[0]")));
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
                Arguments.of("x = 1\n", 1, "only function and class definitions"),
                Arguments.of("@cache\ndef f() -> None:\n    pass\n", 1, "decorators are not supported"),
                Arguments.of("async def f() -> None:\n    pass\n", 1, "'async' is not supported"),
                // classes
                Arguments.of("class A(B):\n    x: int\n", 1, "inheritance is not supported"),
                Arguments.of("class A:\n    def f(self) -> None:\n        pass\n", 2, "methods are not supported"),
                Arguments.of("class A:\n    x = 1\n", 2, "field 'x' needs a type hint"),
                Arguments.of("class A:\n    x: int = 1\n    y: int\n", 3,
                        "non-default argument 'y' follows default argument"),
                Arguments.of("class A:\n    \"\"\"Doc.\"\"\"\n    x: int\n    f()\n", 4, "a class holds only fields"),
                Arguments.of("@dataclass\nclass A:\n    x: int\n", 1, "decorators are not supported"),
                Arguments.of(body("class A:\n        pass"), 2, "nested classes are not supported"),
                // signatures
                Arguments.of("def f(x) -> None:\n    pass\n", 1, "parameter 'x' needs a type hint"),
                Arguments.of("def f(x: int):\n    pass\n", 1, "function 'f' needs a return type hint"),
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
                Arguments.of(body("y = x in xs not in [xs]"), 2, "chained comparisons"),
                Arguments.of(body("y = x is None is None"), 2, "chained comparisons"),
                Arguments.of(body("y = x ** 2"), 2, "'**' is not supported"),
                Arguments.of(body("y = x & 1"), 2, "'&' is not supported"),
                Arguments.of(body("y = x << 1"), 2, "'<<' is not supported"),
                Arguments.of(body("y = ~x"), 2, "'~' is not supported"),
                Arguments.of(body("y = +x"), 2, "unary '+' is not supported"),
                Arguments.of(body("y = [x for x in xs]"), 2, "comprehensions are not supported"),
                Arguments.of(body("y = {1: 2}"), 2, "dicts and sets are not supported"),
                Arguments.of(body("y = lambda: 0"), 2, "'lambda' is not supported"),
                Arguments.of(body("xs[0](1)"), 2, "only a function name can be called"),
                Arguments.of(body("y = xs[1, 2]"), 2, "tuples are not supported"),
                Arguments.of(body("f(**x)"), 2, "unpacking arguments with '**'"),
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

    /** Mistakes CPython's own parser rejects too, with the same message. */
    static Stream<Arguments> pythonSyntaxErrors() {
        return Stream.of(
                Arguments.of("def f(x: int = 1, y: int) -> None:\n    pass\n", 1,
                        "parameter without a default follows parameter with a default"),
                Arguments.of(body("f(x=1, 2)"), 2, "positional argument follows keyword argument"),
                Arguments.of(body("f(x=1, x=2)"), 2, "keyword argument repeated: x"),
                Arguments.of("def f(x: int, x: int) -> None:\n    pass\n", 1,
                        "duplicate argument 'x' in function definition"),
                Arguments.of(body("break"), 2, "'break' outside loop"),
                Arguments.of(body("if x:\n        continue"), 3, "'continue' not properly in loop"));
    }

    @ParameterizedTest
    @MethodSource("pythonSyntaxErrors")
    void pythonsOwnSyntaxErrorsAreReportedAsPythonDoes(String source, int line, String message) {
        eachUnsupportedConstructIsNamedAtItsLine(source, line, message);
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

    @Test
    void anUnclosedBracketCostsOnlyItsOwnStatement() {
        var result = ScriptParser.parse("""
                def f(gate: Interface) -> None:
                    ae2.craft(
                    mc.redstone(gate, 1)
                    x = [1,
                         2]
                    y = sum(
                        x,
                    )

                def g() -> None:
                    pass
                """);
        assertEquals(1, result.errors().size(), result.errors().toString());
        var error = result.errors().get(0);
        assertEquals(new Position(2, 14), new Position(error.line(), error.column()));
        assertTrue(error.message().contains("'(' was never closed"), error.toString());
        assertEquals(List.of("f", "g"), result.program().functions().stream().map(FunctionDef::name).toList());
        assertEquals(3, result.program().functions().get(0).body().size(), "redstone, x and y still parse");
    }

    /** Inside brackets indentation does not matter, as in Python; recovery applies only to a bracket never closed. */
    static final String CONTINUATION = """
            def f(x: int) -> None:
                y = foo(x,
            1)
                mc.redstone(y)
            """;

    @Test
    void continuationLinesMayHaveAnyIndentation() {
        var result = ScriptParser.parse(CONTINUATION);
        assertEquals(List.of(), result.errors());
        assertEquals(2, result.program().functions().get(0).body().size());
    }

    /** The subset stays valid Python: CPython's compiler accepts every sample and every rejected construct. */
    @Test
    void pythonAcceptsTheSamplesAndTheRejectedConstructs() throws Exception {
        Assumptions.assumeTrue(pythonAvailable(), "python3 is not on PATH");
        assertPythonParses(SAMPLE);
        assertPythonParses(LOOPS);
        assertPythonParses(CONTINUATION);
        assertPythonParses(DEFAULTS);
        assertPythonParses(POSTFIX);
        assertPythonParses(MEMBERSHIP);
        assertPythonParses(RECORDS);
        assertPythonParses(print(SAMPLE));
        assertPythonParses(print(LOOPS));
        for (var arguments : unsupported().toList()) {
            assertPythonParses((String) arguments.get()[0]);
        }
        for (var arguments : pythonSyntaxErrors().toList()) {
            assertPythonRejects((String) arguments.get()[0], (String) arguments.get()[2]);
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
        var output = pythonParse(source);
        assertEquals(0, output.exit(), "Python rejects:\n" + source + "\n" + output.text());
    }

    private static void assertPythonRejects(String source, String message) throws Exception {
        var output = pythonParse(source);
        assertNotEquals(0, output.exit(), "Python accepts:\n" + source);
        assertTrue(output.text().contains(message), "Python says otherwise:\n" + output.text());
    }

    private record PythonOutput(int exit, String text) {
    }

    private static PythonOutput pythonParse(String source) throws Exception {
        var process = new ProcessBuilder("python3", "-I", "-c",
                "import sys; compile(sys.stdin.read(), '<script>', 'exec')")
                .redirectErrorStream(true).start();
        try (var in = process.getOutputStream()) {
            in.write(source.getBytes(StandardCharsets.UTF_8));
        }
        assertTrue(process.waitFor(30, TimeUnit.SECONDS));
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new PythonOutput(process.exitValue(), output);
    }
}
