package space.controlnet.ae2federation.function;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A run of a function for the editor, which shows each node's value while the player edits. It runs at once rather
 * than tick by tick: waits are skipped, and it stops after a budget of nodes. Whether actions act is the host's
 * business; the game's dry-run host only simulates them.
 *
 * <p>Assistant choices: a node run more than once shows its last value; a value is formatted when the run ends, so a
 * list shows what it holds then.
 */
public final class DryRun {
    /** Nodes a dry run may run before it stops. */
    public static final int BUDGET = 4096;
    /** The longest value text; a longer one is cut and ends with {@code ...}. */
    public static final int MAX_TEXT = 48;

    /**
     * What a dry run of a function in {@code file} found: the last value of each of the file's nodes as its repr,
     * the file's nodes that ran, the error that stopped it ({@code null} if none), and whether it ended before the
     * budget ran out.
     */
    public record Result(Map<String, String> values, Set<String> ran, Runner.Failure failure, boolean finished) {
    }

    private DryRun() {
    }

    public static Result run(Workspace workspace, Map<String, Map<String, Graph>> graphs, Evaluator.Host host,
            String file, String function, List<Object> args, int budget) {
        var values = new HashMap<String, Object>();
        var ran = new HashSet<String>();
        int[] steps = {0};
        var trace = new Runner.Trace() {
            @Override
            public void value(String where, String node, Object value) {
                if (where.equals(file)) {
                    values.put(node, value);
                }
            }

            @Override
            public void ran(String where, String node) {
                steps[0]++;
                if (where.equals(file)) {
                    ran.add(node);
                }
            }
        };
        var runner = new Runner(workspace, graphs, host, trace, file, function, args);
        var status = Runner.Status.RUNNING;
        while (steps[0] < budget) {
            status = runner.tick(budget - steps[0]);
            if (status == Runner.Status.DONE || status == Runner.Status.FAILED) {
                break;
            }
            if (status == Runner.Status.WAITING) {
                runner.wake();
            }
        }
        var texts = new LinkedHashMap<String, String>();
        values.forEach((node, value) -> texts.put(node, cut(host.repr(value))));
        boolean finished = status == Runner.Status.DONE || status == Runner.Status.FAILED;
        return new Result(texts, ran, status == Runner.Status.FAILED ? runner.failure() : null, finished);
    }

    private static String cut(String text) {
        return text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT - 3) + "...";
    }
}
