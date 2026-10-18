package space.controlnet.ae2federation.function;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/** Visits or rewrites every node of a program: functions, parameters, statements, branches and expressions. */
public final class Walk {
    private Walk() {
    }

    /** Calls {@code visitor} with each node's ID and a label: the script text of a statement or expression. */
    public static void nodes(Program program, BiConsumer<String, String> visitor) {
        for (var function : program.functions()) {
            visitor.accept(function.id(), "function " + function.name());
            for (var param : function.params()) visitor.accept(param.id(), "param " + param.name());
            block(function.body(), visitor);
        }
    }

    private static void block(List<Stmt> body, BiConsumer<String, String> visitor) {
        for (var stmt : body) {
            visitor.accept(stmt.id(), ScriptPrinter.statement(stmt));
            switch (stmt) {
                case Stmt.Let let -> expr(let.value(), visitor);
                case Stmt.Return ret -> {
                    if (ret.value() != null) expr(ret.value(), visitor);
                }
                case Stmt.Do call -> expr(call.call(), visitor);
                case Stmt.If branch -> {
                    for (var b : branch.branches()) {
                        visitor.accept(b.id(), "branch " + ScriptPrinter.expr(b.condition()));
                        expr(b.condition(), visitor);
                        block(b.body(), visitor);
                    }
                    block(branch.otherwise(), visitor);
                }
            }
        }
    }

    private static void expr(Expr expr, BiConsumer<String, String> visitor) {
        visitor.accept(expr.id(), ScriptPrinter.expr(expr));
        for (var child : children(expr)) expr(child, visitor);
    }

    /** Every call in {@code function}'s body, in script order, one per call site. */
    public static List<Expr.Call> calls(FunctionDef function) {
        var calls = new ArrayList<Expr.Call>();
        exprs(function.body(), expr -> {
            if (expr instanceof Expr.Call call) calls.add(call);
        });
        return calls;
    }

    /** Every expression node in {@code body}, outer before inner. */
    public static void exprs(List<Stmt> body, Consumer<Expr> visitor) {
        for (var stmt : body) {
            switch (stmt) {
                case Stmt.Let let -> exprs(let.value(), visitor);
                case Stmt.Return ret -> {
                    if (ret.value() != null) exprs(ret.value(), visitor);
                }
                case Stmt.Do call -> exprs(call.call(), visitor);
                case Stmt.If branch -> {
                    for (var b : branch.branches()) {
                        exprs(b.condition(), visitor);
                        exprs(b.body(), visitor);
                    }
                    exprs(branch.otherwise(), visitor);
                }
            }
        }
    }

    private static void exprs(Expr expr, Consumer<Expr> visitor) {
        visitor.accept(expr);
        for (var child : children(expr)) exprs(child, visitor);
    }

    public static List<Expr> children(Expr expr) {
        return switch (expr) {
            case Expr.Unary unary -> List.of(unary.operand());
            case Expr.Binary binary -> List.of(binary.left(), binary.right());
            case Expr.Call call -> call.args();
            case Expr.Literal literal -> List.of();
            case Expr.Var var -> List.of();
        };
    }

    /** The same program with every ID passed through {@code ids}. */
    public static Program rewrite(Program program, UnaryOperator<String> ids) {
        return new Program(program.functions().stream().map(f -> new FunctionDef(ids.apply(f.id()), f.comment(),
                f.kind(), f.name(), f.params().stream()
                        .map(p -> new FunctionDef.Param(ids.apply(p.id()), p.name(), p.type())).toList(),
                f.result(), block(f.body(), ids))).toList());
    }

    private static List<Stmt> block(List<Stmt> body, UnaryOperator<String> ids) {
        return body.stream().map(stmt -> statement(stmt, ids)).toList();
    }

    private static Stmt statement(Stmt stmt, UnaryOperator<String> ids) {
        return switch (stmt) {
            case Stmt.Let let -> new Stmt.Let(ids.apply(let.id()), let.comment(), let.name(), expr(let.value(), ids));
            case Stmt.Return ret -> new Stmt.Return(ids.apply(ret.id()), ret.comment(),
                    ret.value() == null ? null : expr(ret.value(), ids));
            case Stmt.Do call -> new Stmt.Do(ids.apply(call.id()), call.comment(), (Expr.Call) expr(call.call(), ids));
            case Stmt.If branch -> new Stmt.If(ids.apply(branch.id()), branch.comment(), branch.branches().stream()
                    .map(b -> new Stmt.Branch(ids.apply(b.id()), expr(b.condition(), ids), block(b.body(), ids)))
                    .toList(), block(branch.otherwise(), ids));
        };
    }

    public static Expr expr(Expr expr, UnaryOperator<String> ids) {
        return switch (expr) {
            case Expr.Literal literal -> new Expr.Literal(ids.apply(literal.id()), literal.type(), literal.value());
            case Expr.Var var -> new Expr.Var(ids.apply(var.id()), var.name());
            case Expr.Unary unary -> new Expr.Unary(ids.apply(unary.id()), unary.op(), expr(unary.operand(), ids));
            case Expr.Binary binary -> new Expr.Binary(ids.apply(binary.id()), binary.op(), expr(binary.left(), ids),
                    expr(binary.right(), ids));
            case Expr.Call call -> new Expr.Call(ids.apply(call.id()), call.function(),
                    call.args().stream().map(arg -> expr(arg, ids)).toList());
        };
    }
}
