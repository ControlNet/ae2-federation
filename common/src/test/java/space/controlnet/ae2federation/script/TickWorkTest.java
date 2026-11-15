package space.controlnet.ae2federation.script;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A run's work per tick is bounded by its budget in values walked, made or scanned, not only by its node count: a
 * node that copies or scans a long list costs what it walks, and a node that passes the budget is paid for by the
 * next ticks (review of 2026-11-15: 10,000 copies of a 100,000-item list took 1.8 s in one tick).
 */
final class TickWorkTest {
    /** Test-only host: the programs here take no actions. */
    private static final class Host implements Evaluator.Host {
        @Override
        public Object call(String namespace, String name, List<Object> args) {
            throw new RunError("RuntimeError", "the test host has no " + namespace + "." + name);
        }
    }

    /** Counts the nodes run. */
    private static final class Count implements Runner.Trace {
        long nodes;

        @Override
        public void value(String file, String node, Object value) {
        }

        @Override
        public void ran(String file, String node) {
            nodes++;
        }
    }

    private static Runner runner(String source, Runner.Trace trace) {
        var parsed = ScriptParser.parse(source);
        assertEquals(List.of(), parsed.errors(), "the program must parse");
        var workspace = new Workspace(Map.of("main", parsed.program()), CompletionTest.CATALOG);
        assertEquals(List.of(), Checker.check(parsed.program(), workspace).problems(), "the program must check");
        var graphs = new HashMap<String, Map<String, Graph>>();
        graphs.put("main", Compiler.compile("main", workspace));
        return new Runner(workspace, graphs, new Host(), trace, "main", "f", List.of());
    }

    /** A loop whose every pass runs {@code statement} with a 100,000-item list and string at hand. */
    private static String loop(String statement) {
        return """
                def f() -> int:
                    xs = [0] * 100000
                    ys = [0] * 100000
                    s = "a" * 100000
                    t = "a" * 99999 + "b"
                    d: dict[int, int] = {}
                    for i in range(100):
                        d[i] = i
                    n = 0
                    while True:
                        %s
                        n += 1
                    return n
                """.formatted(statement);
    }

    @Test
    void plainNodesStillRunTenThousandATick() {
        var count = new Count();
        var runner = runner("""
                def f() -> int:
                    i = 0
                    while True:
                        i = i + 1
                    return i
                """, count);
        runner.tick(Integer.MAX_VALUE, 200_000);
        count.nodes = 0;
        runner.tick(Integer.MAX_VALUE, 200_000);
        assertEquals(200_000 / Runner.STEP_COST, count.nodes);
        assertEquals(200_000, runner.spent());
    }

    @Test
    void everyScanAndCopyCostsWhatItWalks() {
        for (var statement : List.of("b = 1 in xs", "c = xs.count(1)", "c = xs.index(0)", "zs = xs[:]",
                "b = xs == ys", "b = xs < ys", "b = s == t", "b = s < t", "c = s.find('b')", "c = s.count('a')",
                "u = s.replace('b', 'c')", "u = s[1:]", "u = s.strip()", "zs = list(xs)", "b = 'b' in s",
                "ws = s.split('b')", "xs.reverse()", "xs.sort()", "u = s.upper()")) {
            var count = new Count();
            var runner = runner(loop(statement), count);
            // The setup itself makes 300,000 items.
            for (int tick = 0; tick < 5; tick++) {
                runner.tick(Integer.MAX_VALUE, 200_000);
            }
            assertNotEquals(Runner.Status.FAILED, runner.status(), statement + ": " + runner.failure());
            count.nodes = 0;
            for (int tick = 0; tick < 10; tick++) {
                runner.tick(Integer.MAX_VALUE, 200_000);
            }
            // Unmetered, 10 ticks would run 100,000 nodes; at 100,000 values a pass, about two passes a tick.
            assertTrue(count.nodes < 100, statement + " ran " + count.nodes + " nodes in 10 ticks");
        }
    }

    @Test
    void aHeavyNodeIsPaidForByTheNextTicks() {
        var runner = runner("""
                def f() -> int:
                    xs = [0] * 100000
                    n = 0
                    while n < 10:
                        ys = xs[:]
                        n += 1
                    return n
                """, null);
        int ticks = 0;
        int idle = 0;
        while (runner.tick(Integer.MAX_VALUE, 50_000) == Runner.Status.RUNNING) {
            ticks++;
            if (runner.spent() == 0) {
                idle++;
                assertTrue(runner.owed() >= 0);
            }
            assertTrue(ticks < 1000, "the run did not end");
        }
        assertEquals(Runner.Status.DONE, runner.status());
        // 11 nodes of 100,000 values each at 50,000 a tick: each is followed by a tick that only pays its debt.
        assertTrue(ticks >= 20 && ticks <= 26, "took " + ticks + " ticks");
        assertTrue(idle >= 10, "paid debt in " + idle + " ticks");
    }

    @Test
    void aTickWithoutWorkStillCountsDownAWait() {
        var runner = runner("""
                def f() -> int:
                    mc.wait(5)
                    return 1
                """, null);
        assertEquals(Runner.Status.WAITING, runner.tick(Integer.MAX_VALUE, 200_000));
        for (int tick = 0; tick < 4; tick++) {
            assertEquals(Runner.Status.WAITING, runner.tick(Integer.MAX_VALUE, 0));
        }
        assertEquals(Runner.Status.DONE, runner.tick(Integer.MAX_VALUE, 200_000));
    }

    @Test
    void aLongNeedleIsFoundInLinearTime() {
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            var runner = runner("""
                    def f() -> int:
                        s = "a" * 100000
                        t = "a" * 50000 + "b"
                        n = 0
                        for i in range(20):
                            n += s.find(t) + s.count(t) + len(s.split(t)) + len(s.replace(t, ""))
                            if t in s:
                                n += 1
                        return n
                    """, null);
            for (int tick = 0; tick < 1000 && runner.status() == Runner.Status.RUNNING; tick++) {
                runner.tick(Integer.MAX_VALUE, 200_000);
            }
            assertEquals(Runner.Status.DONE, runner.status());
            assertEquals(20L * (-1 + 0 + 1 + 100_000), runner.result());
        });
    }

    @Test
    void longNeedlesAnswerAsPython() {
        var needle = "xy".repeat(20) + "z";
        var text = ("q" + needle).repeat(3) + "xy".repeat(19);
        var runner = runner("""
                def f() -> list[int]:
                    needle = "%s"
                    text = "%s"
                    return [text.find(needle), text.count(needle), len(text.split(needle)),
                            len(text.replace(needle, "-")), text.find(needle + "q")]
                """.formatted(needle, text), null);
        runner.tick(10_000);
        assertEquals(Runner.Status.DONE, runner.status());
        int replaced = text.replace(needle, "-").length();
        assertEquals("[1, 3, 4, " + replaced + ", 1]", Ops.repr(runner.result()));
    }
}
