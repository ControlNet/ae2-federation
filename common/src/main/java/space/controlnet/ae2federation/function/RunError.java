package space.controlnet.ae2federation.function;

/**
 * A mistake found while a program runs, as Python would raise it: a type such as {@code ZeroDivisionError} and
 * CPython's message. It stops the run; the runner adds the node where it happened. Carries no stack trace.
 */
public final class RunError extends RuntimeException {
    private final String type;

    public RunError(String type, String message) {
        super(message, null, false, false);
        this.type = type;
    }

    public String type() {
        return type;
    }

    /** {@code ZeroDivisionError: division by zero}, as Python prints it. */
    @Override
    public String toString() {
        return type + ": " + getMessage();
    }
}
