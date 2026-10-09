package space.controlnet.ae2federation.router;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

import org.junit.jupiter.api.Test;

final class CableConnectionsTest {
    private static int with(int connections, Direction side, int kind) {
        return CableVisualConnections.with(connections, side, kind);
    }

    @Test
    void eachSideKeepsItsOwnKindAndTheMaskFollowsIt() {
        int connections = with(with(0, Direction.EAST, CableVisualConnections.DENSE),
                Direction.UP, CableVisualConnections.COVERED_CAP);
        connections = with(connections, Direction.NORTH, CableVisualConnections.COVERED);

        assertEquals(CableVisualConnections.DENSE, CableVisualConnections.kind(connections, Direction.EAST));
        assertEquals(CableVisualConnections.COVERED_CAP, CableVisualConnections.kind(connections, Direction.UP));
        assertEquals(CableVisualConnections.COVERED, CableVisualConnections.kind(connections, Direction.NORTH));
        assertEquals(CableVisualConnections.NONE, CableVisualConnections.kind(connections, Direction.WEST));
        // E/W/U/D/S/N are mask bits 0 to 5.
        assertEquals(1 | 4 | 32, CableVisualConnections.maskOf(connections));
    }

    @Test
    void onlyTwoOppositeDenseConnectionsMakeAStraightTube() {
        int eastWest = with(with(0, Direction.EAST, CableVisualConnections.DENSE),
                Direction.WEST, CableVisualConnections.DENSE);
        assertTrue(CableVisualConnections.straight(eastWest));
        assertTrue(CableVisualConnections.straight(with(with(0, Direction.UP, CableVisualConnections.DENSE),
                Direction.DOWN, CableVisualConnections.DENSE)));
        assertTrue(CableVisualConnections.straight(with(with(0, Direction.SOUTH, CableVisualConnections.DENSE),
                Direction.NORTH, CableVisualConnections.DENSE)));

        assertFalse(CableVisualConnections.straight(0));
        // West and up are bits 1 and 2: adjacent bits, but not one axis.
        assertFalse(CableVisualConnections.straight(with(with(0, Direction.WEST, CableVisualConnections.DENSE),
                Direction.UP, CableVisualConnections.DENSE)));
        assertFalse(CableVisualConnections.straight(with(0, Direction.EAST, CableVisualConnections.DENSE)));
        assertFalse(CableVisualConnections.straight(with(eastWest, Direction.UP, CableVisualConnections.DENSE)));
        assertFalse(CableVisualConnections.straight(with(with(0, Direction.EAST, CableVisualConnections.DENSE),
                Direction.UP, CableVisualConnections.DENSE)));
        // A machine on one end gets AE2's covered connection, so the line is not one dense tube.
        assertFalse(CableVisualConnections.straight(with(with(0, Direction.EAST, CableVisualConnections.DENSE),
                Direction.WEST, CableVisualConnections.COVERED_CAP)));
    }

    @Test
    void shapesFollowAe2DenseCableGeometry() {
        assertBounds(CableShapes.shape(0), 3, 3, 3, 13, 13, 13);

        int eastWest = with(with(0, Direction.EAST, CableVisualConnections.DENSE),
                Direction.WEST, CableVisualConnections.DENSE);
        assertBounds(CableShapes.shape(eastWest), 0, 3, 3, 16, 13, 13);

        // A dense arm is 8 voxels wide inside the 10-voxel core, so the core still bounds the cross-section.
        assertBounds(CableShapes.shape(with(0, Direction.EAST, CableVisualConnections.DENSE)), 3, 3, 3, 16, 13, 13);
        assertTrue(contains(CableShapes.shape(with(0, Direction.EAST, CableVisualConnections.DENSE)), 15.5, 4.5, 4.5));
        assertFalse(contains(CableShapes.shape(with(0, Direction.EAST, CableVisualConnections.DENSE)), 15.5, 3.5, 3.5));

        // A covered connection to a machine ends in AE2's 6-voxel cap; a covered arm alone is 4 voxels wide.
        var cap = CableShapes.shape(with(0, Direction.EAST, CableVisualConnections.COVERED_CAP));
        assertTrue(contains(cap, 15.5, 5.5, 5.5));
        assertFalse(contains(cap, 15.5, 4.5, 4.5));
        var covered = CableShapes.shape(with(0, Direction.EAST, CableVisualConnections.COVERED));
        assertTrue(contains(covered, 15.5, 6.5, 6.5));
        assertFalse(contains(covered, 15.5, 5.5, 5.5));
    }

    private static boolean contains(net.minecraft.world.phys.shapes.VoxelShape shape, double x, double y, double z) {
        return shape.toAabbs().stream().anyMatch(box -> box.contains(x / 16, y / 16, z / 16));
    }

    private static void assertBounds(net.minecraft.world.phys.shapes.VoxelShape shape, double x1, double y1, double z1,
            double x2, double y2, double z2) {
        assertEquals(new AABB(x1 / 16, y1 / 16, z1 / 16, x2 / 16, y2 / 16, z2 / 16), shape.bounds());
    }
}
