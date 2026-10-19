package space.controlnet.ae2federation.function;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Dry runs as the editor shows them: the last value of every node, the nodes that ran, and where a run stopped. */
final class DryRunTest {
    /** Test-only stand-in for the game: 37 iron in stock, and every action answers True; calls are logged. */
    static final class StockHost implements Evaluator.Host {
        final List<String> log = new ArrayList<>();

        @Override
        public Object call(String namespace, String name, List<Object> args) {
            log.add(namespace + "." + name);
            return switch (namespace + "." + name) {
                case "ae2.amount" -> 37L;
                case "ae2.craft", "mc.set_redstone" -> true;
                default -> throw new RunError("RuntimeError", "the stock host has no " + namespace + "." + name);
            };
        }
    }

    private static final String ALARM = """
            def low_iron_alarm(lamp: Interface) -> None:
                iron: Item = "minecraft:iron_ingot"
                now = ae2.amount(iron)
                if now < 64:
                    ae2.craft(iron, 64 - now)
                    mc.set_redstone(lamp, 15)
                else:
                    mc.set_redstone(lamp, 0)
            """;

    private record Ran(DryRun.Result result, Map<String, String> labels) {
        /** The value of the node labelled {@code label}: an expression's text, or {@code line N} for a statement. */
        String value(String label) {
            return result.values().get(id(label));
        }

        boolean ran(String label) {
            return result.ran().contains(id(label));
        }

        String id(String label) {
            return labels.entrySet().stream().filter(e -> e.getValue().equals(label)).map(Map.Entry::getKey)
                    .findFirst().orElseThrow(() -> new AssertionError("no node " + label + " in " + labels.values()));
        }
    }

    private static Ran dryRun(String source, String function, List<Object> args, int budget) {
        var parsed = ScriptParser.parse(source);
        assertEquals(List.of(), parsed.errors(), "the program must parse");
        var workspace = new Workspace(Map.of("main", parsed.program()), CompletionTest.CATALOG);
        assertEquals(List.of(), Checker.check(parsed.program(), workspace).problems(), "the program must check");
        var graphs = new HashMap<String, Map<String, Graph>>();
        graphs.put("main", Compiler.compile("main", workspace));
        var result = DryRun.run(workspace, graphs, new StockHost(), "main", function, args, budget);
        var labels = new LinkedHashMap<String, String>();
        label(parsed.program(), parsed.positions(), labels);
        return new Ran(result, labels);
    }

    /** Every statement as {@code line N} and every expression as its printed text, by node ID. */
    private static void label(Object node, Map<String, Position> positions, Map<String, String> labels) {
        if (node instanceof Expr e) {
            labels.putIfAbsent(e.id(), ScriptPrinter.expr(e));
        } else if (node instanceof Stmt s) {
            labels.putIfAbsent(s.id(), "line " + positions.get(s.id()).line());
        }
        if (node instanceof Record record) {
            for (var component : record.getClass().getRecordComponents()) {
                try {
                    label(component.getAccessor().invoke(record), positions, labels);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            }
        } else if (node instanceof List<?> list) {
            list.forEach(item -> label(item, positions, labels));
        }
    }

    /** A value of the host's own, such as a selected block, is written as the host describes it, in lists too. */
    @Test
    void theHostDescribesItsOwnValues() {
        var parsed = ScriptParser.parse("""
                def chests(port: Interface) -> list[Block]:
                    chest = ae2f.select(port)
                    return [chest]
                """);
        var workspace = new Workspace(Map.of("main", parsed.program()), CompletionTest.CATALOG);
        assertEquals(List.of(), Checker.check(parsed.program(), workspace).problems(), "the program must check");
        var graphs = new HashMap<String, Map<String, Graph>>();
        graphs.put("main", Compiler.compile("main", workspace));
        var host = new Evaluator.Host() {
            @Override
            public Object call(String namespace, String name, List<Object> args) {
                return new RunnerTest.FakeBlock((String) args.get(0), (Long) args.get(1), (String) args.get(2));
            }

            @Override
            public String repr(Object value) {
                return Ops.repr(value, own -> "Block('minecraft:chest')");
            }
        };
        var result = DryRun.run(workspace, graphs, host, "main", "chests", List.of("north_gate"), DryRun.BUDGET);
        assertTrue(result.values().containsValue("Block('minecraft:chest')"), result.values().toString());
        assertTrue(result.values().containsValue("[Block('minecraft:chest')]"), result.values().toString());
    }

    @Test
    void everyValueOfTheBranchThatRanIsShown() {
        var run = dryRun(ALARM, "low_iron_alarm", List.of("north_gate"), DryRun.BUDGET);
        assertTrue(run.result().finished());
        assertNull(run.result().failure());
        assertEquals("'minecraft:iron_ingot'", run.value("line 2"));
        assertEquals("37", run.value("line 3"));
        assertEquals("37", run.value("ae2.amount(iron)"));
        assertEquals("True", run.value("now < 64"));
        assertEquals("27", run.value("64 - now"));
        assertEquals("True", run.value("ae2.craft(iron, 64 - now)"));
        assertEquals("True", run.value("mc.set_redstone(lamp, 15)"));
        // An action called as a statement runs as its call's node.
        assertTrue(run.ran("mc.set_redstone(lamp, 15)"));
        assertFalse(run.ran("mc.set_redstone(lamp, 0)"), "the else branch did not run");
        assertNull(run.value("mc.set_redstone(lamp, 0)"));
    }

    @Test
    void literalsAndNamesHaveNoValueOfTheirOwn() {
        var run = dryRun(ALARM, "low_iron_alarm", List.of("north_gate"), DryRun.BUDGET);
        assertNull(run.value("64"));
        assertNull(run.value("now"));
    }

    @Test
    void theReturnValueIsTheReturnStatementsValue() {
        var run = dryRun("""
                def low(min: int) -> bool:
                    return ae2.amount("minecraft:iron_ingot") < min
                """, "low", List.of(64L), DryRun.BUDGET);
        assertEquals("True", run.value("line 2"));
        assertEquals("37", run.value("ae2.amount(\"minecraft:iron_ingot\")"));
    }

    @Test
    void aNodeRunAgainShowsItsLastValue() {
        var run = dryRun("""
                def count() -> int:
                    total = 0
                    for i in range(4):
                        total = total + i
                    return total
                """, "count", List.of(), DryRun.BUDGET);
        assertEquals("6", run.value("line 4"));
        assertEquals("3", run.value("line 3"), "the loop's item");
        assertEquals("6", run.value("line 5"));
    }

    @Test
    void anErrorStopsTheRunAtItsNode() {
        var run = dryRun("""
                def half(n: int) -> int:
                    a = n + 1
                    return a // 0
                """, "half", List.of(3L), DryRun.BUDGET);
        assertTrue(run.result().finished());
        var failure = run.result().failure();
        assertNotNull(failure);
        assertEquals("ZeroDivisionError", failure.error().type());
        assertEquals(run.id("a // 0"), failure.node());
        assertEquals("4", run.value("line 2"));
    }

    @Test
    void theBudgetStopsAnEndlessLoop() {
        var run = dryRun("""
                def spin() -> None:
                    n = 0
                    while True:
                        n = n + 1
                """, "spin", List.of(), 100);
        assertFalse(run.result().finished());
        assertNull(run.result().failure());
        assertTrue(Long.parseLong(run.value("line 4")) > 10);
    }

    @Test
    void waitsAreSkipped() {
        var run = dryRun("""
                def slow() -> int:
                    mc.wait(1000000)
                    return 1
                """, "slow", List.of(), DryRun.BUDGET);
        assertTrue(run.result().finished());
        assertEquals("1", run.value("line 3"));
    }

    @Test
    void longValuesAreCut() {
        var run = dryRun("""
                def word() -> str:
                    return "abcdefghij" * 10
                """, "word", List.of(), DryRun.BUDGET);
        var value = run.value("line 2");
        assertEquals(DryRun.MAX_TEXT, value.length());
        assertTrue(value.endsWith("..."));
    }
}
