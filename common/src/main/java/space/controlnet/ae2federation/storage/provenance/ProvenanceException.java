package space.controlnet.ae2federation.storage.provenance;

import java.util.Objects;

public final class ProvenanceException extends IllegalStateException {
    private final ProvenanceDiagnostic diagnostic;

    public ProvenanceException(ProvenanceDiagnostic diagnostic, String message) {
        super(message);
        this.diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
    }

    public ProvenanceDiagnostic diagnostic() {
        return diagnostic;
    }
}
