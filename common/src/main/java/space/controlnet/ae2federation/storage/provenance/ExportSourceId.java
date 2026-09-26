package space.controlnet.ae2federation.storage.provenance;

import java.util.Objects;

public record ExportSourceId(SourceAliasId registration) {
    public ExportSourceId {
        Objects.requireNonNull(registration, "registration");
    }

    @Override
    public String toString() {
        return registration.toString();
    }
}
