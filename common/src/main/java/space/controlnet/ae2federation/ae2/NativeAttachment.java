package space.controlnet.ae2federation.ae2;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import net.minecraft.core.Direction;

public record NativeAttachment(Direction face, IGridNode boundaryNode, IGridNode neighborNode, IGrid grid) {
    public NativeAttachment {
        if (boundaryNode == neighborNode || boundaryNode.getGrid() != grid || neighborNode.getGrid() != grid) {
            throw new IllegalArgumentException("Native attachment must be one connected AE2 Grid edge");
        }
    }
}
