package space.controlnet.ae2federation.domain.port;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import space.controlnet.ae2federation.ae2.NativeAttachment;
import space.controlnet.ae2federation.ae2.NativeAttachmentResolver;

public final class RouterBoundaryTopology {
    private final Map<Direction, NativeAttachment> attachments;
    private final Map<IGrid, List<Direction>> facesByGrid;

    private RouterBoundaryTopology(Map<Direction, NativeAttachment> attachments, Map<IGrid, List<Direction>> facesByGrid) {
        this.attachments = Collections.unmodifiableMap(attachments);
        this.facesByGrid = Collections.unmodifiableMap(facesByGrid);
    }

    public static Optional<RouterBoundaryTopology> resolve(Level level, Map<Direction, BoundaryPort> ports) {
        var attachments = new EnumMap<Direction, NativeAttachment>(Direction.class);
        var boundaryNodes = Collections.newSetFromMap(new IdentityHashMap<IGridNode, Boolean>());
        for (var face : Direction.values()) {
            var port = ports.get(face);
            if (port == null || !boundaryNodes.add(port.node())) {
                return Optional.empty();
            }
            var attachment = NativeAttachmentResolver.resolve(level, port.position(), face, port.node());
            if (attachment.isEmpty()) {
                return Optional.empty();
            }
            attachments.put(face, attachment.orElseThrow());
        }
        for (var attachment : attachments.values()) {
            if (attachment.boundaryNode().getConnections().stream()
                    .map(connection -> connection.getOtherSide(attachment.boundaryNode()))
                    .anyMatch(boundaryNodes::contains)) {
                return Optional.empty();
            }
        }

        var mutableFacesByGrid = new IdentityHashMap<IGrid, java.util.ArrayList<Direction>>();
        attachments.forEach((face, attachment) -> mutableFacesByGrid
                .computeIfAbsent(attachment.grid(), ignored -> new java.util.ArrayList<>()).add(face));
        var facesByGrid = new IdentityHashMap<IGrid, List<Direction>>();
        mutableFacesByGrid.forEach((grid, faces) -> facesByGrid.put(grid, List.copyOf(faces)));
        return Optional.of(new RouterBoundaryTopology(attachments, facesByGrid));
    }

    public Map<Direction, NativeAttachment> attachments() {
        return attachments;
    }

    public Map<IGrid, List<Direction>> facesByGrid() {
        return facesByGrid;
    }

    public boolean permitsNativeJoin(Direction first, Direction second) {
        if (!attachments.containsKey(first) || !attachments.containsKey(second)) {
            throw new IllegalArgumentException("Both Router faces must be attached");
        }
        return false;
    }

    public record BoundaryPort(BlockPos position, IGridNode node) {
    }
}
