package space.controlnet.ae2federation.function;

import java.util.List;

/** Statements of the Python subset, each with its comments. */
public sealed interface Stmt {
    String id();

    Notes notes();

    /**
     * {@code target = value}, or {@code target: type = value} when {@code type} is not {@code null}. The target is a
     * {@link Expr.Name}, {@link Expr.Attribute}, {@link Expr.Index} or {@link Expr.Slice}.
     */
    record Assign(String id, Notes notes, Expr target, TypeRef type, Expr value) implements Stmt {
    }

    /** {@code target += value} and the other arithmetic forms; targets as for {@link Assign}. */
    record AugAssign(String id, Notes notes, Expr target, BinaryOp op, Expr value) implements Stmt {
    }

    record CallStmt(String id, Notes notes, Expr.Call call) implements Stmt {
    }

    /** {@code if}, its {@code elif} arms in order, and {@code else} ({@code null} if absent). */
    record If(String id, Notes notes, Expr condition, List<Stmt> body, List<Elif> elifs, Else orElse) implements Stmt {
        public If {
            body = List.copyOf(body);
            elifs = List.copyOf(elifs);
        }
    }

    record Elif(Notes notes, Expr condition, List<Stmt> body) {
        public Elif {
            body = List.copyOf(body);
        }
    }

    record Else(Notes notes, List<Stmt> body) {
        public Else {
            body = List.copyOf(body);
        }
    }

    record While(String id, Notes notes, Expr condition, List<Stmt> body) implements Stmt {
        public While {
            body = List.copyOf(body);
        }
    }

    record For(String id, Notes notes, String variable, Expr iterable, List<Stmt> body) implements Stmt {
        public For {
            body = List.copyOf(body);
        }
    }

    /** {@code return} with a value, or bare when {@code value} is {@code null}. */
    record Return(String id, Notes notes, Expr value) implements Stmt {
    }

    record Break(String id, Notes notes) implements Stmt {
    }

    record Continue(String id, Notes notes) implements Stmt {
    }

    record Pass(String id, Notes notes) implements Stmt {
    }
}
