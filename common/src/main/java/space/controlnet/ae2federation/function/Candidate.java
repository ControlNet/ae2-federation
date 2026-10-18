package space.controlnet.ae2federation.function;

/** One completion candidate: the text to insert, what it is, and a short detail (type, signature, display name). */
public record Candidate(String label, Kind kind, String detail) {
    public enum Kind { VARIABLE, FUNCTION, FILE, NAMESPACE, KEYWORD, TYPE, VALUE, METHOD, FIELD }
}
