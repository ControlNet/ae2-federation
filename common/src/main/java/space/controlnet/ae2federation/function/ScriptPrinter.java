package space.controlnet.ae2federation.function;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Prints a {@link Program} as canonical script text: four-space indentation, one blank line between functions, double
 * quotes, parentheses only where precedence needs them, an empty body as {@code pass}. Parsing the output gives the
 * same program, and printing a parsed canonical script gives the same text.
 */
public final class ScriptPrinter {
    private static final String INDENT = "    ";
    /** Atoms, calls, fields and items bind tighter than any operator. */
    private static final int POSTFIX = 8;

    private final StringBuilder out = new StringBuilder();

    private ScriptPrinter() {
    }

    public static String print(Program program) {
        var printer = new ScriptPrinter();
        var definitions = program.definitions();
        for (int i = 0; i < definitions.size(); i++) {
            if (i > 0) {
                printer.out.append('\n');
            }
            switch (definitions.get(i)) {
                case FunctionDef function -> printer.function(function);
                case ClassDef record -> printer.classDef(record);
            }
        }
        if (!program.endComments().isEmpty()) {
            if (!definitions.isEmpty()) {
                printer.out.append('\n');
            }
            printer.comments(program.endComments(), 0);
        }
        return printer.out.toString();
    }

    private void function(FunctionDef function) {
        comments(function.notes().above(), 0);
        out.append("def ").append(function.name()).append('(')
                .append(function.params().stream().map(ScriptPrinter::param).collect(Collectors.joining(", ")))
                .append(')').append(function.result() == null ? "" : " -> " + function.result()).append(':');
        lineEnd(function.notes());
        if (function.docstring() != null) {
            out.append(INDENT).append(docstring(function.docstring())).append('\n');
        }
        if (function.docstring() == null || !function.body().isEmpty()) {
            body(function.body(), 1);
        }
    }

    private void classDef(ClassDef record) {
        comments(record.notes().above(), 0);
        out.append("class ").append(record.name()).append(':');
        lineEnd(record.notes());
        if (record.docstring() != null) {
            out.append(INDENT).append(docstring(record.docstring())).append('\n');
        } else if (record.fields().isEmpty()) {
            out.append(INDENT).append("pass\n");
        }
        for (var field : record.fields()) {
            comments(field.notes().above(), 1);
            indent(1).append(field.name()).append(": ").append(field.type());
            if (field.defaultValue() != null) {
                out.append(" = ").append(expr(field.defaultValue()));
            }
            lineEnd(field.notes());
        }
    }

    private static String param(Param param) {
        var text = param.name() + ": " + param.type();
        return param.defaultValue() == null ? text : text + " = " + expr(param.defaultValue());
    }

    private static String docstring(String text) {
        var escaped = text.replace("\\", "\\\\").replace("\"\"\"", "\\\"\\\"\\\"");
        if (escaped.endsWith("\"")) {
            escaped = escaped.substring(0, escaped.length() - 1) + "\\\"";
        }
        var lines = escaped.split("\n", -1);
        var joined = new StringBuilder(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            joined.append('\n');
            if (!lines[i].isEmpty()) {
                joined.append(INDENT).append(lines[i]);
            }
        }
        return "\"\"\"" + joined + "\"\"\"";
    }

    private void body(List<Stmt> body, int depth) {
        if (body.isEmpty()) {
            indent(depth).append("pass\n");
            return;
        }
        for (var statement : body) {
            statement(statement, depth);
        }
    }

    private void statement(Stmt statement, int depth) {
        comments(statement.notes().above(), depth);
        indent(depth);
        switch (statement) {
            case Stmt.Assign s -> {
                out.append(expr(s.target()));
                if (s.type() != null) {
                    out.append(": ").append(s.type());
                }
                out.append(" = ").append(expr(s.value()));
            }
            case Stmt.AugAssign s -> out.append(expr(s.target())).append(' ').append(s.op().symbol).append("= ")
                    .append(expr(s.value()));
            case Stmt.CallStmt s -> out.append(expr(s.call()));
            case Stmt.If s -> {
                out.append("if ").append(expr(s.condition())).append(':');
                lineEnd(s.notes());
                body(s.body(), depth + 1);
                for (var elif : s.elifs()) {
                    comments(elif.notes().above(), depth);
                    indent(depth).append("elif ").append(expr(elif.condition())).append(':');
                    lineEnd(elif.notes());
                    body(elif.body(), depth + 1);
                }
                if (s.orElse() != null) {
                    comments(s.orElse().notes().above(), depth);
                    indent(depth).append("else:");
                    lineEnd(s.orElse().notes());
                    body(s.orElse().body(), depth + 1);
                }
                return;
            }
            case Stmt.While s -> {
                out.append("while ").append(expr(s.condition())).append(':');
                lineEnd(s.notes());
                body(s.body(), depth + 1);
                return;
            }
            case Stmt.For s -> {
                out.append("for ").append(s.variable()).append(" in ").append(expr(s.iterable())).append(':');
                lineEnd(s.notes());
                body(s.body(), depth + 1);
                return;
            }
            case Stmt.Return s -> out.append(s.value() == null ? "return" : "return " + expr(s.value()));
            case Stmt.Break s -> out.append("break");
            case Stmt.Continue s -> out.append("continue");
            case Stmt.Pass s -> out.append("pass");
        }
        lineEnd(statement.notes());
    }

    private void comments(List<String> comments, int depth) {
        for (var comment : comments) {
            indent(depth).append('#').append(comment).append('\n');
        }
    }

    private void lineEnd(Notes notes) {
        if (notes.end() != null) {
            out.append("  #").append(notes.end());
        }
        out.append('\n');
    }

    private StringBuilder indent(int depth) {
        return out.append(INDENT.repeat(depth));
    }

    // ---- expressions ----

    public static String expr(Expr e) {
        return expr(e, 0);
    }

    /** {@code e}, in parentheses if it binds looser than {@code min}. */
    private static String expr(Expr e, int min) {
        String text = switch (e) {
            case Expr.IntLit l -> Long.toString(l.value());
            case Expr.FloatLit l -> Double.toString(l.value());
            case Expr.StrLit l -> string(l.value());
            case Expr.BoolLit l -> l.value() ? "True" : "False";
            case Expr.NoneLit l -> "None";
            case Expr.ListLit l -> l.items().stream().map(ScriptPrinter::expr).collect(Collectors.joining(", ", "[", "]"));
            case Expr.Name n -> n.name();
            case Expr.Call c -> (c.receiver() == null ? "" : receiver(c.receiver()) + ".") + c.name()
                    + Stream.concat(c.args().stream().map(ScriptPrinter::expr),
                            c.keywords().stream().map(k -> k.name() + "=" + expr(k.value())))
                    .collect(Collectors.joining(", ", "(", ")"));
            case Expr.Attribute a -> receiver(a.target()) + "." + a.name();
            case Expr.Index i -> receiver(i.target()) + "[" + expr(i.index()) + "]";
            case Expr.Slice s -> receiver(s.target()) + "[" + (s.lower() == null ? "" : expr(s.lower())) + ":"
                    + (s.upper() == null ? "" : expr(s.upper())) + (s.step() == null ? "" : ":" + expr(s.step())) + "]";
            case Expr.Unary u -> u.op().symbol + expr(u.operand(), u.op().precedence());
            case Expr.Binary b -> {
                int p = b.op().precedence;
                // Comparisons do not chain in the subset, so a comparison on either side needs parentheses.
                int left = p == BinaryOp.COMPARISON ? p + 1 : p;
                yield expr(b.left(), left) + " " + b.op().symbol + " " + expr(b.right(), p + 1);
            }
        };
        return precedence(e) < min ? "(" + text + ")" : text;
    }

    /** What a field, item or method is taken from; an int literal needs parentheses ({@code 1.x} is a float). */
    private static String receiver(Expr e) {
        return e instanceof Expr.IntLit ? "(" + expr(e) + ")" : expr(e, POSTFIX);
    }

    private static int precedence(Expr e) {
        return switch (e) {
            case Expr.Binary b -> b.op().precedence;
            case Expr.Unary u -> u.op().precedence();
            default -> POSTFIX;
        };
    }

    private static String string(String value) {
        var out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\t' -> out.append("\\t");
                case '\r' -> out.append("\\r");
                default -> out.append(c);
            }
        }
        return out.append('"').toString();
    }
}
