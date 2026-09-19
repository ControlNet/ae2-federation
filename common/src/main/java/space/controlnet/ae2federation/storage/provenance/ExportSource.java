package space.controlnet.ae2federation.storage.provenance;

import appeng.api.storage.MEStorage;
import java.util.List;
import java.util.Objects;

public record ExportSource(ExportSourceId id, OriginNetworkId origin, SourceGeneration generation,
        MEStorage storage, int priority, List<SourceAlias> aliases) {
    public ExportSource {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(storage, "storage");
        aliases = List.copyOf(aliases);
        if (aliases.isEmpty()) {
            throw new IllegalArgumentException("Export source requires callback-owned aliases");
        }
    }
}
