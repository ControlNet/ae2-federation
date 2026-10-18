package space.controlnet.ae2federation.function;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Runs a program the {@link Checker} accepted. A query runs with {@link Observations} only; an action also gets
 * {@link Effects}, which it does not pass to the queries it calls, so a query cannot act however it is reached.
 *
 * <p>Every statement and expression visited is one step; a run that takes more than its budget stops with
 * {@link BudgetExceeded}. Effects an action made before that stay made. {@link Cost#compute} bounds a run's steps.
 *
 * <p>{@link Value#UNAVAILABLE} flows through operators and calls: {@code false and x} is false and {@code true or x}
 * true whatever {@code x} is, anything else touching an unavailable value is unavailable. Dividing by 0 is unavailable
 * too. Where the program must decide on an unavailable value (a condition, an effect's argument) it cannot go on: a
 * query then answers unavailable, and an action stops there.
 */
public final class Evaluator {
    /** The run took more steps than its budget. */
    public static final class BudgetExceeded extends RuntimeException {
        BudgetExceeded(int budget) {
            super("The run took more than its budget of " + budget + " steps");
        }
    }

    /** The run met an unavailable value it had to decide on. */
    private static final class Undecided extends RuntimeException {
        Undecided() {
            super(null, null, false, false);
        }
    }

    private final Program program;
    private final Observations observations;
    private final int budget;
    private int steps;

    private Evaluator(Program program, Observations observations, int budget) {
        this.program = program;
        this.observations = observations;
        this.budget = budget;
    }

    /** Asks the query {@code name}: its answer, possibly {@link Value#UNAVAILABLE}. */
    public static Value query(Program program, String name, List<Value> args, Observations observations, int budget) {
        var function = function(program, name, FunctionDef.Kind.QUERY);
        return new Evaluator(program, observations, budget).run(function, args, null);
    }

    /** Runs the action {@code name}, acting through {@code effects}. */
    public static void action(Program program, String name, List<Value> args, Observations observations,
            Effects effects, int budget) {
        var function = function(program, name, FunctionDef.Kind.ACTION);
        try {
            new Evaluator(program, observations, budget).run(function, args, effects);
        } catch (Undecided stopped) {
            // The action could not tell what to do next; what it did so far stands.
        }
    }

    private static FunctionDef function(Program program, String name, FunctionDef.Kind kind) {
        var function = program.function(name).orElseThrow(() -> new IllegalArgumentException("No function " + name));
        if (function.kind() != kind) throw new IllegalArgumentException(name + " is not a " + kind.word());
        return function;
    }

    private void step() {
        if (++steps > budget) throw new BudgetExceeded(budget);
    }

    /** {@code effects} is {@code null} in a query. */
    private Value run(FunctionDef function, List<Value> args, Effects effects) {
        if (function.kind() == FunctionDef.Kind.QUERY) effects = null;
        var scope = new HashMap<String, Value>();
        for (int i = 0; i < function.params().size(); i++) scope.put(function.params().get(i).name(), args.get(i));
        try {
            var result = block(function.body(), scope, effects);
            if (result != null) return result;
            if (function.kind() == FunctionDef.Kind.QUERY) throw new IllegalStateException(function.name() + " ended without an answer");
            return Value.NOTHING;
        } catch (Undecided undecided) {
            if (function.kind() == FunctionDef.Kind.QUERY) return Value.UNAVAILABLE;
            throw undecided;
        }
    }

    /** Runs a block in a scope of its own; the returned value, or {@code null} if it ran to its end. */
    private Value block(List<Stmt> body, Map<String, Value> outer, Effects effects) {
        var scope = new HashMap<>(outer);
        for (var stmt : body) {
            step();
            switch (stmt) {
                case Stmt.Let let -> scope.put(let.name(), expr(let.value(), scope, effects));
                case Stmt.Return ret -> {
                    return ret.value() == null ? Value.NOTHING : expr(ret.value(), scope, effects);
                }
                case Stmt.Do call -> expr(call.call(), scope, effects);
                case Stmt.If branch -> {
                    List<Stmt> chosen = branch.otherwise();
                    for (var b : branch.branches()) {
                        if (decide(expr(b.condition(), scope, effects))) {
                            chosen = b.body();
                            break;
                        }
                    }
                    var result = block(chosen, scope, effects);
                    if (result != null) return result;
                }
            }
        }
        return null;
    }

    private static boolean decide(Value condition) {
        if (condition == Value.UNAVAILABLE) throw new Undecided();
        return ((Value.Bool) condition).value();
    }

    private Value expr(Expr expr, Map<String, Value> scope, Effects effects) {
        step();
        return switch (expr) {
            case Expr.Literal literal -> switch (literal.value()) {
                case Boolean bool -> new Value.Bool(bool);
                case Double number -> new Value.Num(number);
                case String text -> new Value.Text(text);
                case ResourceRef resource -> new Value.Resource(resource);
                default -> throw new IllegalStateException();
            };
            case Expr.Var var -> scope.get(var.name());
            case Expr.Unary unary -> {
                var operand = expr(unary.operand(), scope, effects);
                if (operand == Value.UNAVAILABLE) yield operand;
                yield unary.op() == Expr.Op.NOT ? new Value.Bool(!((Value.Bool) operand).value())
                        : new Value.Num(-((Value.Num) operand).value());
            }
            case Expr.Binary binary -> binary(binary, scope, effects);
            case Expr.Call call -> call(call, scope, effects);
        };
    }

    private Value binary(Expr.Binary binary, Map<String, Value> scope, Effects effects) {
        var left = expr(binary.left(), scope, effects);
        if (binary.op() == Expr.Op.AND || binary.op() == Expr.Op.OR) {
            // The left side settles it when it is false for "and", true for "or".
            var settles = new Value.Bool(binary.op() == Expr.Op.OR);
            if (left.equals(settles)) return left;
            var right = expr(binary.right(), scope, effects);
            if (left != Value.UNAVAILABLE || right.equals(settles)) return right;
            return Value.UNAVAILABLE;
        }
        var right = expr(binary.right(), scope, effects);
        if (left == Value.UNAVAILABLE || right == Value.UNAVAILABLE) return Value.UNAVAILABLE;
        return switch (binary.op()) {
            case EQ -> new Value.Bool(left.equals(right));
            case NE -> new Value.Bool(!left.equals(right));
            default -> {
                double a = ((Value.Num) left).value(), b = ((Value.Num) right).value();
                yield switch (binary.op()) {
                    case LT -> new Value.Bool(a < b);
                    case LE -> new Value.Bool(a <= b);
                    case GT -> new Value.Bool(a > b);
                    case GE -> new Value.Bool(a >= b);
                    case ADD -> new Value.Num(a + b);
                    case SUB -> new Value.Num(a - b);
                    case MUL -> new Value.Num(a * b);
                    case DIV -> b == 0 ? Value.UNAVAILABLE : new Value.Num(a / b);
                    default -> throw new IllegalStateException(binary.op().name());
                };
            }
        };
    }

    private Value call(Expr.Call call, Map<String, Value> scope, Effects effects) {
        var args = call.args().stream().map(arg -> expr(arg, scope, effects)).toList();
        var function = program.function(call.function());
        if (function.isPresent()) {
            if (function.get().kind() == FunctionDef.Kind.ACTION && effects == null) {
                throw new IllegalStateException("A query reached the action " + call.function());
            }
            return run(function.get(), args, effects);
        }
        return switch (call.function()) {
            case "stock" -> args.get(0) == Value.UNAVAILABLE ? Value.UNAVAILABLE
                    : observations.stock(((Value.Resource) args.get(0)).value());
            case "online" -> observations.online();
            case "redstone_in" -> observations.redstoneIn();
            case "available" -> new Value.Bool(args.get(0) != Value.UNAVAILABLE);
            case "all" -> new Value.Filter(Value.Filter.Mode.ALL, java.util.Set.of());
            case "allow", "deny" -> {
                if (args.contains(Value.UNAVAILABLE)) yield Value.UNAVAILABLE;
                var entries = new HashSet<ResourceRef>();
                for (var arg : args) entries.add(((Value.Resource) arg).value());
                yield new Value.Filter(call.function().equals("allow") ? Value.Filter.Mode.ALLOW_LIST
                        : Value.Filter.Mode.DENY_LIST, entries);
            }
            case "redstone" -> {
                act(effects, call, args).redstone((int) Math.max(0, Math.min(15, Math.round(number(args.get(0))))));
                yield Value.NOTHING;
            }
            case "craft" -> {
                var target = act(effects, call, args);
                long amount = Math.round(number(args.get(1)));
                if (amount > 0) target.craft(((Value.Resource) args.get(0)).value(), amount);
                yield Value.NOTHING;
            }
            default -> throw new IllegalStateException("Unknown function " + call.function());
        };
    }

    /** The effects an action call acts through, once its arguments are all known. */
    private static Effects act(Effects effects, Expr.Call call, List<Value> args) {
        if (effects == null) throw new IllegalStateException("A query reached the action " + call.function());
        if (args.contains(Value.UNAVAILABLE)) throw new Undecided();
        return effects;
    }

    private static double number(Value value) {
        return ((Value.Num) value).value();
    }
}
