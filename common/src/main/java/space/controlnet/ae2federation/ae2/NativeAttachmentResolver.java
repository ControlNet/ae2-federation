package space.controlnet.ae2federation.ae2;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

public final class NativeAttachmentResolver {
    private NativeAttachmentResolver() {
    }

    public static Optional<NativeAttachment> resolve(Level level, BlockPos boundaryPosition, Direction face,
            IGridNode boundaryNode) {
        var connections = boundaryNode.getInWorldConnections();
        if (connections.size() != 1 || !connections.containsKey(face) || boundaryNode.getConnections().size() != 1) {
            return Optional.empty();
        }

        var neighborPosition = boundaryPosition.relative(face);
        var exposedNeighbor = GridHelper.getExposedNode(level, neighborPosition, face.getOpposite());
        var connectedNeighbor = connections.get(face).getOtherSide(boundaryNode);
        if (exposedNeighbor == null || exposedNeighbor != connectedNeighbor
                || boundaryNode.getGrid() != connectedNeighbor.getGrid()) {
            return Optional.empty();
        }
        return Optional.of(new NativeAttachment(face, boundaryNode, connectedNeighbor, boundaryNode.getGrid()));
    }
}
