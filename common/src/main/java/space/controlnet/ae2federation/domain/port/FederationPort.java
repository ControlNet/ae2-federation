package space.controlnet.ae2federation.domain.port;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * A Federation port on {@code outwardFace} of the block at {@code ownerPosition}. {@code part} names the part of that
 * block that owns it, such as a cable bus part's side, when one block holds several Federation nodes; it is empty for a
 * block that is one node.
 */
public record FederationPort(BlockPos ownerPosition, Direction outwardFace, String part) {
    public FederationPort {
        ownerPosition = ownerPosition.immutable();
        Objects.requireNonNull(part);
    }

    public FederationPort(BlockPos ownerPosition, Direction outwardFace) {
        this(ownerPosition, outwardFace, "");
    }

    public boolean connectsTo(FederationPort other) {
        return ownerPosition.relative(outwardFace).equals(other.ownerPosition())
                && other.ownerPosition().relative(other.outwardFace()).equals(ownerPosition);
    }
}
