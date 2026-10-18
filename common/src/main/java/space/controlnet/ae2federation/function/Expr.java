package space.controlnet.ae2federation.function;

import java.util.List;
import java.util.Objects;

/**
 * An expression: a pure value. Every node carries a stable {@code id}, so a graph's layout and a script's comments
 * stay with it while either view is edited ({@link Reconciler}).
 */
public sealed interface Expr {
    String id();

    /** A number, a boolean, a text or a resource written in place. */
    record Literal(String id, ValueType type, Object value) implements Expr {
        public Literal {
            Objects.requireNonNull(id);
            Objects.requireNonNull(type);
            Objects.requireNonNull(value);
            boolean fits = switch (type) {
                case BOOL -> value instanceof Boolean;
                case NUMBER -> value instanceof Double;
                case TEXT -> value instanceof String;
                case RESOURCE -> value instanceof ResourceRef;
                case FILTER, NOTHING -> false;
            };
            if (!fits) throw new IllegalArgumentException("A " + type.word() + " literal cannot hold " + value);
        }
    }

    /** A parameter or a local, by name. */
    record Var(String id, String name) implements Expr {
    }

    record Unary(String id, Op op, Expr operand) implements Expr {
    }

    record Binary(String id, Op op, Expr left, Expr right) implements Expr {
    }

    /** A call of a library function or of another function in the program. */
    record Call(String id, String function, List<Expr> args) implements Expr {
        public Call {
            args = List.copyOf(args);
        }
    }

    /** Operators, with the script's spelling and binding strength (higher binds tighter). */
    enum Op {
        OR("or", 1), AND("and", 2), NOT("not", 3),
        EQ("==", 4), NE("~=", 4), LT("<", 4), LE("<=", 4), GT(">", 4), GE(">=", 4),
        ADD("+", 5), SUB("-", 5), MUL("*", 6), DIV("/", 6), NEG("-", 7);

        private final String symbol;
        private final int precedence;

        Op(String symbol, int precedence) {
            this.symbol = symbol;
            this.precedence = precedence;
        }

        public String symbol() {
            return symbol;
        }

        public int precedence() {
            return precedence;
        }

        public boolean unary() {
            return this == NOT || this == NEG;
        }

        public boolean comparison() {
            return precedence == 4;
        }
    }
}
