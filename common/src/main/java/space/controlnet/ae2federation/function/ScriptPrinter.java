package space.controlnet.ae2federation.function;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The script view of a program: one canonical text, so printing what {@link ScriptParser} read gives back what the
 * player wrote when they wrote it in this form. Two spaces indent a block; a comment stands on its own lines above
 * its statement or function; parentheses appear only where the operators would otherwise bind differently.
 */
public final class ScriptPrinter {
    private ScriptPrinter() {
    }

    public static String print(Program program) {
        return program.functions().stream().map(ScriptPrinter::function).collect(Collectors.joining("\n"));
    }

    static String function(FunctionDef function) {
        var out = new StringBuilder();
        comment(out, function.comment(), "");
        out.append(function.kind().word()).append(' ').append(function.name()).append('(')
                .append(function.params().stream().map(p -> p.name() + ": " + p.type().word())
                        .collect(Collectors.joining(", ")))
                .append(')');
        if (function.kind() == FunctionDef.Kind.QUERY) out.append(": ").append(function.result().word());
        out.append('\n');
        block(out, function.body(), "  ");
        return out.append("end\n").toString();
    }

    /** One statement's text, nested blocks included, without its comment: what a player sees it as. */
    public static String statement(Stmt stmt) {
        var out = new StringBuilder();
        statement(out, stmt, "", false);
        return out.toString();
    }

    private static void comment(StringBuilder out, String comment, String indent) {
        if (comment.isEmpty()) return;
        for (var line : comment.split("\n", -1)) out.append(indent).append("-- ").append(line).append('\n');
    }

    private static void block(StringBuilder out, List<Stmt> body, String indent) {
        for (var stmt : body) statement(out, stmt, indent, true);
    }

    private static void statement(StringBuilder out, Stmt stmt, String indent, boolean comments) {
        if (comments) comment(out, stmt.comment(), indent);
        switch (stmt) {
            case Stmt.Let let -> out.append(indent).append("local ").append(let.name()).append(" = ")
                    .append(expr(let.value())).append('\n');
            case Stmt.Return ret -> out.append(indent).append("return")
                    .append(ret.value() == null ? "" : " " + expr(ret.value())).append('\n');
            case Stmt.Do call -> out.append(indent).append(expr(call.call())).append('\n');
            case Stmt.If branch -> {
                for (int i = 0; i < branch.branches().size(); i++) {
                    var b = branch.branches().get(i);
                    out.append(indent).append(i == 0 ? "if " : "elseif ").append(expr(b.condition())).append(" then\n");
                    block(out, b.body(), indent + "  ");
                }
                if (!branch.otherwise().isEmpty()) {
                    out.append(indent).append("else\n");
                    block(out, branch.otherwise(), indent + "  ");
                }
                out.append(indent).append("end\n");
            }
        }
    }

    public static String expr(Expr expr) {
        return switch (expr) {
            case Expr.Literal literal -> literal(literal);
            case Expr.Var var -> var.name();
            case Expr.Call call -> call.function() + "("
                    + call.args().stream().map(ScriptPrinter::expr).collect(Collectors.joining(", ")) + ")";
            case Expr.Unary unary -> {
                var operand = expr(unary.operand());
                // "--" would start a comment, and "-5" would read back as the literal -5.
                boolean wrap = precedence(unary.operand()) < unary.op().precedence()
                        || unary.op() == Expr.Op.NEG && (unary.operand() instanceof Expr.Literal
                                || unary.operand() instanceof Expr.Unary u && u.op() == Expr.Op.NEG);
                yield (unary.op() == Expr.Op.NOT ? "not " : "-") + (wrap ? "(" + operand + ")" : operand);
            }
            case Expr.Binary binary -> {
                int p = binary.op().precedence();
                var left = expr(binary.left());
                var right = expr(binary.right());
                // Left-associative: a right operand of the same strength needs parentheses, a left one does not.
                if (precedence(binary.left()) < p) left = "(" + left + ")";
                if (precedence(binary.right()) <= p) right = "(" + right + ")";
                yield left + " " + binary.op().symbol() + " " + right;
            }
        };
    }

    private static int precedence(Expr expr) {
        return switch (expr) {
            case Expr.Binary binary -> binary.op().precedence();
            case Expr.Unary unary -> unary.op().precedence();
            default -> Integer.MAX_VALUE;
        };
    }

    private static String literal(Expr.Literal literal) {
        return switch (literal.type()) {
            case BOOL -> literal.value().toString();
            case NUMBER -> BigDecimal.valueOf((Double) literal.value()).stripTrailingZeros().toPlainString();
            case TEXT -> '"' + ((String) literal.value()).replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n") + '"';
            case RESOURCE -> {
                var resource = (ResourceRef) literal.value();
                yield "@" + (resource.type().equals(ResourceRef.ITEM) ? "" : "[" + resource.type() + "]") + resource.id();
            }
            case FILTER, NOTHING -> throw new IllegalStateException("No literal of " + literal.type());
        };
    }
}
