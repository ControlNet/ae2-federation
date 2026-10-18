package space.controlnet.ae2federation.function;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A first measure of how much computing a function needs: its node count, plus, at every call site of another
 * function in the program, that function's measure. With no loops and no recursion a run visits each node at most
 * once per call, so the measure bounds the steps any run takes ({@link Evaluator}), whatever the world looks like.
 *
 * <p>How the measure turns into a device's computing units is not decided yet.
 */
public final class Cost {
    private final Program program;
    private final Map<String, Integer> known = new HashMap<>();
    private final Set<String> open = new HashSet<>();

    private Cost(Program program) {
        this.program = program;
    }

    public static int compute(Program program, String name) {
        return new Cost(program).of(name);
    }

    private int of(String name) {
        var cached = known.get(name);
        if (cached != null) return cached;
        var function = program.function(name).orElseThrow(() -> new IllegalArgumentException("No function " + name));
        if (!open.add(name)) throw new IllegalArgumentException(name + " calls itself");
        int[] total = {0};
        Walk.nodes(new Program(java.util.List.of(function)), (id, label) -> total[0]++);
        for (var call : Walk.calls(function)) {
            if (program.function(call.function()).isPresent()) total[0] += of(call.function());
        }
        open.remove(name);
        known.put(name, total[0]);
        return total[0];
    }
}
