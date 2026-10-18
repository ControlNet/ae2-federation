package space.controlnet.ae2federation.function;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Whole programs compiled to graphs and run tick by tick. */
final class RunnerTest {
    /** Test-only stand-in for a block the host selected. */
    record FakeBlock(String interfaceName, long reach, String side) {
        @Override
        public String toString() {
            return "Block(" + Ops.repr(interfaceName) + ", " + reach + ", " + Ops.repr(side) + ")";
        }
    }

    /**
     * Test-only stand-in for the game, matching the CPython stubs that produced the expected values: each call is
     * logged with every argument, defaults included, and answers from its arguments alone.
     */
    static final class FakeHost implements Evaluator.Host {
        final List<String> log = new ArrayList<>();

        @Override
        public Object call(String namespace, String name, List<Object> args) {
            log.add(namespace + "." + name + "(" + args.stream().map(Ops::repr).collect(Collectors.joining(", ")) + ")");
            return switch (namespace + "." + name) {
                case "ae2.amount" -> ((String) args.get(0)).length() * 10L;
                case "ae2.storage" -> new ListValue(List.of(stack("minecraft:iron_ingot", 640),
                        stack("minecraft:water", 1000)));
                case "ae2.craft" -> (Long) args.get(1) < 100;
                case "ae2f.select" -> new FakeBlock((String) args.get(0), (Long) args.get(1), (String) args.get(2));
                case "mc.move", "mc.set_redstone" -> true;
                case "mc.amount" -> 7L;
                default -> throw new RunError("RuntimeError", "the fake host has no " + namespace + "." + name);
            };
        }
    }

    /** A {@code Stack} with its fields in declared order, as the repr shows them. */
    static RecordValue stack(String resource, long amount) {
        var fields = new java.util.LinkedHashMap<String, Object>();
        fields.put("resource", resource);
        fields.put("amount", amount);
        return new RecordValue("Stack", fields, true);
    }

    private static Workspace workspace(String source) {
        var parsed = ScriptParser.parse(source);
        assertEquals(List.of(), parsed.errors(), "the program must parse");
        var workspace = new Workspace(Map.of("main", parsed.program(), "iron_tools", CheckerTest.FILES.get("iron_tools")),
                CompletionTest.CATALOG);
        assertEquals(List.of(), Checker.check(parsed.program(), workspace).problems(), "the program must check");
        return workspace;
    }

    private static Map<String, Map<String, Graph>> graphs(Workspace workspace) {
        var graphs = new HashMap<String, Map<String, Graph>>();
        workspace.files().keySet().forEach(file -> graphs.put(file, Compiler.compile(file, workspace)));
        return graphs;
    }

    private static Runner start(Workspace workspace, Evaluator.Host host, Runner.Trace trace, String entry,
            List<String> args) {
        var values = new ArrayList<Object>();
        for (var arg : args) {
            var parsed = ScriptParser.parse("def f() -> None:\n    x = " + arg + "\n");
            var expr = ((Stmt.Assign) parsed.program().functions().get(0).body().get(0)).value();
            values.add(new Evaluator(workspace, host).eval("main", expr, Map.of()));
        }
        return new Runner(workspace, graphs(workspace), host, trace, "main", entry, values);
    }

    /** Ticks until the run ends; the result's repr, or {@code Type: message}. */
    private static String finish(Runner runner) {
        for (int tick = 0; tick < 100_000; tick++) {
            var status = runner.tick(1000);
            if (status == Runner.Status.DONE) {
                return Ops.repr(runner.result());
            }
            if (status == Runner.Status.FAILED) {
                return runner.failure().error().toString();
            }
        }
        return fail("the run did not end");
    }

    /** Expected logs and results were produced by CPython 3.12.2 running each program with logging stubs. */
    static Stream<Arguments> programs() {
        return Stream.of(
                Arguments.of("left operand is read before the call that changes it", """
                def bump(xs: list[int]) -> int:
                    xs[0] = 100
                    return 1

                def main() -> list[int]:
                    xs = [5, 0]
                    y = xs[0] + bump(xs)
                    return [y, xs[0]]
                """, "main", List.of(),
                        List.of(),
                        "[6, 100]"),
                Arguments.of("a call result indexed before a later call changes it", """
                def same(xs: list[int]) -> list[int]:
                    return xs

                def grow(xs: list[int]) -> int:
                    xs[0] = 50
                    return 2

                def main() -> list[int]:
                    xs = [1]
                    y = same(xs)[0] + grow(xs)
                    return [y, xs[0]]
                """, "main", List.of(),
                        List.of(),
                        "[3, 50]"),
                Arguments.of("library reads and actions in Python order", """
                def main() -> bool:
                    b = ae2f.select("north_gate", 2)
                    return mc.move(b, ae2f.select("mine_entry"), "minecraft:iron_ingot", mc.amount(b, "minecraft:iron_ingot"))
                """, "main", List.of(),
                        List.of("ae2f.select('north_gate', 2, None)", "ae2f.select('mine_entry', 1, None)", "mc.amount(Block('north_gate', 2, None), 'minecraft:iron_ingot')", "mc.move(Block('north_gate', 2, None), Block('mine_entry', 1, None), 'minecraft:iron_ingot', 7)"),
                        "True"),
                Arguments.of("and skips an action when the left side is false", """
                def main() -> bool:
                    ok = ae2.craft("minecraft:iron_ingot", ae2.amount("minecraft:gold_ingot")) and mc.set_redstone("north_gate", 15)
                    return ok
                """, "main", List.of(),
                        List.of("ae2.amount('minecraft:gold_ingot', None)", "ae2.craft('minecraft:iron_ingot', 200, None)"),
                        "False"),
                Arguments.of("or skips an action when the left side is true", """
                def main() -> bool:
                    first = ae2.craft("minecraft:iron_ingot", 1) or mc.set_redstone("north_gate", 15)
                    second = ae2.craft("minecraft:iron_ingot", 500) or mc.set_redstone("mine_entry", 3)
                    return first and second
                """, "main", List.of(),
                        List.of("ae2.craft('minecraft:iron_ingot', 1, None)", "ae2.craft('minecraft:iron_ingot', 500, None)", "mc.set_redstone('mine_entry', 3)"),
                        "True"),
                Arguments.of("the value is computed before the target index", """
                def f(log: list[str]) -> int:
                    log.append("f")
                    return 0

                def g(log: list[str]) -> int:
                    log.append("g")
                    return 9

                def main() -> list[str]:
                    log: list[str] = []
                    xs = [0, 0]
                    xs[f(log)] = g(log)
                    log.append(str(xs))
                    return log
                """, "main", List.of(),
                        List.of(),
                        "['g', 'f', '[9, 0]']"),
                Arguments.of("augmented assignment reads the item before the call", """
                def bump(xs: list[int]) -> int:
                    xs[0] = 100
                    return 1

                def main() -> list[int]:
                    xs = [5]
                    xs[0] += bump(xs)
                    return xs
                """, "main", List.of(),
                        List.of(),
                        "[6]"),
                Arguments.of("+= on a list extends the list the call changed", """
                def more(xs: list[int]) -> list[int]:
                    xs.append(1)
                    return [7]

                def main() -> list[int]:
                    xs = [1]
                    ys = xs
                    xs += more(xs)
                    return ys
                """, "main", List.of(),
                        List.of(),
                        "[1, 1, 7]"),
                Arguments.of("positional arguments before keywords, left to right", """
                def note(log: list[str], text: str) -> str:
                    log.append(text)
                    return text

                def pair(a: str, b: str, c: str = "c") -> str:
                    return a + b + c

                def main() -> list[str]:
                    log: list[str] = []
                    log.append(pair(note(log, "1"), c=note(log, "3"), b=note(log, "2")))
                    return log
                """, "main", List.of(),
                        List.of(),
                        "['1', '3', '2', '123']"),
                Arguments.of("a for loop sees items appended while it runs", """
                def main() -> list[int]:
                    xs = [1, 2, 3]
                    for x in xs:
                        if x < 5:
                            xs.append(x + 3)
                    return xs
                """, "main", List.of(),
                        List.of(),
                        "[1, 2, 3, 4, 5, 6, 7]"),
                Arguments.of("break and continue in nested loops", """
                def main() -> list[int]:
                    out: list[int] = []
                    for i in range(4):
                        for j in range(4):
                            if j == i:
                                break
                            if j == 1:
                                continue
                            out.append(i * 10 + j)
                        if i == 2:
                            continue
                        out.append(-i)
                    return out
                """, "main", List.of(),
                        List.of(),
                        "[0, 10, -1, 20, 30, 32, -3]"),
                Arguments.of("a while condition with an action runs once per test", """
                def main() -> int:
                    n = 0
                    while ae2.craft("minecraft:iron_ingot", n * 40):
                        n += 1
                    return n
                """, "main", List.of(),
                        List.of("ae2.craft('minecraft:iron_ingot', 0, None)", "ae2.craft('minecraft:iron_ingot', 40, None)", "ae2.craft('minecraft:iron_ingot', 80, None)", "ae2.craft('minecraft:iron_ingot', 120, None)"),
                        "3"),
                Arguments.of("recursion returns through frames", """
                def fact(n: int) -> int:
                    if n <= 1:
                        return 1
                    return n * fact(n - 1)

                def main() -> int:
                    return fact(20)
                """, "main", List.of(),
                        List.of(),
                        "2432902008176640000"),
                Arguments.of("unbounded recursion stops with the recursion error", """
                def down(n: int) -> int:
                    return down(n + 1)

                def main() -> int:
                    return down(0)
                """, "main", List.of(),
                        List.of(),
                        "RecursionError: maximum recursion depth exceeded"),
                Arguments.of("an error inside a call stops the run", """
                def ratio(a: int, b: int) -> float:
                    return a / b

                def main() -> float:
                    return ratio(1, 2) + ratio(1, 0)
                """, "main", List.of(),
                        List.of(),
                        "ZeroDivisionError: division by zero"),
                Arguments.of("records are shared with the functions they are passed to", """
                class Job:
                    item: Item
                    done: int = 0

                def work(job: Job, n: int) -> None:
                    job.done += n

                def main() -> Job:
                    job = Job("minecraft:iron_ingot")
                    work(job, 3)
                    work(job, n=4)
                    return job
                """, "main", List.of(),
                        List.of(),
                        "Job(item='minecraft:iron_ingot', done=7)"),
                Arguments.of("defaults and arguments of the entry function", """
                def main(n: int, scale: float = 0.5, label: str = "x") -> str:
                    return label + str(n * scale)
                """, "main", List.of("3"),
                        List.of(),
                        "'x1.5'"),
                Arguments.of("a string is walked by character; elif picks the first match", """
                def kind(c: str) -> str:
                    if c in "aeiou":
                        return "v"
                    elif c == " ":
                        return "_"
                    elif c.upper() == c:
                        return "U"
                    else:
                        return "c"

                def main() -> str:
                    out = ""
                    for c in "Iron ore":
                        out += kind(c)
                    return out
                """, "main", List.of(),
                        List.of(),
                        "'Ucvc_vcv'"),
                Arguments.of("a function without return gives None; return leaves the loop", """
                def find(xs: list[int], x: int) -> Optional[int]:
                    for i in range(len(xs)):
                        if xs[i] == x:
                            return i
                    return None

                def nothing() -> None:
                    pass

                def main() -> list[Optional[int]]:
                    nothing()
                    return [find([4, 5, 6], 6), find([4, 5, 6], 7)]
                """, "main", List.of(),
                        List.of(),
                        "[2, None]"),
                Arguments.of("stacks from storage are records", """
                def main() -> list[str]:
                    total = 0
                    for stack in ae2.storage():
                        total += stack.amount
                    first = ae2.storage()[0]
                    return [str(first), first.resource + " " + str(total)]
                """, "main", List.of(),
                        List.of("ae2.storage(None)", "ae2.storage(None)"),
                        "[\"Stack(resource='minecraft:iron_ingot', amount=640)\", 'minecraft:iron_ingot 1640']"),
                Arguments.of("functions and records of another file", """
                def main() -> list[iron_tools.Order]:
                    orders = [iron_tools.make("minecraft:iron_ingot"), iron_tools.Order("minecraft:gold_ingot", 3)]
                    iron_tools.pulse("north_gate")
                    orders[0].amount += ae2.amount(orders[1].item)
                    return orders
                """, "main", List.of(),
                        List.of("mc.set_redstone('north_gate', 15)", "ae2.amount('minecraft:gold_ingot', None)"),
                        "[Order(item='minecraft:iron_ingot', amount=201), Order(item='minecraft:gold_ingot', amount=3)]"),
                Arguments.of("mc.wait does not change the result", """
                def main() -> int:
                    total = 0
                    for i in range(3):
                        mc.wait(i)
                        total += ae2.amount("minecraft:water")
                    return total
                """, "main", List.of(),
                        List.of("ae2.amount('minecraft:water', None)", "ae2.amount('minecraft:water', None)", "ae2.amount('minecraft:water', None)"),
                        "450"),
                Arguments.of("an action inside a list literal and a method call", """
                def main() -> list[bool]:
                    xs = [ae2.craft("minecraft:iron_ingot", 1)]
                    xs.append(mc.set_redstone("north_gate", 2) and ae2.craft("minecraft:iron_ingot", 200))
                    xs.insert(0, len(xs) > 1)
                    return xs
                """, "main", List.of(),
                        List.of("ae2.craft('minecraft:iron_ingot', 1, None)", "mc.set_redstone('north_gate', 2)", "ae2.craft('minecraft:iron_ingot', 200, None)"),
                        "[True, True, False]"),
                Arguments.of("a slice target and an attribute target with calls", """
                class Box:
                    n: int = 0

                def three(log: list[str]) -> int:
                    log.append("three")
                    return 3

                def box(log: list[str], b: Box) -> Box:
                    log.append("box")
                    return b

                def main() -> list[str]:
                    log: list[str] = []
                    xs = [0, 1, 2, 3, 4]
                    xs[1:three(log)] = [9]
                    b = Box()
                    box(log, b).n = three(log)
                    log.append(str(xs) + str(b.n))
                    return log
                """, "main", List.of(),
                        List.of(),
                        "['three', 'three', 'box', '[0, 9, 3, 4]3']")
);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void programs(String name, String source, String entry, List<String> args, List<String> log, String result) {
        var host = new FakeHost();
        assertEquals(result, finish(start(workspace(source), host, null, entry, args)));
        assertEquals(log, host.log);
    }

    /** The compiler's promise: no expression left in a node reaches a call that suspends. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("programs")
    void nodesHoldNoSuspendingCalls(String name, String source, String entry, List<String> args, List<String> log,
            String result) {
        var workspace = workspace(source);
        for (var file : workspace.files().keySet()) {
            for (var graph : Compiler.compile(file, workspace).values()) {
                for (var node : graph.nodes()) {
                    for (var expr : expressions(node)) {
                        assertFalse(suspends(workspace, file, expr), name + ": " + node);
                    }
                }
            }
        }
    }

    private static List<Expr> expressions(Node node) {
        var out = new ArrayList<Expr>();
        switch (node) {
            case Node.Assign n -> out.addAll(List.of(n.target(), n.value()));
            case Node.Augment n -> {
                out.add(n.target());
                out.add(n.value());
                if (n.current() != null) {
                    out.add(n.current());
                }
            }
            case Node.Evaluate n -> out.add(n.value());
            case Node.Branch n -> out.add(n.condition());
            case Node.Iterate n -> out.add(n.iterable());
            case Node.Return n -> {
                if (n.value() != null) {
                    out.add(n.value());
                }
            }
            case Node.Call n -> {
                out.addAll(n.args());
                n.keywords().forEach(k -> out.add(k.value()));
            }
            case Node.Action n -> {
                out.addAll(n.args());
                n.keywords().forEach(k -> out.add(k.value()));
            }
            default -> {
            }
        }
        return out;
    }

    /** Whether {@code e} still holds a call of a player function or a library action (no program here shadows one). */
    private static boolean suspends(Workspace workspace, String file, Expr e) {
        if (e == null) {
            return false;
        }
        return switch (e) {
            case Expr.Call c -> {
                boolean suspending;
                if (c.receiver() == null) {
                    suspending = workspace.files().get(file).functions().stream()
                            .anyMatch(f -> f.name().equals(c.name()));
                } else if (c.receiver() instanceof Expr.Name r && workspace.files().containsKey(r.name())) {
                    suspending = workspace.files().get(r.name()).functions().stream()
                            .anyMatch(f -> f.name().equals(c.name()));
                } else if (c.receiver() instanceof Expr.Name r
                        && workspace.catalog().namespaces().containsKey(r.name())) {
                    suspending = workspace.catalog().namespaces().get(r.name()).stream()
                            .anyMatch(s -> s.name().equals(c.name()) && s.effect());
                } else {
                    suspending = suspends(workspace, file, c.receiver());
                }
                yield suspending || c.args().stream().anyMatch(a -> suspends(workspace, file, a))
                        || c.keywords().stream().anyMatch(k -> suspends(workspace, file, k.value()));
            }
            case Expr.ListLit l -> l.items().stream().anyMatch(i -> suspends(workspace, file, i));
            case Expr.Attribute a -> suspends(workspace, file, a.target());
            case Expr.Index i -> suspends(workspace, file, i.target()) || suspends(workspace, file, i.index());
            case Expr.Slice s -> suspends(workspace, file, s.target()) || suspends(workspace, file, s.lower())
                    || suspends(workspace, file, s.upper()) || suspends(workspace, file, s.step());
            case Expr.Unary u -> suspends(workspace, file, u.operand());
            case Expr.Binary b -> suspends(workspace, file, b.left()) || suspends(workspace, file, b.right());
            default -> false;
        };
    }

    private Runner startMain(String source, FakeHost host, Runner.Trace trace) {
        return start(workspace(source), host, trace, "main", List.of());
    }

    @Test
    void oneActionPerTick() {
        var host = new FakeHost();
        var runner = startMain("""
                def main() -> int:
                    n = 0
                    for i in range(3):
                        n += ae2.amount("minecraft:water")
                        ae2.craft("minecraft:iron_ingot", i)
                    return n
                """, host, null);
        assertEquals(Runner.Status.RUNNING, runner.tick(1000));
        assertEquals(3, host.log.size(), "reads run on until the next action, which waits for the next tick");
        assertEquals(Runner.Status.RUNNING, runner.tick(1000));
        assertEquals(Runner.Status.DONE, runner.tick(1000));
        assertEquals(450L, runner.result());
        assertEquals(6, host.log.size());
    }

    @Test
    void waitPausesForItsTicks() {
        var runner = startMain("""
                def main() -> int:
                    mc.wait(0)
                    mc.wait(3)
                    return 1
                """, new FakeHost(), null);
        assertEquals(Runner.Status.WAITING, runner.tick(1000), "wait(0) does not pause; wait(3) does");
        assertEquals(Runner.Status.WAITING, runner.tick(1000));
        assertEquals(Runner.Status.WAITING, runner.tick(1000));
        assertEquals(Runner.Status.DONE, runner.tick(1000), "resumes on the third tick after the wait");
    }

    @Test
    void theBudgetBoundsATickAndAnEndlessLoopNeverBlocksIt() {
        var runner = startMain("""
                def main() -> int:
                    n = 0
                    while True:
                        n += 1
                    return n
                """, new FakeHost(), null);
        for (int tick = 0; tick < 50; tick++) {
            assertEquals(Runner.Status.RUNNING, runner.tick(100));
        }
        var counted = startMain("""
                def main() -> int:
                    n = 0
                    for i in range(10):
                        n += i
                    return n
                """, new FakeHost(), null);
        int ticks = 1;
        while (counted.tick(5) == Runner.Status.RUNNING) {
            ticks++;
        }
        // n = 0, Iterate, then 10 x (Next, Augment, Jump), the last Next, Return: 34 nodes.
        assertEquals(7, ticks);
        assertEquals(45L, counted.result());
    }

    @Test
    void anErrorPointsAtTheInnermostExpression() {
        var source = """
                def item(xs: list[int], i: int) -> int:
                    return xs[0] + xs[i]

                def main() -> int:
                    total = item([1, 2], 1)
                    return total + item([1, 2], 5)
                """;
        var runner = startMain(source, new FakeHost(), null);
        assertEquals("IndexError: list index out of range", finish(runner));
        var failure = runner.failure();
        assertEquals("main", failure.file());
        var at = ScriptParser.parse(source).positions().get(failure.node());
        assertEquals(new Position(2, 20), at, "xs[i]");
    }

    @Test
    void theTraceShowsValuesByProgramNodeWithoutTemporaries() {
        var source = """
                def double(n: int) -> int:
                    return n * 2

                def main() -> int:
                    y = 1 + double(3)
                    if y > 5 and ae2.craft("minecraft:iron_ingot", y):
                        y += 1
                    for c in "ab":
                        pass
                    return y
                """;
        var positions = ScriptParser.parse(source).positions();
        var seen = new ArrayList<String>();
        var runner = startMain(source, new FakeHost(), (file, node, value) -> seen.add(
                positions.get(node).line() + ":" + positions.get(node).column() + " " + Ops.repr(value)));
        assertEquals("8", finish(runner));
        assertEquals(List.of(
                "2:5 6",           // return n * 2
                "5:5 7",           // y = 1 + double(3)
                "6:18 True",       // ae2.craft(...)
                "6:5 True",        // the if
                "7:9 8",           // y += 1
                "8:5 'a'",         // for c in "ab"
                "8:5 'b'",
                "10:5 8"), seen);  // return y
    }
}
