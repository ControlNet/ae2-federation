package space.controlnet.ae2federation.function;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The static checks a program must pass before it runs: names and types agree, a query never calls an action, no
 * function calls itself (directly or through others), an action returns nothing and a query returns on every path.
 * Each problem names the node it is about, so the graph can mark the node and the script its line.
 *
 * <p>Without loops or recursion every run ends, and its length is bounded by the program's size ({@link Cost}).
 */
public final class Checker {
    public record Problem(String node, String message) {
    }

    private final Program program;
    private final List<Problem> problems = new ArrayList<>();
    private FunctionDef current;

    private Checker(Program program) {
        this.program = program;
    }

    public static List<Problem> check(Program program) {
        var checker = new Checker(program);
        checker.names();
        for (var function : program.functions()) checker.function(function);
        checker.recursion();
        return List.copyOf(checker.problems);
    }

    private void problem(String node, String message) {
        problems.add(new Problem(node, message));
    }

    private void names() {
        var seen = new HashSet<String>();
        for (var function : program.functions()) {
            if (Library.builtin(function.name()).isPresent()) {
                problem(function.id(), function.name() + " is a library function");
            } else if (!seen.add(function.name())) {
                problem(function.id(), "two functions are named " + function.name());
            }
        }
    }

    private void function(FunctionDef function) {
        current = function;
        var scope = new HashMap<String, ValueType>();
        for (var param : function.params()) {
            if (scope.put(param.name(), param.type()) != null) {
                problem(param.id(), "two parameters are named " + param.name());
            }
        }
        block(function.body(), scope);
        if (function.kind() == FunctionDef.Kind.QUERY && !returns(function.body())) {
            problem(function.id(), function.name() + " may end without returning");
        }
    }

    /** Whether running {@code body} always reaches a return. */
    private static boolean returns(List<Stmt> body) {
        for (var stmt : body) {
            if (stmt instanceof Stmt.Return) return true;
            if (stmt instanceof Stmt.If branch && !branch.otherwise().isEmpty() && returns(branch.otherwise())
                    && branch.branches().stream().allMatch(b -> returns(b.body()))) {
                return true;
            }
        }
        return false;
    }

    /** Checks a block; its locals end with it. */
    private void block(List<Stmt> body, Map<String, ValueType> outer) {
        var scope = new HashMap<>(outer);
        for (var stmt : body) {
            switch (stmt) {
                case Stmt.Let let -> {
                    var type = expr(let.value(), scope);
                    scope.put(let.name(), type);
                }
                case Stmt.Return ret -> {
                    if (current.kind() == FunctionDef.Kind.ACTION) {
                        if (ret.value() != null) problem(ret.id(), "an action returns nothing");
                    } else if (ret.value() == null) {
                        problem(ret.id(), "a query returns a value");
                    } else {
                        var type = expr(ret.value(), scope);
                        if (type != null && type != current.result()) {
                            problem(ret.value().id(), current.name() + " returns " + current.result().word() + ", not "
                                    + type.word());
                        }
                    }
                }
                case Stmt.Do call -> {
                    var result = call(call.call(), scope);
                    if (result != null && result != ValueType.NOTHING) {
                        problem(call.call().id(), current.kind() == FunctionDef.Kind.QUERY ? "a query has no effects"
                                : "the value of " + call.call().function() + " is unused");
                    }
                }
                case Stmt.If branch -> {
                    for (var b : branch.branches()) {
                        var type = expr(b.condition(), scope);
                        if (type != null && type != ValueType.BOOL) {
                            problem(b.condition().id(), "a condition is a bool, not " + type.word());
                        }
                        block(b.body(), scope);
                    }
                    block(branch.otherwise(), scope);
                }
            }
        }
    }

    /** The type of a value expression, or {@code null} once a problem has been found in it. */
    private ValueType expr(Expr expr, Map<String, ValueType> scope) {
        return switch (expr) {
            case Expr.Literal literal -> literal.type();
            case Expr.Var var -> {
                if (!scope.containsKey(var.name())) problem(var.id(), "unknown name " + var.name());
                yield scope.get(var.name());
            }
            case Expr.Unary unary -> {
                var want = unary.op() == Expr.Op.NOT ? ValueType.BOOL : ValueType.NUMBER;
                var type = expr(unary.operand(), scope);
                if (type == null) yield null;
                if (type != want) {
                    problem(unary.id(), unary.op().symbol() + " takes " + want.word() + ", not " + type.word());
                    yield null;
                }
                yield want;
            }
            case Expr.Binary binary -> binary(binary, scope);
            case Expr.Call call -> {
                var result = call(call, scope);
                if (result == ValueType.NOTHING) {
                    problem(call.id(), call.function() + " gives no value");
                    yield null;
                }
                yield result;
            }
        };
    }

    private ValueType binary(Expr.Binary binary, Map<String, ValueType> scope) {
        var left = expr(binary.left(), scope);
        var right = expr(binary.right(), scope);
        if (left == null || right == null) return null;
        var symbol = binary.op().symbol();
        switch (binary.op()) {
            case EQ, NE -> {
                if (left != right) {
                    problem(binary.id(), symbol + " compares like with like, not " + left.word() + " and " + right.word());
                    return null;
                }
                return ValueType.BOOL;
            }
            case AND, OR -> {
                return operands(binary, ValueType.BOOL, left, right) ? ValueType.BOOL : null;
            }
            case LT, LE, GT, GE -> {
                return operands(binary, ValueType.NUMBER, left, right) ? ValueType.BOOL : null;
            }
            default -> {
                return operands(binary, ValueType.NUMBER, left, right) ? ValueType.NUMBER : null;
            }
        }
    }

    private boolean operands(Expr.Binary binary, ValueType want, ValueType left, ValueType right) {
        var wrong = left != want ? left : right != want ? right : null;
        if (wrong == null) return true;
        problem(binary.id(), binary.op().symbol() + " takes " + want.word() + ", not " + wrong.word());
        return false;
    }

    /** Checks a call's callee and arguments; its result type, or {@code null} once a problem has been found. */
    private ValueType call(Expr.Call call, Map<String, ValueType> scope) {
        var name = call.function();
        var args = call.args().stream().map(arg -> expr(arg, scope)).toList();
        var builtin = Library.builtin(name);
        if (builtin.isPresent()) {
            var b = builtin.get();
            if (!acting(call, b.action())) return null;
            if (!b.accepts(args.size())) {
                var count = b.variadic() ? b.params().size() - 1 + " or more" : String.valueOf(b.params().size());
                problem(call.id(), name + " takes " + count + " arguments, not " + args.size());
                return null;
            }
            for (int i = 0; i < args.size(); i++) argument(call, i, b.param(i), args.get(i));
            return b.result();
        }
        var function = program.function(name);
        if (function.isEmpty()) {
            problem(call.id(), "unknown function " + name);
            return null;
        }
        var f = function.get();
        if (!acting(call, f.kind() == FunctionDef.Kind.ACTION)) return null;
        if (args.size() != f.params().size()) {
            problem(call.id(), name + " takes " + f.params().size() + " arguments, not " + args.size());
            return null;
        }
        for (int i = 0; i < args.size(); i++) argument(call, i, f.params().get(i).type(), args.get(i));
        return f.result();
    }

    private boolean acting(Expr.Call call, boolean action) {
        if (action && current.kind() == FunctionDef.Kind.QUERY) {
            problem(call.id(), "a query cannot call the action " + call.function());
            return false;
        }
        return true;
    }

    private void argument(Expr.Call call, int index, ValueType want, ValueType type) {
        if (want != null && type != null && type != want) {
            problem(call.args().get(index).id(), call.function() + " takes a " + want.word() + " as argument "
                    + (index + 1) + ", not " + type.word());
        }
    }

    // ---- recursion ----

    private void recursion() {
        var calls = new HashMap<String, Set<String>>();
        for (var function : program.functions()) {
            var callees = new LinkedHashSet<String>();
            for (var call : Walk.calls(function)) {
                if (program.function(call.function()).isPresent()) callees.add(call.function());
            }
            calls.put(function.name(), callees);
        }
        for (var function : program.functions()) {
            var path = cycle(function.name(), function.name(), calls, new ArrayList<>(), new HashSet<>());
            if (path == null) continue;
            problem(function.id(), function.name() + " calls itself"
                    + (path.size() > 1 ? " through " + String.join(", ", path.subList(1, path.size())) : ""));
        }
    }

    /** The calls from {@code from} that lead back to {@code target}, or {@code null}. */
    private static List<String> cycle(String target, String from, Map<String, Set<String>> calls, List<String> path,
            Set<String> seen) {
        path.add(from);
        for (var callee : calls.getOrDefault(from, Set.of())) {
            if (callee.equals(target)) return path;
            if (seen.add(callee)) {
                var found = cycle(target, callee, calls, path, seen);
                if (found != null) return found;
            }
        }
        path.remove(path.size() - 1);
        return null;
    }
}
