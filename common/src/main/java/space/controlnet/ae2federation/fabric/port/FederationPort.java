package space.controlnet.ae2federation.fabric.port;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record FederationPort(BlockPos ownerPosition, Direction outwardFace) {
    public FederationPort {
        ownerPosition = ownerPosition.immutable();
    }

    public boolean connectsTo(FederationPort other) {
        return ownerPosition.relative(outwardFace).equals(other.ownerPosition())
                && other.ownerPosition().relative(other.outwardFace()).equals(ownerPosition);
    }
}
