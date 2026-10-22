package space.controlnet.ae2federation.script;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Paths through one function: a local read where some path reaches it without an assignment, and a function with a
 * result whose end can be reached. Works on the program tree; a block's outcome is the set of locals assigned on
 * every path through it, or {@code null} when no path leaves it at its end.
 */
final class Flow {
    private final Set<String> locals;
    private final BiConsumer<String, String> problem;
    private final Set<String> reported = new HashSet<>();
    /** For each loop being walked, the locals assigned on each path that reaches one of its {@code break}s. */
    private final java.util.Deque<List<Set<String>>> breaks = new java.util.ArrayDeque<>();

    private Flow(Set<String> locals, BiConsumer<String, String> problem) {
        this.locals = locals;
        this.problem = problem;
    }

    /** {@code locals} are the names assigned anywhere in the function; reads of other names are not this check's. */
    static void check(FunctionDef function, TypeRef result, Set<String> locals, BiConsumer<String, String> problem) {
        var flow = new Flow(locals, problem);
        var assigned = new HashSet<String>();
        function.params().forEach(p -> assigned.add(p.name()));
        var end = flow.block(function.body(), assigned);
        if (end != null && result != null && !result.equals(Types.NONE)) {
            problem.accept(function.id(), function.name() + "() can end without returning " + result);
        }
    }

    /** Whether some path runs off the end of {@code function}, which then returns None. */
    static boolean canEnd(FunctionDef function) {
        var assigned = new HashSet<String>();
        function.params().forEach(p -> assigned.add(p.name()));
        return new Flow(Set.of(), (node, message) -> {
        }).block(function.body(), assigned) != null;
    }

    private Set<String> block(List<Stmt> body, Set<String> in) {
        var assigned = in;
        for (var statement : body) {
            assigned = statement(statement, assigned);
            if (assigned == null) {
                return null;
            }
        }
        return assigned;
    }

    private Set<String> statement(Stmt statement, Set<String> in) {
        return switch (statement) {
            case Stmt.Assign s -> {
                reads(s.value(), in);
                if (s.target() instanceof Expr.Name n) {
                    yield with(in, n.name());
                }
                reads(s.target(), in);
                yield in;
            }
            case Stmt.AugAssign s -> {
                reads(s.target(), in);
                reads(s.value(), in);
                yield in;
            }
            case Stmt.CallStmt s -> {
                reads(s.call(), in);
                yield in;
            }
            case Stmt.If s -> {
                reads(s.condition(), in);
                Set<String> out = block(s.body(), copy(in));
                for (var elif : s.elifs()) {
                    reads(elif.condition(), in);
                    out = join(out, block(elif.body(), copy(in)));
                }
                yield join(out, s.orElse() == null ? in : block(s.orElse().body(), copy(in)));
            }
            case Stmt.While s -> {
                reads(s.condition(), in);
                // `while True:` is left only by a break; any other loop may also run zero times.
                boolean forever = s.condition() instanceof Expr.BoolLit b && b.value();
                yield loop(s.body(), copy(in), forever ? null : in);
            }
            case Stmt.For s -> {
                reads(s.iterable(), in);
                yield loop(s.body(), with(in, s.variable()), in);
            }
            case Stmt.Return s -> {
                if (s.value() != null) {
                    reads(s.value(), in);
                }
                yield null;
            }
            case Stmt.Break s -> {
                breaks.peek().add(in);
                yield null;
            }
            case Stmt.Continue s -> null;
            case Stmt.Pass s -> in;
        };
    }

    /** Reports reads of locals not assigned on every path to here. */
    private void reads(Expr e, Set<String> assigned) {
        switch (e) {
            case Expr.Name n -> read(n.id(), n.name(), assigned);
            case Expr.Call c -> {
                if (c.receiver() != null) {
                    reads(c.receiver(), assigned);
                } else if (locals.contains(c.name())) {
                    read(c.id(), c.name(), assigned);
                }
                c.args().forEach(a -> reads(a, assigned));
                c.keywords().forEach(k -> reads(k.value(), assigned));
            }
            case Expr.Attribute a -> reads(a.target(), assigned);
            case Expr.Index i -> {
                reads(i.target(), assigned);
                reads(i.index(), assigned);
            }
            case Expr.Slice s -> {
                reads(s.target(), assigned);
                for (var part : java.util.Arrays.asList(s.lower(), s.upper(), s.step())) {
                    if (part != null) {
                        reads(part, assigned);
                    }
                }
            }
            case Expr.Unary u -> reads(u.operand(), assigned);
            case Expr.Binary b -> {
                reads(b.left(), assigned);
                reads(b.right(), assigned);
            }
            case Expr.ListLit l -> l.items().forEach(item -> reads(item, assigned));
            case Expr.DictLit d -> {
                for (int i = 0; i < d.keys().size(); i++) {
                    reads(d.keys().get(i), assigned);
                    reads(d.values().get(i), assigned);
                }
            }
            case Expr.SetLit l -> l.items().forEach(item -> reads(item, assigned));
            default -> {
            }
        }
    }

    private void read(String node, String name, Set<String> assigned) {
        if (locals.contains(name) && !assigned.contains(name) && reported.add(name)) {
            problem.accept(node, "cannot access local variable '" + name + "' where it is not associated with a value");
        }
    }

    /**
     * A loop's outcome: what {@code exit} (the paths that leave by the condition, {@code null} if none) and every
     * {@code break} have in common.
     */
    private Set<String> loop(List<Stmt> body, Set<String> entry, Set<String> exit) {
        breaks.push(new java.util.ArrayList<>());
        block(body, entry);
        var out = exit;
        for (var state : breaks.pop()) {
            out = join(out, state);
        }
        return out;
    }

    /** Locals assigned on both paths; a path that does not reach here counts as having assigned everything. */
    private static Set<String> join(Set<String> a, Set<String> b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        var both = new HashSet<>(a);
        both.retainAll(b);
        return both;
    }

    private static Set<String> with(Set<String> in, String name) {
        var out = copy(in);
        out.add(name);
        return out;
    }

    private static Set<String> copy(Set<String> in) {
        return new HashSet<>(in);
    }
}
