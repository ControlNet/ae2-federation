package space.controlnet.ae2federation.router;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.Direction;

import org.junit.jupiter.api.Test;

final class CableCoreFacesTest {
    private static int with(int connections, Direction side, int kind) {
        return CableVisualConnections.with(connections, side, kind);
    }

    private static int cables(Direction... sides) {
        int connections = 0;
        for (var side : sides) connections = with(connections, side, CableVisualConnections.CABLE);
        return connections;
    }

    @Test
    void everyEdgeSetHasOneTextureTurnedOntoIt() {
        for (int edges = 0; edges < 16; edges++) {
            var face = CableCoreFaces.forEdges(edges);
            assertEquals(edges, CableCoreFaces.turned(face.variant().edges(), face.quarterTurns()), "edges " + edges);
            assertEquals(Integer.bitCount(edges), Integer.bitCount(face.variant().edges()), "edges " + edges);
        }
        assertEquals(CableCoreFaces.Variant.OPPOSITE, CableCoreFaces.forEdges(CableCoreFaces.TOP | CableCoreFaces.BOTTOM)
                .variant());
        assertEquals(CableCoreFaces.Variant.ADJACENT, CableCoreFaces.forEdges(CableCoreFaces.BOTTOM | CableCoreFaces.LEFT)
                .variant());
    }

    @Test
    void aFaceOpensTheEdgesTowardCablesInItsTexturePlane() {
        // A tee running east-west with a branch up: the north face opens its top and both sides.
        int tee = cables(Direction.EAST, Direction.WEST, Direction.UP);
        assertEquals(new CableCoreFaces.Face(CableCoreFaces.Variant.THREE, 0), CableCoreFaces.face(tee, Direction.NORTH));
        assertEquals(new CableCoreFaces.Face(CableCoreFaces.Variant.THREE, 0), CableCoreFaces.face(tee, Direction.SOUTH));
        // Its bottom face opens east and west, its texture's right and left edges.
        assertEquals(new CableCoreFaces.Face(CableCoreFaces.Variant.OPPOSITE, 1), CableCoreFaces.face(tee, Direction.DOWN));

        // A corner east and up: south shows the texture's top and right, north (mirrored view) its top and left.
        int corner = cables(Direction.EAST, Direction.UP);
        assertEquals(new CableCoreFaces.Face(CableCoreFaces.Variant.ADJACENT, 0),
                CableCoreFaces.face(corner, Direction.SOUTH));
        assertEquals(new CableCoreFaces.Face(CableCoreFaces.Variant.ADJACENT, 3),
                CableCoreFaces.face(corner, Direction.NORTH));
        // Its top face opens only east, the texture's right edge.
        assertEquals(new CableCoreFaces.Face(CableCoreFaces.Variant.ONE, 1), CableCoreFaces.face(corner, Direction.UP));

        // A flat cross: the top face opens all four edges.
        int cross = cables(Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH);
        assertEquals(new CableCoreFaces.Face(CableCoreFaces.Variant.ALL, 0), CableCoreFaces.face(cross, Direction.UP));
    }

    @Test
    void onlyCablesOpenAnEdge() {
        int machines = with(with(0, Direction.EAST, CableVisualConnections.DENSE), Direction.UP,
                CableVisualConnections.COVERED);
        assertEquals(new CableCoreFaces.Face(CableCoreFaces.Variant.CLOSED, 0),
                CableCoreFaces.face(machines, Direction.SOUTH));
    }
}
