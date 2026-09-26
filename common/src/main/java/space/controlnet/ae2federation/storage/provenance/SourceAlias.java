package space.controlnet.ae2federation.storage.provenance;

import java.util.Objects;

public record SourceAlias(SourceAliasId id, ExportSourceId source, int priority) {
    public SourceAlias {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(source, "source");
    }
}
