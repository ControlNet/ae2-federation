package space.controlnet.ae2federation.storage.subscription;

import java.util.Objects;
import space.controlnet.ae2federation.storage.provenance.ExportSourceId;
import space.controlnet.ae2federation.storage.provenance.SourceGeneration;

public record SourceSubscriptionKey(ExportSourceId sourceId, SourceGeneration generation) {
    public SourceSubscriptionKey {
        Objects.requireNonNull(sourceId);
        Objects.requireNonNull(generation);
    }
}
