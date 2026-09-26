package space.controlnet.ae2federation.ae2.storage;

public final class StorageProvenanceException extends IllegalStateException {
    private final Diagnostic diagnostic;

    public StorageProvenanceException(Diagnostic diagnostic, String message) {
        super(message);
        this.diagnostic = diagnostic;
    }

    public Diagnostic diagnostic() {
        return diagnostic;
    }

    public enum Diagnostic {
        COMPLETE_AGGREGATE_MOUNT,
        OPAQUE_ALIAS,
        INVALID_ALIAS_TARGET,
        UNKNOWN_PROVIDER
    }
}
