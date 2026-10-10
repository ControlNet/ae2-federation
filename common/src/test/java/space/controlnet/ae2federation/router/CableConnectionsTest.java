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
                Direction.UP, CableVisualConnections.DENSE);
        connections = with(connections, Direction.NORTH, CableVisualConnections.COVERED);
        connections = with(connections, Direction.SOUTH, CableVisualConnections.CABLE);

        assertEquals(CableVisualConnections.DENSE, CableVisualConnections.kind(connections, Direction.EAST));
        assertEquals(CableVisualConnections.DENSE, CableVisualConnections.kind(connections, Direction.UP));
        assertEquals(CableVisualConnections.COVERED, CableVisualConnections.kind(connections, Direction.NORTH));
        assertEquals(CableVisualConnections.CABLE, CableVisualConnections.kind(connections, Direction.SOUTH));
        assertEquals(CableVisualConnections.NONE, CableVisualConnections.kind(connections, Direction.WEST));
        // E/W/U/D/S/N are mask bits 0 to 5.
        assertEquals(1 | 4 | 16 | 32, CableVisualConnections.maskOf(connections));
    }

    @Test
    void onlyTwoOppositeCableConnectionsMakeAStraightTube() {
        int eastWest = with(with(0, Direction.EAST, CableVisualConnections.CABLE),
                Direction.WEST, CableVisualConnections.CABLE);
        assertTrue(CableVisualConnections.straight(eastWest));
        assertTrue(CableVisualConnections.straight(with(with(0, Direction.UP, CableVisualConnections.CABLE),
                Direction.DOWN, CableVisualConnections.CABLE)));
        assertTrue(CableVisualConnections.straight(with(with(0, Direction.SOUTH, CableVisualConnections.CABLE),
                Direction.NORTH, CableVisualConnections.CABLE)));

        assertFalse(CableVisualConnections.straight(0));
        // West and up are bits 1 and 2: adjacent bits, but not one axis.
        assertFalse(CableVisualConnections.straight(with(with(0, Direction.WEST, CableVisualConnections.CABLE),
                Direction.UP, CableVisualConnections.CABLE)));
        assertFalse(CableVisualConnections.straight(with(0, Direction.EAST, CableVisualConnections.CABLE)));
        assertFalse(CableVisualConnections.straight(with(eastWest, Direction.UP, CableVisualConnections.CABLE)));
        // A Router, Provider or Endpoint on one end joins through a narrower dense arm off a core.
        assertFalse(CableVisualConnections.straight(with(with(0, Direction.EAST, CableVisualConnections.CABLE),
                Direction.WEST, CableVisualConnections.DENSE)));
        assertFalse(CableVisualConnections.straight(with(with(0, Direction.EAST, CableVisualConnections.DENSE),
                Direction.WEST, CableVisualConnections.DENSE)));
        // A Federation P2P tunnel on one end gets a covered connection.
        assertFalse(CableVisualConnections.straight(with(with(0, Direction.EAST, CableVisualConnections.CABLE),
                Direction.WEST, CableVisualConnections.COVERED)));
    }

    @Test
    void shapesFollowAe2DenseCableGeometry() {
        assertBounds(CableShapes.shape(0), 2, 2, 2, 14, 14, 14);

        int eastWest = with(with(0, Direction.EAST, CableVisualConnections.CABLE),
                Direction.WEST, CableVisualConnections.CABLE);
        assertBounds(CableShapes.shape(eastWest), 0, 2, 2, 16, 14, 14);

        // An arm to another cable is as wide as the core, so cables join without a step.
        var cable = CableShapes.shape(with(0, Direction.EAST, CableVisualConnections.CABLE));
        assertBounds(cable, 2, 2, 2, 16, 14, 14);
        assertTrue(contains(cable, 15.5, 2.5, 2.5));

        // A dense arm to a Router, Provider or Endpoint is 10 voxels wide beside the 12-voxel core.
        var dense = CableShapes.shape(with(0, Direction.EAST, CableVisualConnections.DENSE));
        assertBounds(dense, 2, 2, 2, 16, 14, 14);
        assertTrue(contains(dense, 15.5, 3.5, 3.5));
        assertFalse(contains(dense, 15.5, 2.5, 2.5));

        // A covered arm to a Federation P2P tunnel is 4 voxels wide and reaches the face.
        var covered = CableShapes.shape(with(0, Direction.EAST, CableVisualConnections.COVERED));
        assertBounds(covered, 2, 2, 2, 16, 14, 14);
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
