package space.controlnet.ae2federation.script;

public enum UnaryOp {
    NOT("not "),
    NEG("-");

    public final String symbol;

    UnaryOp(String symbol) {
        this.symbol = symbol;
    }

    /** Python's binding: {@code not} is looser than comparisons, {@code -} tighter than {@code *}. */
    public int precedence() {
        return this == NOT ? 3 : 7;
    }
}
