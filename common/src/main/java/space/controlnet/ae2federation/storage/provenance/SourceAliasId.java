package space.controlnet.ae2federation.storage.provenance;

import java.util.Objects;
import java.util.UUID;

public record SourceAliasId(UUID registrationNodeId, int callbackIndex) implements Comparable<SourceAliasId> {
    public SourceAliasId {
        Objects.requireNonNull(registrationNodeId, "registrationNodeId");
        if (callbackIndex < 0) {
            throw new IllegalArgumentException("Callback index must not be negative");
        }
    }

    @Override
    public int compareTo(SourceAliasId other) {
        var most = Long.compareUnsigned(registrationNodeId.getMostSignificantBits(),
                other.registrationNodeId.getMostSignificantBits());
        if (most != 0) {
            return most;
        }
        var least = Long.compareUnsigned(registrationNodeId.getLeastSignificantBits(),
                other.registrationNodeId.getLeastSignificantBits());
        return least != 0 ? least : Integer.compare(callbackIndex, other.callbackIndex);
    }

    @Override
    public String toString() {
        return registrationNodeId + ":" + callbackIndex;
    }
}
