package space.controlnet.ae2federation.function;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Static checks: one case per rule, each pointing at its line. Messages follow CPython's where it has one. */
final class CheckerTest {
    /** Another file of the server's programs. */
    static final Map<String, Program> FILES = Map.of("iron_tools", ScriptParser.parse("""
            class Order:
                item: Item
                amount: int = 1

            def _low(name: Item, min: int) -> bool:
                return ae2.amount(name) < min

            def make(item: Item) -> Order:
                return Order(item)

            def refill(name: Item, min: int) -> None:
                pass

            def pulse(gate: Interface) -> None:
                _emit(gate)

            def _emit(gate: Interface) -> None:
                mc.set_redstone(gate, 15)
            """).program());

    static final Workspace WORKSPACE = new Workspace(FILES, CompletionTest.CATALOG);

    /** Definitions every rule case can use; the case's body starts at line {@value #BODY_LINE}. */
    private static final String PRELUDE = """
            class Order:
                item: Item
                amount: int = 1

            def g(x: int, y: int = 1) -> int:
                return x + y

            def f(n: int, s: str, xs: list[int], o: Order, st: Stack, b: Block) -> None:
            """;
    private static final int BODY_LINE = 9;

    private static Checker.Result check(String source) {
        var parsed = ScriptParser.parse(source);
        assertEquals(List.of(), parsed.errors(), "the source must parse");
        return Checker.check(parsed.program(), WORKSPACE);
    }

    /** Problems as {@code line: message}, by line. */
    private static List<String> problems(String source) {
        var parsed = ScriptParser.parse(source);
        assertEquals(List.of(), parsed.errors(), "the source must parse");
        return Checker.check(parsed.program(), WORKSPACE).problems().stream()
                .sorted(java.util.Comparator.comparingInt(p -> parsed.positions().get(p.node()).line()))
                .map(p -> parsed.positions().get(p.node()).line() + ": " + p.message()).toList();
    }

    @Test
    void printTakesAnyValuesAndASeparator() {
        assertEquals(List.of(), problems("""
                def main() -> None:
                    print("stock", 64, [1, 2], sep=": ")
                    print()
                """));
        assertEquals(List.of("2: print() end= is not supported; each print is one log line",
                "3: 'colour' is an invalid keyword argument for print()",
                "4: sep must be None or a string, not int"), problems("""
                def main() -> None:
                    print("a", end="")
                    print("a", colour=1)
                    print("a", sep=1)
                """));
    }

    @Test
    void theSamplesHaveNoProblems() {
        for (var sample : List.of(ScriptTest.SAMPLE, ScriptTest.LOOPS, ScriptTest.DEFAULTS, ScriptTest.MEMBERSHIP,
                ScriptTest.RECORDS)) {
            assertEquals(List.of(), problems(sample), sample);
        }
    }

    @Test
    void anInterfaceIsABlockButABlockIsNoInterface() {
        assertEquals(List.of(), problems("""
                def h(port: Interface, b: Block) -> None:
                    level = mc.redstone(port)
                    ahead = mc.redstone(ae2f.select(port))
                    name = mc.block_id(port)
                    age: Optional[str] = mc.state(b, "age")
                    ripe = int(mc.state(b, "age")) == 7
                    made: list[Resource] = ae2.patterns()
                    mc.set_redstone(port, 15)
                """));
        assertEquals(List.of("2: argument 'interface' of mc.set_redstone() must be Interface, not Block"),
                problems("""
                        def h(b: Block) -> None:
                            mc.set_redstone(b, 15)
                        """));
    }

    static Stream<Arguments> rules() {
        return Stream.of(
                // names
                Arguments.of("y = z", 1, "name 'z' is not defined"),
                Arguments.of("y = g", 1, "functions are not values; call g(...)"),
                Arguments.of("y = ae2", 1, "'ae2' is a namespace; call its functions as ae2.name(...)"),
                Arguments.of("y = iron_tools", 1, "'iron_tools' is a file; use it as iron_tools.name(...)"),
                Arguments.of("n(1)", 1, "'int' object is not callable"),
                // calls, with CPython's messages
                Arguments.of("g()", 1, "g() missing 1 required positional argument: 'x'"),
                Arguments.of("g(1, 2, 3)", 1, "g() takes from 1 to 2 positional arguments but 3 were given"),
                Arguments.of("g(1, z=2)", 1, "g() got an unexpected keyword argument 'z'"),
                Arguments.of("g(1, x=2)", 1, "g() got multiple values for argument 'x'"),
                Arguments.of("y = Order()", 1, "Order() missing 1 required positional argument: 'item'"),
                Arguments.of("ae2.craft(\"minecraft:iron_ingot\", \"many\")", 1,
                        "argument 'amount' of ae2.craft() must be int, not str"),
                Arguments.of("ae2.amount(s)", 1, "argument 'resource' of ae2.amount() must be Resource, not str;"
                        + " a str becomes a Resource through an annotation, as in x: Resource = ..."),
                Arguments.of("y = ae2.missing()", 1, "ae2 has no function 'missing'"),
                Arguments.of("y = iron_tools.missing()", 1, "iron_tools has no function 'missing'"),
                Arguments.of("y = no_file.low()", 1, "name 'no_file' is not defined"),
                Arguments.of("y = iron_tools._low(\"minecraft:iron_ingot\", 1)", 1, "'_low' is private to iron_tools"),
                // assignments
                Arguments.of("y = 1\n    y = \"a\"", 2, "y is int; it cannot hold str"),
                Arguments.of("y: Item = n", 1, "y is Item; it cannot hold int"),
                Arguments.of("n: str = s", 1, "n is int; it cannot be annotated str"),
                Arguments.of("y: Foo = 1", 1, "unknown type 'Foo'"),
                Arguments.of("y: list = []", 1, "list needs its element type, as in list[int]"),
                Arguments.of("y = []", 1, "annotate the empty list, as in y: list[int] = []"),
                Arguments.of("y = [1, \"a\"]", 1, "list items must share one type, here int and str"),
                Arguments.of("return 1", 1, "f() returns None; this return has a value"),
                // fields, items and slices
                Arguments.of("y = o.weight", 1, "'Order' object has no attribute 'weight'"),
                Arguments.of("st.amount = 1", 1, "cannot assign to field 'amount'"),
                Arguments.of("o.amount = \"x\"", 1, "field 'amount' of Order is int; it cannot hold str"),
                Arguments.of("y = n[0]", 1, "'int' object is not subscriptable"),
                Arguments.of("y = xs[\"a\"]", 1, "list indices must be integers or slices, not str"),
                Arguments.of("s[0] = \"a\"", 1, "'str' object does not support item assignment"),
                Arguments.of("s[\"a\"] = 1", 1, "'str' object does not support item assignment"),
                Arguments.of("y = xs[\"a\":]", 1, "slice indices must be integers or None"),
                // operators
                Arguments.of("y = n + s", 1, "unsupported operand type(s) for +: 'int' and 'str'"),
                Arguments.of("y = s % n", 1, "% on strings is not supported; build the text with + and str()"),
                Arguments.of("y = n < s", 1, "'<' not supported between instances of 'int' and 'str'"),
                Arguments.of("y = n in n", 1, "argument of type 'int' is not iterable"),
                Arguments.of("y = n in s", 1, "'in <string>' requires string as left operand, not int"),
                Arguments.of("y = n and s", 1, "'and' needs bool on both sides, here int and str; compare first, as in n > 0"),
                Arguments.of("y = n > 0 or s", 1, "'or' needs bool on both sides, here bool and str; compare first, as in n > 0"),
                Arguments.of("y = None", 1, "y cannot start as None; give it a type, as in y: Optional[int] = None"),
                Arguments.of("y = -s", 1, "bad operand type for unary -: 'str'"),
                Arguments.of("n += 0.5", 1, "n is int; it cannot hold float"),
                Arguments.of("for c in n:\n        pass", 1, "'int' object is not iterable"),
                // methods and built-ins
                Arguments.of("xs.append(s)", 1, "argument 'object' of list.append() must be int, not str"),
                Arguments.of("xs.push(1)", 1, "'list' object has no attribute 'push'"),
                Arguments.of("y = st.split()", 1, "'Stack' object has no attribute 'split'"),
                Arguments.of("y = len(n)", 1, "object of type 'int' has no len()"),
                Arguments.of("y = len(xs, xs)", 1, "len() takes exactly one argument (2 given)"),
                Arguments.of("y = len(obj=xs)", 1, "len() takes no keyword arguments"),
                Arguments.of("y = round(1.5, nd=1)", 1, "'nd' is an invalid keyword argument for round()"),
                Arguments.of("y = round(2.5, number=1)", 1,
                        "argument for round() given by name ('number') and position (1)"),
                Arguments.of("y = round(2.5, 1, ndigits=1)", 1, "round() takes at most 2 arguments (3 given)"),
                Arguments.of("y = round(ndigits=1)", 1, "round() missing required argument 'number' (pos 1)"),
                Arguments.of("y = round(1.5, 0.5)", 1, "'float' object cannot be interpreted as an integer"),
                Arguments.of("y = sum(iterable=xs)", 1, "'iterable' is an invalid keyword argument for sum()"),
                Arguments.of("y = sum(start=1)", 1, "sum() takes at least 1 positional argument (0 given)"),
                Arguments.of("y = sum(xs, start=s)", 1, "sum() can't sum strings [use ''.join(seq) instead]"),
                Arguments.of("y = sum(xs, xs)", 1, "sum() start must be a number, not 'list'"),
                Arguments.of("y = min(xs, key=s)", 1, "min() key= is not supported; functions are not values"),
                Arguments.of("y = max(xs, x=1)", 1, "'x' is an invalid keyword argument for max()"),
                Arguments.of("y = min(1, 2, default=0)", 1,
                        "Cannot specify a default for min() with multiple positional arguments"),
                Arguments.of("y = max(xs, default=s)", 1, "default of max() must be int, not str"),
                Arguments.of("y = range(s)", 1, "'str' object cannot be interpreted as an integer"),
                Arguments.of("y = abs(s)", 1, "bad operand type for abs(): 'str'"),
                Arguments.of("y = int(xs)", 1, "int() argument must be a string or a number, not 'list'"),
                Arguments.of("y = max(n, s)", 1, "'>' not supported between instances of 'str' and 'int'"),
                Arguments.of("y = s[s]", 1, "string indices must be integers, not 'str'"),
                Arguments.of("y = round(s)", 1, "type str doesn't define __round__ method"),
                // a range is not a list
                Arguments.of("r = range(3)\n    r.append(1)", 2, "'range' object has no attribute 'append'"),
                Arguments.of("ys: list[int] = range(3)", 1, "ys is list[int]; it cannot hold range"),
                Arguments.of("y = range(3)[s]", 1, "range indices must be integers or slices, not str"),
                Arguments.of("range(3)[0] = 1", 1, "'range' object does not support item assignment"),
                Arguments.of("y = list(n)", 1, "'int' object is not iterable"),
                Arguments.of("y = [n] < [s]", 1, "'<' not supported between instances of 'list' and 'list'"));
    }

    @ParameterizedTest
    @MethodSource("rules")
    void eachRuleNamesItsLine(String body, int line, String message) {
        assertEquals(List.of((BODY_LINE + line - 1) + ": " + message), problems(PRELUDE + "    " + body + "\n"));
    }

    @Test
    void definitionsAreChecked() {
        assertEquals(List.of(
                "1: unknown type 'Foo'",
                "4: default of 'x' must be int, not str",
                "7: 'h' is already defined in this file",
                "13: h2() must return int",
                "15: 'Item' is a built-in type",
                "20: default of 'amount' must be int, not str",
                "21: field 'item' is already defined",
                "24: h3() must return int, not str",
                "26: default of 'x' must be a constant: a number, a string, True, False or None",
                "30: mutable default <class 'list'> for field xs is not allowed: use default_factory"), problems("""
                def h(x: Foo) -> None:
                    pass

                def k(x: int = "a") -> None:
                    pass

                def h() -> None:
                    pass

                def h2() -> int:
                    if True:
                        return 1
                    return

                class Item:
                    name: str

                class Order:
                    item: Item
                    amount: int = "a"
                    item: str = "x"

                def h3() -> int:
                    return "a"

                def h4(x: list[int] = [], y: int = -2 * 3, z: str = "a" + "b", w: bool = True) -> None:
                    pass

                class Bag:
                    xs: list[int] = []
                    n: float = 1 / 4
                """));
    }

    @Test
    void conversionsAndWideningPass() {
        assertEquals(List.of(), problems(PRELUDE + """
                    r: Item = "minecraft:" + s
                    res: Resource = r
                    x: float = 1
                    x = n
                    ok: int = True
                    f(n, r, [1, 2], Order(r), st, b)
                    ae2.amount("minecraft:iron_ingot", None)
                    ae2.amount("minecraft:iron_ingot", network="main_base")
                    side: Side = "up"
                    there = ae2f.select("north_gate", 2, side=side)
                    mc.move(there, b, r, 64)
                    names = [s, r]
                    total = sum(xs) + max(xs) + min(1, 2.5) + abs(-n) + round(1.5)
                    text = str(n) + s.upper() + ",".join(["a", "b"])
                    for i in range(len(xs)):
                        xs[i] += 1
                    for stack in ae2.storage():
                        if stack.amount > 0 and stack.resource in [r]:
                            xs.append(stack.amount)
                    middle = xs[1:-1]
                    r1: int = round(2.5, ndigits=None) + round(number=n)
                    r2: float = round(1.25, ndigits=1) + sum(xs, start=0.5) + sum(xs, 2)
                    top: Optional[int] = max(xs, default=None)
                    low: int = min(xs, default=0)
                    word = str(object=n)
                    rng = range(0, 10, 2)
                    y = rng[1] + rng.stop + len(rng) + rng.index(4) + sum(rng) + max(rng)
                    ys = list(rng) + xs[::-1] + list(range(3)[1:])
                    xs.extend(rng)
                    joined = "-".join("abc") + "-".join(names)
                    later = [1, 2] < [1, 3] and 2 in rng and [[1]] <= [[]]
                    for i in rng[::-1]:
                        xs.append(i)
                """));
    }

    @Test
    void aLocalStartingAsNoneNeedsItsType() {
        var result = check(PRELUDE + """
                    best: Optional[int] = None
                    for x in xs:
                        best = x
                    ok = n > 0 and best is not None
                """);
        assertEquals(List.of(), result.problems());
    }

    static Stream<Arguments> constants() {
        return Stream.of(
                Arguments.of("y = ae2f.select(\"north_gate\", 5)", "reach of ae2f.select() must be from 1 to 4, not 5"),
                Arguments.of("y = ae2f.select(\"north_gate\", 2 + 3)", "reach of ae2f.select() must be from 1 to 4, not 5"),
                Arguments.of("mc.set_redstone(\"north_gate\", 16)", "level of mc.set_redstone() must be from 0 to 15, not 16"),
                Arguments.of("ae2.craft(\"minecraft:iron_ingot\", -1)", "amount of ae2.craft() must be at least 0, not -1"),
                Arguments.of("y = 1 / 0", "division by zero"),
                Arguments.of("y = 1 // (2 - 2)", "integer division or modulo by zero"),
                Arguments.of("y = 1 % 0", "integer modulo by zero"),
                Arguments.of("y = 1.5 / 0", "float division by zero"),
                Arguments.of("y = 9223372036854775807 + 1", "integers are 64-bit; this result is too large"),
                Arguments.of("y = ae2f.select(\"north_gate\", side=\"upp\")",
                        "'upp' is not a Side; expected one of up, down, north, south, west, east"),
                Arguments.of("y = ae2.amount(\"minecraft:iron_ingt\")", "unknown resource 'minecraft:iron_ingt'"),
                Arguments.of("y: Item = \"minecraft:\" + \"iron_ingt\"", "unknown item 'minecraft:iron_ingt'"),
                Arguments.of("y: Item = \"minecraft:water\"", "'minecraft:water' is a fluid, not an item"),
                Arguments.of("y = ae2.amount(\"testmod:goo\")",
                        "'testmod:goo' is both an item and a fluid; annotate it as Item or Fluid"),
                Arguments.of("mc.set_redstone(\"south_gate\", 1)", "no interface 'south_gate' in this domain"),
                Arguments.of("y = ae2.amount(\"minecraft:iron_ingot\", \"nowhere\")", "no network 'nowhere' in this domain"),
                Arguments.of("ae2f.set_filter(\"main_base\", \"main_base\", \"storage\", \"allow\", [\"minecraft:iron\"])",
                        "unknown resource 'minecraft:iron'"));
    }

    @ParameterizedTest
    @MethodSource("constants")
    void constantsAreCheckedBeforeTheProgramRuns(String body, String message) {
        assertEquals(List.of(BODY_LINE + ": " + message), problems(PRELUDE + "    " + body + "\n"));
    }

    @Test
    void knownConstantsAndRuntimeValuesPass() {
        assertEquals(List.of(), problems(PRELUDE + """
                    y = ae2f.select("interface-k7m2qx", 4, side="down")
                    mc.set_redstone("mine_entry", 15 - n)
                    goo: Item = "testmod:goo"
                    z: Resource = "minecraft:" + s
                    w = ae2.amount("minecraft:water", network="network-p3x9ka")
                    big = 9223372036854775807 - 1 + n * 2
                    q = 7 // -2 + 7 % -2 + 7.5 // 2.0
                """));
    }

    static Stream<Arguments> flow() {
        var unbound = "cannot access local variable 'y' where it is not associated with a value";
        return Stream.of(
                Arguments.of("if n > 0:\n        y = 1\n    z = y", 3, unbound),
                Arguments.of("for x in xs:\n        y = x\n    z = y", 3, unbound),
                Arguments.of("while n > 0:\n        y = 1\n        n -= 1\n    z = y", 4, unbound),
                Arguments.of("for x in xs:\n        if x > 0:\n            y = 1\n        z = y", 4, unbound),
                Arguments.of("y += 1\n    y = 0", 1, unbound),
                Arguments.of("z = y\n    y = 1", 1, unbound),
                Arguments.of("if n > 0:\n        y = 1\n    elif n < 0:\n        y = 2\n    z = y", 5, unbound),
                Arguments.of("for x in xs:\n        if x > 0:\n            continue\n        y = x\n    z = y + y", 5, unbound),
                Arguments.of("while n > 0:\n        y = 1\n        break\n    z = y", 4, unbound),
                Arguments.of("while True:\n        if n > 0:\n            break\n        y = 1\n    z = y", 5, unbound));
    }

    @ParameterizedTest
    @MethodSource("flow")
    void aVariableMustBeAssignedOnEveryPathBeforeItIsRead(String body, int line, String message) {
        assertEquals(List.of((BODY_LINE + line - 1) + ": " + message), problems(PRELUDE + "    " + body + "\n"));
    }

    @Test
    void assignedOnEveryPathIsFine() {
        assertEquals(List.of(), problems(PRELUDE + """
                    if n > 0:
                        y = 1
                    elif n < 0:
                        y = 2
                    else:
                        y = 3
                    z = y
                    for x in xs:
                        w = x
                        z = w
                    if n > 0:
                        return
                    v = 1
                    while True:
                        u = 1
                        break
                    while True:
                        stack = ae2.storage()[0]
                        if stack.amount > 0:
                            break
                    t = stack.amount
                """));
    }

    @Test
    void aFunctionWithAResultMustReturnOnEveryPath() {
        assertEquals(List.of(
                "1: a() can end without returning int",
                "15: d() can end without returning int"), problems("""
                def a(n: int) -> int:
                    if n > 0:
                        return 1

                def b(n: int) -> int:
                    if n > 0:
                        return 1
                    else:
                        return 2

                def c() -> int:
                    while True:
                        return 1

                def d() -> int:
                    while True:
                        break

                def e(n: int) -> None:
                    if n > 0:
                        return
                """));
    }

    @Test
    void anUnwrittenReturnTypeIsInferredFromTheReturns() {
        var result = check("""
                def one():
                    return 1

                def half(n: int):
                    if n > 0:
                        return 0.5
                    return n

                def maybe(n: int):
                    if n > 0:
                        return "x"

                def nothing(n: int):
                    if n > 0:
                        return
                    print(n)

                def fact(n: int):
                    if n <= 1:
                        return 1
                    return n * fact(n - 1)

                def even(n: int):
                    if n == 0:
                        return True
                    return odd(n - 1)

                def odd(n: int):
                    if n == 0:
                        return False
                    return even(n - 1)

                def names():
                    xs: list[str] = []
                    if len(xs) > 0:
                        return []
                    return xs

                def order():
                    return iron_tools.make("minecraft:iron_ingot")

                def use() -> int:
                    return one() + fact(3) + len(names())
                """);
        assertEquals(List.of(), result.problems());
        var results = new java.util.TreeMap<String, String>();
        result.results().forEach((name, type) -> results.put(name, type.toString()));
        assertEquals(new java.util.TreeMap<>(Map.of("one", "int", "half", "float", "maybe", "Optional[str]",
                "nothing", "None", "fact", "int", "even", "bool", "odd", "bool", "names", "list[str]",
                "order", "iron_tools.Order", "use", "int")), results);
    }

    @Test
    void anInferredReturnTypeChecksItsCallers() {
        assertEquals(List.of("6: x is int; it cannot hold str", "8: argument 'x' of g() must be int, not str"),
                problems("""
                def one():
                    return "a"

                def f() -> None:
                    x = 1
                    x = one()

                    y = g(one())

                def g(x: int):
                    return x
                """));
    }

    @Test
    void returnsThatShareNoTypeOrOnlyCallThemselvesNeedAHint() {
        assertEquals(List.of(
                "1: a() returns both int and str; write its return type",
                "6: ping() has no return type to infer: its returns only call back into it; write its return type",
                "9: pong() has no return type to infer: its returns only call back into it; write its return type",
                "12: empty() returns only an empty list; write its return type, as in -> list[int]"), problems("""
                def a(n: int):
                    if n > 0:
                        return 1
                    return "x"

                def ping(n: int):
                    return pong(n)

                def pong(n: int):
                    return ping(n)

                def empty():
                    return []
                """));
    }

    @Test
    void effectsAreInferredThroughCallsAndFiles() {
        var parsed = ScriptParser.parse("""
                def a() -> None:
                    b()

                def b() -> None:
                    mc.wait(1)

                def c() -> int:
                    return ae2.amount("minecraft:iron_ingot") + len([1])

                def d() -> None:
                    iron_tools.pulse("north_gate")

                def down(n: int) -> None:
                    if n > 0:
                        down(n - 1)

                def ticking(n: int) -> None:
                    if n > 0:
                        ticking(n - 1)
                    mc.wait(1)

                def shadow(mc: list[int]) -> None:
                    mc.append(1)
                """);
        var result = Checker.check(parsed.program(), WORKSPACE);
        assertEquals(List.of(), result.problems());
        assertEquals(java.util.Set.of("a", "b", "d", "ticking"), result.effectfulFunctions());
        var functions = parsed.program().functions();
        var callB = ((Stmt.CallStmt) functions.get(0).body().get(0)).call();
        var amount = ((Expr.Binary) ((Stmt.Return) functions.get(2).body().get(0)).value()).left();
        var append = ((Stmt.CallStmt) functions.get(6).body().get(0)).call();
        assertTrue(result.effectfulCalls().contains(callB.id()));
        assertFalse(result.effectfulCalls().contains(amount.id()));
        assertFalse(result.effectfulCalls().contains(append.id()), "a list method, not the mc namespace");
    }

    @Test
    void expressionsHaveTypes() {
        var parsed = ScriptParser.parse(PRELUDE + """
                    parts = "a,b".split(",")
                    made = iron_tools.make("minecraft:iron_ingot")
                    count = made.amount
                    k(made)

                def k(o: iron_tools.Order) -> None:
                    pass
                """);
        var result = Checker.check(parsed.program(), WORKSPACE);
        assertEquals(List.of(), result.problems());
        var body = parsed.program().functions().get(1).body();
        assertEquals("list[str]", result.types().get(((Stmt.Assign) body.get(0)).value().id()).toString());
        assertEquals("iron_tools.Order", result.types().get(((Stmt.Assign) body.get(1)).value().id()).toString());
        assertEquals("int", result.types().get(((Stmt.Assign) body.get(2)).value().id()).toString());
    }
}
