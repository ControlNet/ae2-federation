package space.controlnet.ae2federation.function;

/**
 * One completion candidate: the text to insert, what it is, a short detail (type, signature, display name), its
 * documentation (a function's summary or docstring, else empty), whether it is a library function that acts, and the
 * language key of a library function's summary (else empty), which the client shows in place of {@code doc}.
 */
public record Candidate(String label, Kind kind, String detail, String doc, boolean acts, String docKey) {
    public enum Kind { VARIABLE, FUNCTION, FILE, NAMESPACE, KEYWORD, TYPE, VALUE, METHOD, FIELD }

    public Candidate(String label, Kind kind, String detail) {
        this(label, kind, detail, "", false, "");
    }

    /** A function, method or record constructor, with its signature, summary and whether it acts. */
    static Candidate of(Signature signature, Kind kind) {
        return new Candidate(signature.name(), kind, signature.display(), signature.summary(), signature.effect(),
                StandardLibrary.summaryKey(signature));
    }
}
