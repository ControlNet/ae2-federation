package space.controlnet.ae2federation.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.client.DeviceStructureGeometry.Quad;
import space.controlnet.ae2federation.client.DeviceStructureGeometry.Side;

/**
 * A communication interface's formed face: the Router's tile composition, flat, just outside the block's outward face.
 * A lone interface is framed on every edge; a member of a filled 2x2 square only on the square's outside.
 */
final class FacePlateGeometryTest {
    private static final String PREFIX = "block/eco_computation/interface_";

    private static FacePlateGeometry plate() throws Exception {
        var tiles = new HashMap<String, int[]>();
        for (var name : DeviceStructureGeometry.TILES) {
            var path = "assets/ae2federation/textures/block/router/router_" + name + ".png";
            try (var stream = FacePlateGeometryTest.class.getClassLoader().getResourceAsStream(path)) {
                assertTrue(stream != null, path);
                tiles.put(name, ImageIO.read(stream).getRGB(0, 0, 16, 16, null, 0, 16));
            }
        }
        return new FacePlateGeometry(tiles, PREFIX);
    }

    /** The tile drawn at texel (u, v), from the one quad whose UVs cover it. */
    private static String tileAt(List<Quad> quads, int u, int v) {
        String found = null;
        for (var quad : quads) {
            double u0 = 1, v0 = 1, u1 = 0, v1 = 0;
            for (var t : quad.uv()) {
                u0 = Math.min(u0, t[0]);
                u1 = Math.max(u1, t[0]);
                v0 = Math.min(v0, t[1]);
                v1 = Math.max(v1, t[1]);
            }
            double cu = (u + 0.5) / 16, cv = (v + 0.5) / 16;
            if (cu > u0 && cu < u1 && cv > v0 && cv < v1) {
                assertEquals(null, found, "texel " + u + "," + v + " is drawn twice");
                found = quad.texture().substring(PREFIX.length());
            }
        }
        assertTrue(found != null, "texel " + u + "," + v + " is not drawn");
        return found;
    }

    /** Which tile shows at the middle of each edge and at each corner: left, right, top, bottom, then the corners. */
    private static Map<String, String> frame(List<Quad> quads) {
        return Map.of("left", tileAt(quads, 0, 8), "right", tileAt(quads, 15, 8), "top", tileAt(quads, 8, 0),
                "bottom", tileAt(quads, 8, 15), "topLeft", tileAt(quads, 0, 0), "bottomRight", tileAt(quads, 15, 15));
    }

    @Test
    void aLoneInterfaceIsFramedOnEveryEdgeJustOutsideItsFace() throws Exception {
        var quads = plate().build(Side.NORTH, Set.of());
        for (int v = 0; v < 16; v++) for (int u = 0; u < 16; u++) tileAt(quads, u, v);
        assertEquals(Map.of("left", "side_ver", "right", "side_ver", "top", "side_hor", "bottom", "side_hor",
                "topLeft", "corner", "bottomRight", "corner"), frame(quads));
        assertEquals("base", tileAt(quads, 8, 8));
        for (var quad : quads) {
            for (var p : quad.positions()) assertEquals(-FacePlateGeometry.OFFSET / 16, p[2], 1e-9);
            assertTrue(DeviceStructureGeometry.normal(quad.positions())[2] < 0, "faces north");
        }
    }

    /**
     * Seen from outside its north face, the top left member of a square has its neighbours to the west (on its right)
     * and below: framed on its left and top only.
     */
    @Test
    void aSquareMemberIsFramedOnlyOnTheSquaresOutside() throws Exception {
        var quads = plate().build(Side.NORTH, EnumSet.of(Side.WEST, Side.DOWN));
        assertEquals(Map.of("left", "side_ver", "right", "base", "top", "side_hor", "bottom", "base",
                "topLeft", "corner", "bottomRight", "base"), frame(quads));
    }

    /** A neighbour to the south is on the left of an east face and on the right of a west face. */
    @Test
    void facingDecidesWhichEdgeANeighbourJoins() throws Exception {
        var east = plate().build(Side.EAST, EnumSet.of(Side.SOUTH));
        assertEquals("base", tileAt(east, 0, 8));
        assertEquals("side_ver", tileAt(east, 15, 8));
        for (var quad : east) {
            for (var p : quad.positions()) assertEquals(1 + FacePlateGeometry.OFFSET / 16, p[0], 1e-9);
            assertTrue(DeviceStructureGeometry.normal(quad.positions())[0] > 0, "faces east");
        }
        var west = plate().build(Side.WEST, EnumSet.of(Side.SOUTH));
        assertEquals("side_ver", tileAt(west, 0, 8));
        assertEquals("base", tileAt(west, 15, 8));
    }
}
