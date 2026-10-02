package space.controlnet.ae2federation.storage.provenance;

import java.util.Objects;

/** One mounted handle of a provider network that is not shared, and why its identity could not be proven. */
public record SkippedSource(SourceAliasId alias, ProvenanceDiagnostic diagnostic) {
    public SkippedSource {
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(diagnostic, "diagnostic");
    }
}
