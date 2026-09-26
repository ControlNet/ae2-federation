package space.controlnet.ae2federation.domain;

import java.util.Objects;

public record FederationDomainNodeId(String dimension, long blockPosition) implements Comparable<FederationDomainNodeId> {
    public FederationDomainNodeId {
        Objects.requireNonNull(dimension);
        if (dimension.isBlank()) {
            throw new IllegalArgumentException("Federation Domain node dimension must not be blank");
        }
    }

    @Override
    public int compareTo(FederationDomainNodeId other) {
        var dimensionOrder = dimension.compareTo(other.dimension);
        return dimensionOrder != 0 ? dimensionOrder : Long.compareUnsigned(blockPosition, other.blockPosition);
    }

    @Override
    public String toString() {
        return dimension + "@" + Long.toUnsignedString(blockPosition);
    }
}
