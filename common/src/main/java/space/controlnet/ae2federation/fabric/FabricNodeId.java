package space.controlnet.ae2federation.fabric;

import java.util.Objects;

public record FabricNodeId(String dimension, long blockPosition) implements Comparable<FabricNodeId> {
    public FabricNodeId {
        Objects.requireNonNull(dimension);
        if (dimension.isBlank()) {
            throw new IllegalArgumentException("Fabric node dimension must not be blank");
        }
    }

    @Override
    public int compareTo(FabricNodeId other) {
        var dimensionOrder = dimension.compareTo(other.dimension);
        return dimensionOrder != 0 ? dimensionOrder : Long.compareUnsigned(blockPosition, other.blockPosition);
    }

    @Override
    public String toString() {
        return dimension + "@" + Long.toUnsignedString(blockPosition);
    }
}
