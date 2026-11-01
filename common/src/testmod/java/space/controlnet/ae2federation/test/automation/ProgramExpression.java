package space.controlnet.ae2federation.test.automation;

import java.util.List;
import java.util.Map;
import space.controlnet.ae2federation.automation.ProgramHost;
import space.controlnet.ae2federation.automation.ProgramRun;
import space.controlnet.ae2federation.script.Runner;

/** TEST-ONLY: runs one integer expression as a program, to time library calls where a test needs them. */
public final class ProgramExpression {
    private ProgramExpression() {
    }

    /**
     * Runs {@code return <expression>} as a program in {@code host}'s domain to the end, all in this server tick.
     * Returns the wall time in nanoseconds, how many runner ticks it took and the integer it gave.
     */
    public static long[] run(ProgramHost host, String expression) {
        var text = "def run() -> int:\n    return " + expression + "\n";
        var catalog = host.catalog();
        var started = System.nanoTime();
        var run = ProgramRun.start(ProgramRun.prepare(Map.of("expression", text), catalog), catalog, host,
                "expression", "run", List.of());
        long ticks = 0;
        Runner.Status status;
        do {
            status = run.tick(1_000_000);
            ticks++;
        } while (status != Runner.Status.DONE && status != Runner.Status.FAILED && ticks < 1000);
        var nanos = System.nanoTime() - started;
        if (status != Runner.Status.DONE) {
            throw new IllegalStateException("The program did not finish: " + status
                    + (status == Runner.Status.FAILED ? " " + run.failure() : ""));
        }
        return new long[] {nanos, ticks, ((Number) run.runner().result()).longValue()};
    }
}
