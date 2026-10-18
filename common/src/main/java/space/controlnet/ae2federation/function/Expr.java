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

    /**
     * {@code name(args)} when {@code receiver} is {@code null}, else {@code receiver.name(args)}: a function of a
     * standard namespace or another file ({@code ae2.craft}), or a method ({@code xs.append}). Which one is the
     * checker's concern. Keyword arguments follow the positional ones, as Python requires.
     */
    record Call(String id, Expr receiver, String name, List<Expr> args, List<Keyword> keywords) implements Expr {
        public Call {
            args = List.copyOf(args);
            keywords = List.copyOf(keywords);
        }
    }

    /** {@code name=value} in a call. */
    record Keyword(String name, Expr value) {
    }

    /** {@code target.name}: a field. */
    record Attribute(String id, Expr target, String name) implements Expr {
    }

    /** {@code target[index]}. */
    record Index(String id, Expr target, Expr index) implements Expr {
    }

    /** {@code target[lower:upper:step]}; each part is {@code null} when left out. */
    record Slice(String id, Expr target, Expr lower, Expr upper, Expr step) implements Expr {
    }

    record Unary(String id, UnaryOp op, Expr operand) implements Expr {
    }

    record Binary(String id, BinaryOp op, Expr left, Expr right) implements Expr {
    }
}
