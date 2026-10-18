package space.controlnet.ae2federation.function;

/** A problem in the script text, at a line and column (both from 1). */
public record ScriptError(int line, int column, String message) {
    @Override
    public String toString() {
        return "line " + line + ":" + column + ": " + message;
    }
}
