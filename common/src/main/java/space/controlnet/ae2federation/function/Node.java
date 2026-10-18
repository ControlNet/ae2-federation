package space.controlnet.ae2federation.function;

import java.util.List;

/**
 * One step of a compiled function. Control passes to the next node in the graph's list unless the node says
 * otherwise. {@code source} is the program node it came from, for errors, the trace and the graph view. Expressions
 * in a node never suspend: the compiler has lifted every player function call and library action into its own
 * {@link Call} or {@link Action}, whose result lands in a temporary local named {@code $n}.
 */
public sealed interface Node {
    String source();

    /** {@code target = value}. */
    record Assign(String source, Expr target, Expr value) implements Node {
    }

    /**
     * {@code target op= value}; {@code current} is the target's value read before {@code value} was computed, or
     * {@code null} when the node reads it itself.
     */
    record Augment(String source, Expr target, BinaryOp op, Expr current, Expr value) implements Node {
    }

    /** An expression run for what it does, such as {@code xs.append(1)}. */
    record Evaluate(String source, Expr value) implements Node {
    }

    /**
     * Goes on when {@code condition} is truthy, else to {@code otherwise}. A {@code decision} is the program's own
     * {@code if}, {@code elif} or {@code while} test, shown in the trace; the compiler's branches around the right
     * side of {@code and}/{@code or} are not.
     */
    record Branch(String source, Expr condition, int otherwise, boolean decision) implements Node {
    }

    record Jump(String source, int target) implements Node {
    }

    /** A call of a player function; {@code result} is the temporary for its value, {@code null} if unused. */
    record Call(String source, String file, String function, List<Expr> args, List<Expr.Keyword> keywords,
            String result) implements Node {
        public Call {
            args = List.copyOf(args);
            keywords = List.copyOf(keywords);
        }
    }

    /** A library action, run one per tick; {@code mc.wait} is the runner's own. */
    record Action(String source, String namespace, String name, List<Expr> args, List<Expr.Keyword> keywords,
            String result) implements Node {
        public Action {
            args = List.copyOf(args);
            keywords = List.copyOf(keywords);
        }
    }

    /** Starts a {@code for} loop over {@code iterable}, keeping its position in the temporary {@code cursor}. */
    record Iterate(String source, Expr iterable, String cursor) implements Node {
    }

    /** Puts the cursor's next item in {@code variable} and goes on; when there is none, goes to {@code exit}. */
    record Next(String source, String cursor, String variable, int exit) implements Node {
    }

    /** Returns {@code value}, or {@code None} when it is {@code null}. */
    record Return(String source, Expr value) implements Node {
    }
}
