package space.controlnet.ae2federation.router;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import space.controlnet.ae2federation.bundle.BundleLook;

/**
 * A Federation Cable's outline, with the boxes {@code FederationCableBuilder} draws: a 12-voxel core, 12-voxel arms
 * to other cables, 10-voxel dense arms, 4-voxel covered arms, and one 12-voxel tube for a straight line of cables. A
 * bundled cell's outline is its part of the bundle's shell, as {@code BundleCableBuilder} draws it.
 */
public final class CableShapes {
    private static final VoxelShape[] SHAPES = new VoxelShape[CableVisualConnections.COUNT];
    private static final Map<BundleLook, VoxelShape> BUNDLES = new ConcurrentHashMap<>();

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

    /**
     * A bundled cell's outline: its part of the shell, 2 voxels inside the bundle's outer boundary and its closed ends,
     * a branch's opening out to the cell's face, and a dense arm to each device it reaches.
     */
    public static VoxelShape bundle(BundleLook look) {
        return BUNDLES.computeIfAbsent(look, CableShapes::buildBundle);
    }

    private static VoxelShape buildBundle(BundleLook look) {
        var shape = Shapes.box(shellLow(look, Direction.Axis.X) / 16, shellLow(look, Direction.Axis.Y) / 16,
                shellLow(look, Direction.Axis.Z) / 16, shellHigh(look, Direction.Axis.X) / 16,
                shellHigh(look, Direction.Axis.Y) / 16, shellHigh(look, Direction.Axis.Z) / 16);
        for (var side : Direction.values()) {
            if (look.dense(side)) {
                var arm = denseArm(look, side);
                shape = Shapes.or(shape, Block.box(arm.minX, arm.minY, arm.minZ, arm.maxX, arm.maxY, arm.maxZ));
            }
            if (side.getAxis() == look.axis() || !look.join(side)) continue;
            // The opening onto a branch reaches the cell's face, as far along as the branch's own shell.
            double[] from = new double[3];
            double[] to = new double[3];
            for (var axis : Direction.Axis.values()) {
                from[axis.ordinal()] = shellLow(look, axis);
                to[axis.ordinal()] = shellHigh(look, axis);
            }
            int across = side.getAxis().ordinal();
            if (side.getAxisDirection() == Direction.AxisDirection.POSITIVE) to[across] = 16;
            else from[across] = 0;
            int along = look.axis().ordinal();
            from[along] = look.joinsBefore(side) ? 0 : 2;
            to[along] = look.joinsAfter(side) ? 16 : 14;
            shape = Shapes.or(shape, Block.box(from[0], from[1], from[2], to[0], to[1], to[2]));
        }
        return shape.optimize();
    }

    /** Where the cell's part of the shell starts along {@code axis}, in voxels. */
    public static double shellLow(BundleLook look, Direction.Axis axis) {
        var side = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
        return axis == look.axis() ? look.open(side) ? 0 : 2 : look.edge(side) ? 2 : 0;
    }

    /**
     * A bundled cell's dense arm toward {@code side}, in voxels, from the shell to the cell's face. An ordinary bundle's
     * cell has its own 10-voxel arm, as each of its beams has. A 2x2 or 3x3 bundle is one thick cable, so its cells
     * share one arm across the whole contact, 1 voxel inside the shell's outer edges as a cable's arm is inside its
     * core. A side contact is one layer, so there the arm keeps 3 voxels from the layer's ends.
     */
    public static AABB denseArm(BundleLook look, Direction side) {
        double[] from = new double[3];
        double[] to = new double[3];
        for (var axis : Direction.Axis.values()) {
            boolean shared = look.thick() && axis != look.axis();
            from[axis.ordinal()] = shared && !look.edge(Direction.fromAxisAndDirection(axis,
                    Direction.AxisDirection.NEGATIVE)) ? 0 : 3;
            to[axis.ordinal()] = shared && !look.edge(Direction.fromAxisAndDirection(axis,
                    Direction.AxisDirection.POSITIVE)) ? 16 : 13;
        }
        int along = side.getAxis().ordinal();
        boolean positive = side.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        from[along] = positive ? 14 : 0;
        to[along] = positive ? 16 : 2;
        return new AABB(from[0], from[1], from[2], to[0], to[1], to[2]);
    }

    /** Where the cell's part of the shell ends along {@code axis}, in voxels. */
    public static double shellHigh(BundleLook look, Direction.Axis axis) {
        var side = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        return axis == look.axis() ? look.open(side) ? 16 : 14 : look.edge(side) ? 14 : 16;
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
                case CableVisualConnections.CABLE -> shape = Shapes.or(shape, arm(side, 14, 2, 14));
                case CableVisualConnections.DENSE -> shape = Shapes.or(shape, arm(side, 14, 3, 13));
                case CableVisualConnections.COVERED -> shape = Shapes.or(shape, arm(side, 14, 6, 10));
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
