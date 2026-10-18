package space.controlnet.ae2federation.function;

/**
 * A mistake found while a program runs, as Python would raise it: a type such as {@code ZeroDivisionError} and
 * CPython's message. It stops the run; the runner adds the node where it happened. Carries no stack trace.
 */
public final class RunError extends RuntimeException {
    private final String type;
    private String node;

    public RunError(String type, String message) {
        super(message, null, false, false);
        this.type = type;
    }

    public String type() {
        return type;
    }

    /** The innermost program node where the error was raised; {@code null} until the evaluator or runner sets it. */
    public String node() {
        return node;
    }

    /** Sets the node where the error was raised, unless an inner one was set first. */
    RunError at(String node) {
        if (this.node == null) {
            this.node = node;
        }
        return this;
    }

    /** {@code ZeroDivisionError: division by zero}, as Python prints it. */
    @Override
    public String toString() {
        return type + ": " + getMessage();
    }
}
