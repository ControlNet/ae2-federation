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
        var dimensionOrder = dimension == other.dimension ? 0 : dimension.compareTo(other.dimension);
        return dimensionOrder != 0 ? dimensionOrder : Long.compareUnsigned(blockPosition, other.blockPosition);
    }

    /**
     * Mixes the packed position: the record default hashes {@code BlockPos.asLong()} as {@code high ^ low}, which keeps
     * the low bits nearly constant across one layer of blocks, so a large cable network degrades HashMaps to tree bins.
     */
    @Override
    public int hashCode() {
        var mixed = blockPosition * 0x9E3779B97F4A7C15L;
        return 31 * dimension.hashCode() + (int) (mixed ^ (mixed >>> 32));
    }

    @Override
    public String toString() {
        return dimension + "@" + Long.toUnsignedString(blockPosition);
    }
}
