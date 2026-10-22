package space.controlnet.ae2federation.script;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A dry run's values as the script view writes them after the lines of the function that ran: the parameters on its
 * {@code def} line, and on a statement's line the values computed inside it, each as {@code text = value} in the
 * order they were computed, then the statement's own value. The line that stopped the run shows the error instead.
 *
 * <p>Assistant choices: an assignment of a literal shows nothing (its value is written there already); the value
 * an assignment, an {@code if}, a {@code while} or a {@code return} takes is shown once, as the statement's.
 */
public final class DryRunNotes {
    private static final String SEPARATOR = " · ";

    /** The text after a line, and whether it is the error that stopped the run. */
    public record Note(String text, boolean error) {
    }

    private DryRunNotes() {
    }

    /** The function whose definition {@code line} (from 1) is in or follows, if it is a function's. */
    public static Optional<String> functionAt(ParseResult parsed, int line) {
        Definition found = null;
        int foundLine = 0;
        for (var definition : parsed.program().definitions()) {
            var at = parsed.positions().get(definition.id());
            if (at != null && at.line() <= line && at.line() >= foundLine) {
                found = definition;
                foundLine = at.line();
            }
        }
        return found instanceof FunctionDef function ? Optional.of(function.name()) : Optional.empty();
    }

    /**
     * The notes of {@code function}'s lines, by line from 1, from a dry run's values by node; {@code failureNode} and
     * {@code failure} are empty when the run did not stop on an error.
     */
    public static Map<Integer, Note> of(ParseResult parsed, String function, Map<String, String> values,
            String failureNode, String failure) {
        var definition = parsed.program().functions().stream().filter(f -> f.name().equals(function)).findFirst()
                .orElse(null);
        var notes = new HashMap<Integer, Note>();
        if (definition == null) return notes;
        var parts = new HashMap<Integer, List<String>>();
        var defLine = line(parsed, definition.id());
        for (var param : definition.params()) {
            var value = values.get(param.id());
            if (value != null) add(parts, defLine, param.name() + " = " + value);
        }
        definition.body().forEach(statement -> statement(parsed, statement, values, parts));
        parts.forEach((line, texts) -> notes.put(line, new Note(String.join(SEPARATOR, texts), false)));
        if (!failureNode.isEmpty() && parsed.positions().containsKey(failureNode)) {
            notes.put(line(parsed, failureNode), new Note(failure, true));
        }
        return notes;
    }

    private static void statement(ParseResult parsed, Stmt statement, Map<String, String> values,
            Map<Integer, List<String>> parts) {
        // The expression whose value is the statement's own, shown once as the statement's.
        Expr own = switch (statement) {
            case Stmt.Assign s -> s.value();
            case Stmt.If s -> s.condition();
            case Stmt.While s -> s.condition();
            case Stmt.Return s -> s.value();
            case Stmt.CallStmt s -> s.call();
            default -> null;
        };
        var inside = new ArrayList<Expr>();
        switch (statement) {
            case Stmt.Assign s -> inside.add(s.value());
            case Stmt.AugAssign s -> inside.add(s.value());
            case Stmt.CallStmt s -> inside.add(s.call());
            case Stmt.If s -> inside.add(s.condition());
            case Stmt.While s -> inside.add(s.condition());
            case Stmt.For s -> inside.add(s.iterable());
            case Stmt.Return s -> {
                if (s.value() != null) inside.add(s.value());
            }
            default -> {
            }
        }
        for (var expr : inside) computed(parsed, expr, own, values, parts);
        // A suspending call statement runs as its call: the call's value is the statement's.
        var value = values.get(statement instanceof Stmt.CallStmt call ? call.call().id() : statement.id());
        boolean literal = statement instanceof Stmt.Assign assign && literal(assign.value());
        if (value != null && !literal) add(parts, line(parsed, statement.id()), value);
        switch (statement) {
            case Stmt.If s -> {
                s.body().forEach(inner -> statement(parsed, inner, values, parts));
                for (var arm : s.elifs()) {
                    computed(parsed, arm.condition(), null, values, parts);
                    arm.body().forEach(inner -> statement(parsed, inner, values, parts));
                }
                if (s.orElse() != null) s.orElse().body().forEach(inner -> statement(parsed, inner, values, parts));
            }
            case Stmt.While s -> s.body().forEach(inner -> statement(parsed, inner, values, parts));
            case Stmt.For s -> s.body().forEach(inner -> statement(parsed, inner, values, parts));
            default -> {
            }
        }
    }

    /** {@code text = value} for each value computed in {@code expr}, inner ones first; {@code own} is left out. */
    private static void computed(ParseResult parsed, Expr expr, Expr own, Map<String, String> values,
            Map<Integer, List<String>> parts) {
        if (expr == null) return;
        switch (expr) {
            case Expr.ListLit e -> e.items().forEach(item -> computed(parsed, item, own, values, parts));
            case Expr.DictLit e -> {
                for (int i = 0; i < e.keys().size(); i++) {
                    computed(parsed, e.keys().get(i), own, values, parts);
                    computed(parsed, e.values().get(i), own, values, parts);
                }
            }
            case Expr.SetLit e -> e.items().forEach(item -> computed(parsed, item, own, values, parts));
            case Expr.Call e -> {
                computed(parsed, e.receiver(), own, values, parts);
                e.args().forEach(arg -> computed(parsed, arg, own, values, parts));
                e.keywords().forEach(keyword -> computed(parsed, keyword.value(), own, values, parts));
            }
            case Expr.Attribute e -> computed(parsed, e.target(), own, values, parts);
            case Expr.Index e -> {
                computed(parsed, e.target(), own, values, parts);
                computed(parsed, e.index(), own, values, parts);
            }
            case Expr.Slice e -> {
                computed(parsed, e.target(), own, values, parts);
                computed(parsed, e.lower(), own, values, parts);
                computed(parsed, e.upper(), own, values, parts);
                computed(parsed, e.step(), own, values, parts);
            }
            case Expr.Unary e -> computed(parsed, e.operand(), own, values, parts);
            case Expr.Binary e -> {
                computed(parsed, e.left(), own, values, parts);
                computed(parsed, e.right(), own, values, parts);
            }
            default -> {
            }
        }
        var value = values.get(expr.id());
        if (expr != own && value != null && parsed.positions().containsKey(expr.id())) {
            add(parts, line(parsed, expr.id()), ScriptPrinter.expr(expr) + " = " + value);
        }
    }

    private static boolean literal(Expr expr) {
        return expr instanceof Expr.IntLit || expr instanceof Expr.FloatLit || expr instanceof Expr.StrLit
                || expr instanceof Expr.BoolLit || expr instanceof Expr.NoneLit;
    }

    private static int line(ParseResult parsed, String node) {
        return parsed.positions().get(node).line();
    }

    private static void add(Map<Integer, List<String>> parts, int line, String text) {
        parts.computeIfAbsent(line, ignored -> new ArrayList<>()).add(text);
    }
}
