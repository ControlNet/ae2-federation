package space.controlnet.ae2federation.function;

import java.util.Arrays;
import java.util.Optional;

/** Python's binary operators in the subset, with Python's precedence (higher binds tighter). */
public enum BinaryOp {
    OR("or", 1),
    AND("and", 2),
    EQ("==", 4),
    NE("!=", 4),
    LT("<", 4),
    LE("<=", 4),
    GT(">", 4),
    GE(">=", 4),
    IN("in", 4),
    NOT_IN("not in", 4),
    ADD("+", 5),
    SUB("-", 5),
    MUL("*", 6),
    DIV("/", 6),
    FLOOR_DIV("//", 6),
    MOD("%", 6);

    public static final int COMPARISON = 4;

    public final String symbol;
    public final int precedence;

    BinaryOp(String symbol, int precedence) {
        this.symbol = symbol;
        this.precedence = precedence;
    }

    /** Arithmetic operators also have an augmented assignment form ({@code +=}). */
    public boolean augmentable() {
        return precedence >= 5;
    }

    public static Optional<BinaryOp> ofSymbol(String symbol) {
        return Arrays.stream(values()).filter(op -> op.symbol.equals(symbol)).findFirst();
    }
}
