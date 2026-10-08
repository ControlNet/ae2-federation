package space.controlnet.ae2federation.domain;

import java.util.Objects;

/**
 * One Federation node: a block in a dimension or, when one block holds several nodes (cable bus parts), one {@code part}
 * of it. A block that is one node has an empty part.
 */
public record FederationDomainNodeId(String dimension, long blockPosition, String part)
        implements Comparable<FederationDomainNodeId> {
    public FederationDomainNodeId {
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(part);
        if (dimension.isBlank()) {
            throw new IllegalArgumentException("Federation Domain node dimension must not be blank");
        }
    }

    public FederationDomainNodeId(String dimension, long blockPosition) {
        this(dimension, blockPosition, "");
    }

    @Override
    public int compareTo(FederationDomainNodeId other) {
        var dimensionOrder = dimension == other.dimension ? 0 : dimension.compareTo(other.dimension);
        if (dimensionOrder != 0) return dimensionOrder;
        var positionOrder = Long.compareUnsigned(blockPosition, other.blockPosition);
        return positionOrder != 0 ? positionOrder : part.compareTo(other.part);
    }

    /**
     * Mixes the packed position: the record default hashes {@code BlockPos.asLong()} as {@code high ^ low}, which keeps
     * the low bits nearly constant across one layer of blocks, so a large cable network degrades HashMaps to tree bins.
     */
    @Override
    public int hashCode() {
        var mixed = blockPosition * 0x9E3779B97F4A7C15L;
        return 31 * dimension.hashCode() + (int) (mixed ^ (mixed >>> 32)) + 961 * part.hashCode();
    }

    @Override
    public String toString() {
        var block = dimension + "@" + Long.toUnsignedString(blockPosition);
        return part.isEmpty() ? block : block + "#" + part;
    }
}
