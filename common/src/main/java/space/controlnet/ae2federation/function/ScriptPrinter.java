package space.controlnet.ae2federation.function;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Prints a {@link Program} as canonical script text: four-space indentation, one blank line between functions, double
 * quotes, parentheses only where precedence needs them, an empty body as {@code pass}. Parsing the output gives the
 * same program, and printing a parsed canonical script gives the same text.
 */
public final class ScriptPrinter {
    private static final String INDENT = "    ";

    private final StringBuilder out = new StringBuilder();

    private ScriptPrinter() {
    }

    public static String print(Program program) {
        var printer = new ScriptPrinter();
        var functions = program.functions();
        for (int i = 0; i < functions.size(); i++) {
            if (i > 0) {
                printer.out.append('\n');
            }
            printer.function(functions.get(i));
        }
        if (!program.endComments().isEmpty()) {
            if (!functions.isEmpty()) {
                printer.out.append('\n');
            }
            printer.comments(program.endComments(), 0);
        }
        return printer.out.toString();
    }

    private void function(FunctionDef function) {
        comments(function.notes().above(), 0);
        out.append("def ").append(function.name()).append('(')
                .append(function.params().stream().map(p -> p.name() + ": " + p.type()).collect(Collectors.joining(", ")))
                .append(") -> ").append(function.result()).append(':');
        lineEnd(function.notes());
        if (function.docstring() != null) {
            out.append(INDENT).append(docstring(function.docstring())).append('\n');
        }
        if (function.docstring() == null || !function.body().isEmpty()) {
            body(function.body(), 1);
        }
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
                out.append(s.target());
                if (s.type() != null) {
                    out.append(": ").append(s.type());
                }
                out.append(" = ").append(expr(s.value()));
            }
            case Stmt.AugAssign s -> out.append(s.target()).append(' ').append(s.op().symbol).append("= ")
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
            case Expr.Call c -> (c.qualifier() == null ? "" : c.qualifier() + ".") + c.name()
                    + c.args().stream().map(ScriptPrinter::expr).collect(Collectors.joining(", ", "(", ")"));
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

    private static int precedence(Expr e) {
        return switch (e) {
            case Expr.Binary b -> b.op().precedence;
            case Expr.Unary u -> u.op().precedence();
            default -> 8;
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
