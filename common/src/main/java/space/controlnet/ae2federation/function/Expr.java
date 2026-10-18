package space.controlnet.ae2federation.function;

import java.util.List;

/** Expressions of the Python subset. Every node has an ID, unique within its program, for the graph view. */
public sealed interface Expr {
    String id();

    record IntLit(String id, long value) implements Expr {
    }

    record FloatLit(String id, double value) implements Expr {
    }

    record StrLit(String id, String value) implements Expr {
    }

    record BoolLit(String id, boolean value) implements Expr {
    }

    record NoneLit(String id) implements Expr {
    }

    record ListLit(String id, List<Expr> items) implements Expr {
        public ListLit {
            items = List.copyOf(items);
        }
    }

    record Name(String id, String name) implements Expr {
    }

    /** {@code name(args)}, or {@code qualifier.name(args)} for a standard namespace or another file. */
    record Call(String id, String qualifier, String name, List<Expr> args) implements Expr {
        public Call {
            args = List.copyOf(args);
        }
    }

    record Unary(String id, UnaryOp op, Expr operand) implements Expr {
    }

    record Binary(String id, BinaryOp op, Expr left, Expr right) implements Expr {
    }
}
