package space.controlnet.ae2federation.identity;

import java.util.Objects;
import java.util.UUID;

public record NodeLineage(NetworkId networkId, UUID nodeId, long revision) {
    public NodeLineage {
        Objects.requireNonNull(networkId);
        Objects.requireNonNull(nodeId);
        if (revision < 1) {
            throw new IllegalArgumentException("Lineage revision must be positive");
        }
    }
}
