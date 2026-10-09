package space.controlnet.ae2federation.router;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Federation Cable's outline, with the boxes AE2 draws a dense cable with: a 10-voxel core, 8-voxel dense arms,
 * 4-voxel covered arms with a 6-voxel cap against a machine, and one 10-voxel tube for a straight dense line.
 */
public final class CableShapes {
    private static final VoxelShape[] SHAPES = new VoxelShape[CableVisualConnections.COUNT];

    private CableShapes() {
    }

    /** The outline for {@code connections}, as {@link CableVisualConnections#connections} encodes them. */
    public static VoxelShape shape(int connections) {
        var shape = SHAPES[connections];
        if (shape == null) {
            shape = build(connections);
            SHAPES[connections] = shape;
        }
        return shape;
    }

    private static VoxelShape build(int connections) {
        if (CableVisualConnections.straight(connections)) {
            int first = Integer.numberOfTrailingZeros(CableVisualConnections.maskOf(connections));
            return switch (CableVisualConnections.DIRECTIONS[first].getAxis()) {
                case X -> Block.box(0, 2, 2, 16, 14, 14);
                case Y -> Block.box(2, 0, 2, 14, 16, 14);
                case Z -> Block.box(2, 2, 0, 14, 14, 16);
            };
        }
        var shape = Block.box(2, 2, 2, 14, 14, 14);
        for (var side : CableVisualConnections.DIRECTIONS) {
            switch (CableVisualConnections.kind(connections, side)) {
                case CableVisualConnections.DENSE -> shape = Shapes.or(shape, arm(side, 13, 3, 13));
                case CableVisualConnections.COVERED_CAP -> shape = Shapes.or(shape, arm(side, 12, 4, 12),
                        arm(side, 12, 5, 11));
                case CableVisualConnections.COVERED -> shape = Shapes.or(shape, arm(side, 6, 6, 6));
                default -> {
                }
            }
        }
        return shape.optimize();
    }

    /** A box from {@code start} voxels along {@code side} to that face, {@code low} to {@code high} across. */
    private static VoxelShape arm(Direction side, double start, double low, double high) {
        double[] from = {low, low, low};
        double[] to = {high, high, high};
        int axis = side.getAxis().ordinal();
        boolean positive = side.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        from[axis] = positive ? start : 0;
        to[axis] = positive ? 16 : 16 - start;
        return Block.box(from[0], from[1], from[2], to[0], to[1], to[2]);
    }
}
